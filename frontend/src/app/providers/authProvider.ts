import type { AuthProvider } from "@refinedev/core";
import { axiosInstance, setAccessToken, getAccessToken } from "./axios";

// 本地缓存，避免频繁调用后端验证
let lastCheckTime = 0;
const CHECK_CACHE_TTL = 5 * 60 * 1000; // 5分钟缓存
let lastCheckResult: boolean | null = null;

const mapIdentity = (payload: Record<string, unknown>) => ({
  id: String(payload.id ?? payload.user_id ?? payload.email ?? "unknown"),
  fullName: String(payload.name ?? payload.full_name ?? payload.email ?? "Control Plane Operator"),
  email: String(payload.email ?? payload.user_email ?? "")
});

export const authProvider: AuthProvider = {
  login: async ({ email, password }) => {
    console.debug("authProvider.login: attempting", { email });
    const response = await axiosInstance.post("/auth/login", { email, password });
    const token = response.data?.access_token;
    if (!token) {
      throw new Error("Authentication failed");
    }
    console.debug("authProvider.login: received token", token);
    setAccessToken(token);

    // 登录成功后，更新检查缓存
    lastCheckResult = true;
    lastCheckTime = Date.now();

    return { success: true, redirectTo: "/dashboard" };
  },
  logout: async () => {
    await axiosInstance.post("/auth/logout").catch(() => undefined);
    setAccessToken(null);
    // 退出登录后，清除缓存
    lastCheckResult = false;
    lastCheckTime = Date.now();
    return "/";
  },
  check: async () => {
    const token = getAccessToken();
    console.debug("authProvider.check token", token ? "exists" : "missing");

    if (!token) {
      return Promise.resolve({ authenticated: false });
    }

    // 检查缓存，避免频繁调用后端
    const now = Date.now();
    if (lastCheckResult !== null && (now - lastCheckTime) < CHECK_CACHE_TTL) {
      console.debug("authProvider.check: using cached result", lastCheckResult);
      return Promise.resolve({ authenticated: Boolean(lastCheckResult) });
    }

    // 调用后端验证 token 有效性
    try {
      const response = await axiosInstance.get("/auth/verify");
      console.debug("authProvider.check: token is valid", response.data);
      lastCheckResult = true;
      lastCheckTime = now;
      return Promise.resolve({ authenticated: true });
    } catch (error: any) {
      console.debug("authProvider.check: token verification failed", error);
      lastCheckResult = false;
      lastCheckTime = now;
      if (error?.response?.status === 401) {
        setAccessToken(null);  // 清除无效 token
      }
      return Promise.resolve({ authenticated: false });
    }
  },
  getPermissions: async () => [],
  getIdentity: async () => {
    console.debug("authProvider.getIdentity");
    const response = await axiosInstance.get("/user/me");
    const payload = response.data?.data ?? response.data ?? {};
    return mapIdentity(payload as Record<string, unknown>);
  }
};
