# Deployment

The Griff Gym API runs on an OVH VPS (Ubuntu, x86_64) that also hosts other, unrelated projects
(e.g. BajaBox) side by side. Because of that, GriffGym does **not** run its own reverse proxy —
the box has exactly one Caddy instance, installed at the host level and shared by every site on
it, and GriffGym's deployment only ever adds its own clearly-marked block to that shared config.

Everything here is driven by two files at the repository root — `deploy.config.sh` and
`deploy-backend.sh` — so that provisioning GriffGym on a fresh server and shipping a routine
update are the same command.

```
Internet ──► HTTPS ──► shared Caddy (:80/:443) ──► Griff Gym API (127.0.0.1:API_LOCAL_PORT)
                              │
                              └──► other sites already on the box (untouched)
```

---

## One-time setup

### 1. DNS

Point an A record for the domain you're deploying under (e.g. `api.griffsoftware.com`) at the
server's IP address. Let's Encrypt validates ownership over HTTP before issuing a certificate, so
this has to resolve correctly *before* the first deploy — Caddy will keep retrying and failing
otherwise.

### 2. `deploy.config.sh`

At the repository root, edit:

```bash
SSH_USER="ubuntu"                     # or "root" if the server allows direct root login
SSH_HOST="<server ip or hostname>"
API_DOMAIN="api.griffsoftware.com"    # must match the DNS record above
ACME_EMAIL="you@example.com"          # Let's Encrypt sends expiry/renewal notices here
API_LOCAL_PORT="8080"                 # loopback port the shared Caddy proxies to — see below
```

**`API_LOCAL_PORT` must not collide with another app already on the box.** Check what's already
taken before changing it:

```bash
sudo cat /etc/caddy/Caddyfile
```

Every `reverse_proxy 127.0.0.1:<port>` line in there is a port already spoken for by another site.

This file is meant to be committed — it holds server addressing and a domain, not secrets. The
actual secrets (database password, JWT signing key) are generated on the server itself and never
pass through this file, git, or the local machine.

### 3. Run it

```bash
./deploy-backend.sh
```

You're asked for the SSH password once — the script opens an SSH `ControlMaster` connection so
that authenticates once and is reused for syncing the code. If `SSH_USER` isn't `root` (the OVH
default: root login is disabled, `ubuntu` + sudo is the account), you'll be asked for a sudo
password once more right before the actual provisioning step, since that needs root (installing
Docker/Caddy, editing the firewall, writing to `/opt` and `/etc/caddy`). The code itself is synced
to the SSH user's home directory first, then moved into `/opt/griffgym/app` by the root-run part
of the script.

On a fresh server this single run:

1. Installs Docker Engine and the Compose plugin, if missing.
2. Installs Caddy, if missing, and opens the firewall (`ufw`) for SSH, HTTP, and HTTPS only.
3. Generates `POSTGRES_PASSWORD` and `JWT_SIGNING_KEY` and stores them in `/opt/griffgym/.env`,
   outside the directory that gets overwritten on every redeploy.
4. Builds the images, applies migrations, and starts the `postgres` + `api` containers (no proxy
   container — `api` only publishes to `127.0.0.1:API_LOCAL_PORT`).
5. Adds a site block for `API_DOMAIN` to the shared `/etc/caddy/Caddyfile`, validates the result,
   and reloads Caddy — only then does the new site actually become reachable.

---

## Routine updates

Same command:

```bash
./deploy-backend.sh
```

It rsyncs the current `GriffGym.Backend/` source to the server, rebuilds the API image, applies
any new EF Core migrations against the running database, restarts the containers, and re-syncs
the Caddy block (so changing `API_DOMAIN`/`ACME_EMAIL`/`API_LOCAL_PORT` in `deploy.config.sh` and
redeploying is enough to pick them up). There is no separate "first deploy" vs. "update" mode —
the script is idempotent and safe to run as often as you push changes.

Nothing here uses Git on the server. The server never needs GitHub credentials; whatever is on
your machine's working tree when you run the script is what gets deployed. Commit and push
separately if you want the deployed state to match a specific commit.

