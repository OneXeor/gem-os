#!/usr/bin/env bash
set -euo pipefail

cd "${GEM_HOME:-/workspace/gem-os}"

if [ ! -f .env ]; then
  cp .env.example .env
fi

gradle --no-daemon test
gradle --no-daemon schedulerStatus
