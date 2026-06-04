#!/usr/bin/env bash
set -euo pipefail

cd "${GEM_HOME:-/workspace/gem-os}"

if [ ! -f .env ]; then
  cp .env.example .env
fi

python -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip wheel
python -m pip install -e ".[dev]"

python -m services.scheduler.cli --status
