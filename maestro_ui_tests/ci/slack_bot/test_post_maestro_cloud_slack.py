import tempfile
import unittest
from pathlib import Path

from post_maestro_cloud_slack import (
    build_github_summary,
    build_main_text,
    build_thread_text,
    parse_junit_report,
)

SAMPLE_JUNIT = """<?xml version='1.0' encoding='UTF-8'?>
<testsuites>
  <testsuite name="Test Suite" device="Pixel-6" tests="3" failures="1" time="3612.4" timestamp="2026-08-31T04:12:35">
    <properties>
      <property name="cloud.uploadId" value="mupload_01m0z5ts4wfpd87cng4dj93r37"/>
      <property name="cloud.url" value="https://app.maestro.dev/project/proj_x/maestro-test/app/app_y/upload/mupload_01m0z5ts4wfpd87cng4dj93r37"/>
    </properties>
    <testcase id="Transactions" name="Transactions" classname="Transactions" time="412.2" status="ERROR">
      <properties>
        <property name="cloud.runId" value="run_tx"/>
        <property name="cloud.runUrl" value="https://app.maestro.dev/project/proj_x/maestro-test/flow/run_tx"/>
      </properties>
      <failure message="Assertion is false: transactions shard: failed send GRAM">Assertion is false</failure>
    </testcase>
    <testcase id="Multichain — send" name="Multichain — send" classname="Multichain — send" time="1502.0" status="SUCCESS">
      <properties>
        <property name="cloud.runId" value="run_send"/>
        <property name="cloud.runUrl" value="https://app.maestro.dev/project/proj_x/maestro-test/flow/run_send"/>
      </properties>
    </testcase>
    <testcase id="Deeplinks" name="Deeplinks" classname="Deeplinks" time="120.0" status="WARNING"/>
  </testsuite>
</testsuites>
"""


def _write_report(content: str) -> Path:
    tmp = tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False)
    tmp.write(content)
    tmp.close()
    return Path(tmp.name)


class ParseJunitReportTests(unittest.TestCase):
    def test_parses_flows_statuses_errors_and_urls(self):
        report = parse_junit_report(_write_report(SAMPLE_JUNIT))
        self.assertEqual(len(report.flows), 3)
        self.assertIn("mupload_01m0z5ts4wfpd87cng4dj93r37", report.upload_url)

        tx = report.flows[0]
        self.assertEqual(tx.name, "Transactions")
        self.assertEqual(tx.status, "ERROR")
        self.assertFalse(tx.passed)
        self.assertIn("transactions shard: failed send GRAM", tx.error)
        self.assertTrue(tx.run_url.endswith("/flow/run_tx"))

        send = report.flows[1]
        self.assertTrue(send.passed)
        self.assertEqual(send.error, "")

        # WARNING (optional-command warnings) counts as passed: real case
        # failures are turned into ERROR by the shard wrappers' assertTrue.
        self.assertTrue(report.flows[2].passed)

    def test_failed_flows_and_ok(self):
        report = parse_junit_report(_write_report(SAMPLE_JUNIT))
        self.assertEqual([f.name for f in report.failed_flows], ["Transactions"])
        self.assertFalse(report.ok)


class MessageTests(unittest.TestCase):
    def setUp(self):
        self.report = parse_junit_report(_write_report(SAMPLE_JUNIT))

    def test_main_text_lists_flows_and_links(self):
        text = build_main_text(
            report=self.report,
            run_url="https://github.com/tonkeeper/android_private/actions/runs/1",
            console_url="",
            apk_source="fresh build: tonkeeper-nightly-debug-123",
        )
        self.assertIn("1 / 3 flows failed", text)
        self.assertIn("`Transactions` ❌", text)
        self.assertIn("`Multichain — send` ✅", text)
        self.assertIn("`Deeplinks` ✅ (warnings)", text)
        self.assertIn("apk: fresh build: tonkeeper-nightly-debug-123", text)
        # console link falls back to the suite-level cloud.url from the report
        self.assertIn("upload/mupload_01m0z5ts4wfpd87cng4dj93r37|Maestro Cloud console", text)
        self.assertIn("actions/runs/1|Open workflow run", text)

    def test_main_text_all_green(self):
        for flow in self.report.flows:
            flow.status = "SUCCESS"
        text = build_main_text(
            report=self.report,
            run_url="https://example.com/run",
            console_url="https://app.maestro.dev/console",
            apk_source="",
        )
        self.assertIn("all 3 flows passed", text)
        self.assertIn("✅ *Android Maestro UI tests (Cloud)*", text)

    def test_main_text_without_report(self):
        text = build_main_text(
            report=None,
            run_url="https://example.com/run",
            console_url="https://app.maestro.dev/console",
            apk_source="reuse artifact: 42",
        )
        self.assertIn("no flow results", text)
        self.assertIn("Maestro Cloud console", text)

    def test_thread_text_has_error_and_run_link(self):
        text = build_thread_text(self.report.failed_flows[0])
        self.assertIn("*Failed flow:* `Transactions` — ERROR", text)
        self.assertIn("transactions shard: failed send GRAM", text)
        self.assertIn("flow/run_tx|Open the run in Maestro Cloud", text)

    def test_github_summary_table(self):
        md = build_github_summary(
            report=self.report,
            console_url="",
            apk_source="fresh build: x",
        )
        self.assertIn("| flow | status | error |", md)
        self.assertIn("[Transactions](https://app.maestro.dev/project/proj_x/maestro-test/flow/run_tx)", md)
        self.assertIn("3 total, 2 passed, 1 failed", md)


if __name__ == "__main__":
    unittest.main()
