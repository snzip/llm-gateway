import axios from "axios";
import { API_BASE_URL, ACCESS_TOKEN_STORAGE_KEY, REQUEST_ID_HEADER } from "../constants";

const storage = window.localStorage;

export const getAccessToken = () => storage.getItem(ACCESS_TOKEN_STORAGE_KEY);

export const setAccessToken = (token: string | null) => {
  if (token) {
    storage.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
  } else {
    storage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  }
};

const buildRequestId = () => {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `req-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
};

export const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json"
  }
});

axiosInstance.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers = {
      ...config.headers,
      Authorization: `Bearer ${token}`
    };
  }
  config.headers = {
    ...config.headers,
    [REQUEST_ID_HEADER]: buildRequestId()
  };
  return config;
});

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      setAccessToken(null);
      // 如果当前不在登录页，则重定向
      if (window.location.pathname !== "/login") {
        console.debug("axios: 401 error, redirecting to login");
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);
