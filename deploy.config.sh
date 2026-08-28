# Configuration for ./deploy-backend.sh. Edit once, then just run the script.

# This OVH image has direct root SSH login disabled — "ubuntu" is the account, with sudo. If a
# server ever does allow root login directly, set SSH_USER="root" here and deploy-backend.sh
# skips sudo automatically.
SSH_USER="ubuntu"
SSH_HOST="51.83.131.88"

# Must already have (or will have, before you deploy) a DNS A record pointing at $SSH_HOST —
# Let's Encrypt cannot issue a certificate for a bare IP address.
API_DOMAIN="api.griffsoftware.com"

# Let's Encrypt sends expiry/renewal notices here. Set a real address you actually read.
ACME_EMAIL="contact@griffsoftware.com"

# The server already runs a shared, host-level Caddy for other sites (see docs/DEPLOYMENT.md) —
# GriffGym doesn't get its own reverse proxy. The API container publishes only to this loopback
# port, which the shared Caddy is pointed at. Must not collide with another app's port on the
# same box (check the existing blocks in /etc/caddy/Caddyfile before changing this).
API_LOCAL_PORT="8080"
