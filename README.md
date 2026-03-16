# llm-gateway

`llm-gateway` is a Java 21 / Spring Boot implementation of an enterprise LLM gateway and control plane.

中文版本: [readme_cn.md](/Volumes/hpssd/projects/lab/others/@llmgatejava/readme_cn.md)

The project targets AI platform scenarios such as:
- unified access to multiple model providers
- OpenAI-compatible and Anthropic-compatible API exposure
- organization / project / API key based multi-tenant management
- routing, fallback, guardrails, audit, cost visibility, and operational controls
- MCP and OAuth support for runtime integrations

Package root: `com.qizlan.llm.gateway`

## Purpose

This project exists to provide a production-oriented gateway layer between AI applications and upstream LLM providers.

It is designed for teams building:
- AI platform products
- internal enterprise AI gateways
- multi-tenant model access platforms
- billable LLM infrastructure and governance services

The goal is not only protocol compatibility, but also operational control:
- who can call which model
- how requests are routed
- how failures fall back
- how usage, audit, and costs are tracked

## Architecture

The repository is a Maven multi-module project.

Core modules:
- `gateway-app`
  WebFlux data plane for `/v1/*`, streaming, provider routing, MCP, OAuth, and gateway runtime operations.
- `control-plane`
  Spring MVC control plane for admin/auth/user/org/project/key/IAM/log/audit/guardrail/cost APIs.
- `gateway-common`
  shared config and cross-cutting primitives
- `gateway-contracts`
  shared DTO/contracts
- `gateway-provider-spi`
  provider adapter SPI
- `gateway-routing`
  routing, catalog, sync, and projection invalidation logic
- `gateway-persistence`
  JPA entities and repositories

Current design direction:
- data plane stays WebFlux
- control plane stays servlet + JPA
- routing metadata cache invalidation uses PostgreSQL-backed projection versioning

## Technology Stack

Backend:
- Java 21
- Spring Boot 3.3
- Spring WebFlux for gateway/data plane
- Spring MVC for control plane
- Spring Security
- Spring Data JPA
- H2 for local/in-memory development and tests
- PostgreSQL-oriented schema and runtime design
- Micrometer / Prometheus
- springdoc OpenAPI / Swagger

Testing:
- JUnit 5
- Spring Boot Test
- WebTestClient / MockMvc depending on module

Frontend:
- `frontend/` contains the console application under active development

## Development

### Requirements

- Java 21
- Maven 3.9+
- Node.js / pnpm if working on `frontend/`

### Build

From the repository root:

```bash
mvn -q -DskipTests compile
```

### Run modules

Run gateway:

```bash
mvn -pl gateway-app spring-boot:run
```

Run control plane:

```bash
mvn -pl control-plane spring-boot:run
```

### Test

Run all unit/module tests:

```bash
mvn test
```

Run a single module:

```bash
mvn -pl gateway-app -am test
mvn -pl control-plane -am test
```

### Local configuration

The project uses environment-variable placeholders with development defaults in module `application.yml` files.

Typical local overrides:

```bash
export OPENAI_API_KEY=...
export ANTHROPIC_API_KEY=...
export GOOGLE_API_KEY=...
```

Gateway and control-plane defaults are intended to be usable in local development without a full external stack.

## API Documentation

When the applications are running:

- gateway swagger: `http://localhost:8080/docs`
- control-plane swagger: `http://localhost:8081/docs`

OpenAPI JSON:

- gateway: `http://localhost:8080/json`
- control-plane: `http://localhost:8081/json`

## Status

The repository is under active development, but it is already structured to support:
- frontend console development against the control plane
- gateway runtime development against the WebFlux data plane
- further platform features through incremental module evolution
