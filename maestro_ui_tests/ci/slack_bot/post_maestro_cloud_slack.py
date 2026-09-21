#!/usr/bin/env python3
"""Post Android Maestro Cloud results to Slack.

Parses the JUnit report written by `maestro cloud --format junit` (see
tools/ci/run-maestro-cloud.sh) and posts:

- one root message: per-flow ✅/❌ list, apk source, console + workflow links;
- one thread reply per failed flow: failure reason (our shard assertTrue
  labels, e.g. "send shard: failed BTC, GRAM") + a deep link to that flow's
  run page in Maestro Cloud (video, screenshots, logs).

Maestro Cloud has no public artifact API, so screenshots cannot be attached
as images; `cloud.runUrl` per testcase is the closest one-click substitute.

Modes:
  summarize — print a GitHub step summary (markdown) to stdout;
  post      — post to Slack (honors --dry-run).

Stdlib only. Slack client mirrors ios_private's slack_bot (retries/backoff).
"""
from __future__ import annotations

import argparse
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

DEFAULT_CHANNEL = "tk-autotests-status"

# Terminal FlowStatus values from maestro-cli. WARNING = optional-command
# warnings only; our shard wrappers turn real case failures into ERROR via the
# final assertTrue, so WARNING counts as passed here.
PASSED_STATUSES = {"SUCCESS", "WARNING"}


@dataclass
class FlowResult:
    name: str
    status: str
    error: str = ""
    run_url: str = ""

    @property
    def passed(self) -> bool:
        return self.status.upper() in PASSED_STATUSES


@dataclass
class CloudReport:
    flows: list[FlowResult] = field(default_factory=list)
    upload_url: str = ""

    @property
    def failed_flows(self) -> list[FlowResult]:
        return [f for f in self.flows if not f.passed]

    @property
    def ok(self) -> bool:
        return bool(self.flows) and not self.failed_flows


def parse_junit_report(path: Path) -> CloudReport:
    """Parse `maestro cloud --format junit` output.

    Shape (JUnitTestSuiteReporter.kt): <testsuites><testsuite>
    <properties><property name="cloud.url" …/></properties>
    <testcase name status><properties><property name="cloud.runUrl" …/>
    </properties><failure message="…">…</failure></testcase>.
    """
    report = CloudReport()
    root = ET.parse(path).getroot()
    suites = root.findall("testsuite") if root.tag == "testsuites" else [root]
    for suite in suites:
        for prop in suite.findall("./properties/property"):
            if prop.get("name") == "cloud.url" and prop.get("value"):
                report.upload_url = prop.get("value", "")
        for case in suite.findall("testcase"):
            name = case.get("name") or case.get("id") or ""
            if not name:
                continue
            status = (case.get("status") or "").upper() or "ERROR"
            error = ""
            failure = case.find("failure")
            if failure is not None:
                error = (failure.get("message") or failure.text or "").strip()
            run_url = ""
            for prop in case.findall("./properties/property"):
                if prop.get("name") == "cloud.runUrl" and prop.get("value"):
                    run_url = prop.get("value", "")
            report.flows.append(
                FlowResult(name=name, status=status, error=error, run_url=run_url)
            )
    return report


def load_report(path: Path | None) -> CloudReport | None:
    if path is None or not path.is_file():
        return None
    try:
        return parse_junit_report(path)
    except ET.ParseError as exc:
        print(f"slack: cannot parse junit report {path}: {exc}", file=sys.stderr)
        return None


def _status_emoji(passed: bool) -> str:
    return "✅" if passed else "❌"


def _flow_line(flow: FlowResult) -> str:
    line = f"• `{flow.name}` {_status_emoji(flow.passed)}"
    if flow.status == "WARNING":
        line += " (warnings)"
    elif not flow.passed and flow.status not in ("ERROR", "FAILURE"):
        line += f" ({flow.status})"
    return line


