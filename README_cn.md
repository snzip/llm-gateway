# llm-gateway

`llm-gateway` 是一个基于 Java 21 / Spring Boot 的 LLM Gateway 与控制面实现。

License: Apache-2.0

English version: [README.md](README.md)

项目当前面向的平台场景包括：
- 多模型提供方统一接入
- OpenAI 兼容与 Anthropic 兼容 API 暴露
- 基于 organization / project / API key 的多租户管理
- 路由、fallback、guardrails、audit、cost 与运维控制
- 面向运行时集成的 MCP 与 OAuth 支持

包名前缀：`com.qizlan.llm.gateway`

## 项目目的

本项目的目标是在 AI 应用与上游 LLM 提供方之间提供一层可运营、可治理、可扩展的网关能力。

它适用于以下类型的平台建设：
- AI 平台产品
- 企业内部 AI Gateway
- 多租户模型接入平台
- 带有审计、成本与权限控制要求的 LLM 基础设施

项目目标不仅是协议兼容，还包括运行控制能力：
- 谁可以调用哪些模型
- 请求如何路由
- 失败后如何 fallback
- 用量、审计与成本如何追踪

## 架构

仓库采用 Maven 多模块结构。

核心模块：
- `gateway-app`
  WebFlux 数据面，负责 `/v1/*`、streaming、provider routing、MCP、OAuth 与网关运行时操作
- `control-plane`
  Spring MVC 控制面，负责 admin/auth/user/org/project/key/IAM/log/audit/guardrail/cost 等管理接口
- `gateway-common`
  共享配置与横切基础能力
- `gateway-contracts`
  共享 DTO / 协议对象
- `gateway-provider-spi`
  Provider 适配器 SPI
- `gateway-routing`
  路由、模型目录、同步、投影失效逻辑
- `gateway-persistence`
  JPA 实体与仓储

当前架构方向：
- 数据面保持 WebFlux
- 控制面保持 servlet + JPA
- 路由元数据缓存失效基于 PostgreSQL 投影版本机制

## 技术栈

后端：
- Java 21
- Spring Boot 3.3
- Spring WebFlux
- Spring MVC
- Spring Security
- Spring Data JPA
- H2 用于本地与测试
- 面向 PostgreSQL 的 schema 与运行设计
- Micrometer / Prometheus
- springdoc OpenAPI / Swagger

测试：
- JUnit 5
- Spring Boot Test
- WebTestClient / MockMvc

前端：
- `frontend/` 目录下为控制台前端工程

## 开发方式

### 环境要求

- Java 21
- Maven 3.9+
- 如果需要开发前端，还需要 Node.js / pnpm

### 编译

在仓库根目录执行：

```bash
mvn -q -DskipTests compile
```

### 运行模块

启动 gateway：

```bash
mvn -pl gateway-app spring-boot:run
```

启动 control-plane：

```bash
mvn -pl control-plane spring-boot:run
```

### 测试

执行全部测试：

```bash
mvn test
```

执行单模块测试：

```bash
mvn -pl gateway-app -am test
mvn -pl control-plane -am test
```

### 本地配置

模块内 `application.yml` 已经使用环境变量占位，并带有开发默认值。

常见本地变量：

```bash
export OPENAI_API_KEY=...
export ANTHROPIC_API_KEY=...
export GOOGLE_API_KEY=...
```

当前默认配置适合本地开发与测试，不要求一开始就接入完整外部基础设施。

## API 文档

服务启动后：

- gateway swagger: `http://localhost:8080/docs`
- control-plane swagger: `http://localhost:8081/docs`

OpenAPI JSON：

- gateway: `http://localhost:8080/json`
- control-plane: `http://localhost:8081/json`

## 当前状态

项目仍在持续迭代，但已经可以支持：
- 前端控制台围绕 control-plane 开发与联调
- WebFlux 数据面与运行时网关能力继续演进
- 多模块架构下的后续平台能力扩展
