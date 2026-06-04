from __future__ import annotations

import argparse
import json

from gem_core.config import load_config


def _status() -> dict[str, object]:
    cfg = load_config()
    enabled = [pipeline for pipeline in cfg.pipelines.pipelines if pipeline.enabled]
    return {
        "service": "scheduler",
        "status": "ok",
        "pipelines_total": len(cfg.pipelines.pipelines),
        "pipelines_enabled": len(enabled),
        "enabled_pipeline_ids": [pipeline.id for pipeline in enabled],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Gem OS scheduler stub")
    parser.add_argument("--status", action="store_true", help="Print scheduler status")
    parser.add_argument("--list", action="store_true", help="List configured pipelines")
    args = parser.parse_args()

    cfg = load_config()
    if args.list:
        print(json.dumps([pipeline.model_dump() for pipeline in cfg.pipelines.pipelines], indent=2))
        return 0

    print(json.dumps(_status(), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
