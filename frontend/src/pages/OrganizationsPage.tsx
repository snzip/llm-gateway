import { ColumnType } from "antd";
import { StandardList } from "../shared/components/StandardList";

type Organization = {
  id: string;
  name: string;
  active: boolean;
};

export const OrganizationsPage = () => {
  const columns: ColumnType<Organization>[] = [
    { title: "ID", dataIndex: "id" },
    { title: "Name", dataIndex: "name" },
    {
      title: "Status",
      dataIndex: "active",
      render: (active: boolean) => (active ? "Active" : "Disabled")
    }
  ];

  return (
    <StandardList<Organization>
      resource="organizations"
      title="Organizations"
      columns={columns}
    />
  );
};
