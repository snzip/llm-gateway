# llmgatejava

Spring Boot 3.3 + Java 21 implementation of an LLM gateway.  
Package root: `com.qizlan.llm.gateway`

This project provides:
- OpenAI-compatible `chat/completions`
- Anthropic-compatible `messages`
- image generation and image edit APIs
- provider routing, fallback, health, probe, IAM, logging, costs, audit, guardrails
- MCP + OAuth support
- WebFlux data plane

## Status

Current version is usable as a local or internal gateway baseline.

Implemented:
- WebFlux data plane
- OpenAI / Anthropic / Google provider adapters
- organization / project / API key / IAM control plane
- logs / costs / audit / guardrails
- provider probe / model sync
- MCP / OAuth
- metrics and structured access log

Deferred:
- Flyway migration takeover
- full end-to-end non-blocking persistence

## Requirements

- Java 21
- Maven 3.9+

## Quick Start

Run in dev mode:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Run tests:

```bash
mvn test
```

Build jar:

```bash
mvn package -DskipTests
```

## Configuration

Main config file:
- [application.yml](/Volumes/hpssd/projects/lab/others/@llmgatejava/src/main/resources/application.yml)

Profiles:
- [application-dev.yml](/Volumes/hpssd/projects/lab/others/@llmgatejava/src/main/resources/application-dev.yml)
- [application-prod.yml](/Volumes/hpssd/projects/lab/others/@llmgatejava/src/main/resources/application-prod.yml)

Important environment variables:

```bash
LLM_GATEWAY_PROVIDER_MODE=mock|real
LLM_GATEWAY_API_KEY_HEADER=Authorization

LLM_GATEWAY_SEED_ENABLED=true|false
LLM_GATEWAY_SEED_API_KEY=test-api-key
LLM_GATEWAY_SEED_NAME="Dev seed key"

OPENAI_ENABLED=true|false
OPENAI_BASE_URL=https://api.openai.com
OPENAI_API_KEY=...

ANTHROPIC_ENABLED=true|false
ANTHROPIC_BASE_URL=https://api.anthropic.com
ANTHROPIC_API_KEY=...

GOOGLE_ENABLED=true|false
GOOGLE_BASE_URL=https://generativelanguage.googleapis.com
GOOGLE_API_KEY=...
```

Notes:
- `dev` profile enables seed key by default
- `prod` profile disables seed key
- current schema management still uses `ddl-auto: update`

## User Guide

### 1. Start the server

For local development:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Default local URLs:
- `GET /` health
- `GET /docs` Swagger UI
- `GET /json` OpenAPI
- `GET /metrics` metrics

### 2. Authenticate requests

In `dev`, the default seed key is:

```text
test-api-key
```

Use it like this:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer test-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "hello"}]
  }'
```

### 3. Use the main gateway APIs

List models:

```bash
curl http://localhost:8080/v1/models
```

Chat completions:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer test-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "hello"}]
  }'
```

Streaming chat:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer test-api-key" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "gpt-4o",
    "stream": true,
    "messages": [{"role": "user", "content": "hello stream"}]
  }'
```

Anthropic-compatible messages:

```bash
curl http://localhost:8080/v1/messages \
  -H "Authorization: Bearer test-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-3-5-sonnet",
    "max_tokens": 128,
    "messages": [{"role": "user", "content": "hello"}]
  }'
```

Image generation:

```bash
curl http://localhost:8080/v1/images/generations \
  -H "Authorization: Bearer test-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemini-2.5-flash-image",
    "prompt": "draw a cat"
  }'
```

Image edit with multipart:

```bash
curl http://localhost:8080/v1/images/edits \
  -H "Authorization: Bearer test-api-key" \
  -F "prompt=edit this image" \
  -F "image=@./test.png"
