const http = require("http");

const PORT = process.env.MOCK_SERVER_PORT ? Number(process.env.MOCK_SERVER_PORT) : 8080;
const TOKEN = "mock-control-plane-token";

const sendJson = (res, status, payload) => {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json");
  res.end(JSON.stringify(payload));
};

const setCors = (res, req) => {
  res.setHeader("Access-Control-Allow-Origin", "http://localhost:4174");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,PATCH,PUT,DELETE,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Request-Id");
  res.setHeader("Access-Control-Allow-Credentials", "true");
  res.setHeader("Access-Control-Expose-Headers", "Authorization,X-Request-Id");
  if (req.headers["origin"]) {
    res.setHeader("Access-Control-Allow-Origin", req.headers["origin"]);
  }
};

const server = http.createServer((req, res) => {
  setCors(res, req);
  if (req.method === "OPTIONS") {
    res.statusCode = 204;
    return res.end();
  }

  if (req.url === "/auth/login" && req.method === "POST") {
    let buffer = "";
    req.on("data", (chunk) => (buffer += chunk));
    req.on("end", () => {
      console.log("MockServer: login request", buffer);
      sendJson(res, 200, {
        access_token: TOKEN,
        token_type: "Bearer",
        data: { email: "admin@example.com", name: "Administrator" }
      });
    });
    return;
  }

  if (req.url === "/auth/verify" && req.method === "GET") {
    const auth = req.headers.authorization || "";
    if (auth === `Bearer ${TOKEN}`) {
      return sendJson(res, 200, { valid: true, expires_at: Date.now() + 3600 * 1000 });
    }
    return sendJson(res, 401, { valid: false, error: "Invalid token" });
  }

  if (req.url === "/user/me" && req.method === "GET") {
    const auth = req.headers.authorization || "";
    if (auth === `Bearer ${TOKEN}`) {
      return sendJson(res, 200, { data: { email: "admin@example.com", name: "Administrator" } });
    }
    return sendJson(res, 401, { error: "Unauthorized" });
  }

  if (req.url === "/v1/models" && req.method === "GET") {
    return sendJson(res, 200, { data: [], total: 0 });
  }

  res.writeHead(404);
  res.end("mock server: not found");
});

server.listen(PORT, () => {
  console.log(`Mock control-plane server listening on http://localhost:${PORT}`);
});
