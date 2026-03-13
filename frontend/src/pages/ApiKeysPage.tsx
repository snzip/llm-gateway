import { ColumnType } from "antd";
import { StandardList } from "../shared/components/StandardList";

type ApiKey = {
  id: string;
  name: string;
  project_id: string;
  organization_id: string;
  active: boolean;
  token_prefix: string;
};

export const ApiKeysPage = () => {
  const columns: ColumnType<ApiKey>[] = [
    { title: "ID", dataIndex: "id" },
    { title: "Name", dataIndex: "name" },
    { title: "Token Prefix", dataIndex: "token_prefix" },
    { title: "Organization ID", dataIndex: "organization_id" },
    { title: "Project ID", dataIndex: "project_id" },
    {
      title: "Status",
      dataIndex: "active",
      render: (active: boolean) => (active ? "Active" : "Disabled")
    }
  ];

  return (
    <StandardList<ApiKey>
      resource="api-keys"
      title="API Keys"
      columns={columns}
    />
  );
};
