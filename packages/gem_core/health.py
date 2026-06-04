from __future__ import annotations

from dataclasses import dataclass
from time import time


STARTED_AT = time()


@dataclass(frozen=True)
class Health:
    service: str
    status: str = "ok"
    version: str = "0.1.0"

    def as_dict(self) -> dict[str, str | float]:
        return {
            "service": self.service,
            "status": self.status,
            "version": self.version,
            "uptime_seconds": round(time() - STARTED_AT, 3),
        }
