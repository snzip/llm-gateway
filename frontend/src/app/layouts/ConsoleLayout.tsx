import { Outlet } from "react-router";
import { ThemedLayout } from "@refinedev/antd";

export const ConsoleLayout = () => (
  <ThemedLayout
    Title={({ collapsed }) => (
      <span style={{ fontWeight: 600, fontSize: 18 }}>LLM Gateway Console</span>
    )}
  >
    <Outlet />
  </ThemedLayout>
);
