import { Refine, Authenticated } from "@refinedev/core";
import { notificationProvider } from "@refinedev/antd";
import routerProvider from "@refinedev/react-router-v6";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
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
      <Refine
        routerProvider={routerProvider}
        authProvider={authProvider}
        dataProvider={dataProvider}
        accessControlProvider={accessControlProvider}
        notificationProvider={notificationProvider}
        resources={resources}
        Layout={ConsoleLayout}
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
    </BrowserRouter>
  );
};
