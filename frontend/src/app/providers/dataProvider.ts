import type { DataProvider } from "@refinedev/core";
import type { AxiosRequestConfig, AxiosResponse } from "axios";
import { axiosInstance } from "./axios";
import { RESOURCE_PATHS } from "../constants";

type RequestMeta = {
  path?: string;
  query?: Record<string, unknown>;
};

const resolvePath = (resource: string, meta?: RequestMeta) => {
  if (meta?.path) {
    return meta.path;
  }
  const mapped = RESOURCE_PATHS[resource];
  if (mapped) {
    return mapped;
  }
  return `/${resource}`;
};

const buildQueryParams = ({
  pagination,
  filters,
  sorters,
  meta
}: {
  pagination?: { current?: number; pageSize?: number };
  filters?: Array<{ field: string; operator?: string; value?: unknown }>;
  sorters?: Array<{ field: string; order?: "asc" | "desc" }>;
  meta?: RequestMeta;
}) => {
  const params = new URLSearchParams();
  if (pagination?.current) {
    params.set("page", String(pagination.current));
  }
  if (pagination?.pageSize) {
    params.set("per_page", String(pagination.pageSize));
  }
  filters?.forEach((filter) => {
    if (filter.value !== undefined && filter.value !== null) {
      params.set(filter.field, String(filter.value));
    }
  });
  if (sorters && sorters.length) {
    const sortValue = sorters.map((sorter) => `${sorter.field}:${sorter.order ?? "asc"}`).join(",");
    params.set("sort", sortValue);
  }
  if (meta?.query) {
    Object.entries(meta.query as Record<string, unknown>).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        params.set(key, String(value));
      }
    });
  }
  return params;
};

const normalizeListResponse = (payload: unknown) => {
  if (Array.isArray(payload)) {
    return { data: payload, total: payload.length };
  }
  if (typeof payload === "object" && payload !== null) {
    const candidate = payload as Record<string, unknown>;
    const data = candidate.data ?? candidate.rows ?? [];
    if (Array.isArray(data)) {
      const total =
        typeof candidate.total === "number"
          ? candidate.total
          : data.length;
      return { data, total };
    }
  }
  return { data: [], total: 0 };
};

const normalizeRecord = (payload: unknown) => {
  if (typeof payload === "object" && payload !== null) {
    const candidate = payload as Record<string, unknown>;
    return candidate.data ?? candidate;
  }
  return payload;
};

const handleResponse = (response: AxiosResponse) => response.data;

const request = async (config: AxiosRequestConfig) => {
  try {
    const response = await axiosInstance.request(config);
    return handleResponse(response);
  } catch (error) {
    throw error;
  }
};

export const createControlPlaneDataProvider = (): DataProvider => ({
  getList: async ({ resource, pagination, filters, sorters, meta }) => {
    const response = await request({
      method: "GET",
      url: resolvePath(resource, meta as RequestMeta | undefined),
      params: buildQueryParams({ pagination, filters, sorters, meta: meta as RequestMeta | undefined })
    });
    return normalizeListResponse(response);
  },
  getOne: async ({ resource, id, meta }) => {
    const response = await request({
      method: "GET",
      url: `${resolvePath(resource, meta as RequestMeta | undefined)}/${id}`
    });
    return { data: normalizeRecord(response) };
  },
  create: async ({ resource, variables, meta }) => {
    const response = await request({
      method: "POST",
      url: resolvePath(resource, meta as RequestMeta | undefined),
      data: variables
    });
    return { data: normalizeRecord(response) };
  },
  update: async ({ resource, id, variables, meta }) => {
    const response = await request({
      method: "PATCH",
      url: `${resolvePath(resource, meta as RequestMeta | undefined)}/${id}`,
      data: variables
    });
    return { data: normalizeRecord(response) };
  },
  deleteOne: async ({ resource, id, meta }) => {
    await request({
      method: "DELETE",
      url: `${resolvePath(resource, meta as RequestMeta | undefined)}/${id}`
    });
    return { data: null };
  },
  custom: async ({ url, method = "GET", payload, headers, meta, query }) => {
      const requestConfig: AxiosRequestConfig = {
        method,
        url,
        data: payload,
        headers,
        params: query
      };
    const response = await request(requestConfig);
    return { data: response };
  }
});

export const dataProvider = createControlPlaneDataProvider();
