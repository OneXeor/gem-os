#!/usr/bin/env python3
import json
import os
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


HOST = os.environ.get("CODEX_RUNNER_HOST", "127.0.0.1")
PORT = int(os.environ.get("CODEX_RUNNER_PORT", "8040"))
CODEX_BIN = os.environ.get("CODEX_BIN", "/Users/onexeor/.local/bin/codex")
CODEX_WORKDIR = os.environ.get("CODEX_WORKDIR", "/Users/onexeor/src/gem-os")
CODEX_TIMEOUT_SECONDS = int(os.environ.get("CODEX_TIMEOUT_SECONDS", "900"))
CODEX_SANDBOX = os.environ.get("CODEX_SANDBOX", "workspace-write")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/health":
            self.send_response(404)
            self.end_headers()
            return
        self._write_json({"service": "codex-runner", "ok": True})

    def do_POST(self):
        if self.path != "/codex/execute":
            self.send_response(404)
            self.end_headers()
            return

        try:
            length = int(self.headers.get("content-length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            result = execute_codex(payload)
            self._write_json(result)
        except Exception as exc:
            self._write_json(
                {
                    "ok": False,
                    "exitCode": -1,
                    "output": "",
                    "stderr": str(exc),
                    "durationMs": 0,
                },
                status=500,
            )

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args))

    def _write_json(self, payload, status=200):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def execute_codex(payload):
    started = time.monotonic()
    prompt = build_prompt(payload)
    cmd = [
        CODEX_BIN,
        "exec",
        "--cd",
        CODEX_WORKDIR,
        "--sandbox",
        CODEX_SANDBOX,
        "-",
    ]
    proc = subprocess.run(
        cmd,
        input=prompt,
        text=True,
        cwd=CODEX_WORKDIR,
        capture_output=True,
        timeout=CODEX_TIMEOUT_SECONDS,
    )
    return {
        "ok": proc.returncode == 0,
        "exitCode": proc.returncode,
        "output": proc.stdout[-12000:],
        "stderr": proc.stderr[-12000:],
        "durationMs": int((time.monotonic() - started) * 1000),
    }


def build_prompt(payload):
    context = payload.get("contextMessages") or []
    context_text = "\n".join(
        f"{item.get('role', 'unknown')}: {item.get('text', '')}" for item in context[-12:]
    )
    return "\n".join(
        [
            "You are running as Gem OS Codex executor from Slack.",
            "Work non-interactively. Be concise. Do not ask interactive questions.",
            "If the request is unsafe or unclear, explain what is needed instead of guessing.",
            "",
            f"Run ID: {payload.get('runId', '')}",
            f"Slack user: {payload.get('user', '')}",
            f"Project: {payload.get('projectId') or 'unknown'}",
            "",
            "Recent Slack context:",
            context_text,
            "",
            "Current request:",
            payload.get("text", ""),
        ]
    )


if __name__ == "__main__":
    print(f"codex-runner listening on {HOST}:{PORT}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
