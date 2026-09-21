"""
Local-only response mocking for Maestro runs (mitmproxy addon).

    mitmdump -q -s maestro_ui_tests/mocks/mock.py --set scenario=battery_two_refunds
    mitmdump -q -s maestro_ui_tests/mocks/mock.py --set record=battery.tonkeeper.com

Scenario layout (everything not listed passes through to the real backend):

    maestro_ui_tests/mocks/scenarios/<scenario>/<host>/<path with "/" -> "__">.json
      e.g. scenarios/battery_two_refunds/battery.tonkeeper.com/balance.json
           scenarios/battery_two_refunds/battery.tonkeeper.com/purchases.json
    optional sidecar <same name>.meta.json: {"status": 503, "delay_ms": 1500, "headers": {...}}
    a file whose name starts with "GET__" / "POST__" matches that method only.

Record mode writes real responses of the listed hosts (comma separated) to
    maestro_ui_tests/mocks/_recorded/<host>/<METHOD>__<path>.json
so fixtures can be copied into a scenario instead of hand-written.

Emulator side: settings put global http_proxy 10.0.2.2:8080 and the mitmproxy
CA installed as a user certificate (debug build trusts user CAs, no pinning).
"""
import json
import os
import time
from pathlib import Path

from mitmproxy import ctx, http

ROOT = Path(__file__).resolve().parent
SCENARIOS = ROOT / "scenarios"
RECORDED = ROOT / "_recorded"


def _path_key(path: str) -> str:
    key = path.split("?", 1)[0].strip("/").replace("/", "__")
    return key or "root"


class Mock:
    def load(self, loader):
        loader.add_option("scenario", str, "", "Scenario folder under mocks/scenarios to serve")
        loader.add_option("record", str, "", "Comma separated hosts whose responses are saved to mocks/_recorded")

    def configure(self, updates):
        self.scenario_dir = SCENARIOS / ctx.options.scenario if ctx.options.scenario else None
        if self.scenario_dir and not self.scenario_dir.is_dir():
            ctx.log.error(f"MOCK scenario dir not found: {self.scenario_dir}")
        self.record_hosts = {h.strip() for h in ctx.options.record.split(",") if h.strip()}
        if self.scenario_dir:
            files = sorted(p.relative_to(self.scenario_dir).as_posix() for p in self.scenario_dir.rglob("*.json") if not p.name.endswith(".meta.json"))
            ctx.log.info(f"MOCK scenario={ctx.options.scenario} rules={files}")

    def _find(self, flow: http.HTTPFlow):
        if not self.scenario_dir:
            return None
        host_dir = self.scenario_dir / flow.request.pretty_host
        if not host_dir.is_dir():
            return None
        key = _path_key(flow.request.path)
        for name in (f"{flow.request.method}__{key}.json", f"{key}.json"):
            f = host_dir / name
            if f.is_file():
                return f
        return None

    def request(self, flow: http.HTTPFlow):
        f = self._find(flow)
        if f is None:
            return
        meta = {}
        meta_file = f.with_name(f.name[:-5] + ".meta.json")
        if meta_file.is_file():
            meta = json.loads(meta_file.read_text())
        delay = meta.get("delay_ms", 0)
        if delay:
            time.sleep(delay / 1000.0)
        body = f.read_bytes()
        headers = {"Content-Type": "application/json", "X-Maestro-Mock": f.name}
        headers.update(meta.get("headers", {}))
        flow.response = http.Response.make(int(meta.get("status", 200)), body, headers)
        print(f"MOCK HIT {flow.request.method} {flow.request.pretty_host}{flow.request.path} -> {f.relative_to(ROOT)}", flush=True)

    def response(self, flow: http.HTTPFlow):
        if flow.request.pretty_host not in self.record_hosts or flow.response is None:
            return
        if flow.response.headers.get("X-Maestro-Mock"):
            return
        out_dir = RECORDED / flow.request.pretty_host
        out_dir.mkdir(parents=True, exist_ok=True)
        out = out_dir / f"{flow.request.method}__{_path_key(flow.request.path)}.json"
        try:
            text = json.dumps(json.loads(flow.response.get_text() or "null"), indent=2, ensure_ascii=False)
        except Exception:
            text = flow.response.get_text() or ""
        out.write_text(text)
        print(f"MOCK RECORDED {flow.request.method} {flow.request.pretty_host}{flow.request.path} [{flow.response.status_code}] -> {out.relative_to(ROOT)}", flush=True)


addons = [Mock()]