def build_main_text(
    *,
    report: CloudReport | None,
    run_url: str,
    console_url: str,
    apk_source: str,
    platform: str = "Android",
) -> str:
    title = f"*{platform} Maestro UI tests (Cloud)*"
    console = console_url or (report.upload_url if report else "")

    if report is None or not report.flows:
        lines = [f"{_status_emoji(False)} {title} — no flow results (upload failed?)"]
        if apk_source:
            lines.append(f"apk: {apk_source}")
        links = []
        if console:
            links.append(f"<{console}|Maestro Cloud console>")
        links.append(f"<{run_url}|Open workflow run>")
        lines.append(" · ".join(links))
        return "\n".join(lines)

    failed = report.failed_flows
    total = len(report.flows)
    if failed:
        header = f"{_status_emoji(False)} {title} — {len(failed)} / {total} flows failed"
    else:
        header = f"{_status_emoji(True)} {title} — all {total} flows passed"

    lines = [header]
    if apk_source:
        lines.append(f"apk: {apk_source}")
    lines.append("")
    lines.extend(_flow_line(f) for f in report.flows)
    lines.append("")
    links = []
    if console:
        links.append(f"<{console}|Maestro Cloud console (video + logs)>")
    links.append(f"<{run_url}|Open workflow run>")
    lines.append(" · ".join(links))
    return "\n".join(lines)


# Keep failure text readable in a Slack thread; full text is in the console.
MAX_ERROR_CHARS = 1500


def build_thread_text(flow: FlowResult) -> str:
    lines = [f"*Failed flow:* `{flow.name}` — {flow.status}"]
    if flow.error:
        error = flow.error
        if len(error) > MAX_ERROR_CHARS:
            error = error[:MAX_ERROR_CHARS] + " …(truncated)"
        lines.append(f"*Error:* {error}")
    else:
        lines.append("*Error:* _no failure message in the report_")
    if flow.run_url:
        lines.append(f"<{flow.run_url}|Open the run in Maestro Cloud (video, screenshots, logs)>")
    return "\n".join(lines)


def build_github_summary(
    *,
    report: CloudReport | None,
    console_url: str,
    apk_source: str,
) -> str:
    lines = ["### Maestro (Cloud)", ""]
    if apk_source:
        lines.append(f"- apk: {apk_source}")
    console = console_url or (report.upload_url if report else "")
    if console:
        lines.append(f"- console (video + logs): {console}")
    if report is None or not report.flows:
        lines.append("- no flow results (junit report missing or empty)")
        return "\n".join(lines) + "\n"
    failed = report.failed_flows
    lines.append(
        f"- flows: {len(report.flows)} total, "
        f"{len(report.flows) - len(failed)} passed, {len(failed)} failed"
    )
    lines.append("")
    lines.append("| flow | status | error |")
    lines.append("| --- | --- | --- |")
    for flow in report.flows:
        error = flow.error.replace("|", "\\|").replace("\n", " ")
        if len(error) > 200:
            error = error[:200] + "…"
        name = f"[{flow.name}]({flow.run_url})" if flow.run_url else flow.name
        lines.append(f"| {name} | {flow.status} | {error} |")
    return "\n".join(lines) + "\n"