```

### 4. Use the control plane

Create organization:

```bash
curl http://localhost:8080/orgs \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme"}'
```

Create project:

```bash
curl http://localhost:8080/projects \
  -H "Content-Type: application/json" \
  -d '{"organizationId":"ORG_ID","name":"Gateway"}'
```

Create API key:

```bash
curl http://localhost:8080/keys/api \
  -H "Content-Type: application/json" \
  -d '{"organizationId":"ORG_ID","projectId":"PROJECT_ID","name":"Integration Key"}'
```

List logs:

```bash
curl http://localhost:8080/logs
```

Get cost summary:

```bash
curl "http://localhost:8080/costs/summary?group_by=project"
```

### 5. Use MCP and OAuth

Register client:

```bash
curl http://localhost:8080/oauth/register \
  -H "Content-Type: application/json" \
  -d '{"client_name":"Gateway MCP Client","redirect_uri":"https://client.example/callback"}'
```

OAuth metadata:

```bash
curl http://localhost:8080/.well-known/oauth-authorization-server
```

MCP tool list:

```bash
curl http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"method":"tools/list"}'
```

### 6. Inspect runtime state

Health:

```bash
curl http://localhost:8080/
```

Metrics:

```bash
curl http://localhost:8080/metrics
```

Provider health:

```bash
curl http://localhost:8080/internal/providers/health
```

Provider probe:

```bash
curl -X POST "http://localhost:8080/internal/providers/probe?provider=openai"
```

Model sync:

```bash
curl -X POST "http://localhost:8080/internal/models/sync?provider=openai"
```

## Main API Surface

Data plane:
- `GET /`
- `GET /metrics`
- `GET /docs`
- `GET /json`
- `GET /v1/models`
- `POST /v1/chat/completions`
- `POST /v1/messages`
- `POST /v1/images/generations`
- `POST /v1/images/edits`
- `POST /v1/responses`
- `GET /v1/responses/{responseId}`

Control plane:
- `/orgs`
- `/projects`
- `/keys/api`
- `/keys/api/{id}/iam`
- `/logs`
- `/costs/summary`
- `/costs/timeseries`
- `/audit-logs/{organizationId}`
- `/guardrails/*`
- `/internal/models/sync`
- `/internal/providers/probe`

Admin:
- `/admin/metrics`
- `/admin/metrics/timeseries`
- `/admin/metrics/cost-by-model`
- `/admin/organizations`

MCP / OAuth:
- `POST /mcp`
- `GET /.well-known/oauth-authorization-server`
- `GET /.well-known/oauth-authorization-server/mcp`
- `GET /oauth/authorize`
- `POST /oauth/token`
- `POST /oauth/revoke`
- `POST /oauth/register`
- `POST /oauth/clients/{clientId}/rotate-secret`

## Testing

Current test suite:
- split by feature/controller responsibility
- uses `WebTestClient`
- uses in-memory H2
- uses mocked upstream provider servers

Run:

```bash
mvn test
```

## Known Architecture Gaps

- provider hot path still contains blocking segments
- persistence is still blocking JPA
- schema migration is still `ddl-auto`, Flyway deferred
- tracing bridge is not complete yet

Backlog:
- [backlog.md](/Volumes/hpssd/projects/lab/others/@docs/backlog.md)

## Related Docs

- [README.md](/Volumes/hpssd/projects/lab/others/@docs/README.md)
- [requirements.md](/Volumes/hpssd/projects/lab/others/@docs/requirements.md)
- [technical-design.md](/Volumes/hpssd/projects/lab/others/@docs/technical-design.md)
- [implementation-plan.md](/Volumes/hpssd/projects/lab/others/@docs/implementation-plan.md)
- [database-erd.md](/Volumes/hpssd/projects/lab/others/@docs/database-erd.md)
- [tasklist.md](/Volumes/hpssd/projects/lab/others/@docs/tasklist.md)
- [backlog.md](/Volumes/hpssd/projects/lab/others/@docs/backlog.md)