---

## What's actually on the server

```
/opt/griffgym/
├── .env                     # generated/updated on deploy — POSTGRES_PASSWORD, JWT_SIGNING_KEY,
│                            # API_DOMAIN, ACME_EMAIL, API_LOCAL_PORT
└── app/                     # replaced on every deploy — this is GriffGym.Backend/, minus bin/obj
    ├── src/
    ├── deploy/remote-setup.sh
    ├── Dockerfile
    └── docker-compose.prod.yml

/etc/caddy/Caddyfile         # shared by every site on the box — GriffGym owns only the block
                             # between its "# >>> griffgym-backend ... >>>" / "# <<< ... <<<" markers
```

`/opt/griffgym/.env` is the one thing that persists across deploys by design — losing it would
mean a new signing key (every issued JWT and refresh token invalidated) and, if it also meant
losing the Postgres data volume, the workout history. It never leaves the server.

`/etc/caddy/Caddyfile` is **not GriffGym's file** — it's the box's shared proxy config, and other
sites' blocks live in it too. `remote-setup.sh` only ever touches the content between its own
markers; everything else is left byte-for-byte alone, and a backup is written to
`/etc/caddy/Caddyfile.griffgym-bak` before every edit as an extra safety net.

---

## Operating the stack

Backend containers, from `/opt/griffgym/app`:

```bash
docker compose --env-file /opt/griffgym/.env -f docker-compose.prod.yml ps
docker compose --env-file /opt/griffgym/.env -f docker-compose.prod.yml logs -f api
```

The shared Caddy is a systemd service, not a container:

```bash
sudo systemctl status caddy --no-pager
sudo journalctl -u caddy -n 100 --no-pager
```

Health checks (see main README): `https://<API_DOMAIN>/health/live` and `/health/ready`.

### Migrations

Applied by the `migrate` service, which builds from the `migrate` target in `Dockerfile` — the
SDK layer with the EF Core CLI restored, not the runtime image. It's excluded from `docker compose
up` (Compose profile `migrate`) and only ever run explicitly:

```bash
docker compose --env-file /opt/griffgym/.env -f docker-compose.prod.yml run --rm migrate
```

`deploy-backend.sh` already does this on every deploy; you only need it by hand if you're
debugging a migration failure directly on the server.

### Database backups

Not automated by these scripts. At minimum, before anything risky:

```bash
docker compose --env-file /opt/griffgym/.env -f docker-compose.prod.yml exec postgres \
  pg_dump -U griffgym griffgym > griffgym-backup-$(date +%Y%m%d).sql
```

---

## Troubleshooting

- **`docker compose up` fails with "address already in use" on port 80/443** — something else on
  the box already holds that port. This should no longer happen with the shared-Caddy setup
  above, but if it does: `sudo ss -tlnp | grep -E ':80|:443'` shows what owns it. GriffGym's own
  stack never binds 80/443 — only `127.0.0.1:API_LOCAL_PORT`.
- **First deploy times out waiting on the health check** — almost always DNS: confirm
  `dig +short <API_DOMAIN>` resolves to the server before retrying. `sudo journalctl -u caddy -n
  100` shows the ACME failure directly if that's the cause.
- **Caddy step fails with "Generated Caddy config is invalid"** — the script printed the
  validator's output and left `/etc/caddy/Caddyfile` untouched; every other site on the box kept
  running the whole time. Fix `API_DOMAIN`/`ACME_EMAIL` in `deploy.config.sh` and redeploy.
- **`migrate` fails** — check `docker compose ... logs postgres` first; the container usually
  needs a few seconds to become healthy on a completely fresh volume, which the service's
  `depends_on: condition: service_healthy` already waits for, but a misconfigured
  `POSTGRES_PASSWORD` in `/opt/griffgym/.env` will fail immediately and clearly.
- **Locked out of `/opt/griffgym/.env`** — if it's ever lost, delete it and re-run
  `deploy-backend.sh`: a fresh signing key and database password are generated, every existing
  session is invalidated, but the Postgres data volume (and the workout history in it) survives
  independently of that file.
