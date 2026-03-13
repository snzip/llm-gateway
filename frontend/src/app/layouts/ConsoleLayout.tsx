import { Outlet } from "react-router-dom";
import { ThemedLayoutV2 } from "@refinedev/antd";

export const ConsoleLayout = () => (
  <ThemedLayoutV2
    Title={({ collapsed }) => (
      <span style={{ fontWeight: 600, fontSize: 18 }}>LLM Gateway Console</span>
    )}
  >
    <Outlet />
  </ThemedLayoutV2>
);
