#!/usr/bin/env bash
#
# Runs ON the OVH server, as root (invoked over SSH — directly or via sudo — by
# ../../deploy-backend.sh; not meant to be run by hand from a laptop). Idempotent: safe to run on
# a brand-new box or on the 50th deploy.
#
# Responsibilities, in order:
#   0. Move the freshly-synced code from STAGING_DIR (writable by whatever user SSH'd in) into
#      APP_DIR (root-owned, under /opt).
#   1. Install Docker Engine + Compose plugin if missing.
#   2. Install Caddy if missing, and open the firewall for SSH/HTTP/HTTPS only.
#   3. Generate production secrets once and keep them outside the code directory, so a redeploy
#      (which replaces APP_DIR outright) never touches them.
#   4. Build the images, apply pending EF Core migrations, then (re)start the stack — the api
#      container only, published to a loopback port; no reverse proxy of its own.
#   5. Point the box's single, shared Caddy instance at that port for API_DOMAIN, alongside
#      whatever other sites it already serves (e.g. bajabox.pl), without touching them.
#
# Requires STAGING_DIR, API_DOMAIN, ACME_EMAIL and API_LOCAL_PORT in the environment.

set -euo pipefail

APP_DIR="/opt/griffgym/app"
ENV_FILE="/opt/griffgym/.env"
COMPOSE_FILE="docker-compose.prod.yml"
CADDYFILE="/etc/caddy/Caddyfile"

: "${STAGING_DIR:?STAGING_DIR must be set}"
: "${API_DOMAIN:?API_DOMAIN must be set}"
: "${ACME_EMAIL:?ACME_EMAIL must be set}"
: "${API_LOCAL_PORT:?API_LOCAL_PORT must be set}"
# Not required: Google sign-in is an additional login method, and a deployment that has not
# configured it yet must still update and serve everything else. See GoogleOptions.
: "${GOOGLE_WEB_CLIENT_ID:=}"

log() { printf '\n\033[1;33m==> %s\033[0m\n' "$1"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "remote-setup.sh must run as root (it installs packages and edits the firewall)." >&2
  exit 1
fi

# --- 0. Move code into place -----------------------------------------------------------------
log "Installing synced code into $APP_DIR"
mkdir -p "$(dirname "$APP_DIR")"
rm -rf "$APP_DIR"
cp -a "$STAGING_DIR" "$APP_DIR"

# --- 1. Docker -------------------------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  log "Installing Docker Engine"
  apt-get update -y
  apt-get install -y ca-certificates curl
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
else
  log "Docker already installed, skipping"
fi

# --- 2. Caddy + firewall ----------------------------------------------------------------------
if ! command -v caddy >/dev/null 2>&1; then
  log "Installing Caddy"
  apt-get update -y
  apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
    | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
    > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y
  apt-get install -y caddy
else
  log "Caddy already installed, skipping"
fi

if command -v ufw >/dev/null 2>&1; then
  log "Configuring firewall (SSH, HTTP, HTTPS)"
  ufw allow OpenSSH >/dev/null
  ufw allow 80/tcp >/dev/null
  ufw allow 443/tcp >/dev/null
  ufw --force enable >/dev/null
fi

# --- 3. Secrets ----------------------------------------------------------------------------
mkdir -p "$(dirname "$ENV_FILE")"
if [ ! -f "$ENV_FILE" ]; then
  log "Generating production secrets -> $ENV_FILE"
  {
    echo "POSTGRES_PASSWORD=$(openssl rand -base64 32)"
    echo "JWT_SIGNING_KEY=$(openssl rand -base64 48)"
  } > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
else
  log "Reusing existing secrets from $ENV_FILE"
fi

# API_DOMAIN / ACME_EMAIL / API_LOCAL_PORT / GOOGLE_WEB_CLIENT_ID can change between deploys;
# the generated passwords never should, so they're managed separately from these lines.
sed -i '/^API_DOMAIN=/d;/^ACME_EMAIL=/d;/^API_LOCAL_PORT=/d;/^GOOGLE_WEB_CLIENT_ID=/d' "$ENV_FILE"
{
  echo "API_DOMAIN=$API_DOMAIN"
  echo "ACME_EMAIL=$ACME_EMAIL"
  echo "API_LOCAL_PORT=$API_LOCAL_PORT"
  echo "GOOGLE_WEB_CLIENT_ID=$GOOGLE_WEB_CLIENT_ID"
} >> "$ENV_FILE"

# --- 4. Build, migrate, (re)start -------------------------------------------------------------
cd "$APP_DIR"

log "Building images"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build

log "Applying database migrations"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" run --rm migrate

log "Starting services"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --remove-orphans

log "Pruning dangling images"
docker image prune -f >/dev/null

# --- 5. Register with the shared Caddy ---------------------------------------------------------
#
# This box's one Caddy instance serves every site on it (e.g. bajabox.pl) from a single
# /etc/caddy/Caddyfile. Only the block between these markers is ours to touch: it's replaced
# wholesale on every deploy (so changing API_DOMAIN/ACME_EMAIL/API_LOCAL_PORT here takes effect),
# and everything outside the markers — including every other site — is left byte-for-byte alone.
MARKER_START="# >>> griffgym-backend (managed by GriffGym deploy-backend.sh — do not edit by hand) >>>"
MARKER_END="# <<< griffgym-backend <<<"

log "Registering ${API_DOMAIN} with the shared Caddy instance"

if [ ! -f "$CADDYFILE" ]; then
  touch "$CADDYFILE"
fi
cp "$CADDYFILE" "${CADDYFILE}.griffgym-bak"

awk -v start="$MARKER_START" -v end="$MARKER_END" '
  $0 == start { skip = 1; next }
  $0 == end   { skip = 0; next }
  !skip { print }
' "$CADDYFILE" > "${CADDYFILE}.new"

{
  echo ""
  echo "$MARKER_START"
  echo "${API_DOMAIN} {"
  echo "	reverse_proxy 127.0.0.1:${API_LOCAL_PORT}"
  echo "	tls ${ACME_EMAIL}"
  echo "}"
  echo "$MARKER_END"
} >> "${CADDYFILE}.new"

if caddy validate --config "${CADDYFILE}.new" --adapter caddyfile >/tmp/griffgym-caddy-validate.log 2>&1; then
  mv "${CADDYFILE}.new" "$CADDYFILE"
  systemctl reload caddy
  log "Caddy reloaded for ${API_DOMAIN}"
else
  log "Generated Caddy config is invalid — left the existing /etc/caddy/Caddyfile untouched:"
  cat /tmp/griffgym-caddy-validate.log >&2
  rm -f "${CADDYFILE}.new"
  exit 1
fi

log "Deployed. Waiting for the health check..."
sleep 5
if curl -fsS "https://${API_DOMAIN}/health/live" >/dev/null 2>&1; then
  echo "https://${API_DOMAIN}/health/live is up."
else
  echo "Not reachable yet at https://${API_DOMAIN}/health/live — check 'journalctl -u caddy -n 50' and 'docker compose -f $APP_DIR/$COMPOSE_FILE logs api'." >&2
  echo "If this is the first deploy, confirm the DNS A record for ${API_DOMAIN} points at this server before retrying." >&2
fi
