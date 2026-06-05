#!/bin/zsh
set -eu

export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"

GEM_OS_HOME="${GEM_OS_HOME:-/Users/onexeor/src/gem-os}"
LOG_DIR="$GEM_OS_HOME/logs"
LOG_FILE="$LOG_DIR/startup.log"

mkdir -p "$LOG_DIR"

log() {
  printf '%s %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >> "$LOG_FILE"
}

compose() {
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    docker compose "$@"
  fi
}

cd "$GEM_OS_HOME"
log "Gem OS startup requested"

if ! docker info >/dev/null 2>&1; then
  if command -v colima >/dev/null 2>&1; then
    log "Docker is unavailable; starting Colima"
    colima start >> "$LOG_FILE" 2>&1
  else
    log "Docker is unavailable and Colima is not installed"
    exit 1
  fi
fi

if ! docker info >/dev/null 2>&1; then
  log "Docker is still unavailable after startup attempt"
  exit 1
fi

log "Starting Gem OS core + Slack services"
compose --profile slack up -d postgres redis qdrant litellm provider-router brain admin slack-bot >> "$LOG_FILE" 2>&1

if [ -f ".env" ] && grep -q '^CLOUDFLARED_TUNNEL_TOKEN=.' ".env"; then
  log "Tunnel token found; starting cloudflared"
  compose --profile slack --profile tunnel up -d cloudflared >> "$LOG_FILE" 2>&1
else
  log "Tunnel token missing; skipping cloudflared"
fi

log "Gem OS startup finished"
