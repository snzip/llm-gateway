import { ColumnType } from "antd";
import { StandardList } from "../shared/components/StandardList";

type ProviderKey = {
  id: string;
  name: string;
  provider_id: string;
  active: boolean;
};

export const ProviderKeysPage = () => {
  const columns: ColumnType<ProviderKey>[] = [
    { title: "ID", dataIndex: "id" },
    { title: "Name", dataIndex: "name" },
    { title: "Provider", dataIndex: "provider_id" },
    {
      title: "Status",
      dataIndex: "active",
      render: (active: boolean) => (active ? "Active" : "Disabled")
    }
  ];

  return (
    <StandardList<ProviderKey>
      resource="provider-keys"
      title="Provider Keys"
      columns={columns}
    />
  );
};
