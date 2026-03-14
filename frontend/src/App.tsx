import { Refine, Authenticated } from "@refinedev/core";

 
import { useNotificationProvider /* 其他导入 */ } from "@refinedev/antd";
import { App as AntdApp } from "antd"; // 👈 新增引入 Ant Design 的 App 包裹器

import routerProvider from "@refinedev/react-router";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { DashboardPage } from "./pages/DashboardPage";
import { OrganizationsPage } from "./pages/OrganizationsPage";
import { ProjectsPage } from "./pages/ProjectsPage";
import { ApiKeysPage } from "./pages/ApiKeysPage";
import { ModelsPage } from "./pages/ModelsPage";
import { LogsPage } from "./pages/LogsPage";
import { SettingsPage } from "./pages/SettingsPage";
import { ProviderKeysPage } from "./pages/ProviderKeysPage";
import { LoginPage } from "./pages/LoginPage";
import { ConsoleLayout } from "./app/layouts/ConsoleLayout";
import { CONTROL_PLANE_RESOURCES } from "./app/constants";
import { authProvider } from "./app/providers/authProvider";
import { dataProvider } from "./app/providers/dataProvider";
import { accessControlProvider } from "./app/providers/accessControlProvider";

const resources = CONTROL_PLANE_RESOURCES.map((resource) => ({
  name: resource.name,
  list: resource.name === "dashboard" ? "/dashboard" : `/${resource.name}`,
  options: { label: resource.label }
}));


export const App = () => {
  return (
    <BrowserRouter>
      {/* 👇 加入 AntdApp 以提供全局通知上下文 */}
      <AntdApp>
        <Refine
          routerProvider={routerProvider}
          authProvider={authProvider}
          dataProvider={dataProvider}
          accessControlProvider={accessControlProvider}
          notificationProvider={useNotificationProvider}
          resources={resources}
          // ❌ 删除了 Layout={ConsoleLayout}，交给下面的 Router 处理
          options={{
            syncWithLocation: true,
            warnWhenUnsavedChanges: true
          }}
        >
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            
            <Route
              path="/"
              element={
                <Authenticated fallback={<Navigate to="/login" replace />}>
                  <ConsoleLayout />
                </Authenticated>
              }
            >
              <Route index element={<Navigate to="dashboard" replace />} />
              <Route path="dashboard" element={<DashboardPage />} />
              <Route path="organizations" element={<OrganizationsPage />} />
              <Route path="projects" element={<ProjectsPage />} />
              <Route path="api-keys" element={<ApiKeysPage />} />
              <Route path="provider-keys" element={<ProviderKeysPage />} />
              <Route path="models" element={<ModelsPage />} />
              <Route path="logs" element={<LogsPage />} />
              <Route path="settings" element={<SettingsPage />} />
            </Route>
            
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </Refine>
      </AntdApp>
    </BrowserRouter>
  );
};