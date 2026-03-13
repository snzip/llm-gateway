import type { AccessControlProvider } from "@refinedev/core";

export const accessControlProvider: AccessControlProvider = {
  can: async () => ({ can: true }),
  getPermissions: async () => []
};
