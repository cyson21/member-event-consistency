# Local Infrastructure Draft (P1)

This folder defines the local infrastructure draft for the foundation slice.

Scope for MVP:

- No Kafka
- No complex MSA split
- No 2PC / distributed transactions
- No real external providers
- RabbitMQ is not documented as automatic business-key serialization
- Redis lock is used only as pre-DB contention relief

## Target Topology

This draft models one backend image scaled to three API instances (`api-1`, `api-2`, `api-3`) behind an Nginx fan-out proxy.

- `postgres`: PostgreSQL 16 (single DB)
- `redis`: Redis 7
- `rabbitmq`: RabbitMQ Management with UI on 15672
- `api-1`, `api-2`, `api-3`: API instances of one backend image
- `campaign-worker`: worker role for async campaign command processing
- `nginx`: simple local load-proxy for API fan-out

> Keep in mind: local docker results are **not** production performance proof.  
> They are a repeatable comparison environment under fixed local constraints.

## Files

- `infra/local/docker-compose.yml`
- `infra/local/nginx/default.conf`

## How to run

1. Ensure a local backend image exists or set `BACKEND_IMAGE` to your local tag.
2. Start with:

```bash
docker compose -f infra/local/docker-compose.yml up -d
```

3. Access services:

- `http://localhost:8080` → Nginx proxy to API pool
- `http://localhost:15672` → RabbitMQ management
- `localhost:5432`, `localhost:6379` → PostgreSQL and Redis if needed

4. Stop:

```bash
docker compose -f infra/local/docker-compose.yml down -v
```

## Static checks

Before starting local services, verify the Compose design surface and syntax:

```bash
node infra/local/check-compose-surface.mjs
docker compose -f infra/local/docker-compose.yml config -q
```

## Local MVP Seed Data

Flyway migration `V2__local_mvp_seed_data.sql` seeds deterministic local-only rows for the fixed MVP smoke route IDs:

- First Login Reward members: `93001`, `93002`, `93003`
- Coupon Campaign Issue campaigns: `94001`, `94002`, `94003`, `94004`
- Point Spend accounts: `95001`, `95005`, `95006`, `95007`

These rows are for local route smoke checks after dependency bootstrap. They are not production data and do not prove production performance.

## Environment Variables (optional override)

- `POSTGRES_DB` (default: `member_event_consistency`)
- `POSTGRES_USER` (default: `mec`)
- `POSTGRES_PASSWORD` (default: `mec_pass`)
- `RABBITMQ_USER` (default: `mec`)
- `RABBITMQ_PASS` (default: `mec_rabbit`)
- `BACKEND_IMAGE` (default: `member-event-consistency-backend:local`)

## Concurrency notes

- RabbitMQ concurrency for campaign worker is explicitly set to `1` in the draft:
  - `CAMPAIGN_WORKER_CONCURRENCY=1`
  - `CAMPAIGN_WORKER_LANE_STRATEGY=campaign-id`
- This keeps async campaign processing intentionally conservative in MVP.
- If/when you expand later, switch from single concurrency to explicit lane strategy rather than claiming RabbitMQ alone serializes business keys.

## Health checks

Practical checks are added for infra services:

- PostgreSQL (`pg_isready`)
- Redis (`redis-cli ping`)
- RabbitMQ (`rabbitmq-diagnostics -q ping`)
- Nginx (`nginx -t`)

API instances can enable HTTP health checks once a concrete endpoint (for example, `/actuator/health`) is available.