class SlackClient:
    """Minimal chat.postMessage client with retries (as in ios_private)."""

    _RETRYABLE_STATUS = {429, 500, 502, 503, 504}
    _RETRYABLE_SLACK_ERRORS = {
        "ratelimited",
        "service_unavailable",
        "internal_error",
        "fatal_error",
    }

    def __init__(
        self,
        token: str,
        channel: str,
        *,
        dry_run: bool = False,
        max_attempts: int = 4,
        base_backoff: float = 1.5,
    ) -> None:
        self.token = token
        self.channel = channel.lstrip("#")
        self.dry_run = dry_run
        self.max_attempts = max(1, max_attempts)
        self.base_backoff = max(0.1, base_backoff)

    def _sleep_for(self, attempt: int, *, retry_after: float | None = None) -> None:
        if retry_after is not None and retry_after > 0:
            delay = min(retry_after, 60.0)
        else:
            delay = self.base_backoff * (2**attempt) + random.uniform(0.0, 0.4)
        time.sleep(delay)

    def post_message(self, text: str, *, thread_ts: str | None = None) -> str:
        payload: dict[str, object] = {
            "channel": self.channel,
            "text": text,
            "unfurl_links": False,
            "unfurl_media": False,
        }
        if thread_ts:
            payload["thread_ts"] = thread_ts
        if self.dry_run:
            marker = f" (thread {thread_ts})" if thread_ts else ""
            print(f"[dry-run] chat.postMessage → #{self.channel}{marker}\n{text}\n")
            return "dry-run-ts"
        body = self._request(payload)
        return str(body.get("ts", ""))

    def try_post_message(self, text: str, *, thread_ts: str | None = None) -> str | None:
        try:
            return self.post_message(text, thread_ts=thread_ts)
        except Exception as exc:  # noqa: BLE001 — report-only, never fail CI
            print(f"slack: try_post_message swallowed: {exc}", file=sys.stderr)
            return None

    def _request(self, payload: dict) -> dict:
        data = json.dumps(payload).encode("utf-8")
        last_err: Exception | None = None
        for attempt in range(self.max_attempts):
            req = urllib.request.Request(
                "https://slack.com/api/chat.postMessage",
                data=data,
                headers={
                    "Authorization": f"Bearer {self.token}",
                    "Content-Type": "application/json; charset=utf-8",
                },
                method="POST",
            )
            try:
                with urllib.request.urlopen(req, timeout=60) as resp:
                    body = json.loads(resp.read().decode("utf-8"))
            except urllib.error.HTTPError as exc:
                if exc.code in self._RETRYABLE_STATUS and attempt + 1 < self.max_attempts:
                    last_err = exc
                    retry_after = None
                    try:
                        retry_after = float(exc.headers.get("Retry-After", ""))
                    except (TypeError, ValueError):
                        pass
                    self._sleep_for(attempt, retry_after=retry_after)
                    continue
                detail = exc.read().decode("utf-8", errors="replace")
                raise RuntimeError(f"Slack: HTTP {exc.code}: {detail}") from exc
            except (urllib.error.URLError, TimeoutError, OSError) as exc:
                if attempt + 1 < self.max_attempts:
                    last_err = exc
                    self._sleep_for(attempt)
                    continue
                raise RuntimeError(f"Slack: {exc}") from exc

            if body.get("ok"):
                return body
            err = str(body.get("error") or "unknown_error")
            if err in self._RETRYABLE_SLACK_ERRORS and attempt + 1 < self.max_attempts:
                last_err = RuntimeError(f"Slack: {err}")
                self._sleep_for(attempt)
                continue
            raise RuntimeError(f"Slack: {err} (full response: {body})")
        raise RuntimeError(f"Slack: exhausted retries; last error: {last_err}")


def post_to_slack(
    *,
    report: CloudReport | None,
    slack: SlackClient,
    run_url: str,
    console_url: str,
    apk_source: str,
    platform: str,
) -> int:
    main_text = build_main_text(
        report=report,
        run_url=run_url,
        console_url=console_url,
        apk_source=apk_source,
        platform=platform,
    )
    thread_ts = slack.post_message(main_text)
    if report is None:
        return 0
    for flow in report.failed_flows:
        slack.try_post_message(build_thread_text(flow), thread_ts=thread_ts)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["summarize", "post"])
    ap.add_argument("--report", type=Path, default=None, help="maestro cloud junit xml")
    ap.add_argument("--run-url", default="", help="GitHub workflow run URL")
    ap.add_argument("--console-url", default="", help="Maestro Cloud console URL")
    ap.add_argument("--apk-source", default="", help="APK source description")
    ap.add_argument("--platform", default="Android")
    ap.add_argument("--channel", default=os.environ.get("SLACK_CHANNEL", DEFAULT_CHANNEL))
    ap.add_argument("--token", default=os.environ.get("SLACK_BOT_TOKEN", ""))
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    report = load_report(args.report)

    if args.mode == "summarize":
        sys.stdout.write(
            build_github_summary(
                report=report,
                console_url=args.console_url,
                apk_source=args.apk_source,
            )
        )
        return 0

    if not args.token and not args.dry_run:
        print("post_maestro_cloud_slack: set SLACK_BOT_TOKEN or pass --token", file=sys.stderr)
        return 2
    slack = SlackClient(args.token, args.channel, dry_run=args.dry_run)
    return post_to_slack(
        report=report,
        slack=slack,
        run_url=args.run_url,
        console_url=args.console_url,
        apk_source=args.apk_source,
        platform=args.platform,
    )


if __name__ == "__main__":
    raise SystemExit(main())
