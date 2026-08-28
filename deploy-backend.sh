#!/usr/bin/env bash
#
# Deploys or updates the GriffGym backend on the production server (see deploy.config.sh).
#
# Usage:
#   ./deploy-backend.sh
#
# You'll be asked for the SSH password once (an SSH ControlMaster connection keeps it open for
# the rest of the run), and — unless SSH_USER is "root" — for a sudo password once more, since
# the actual provisioning (installing Docker, editing the firewall, writing to /opt) needs root.
# Safe to run repeatedly: the first run provisions the server, every run after that just ships
# the latest code, migrates the database, and restarts the containers.
#
# Requires: ssh, rsync. Nothing needs to be installed locally beyond that — the .NET SDK, Docker
# etc. are only needed on the server, and remote-setup.sh installs what's missing there.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/GriffGym.Backend"

# shellcheck source=deploy.config.sh
source "$SCRIPT_DIR/deploy.config.sh"

if [ "$ACME_EMAIL" = "you@example.com" ]; then
  echo "Edit deploy.config.sh and set ACME_EMAIL to a real address before deploying." >&2
  exit 1
fi

# The SSH user often can't write to /opt directly, so the code lands in its home directory first;
# remote-setup.sh (run as root via sudo) copies it into /opt/griffgym/app from there.
STAGING_DIR="/home/$SSH_USER/.griffgym-deploy"
if [ "$SSH_USER" = "root" ]; then
  STAGING_DIR="/root/.griffgym-deploy"
  SUDO_PREFIX=""
else
  SUDO_PREFIX="sudo "
fi

CTRL_DIR="$(mktemp -d)"
CTRL_PATH="$CTRL_DIR/ssh-ctrl"
cleanup() {
  ssh -o ControlPath="$CTRL_PATH" -O exit "$SSH_USER@$SSH_HOST" >/dev/null 2>&1 || true
  rm -rf "$CTRL_DIR"
}
trap cleanup EXIT

echo "==> Connecting to $SSH_USER@$SSH_HOST"
echo "    (password requested once; the connection is kept open for the rest of the deploy)"
ssh -o ControlMaster=yes -o ControlPath="$CTRL_PATH" -o ControlPersist=15m -N -f "$SSH_USER@$SSH_HOST"

ssh_remote() { ssh -o ControlPath="$CTRL_PATH" "$SSH_USER@$SSH_HOST" "$@"; }
rsync_to_remote() { rsync -az -e "ssh -o ControlPath=$CTRL_PATH" "$@"; }

echo "==> Preparing staging directory"
ssh_remote "mkdir -p '$STAGING_DIR'"

echo "==> Syncing backend source"
rsync_to_remote --delete \
  --exclude 'bin/' --exclude 'obj/' \
  "$BACKEND_DIR/src" "$BACKEND_DIR/deploy" \
  "$SSH_USER@$SSH_HOST:$STAGING_DIR/"

rsync_to_remote --delete "$BACKEND_DIR/.config/" "$SSH_USER@$SSH_HOST:$STAGING_DIR/.config/"

rsync_to_remote \
  "$BACKEND_DIR/global.json" "$BACKEND_DIR/nuget.config" \
  "$BACKEND_DIR/Directory.Build.props" "$BACKEND_DIR/Directory.Packages.props" \
  "$BACKEND_DIR/Dockerfile" "$BACKEND_DIR/docker-compose.prod.yml" \
  "$SSH_USER@$SSH_HOST:$STAGING_DIR/"

echo "==> Running remote setup / update (sudo may ask for a password here)"
ssh -t -o ControlPath="$CTRL_PATH" "$SSH_USER@$SSH_HOST" \
  "chmod +x '$STAGING_DIR/deploy/remote-setup.sh' && ${SUDO_PREFIX}env STAGING_DIR='$STAGING_DIR' API_DOMAIN='$API_DOMAIN' ACME_EMAIL='$ACME_EMAIL' API_LOCAL_PORT='$API_LOCAL_PORT' GOOGLE_WEB_CLIENT_ID='$GOOGLE_WEB_CLIENT_ID' bash '$STAGING_DIR/deploy/remote-setup.sh'"

echo "==> Done. https://$API_DOMAIN/health/live"
