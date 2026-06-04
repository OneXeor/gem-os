from __future__ import annotations

import json
from datetime import datetime, timezone

from gem_core.config import load_config


def main() -> int:
    cfg = load_config()
    project = next((item for item in cfg.projects.projects if item.id == "aso-fabric"), None)
    result = {
        "pipeline": "aso-monitor",
        "mode": "report-only",
        "status": "stub",
        "project": project.model_dump() if project else None,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
