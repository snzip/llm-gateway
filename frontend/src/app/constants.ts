export const API_BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export const CONTROL_PLANE_RESOURCES = [
  { name: "dashboard", label: "Dashboard" },
  { name: "organizations", label: "Organizations" },
  { name: "projects", label: "Projects" },
  { name: "api-keys", label: "API Keys" },
  { name: "provider-keys", label: "Provider Keys" },
  { name: "models", label: "Models" },
  { name: "logs", label: "Request Logs" },
  { name: "settings", label: "Settings" }
];

export const RESOURCE_PATHS: Record<string, string> = {
  "organizations": "/orgs",
  "projects": "/projects",
  "api-keys": "/keys/api",
  "provider-keys": "/keys/provider",
  "logs": "/logs",
  "guardrails": "/guardrails/rules",
  "models": "/v1/models",
  "settings": "/user/me"
};

export const REQUEST_ID_HEADER = "X-Request-Id";

export const ACCESS_TOKEN_STORAGE_KEY = "llmgatejava_control_plane_token";
