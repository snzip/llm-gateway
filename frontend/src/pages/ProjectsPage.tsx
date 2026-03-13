import { ColumnType } from "antd";
import { StandardList } from "../shared/components/StandardList";

type Project = {
  id: string;
  name: string;
  organization_id: string;
  active: boolean;
};

export const ProjectsPage = () => {
  const columns: ColumnType<Project>[] = [
    { title: "ID", dataIndex: "id" },
    { title: "Name", dataIndex: "name" },
    { title: "Organization ID", dataIndex: "organization_id" },
    {
      title: "Status",
      dataIndex: "active",
      render: (active: boolean) => (active ? "Active" : "Disabled")
    }
  ];

  return (
    <StandardList<Project>
      resource="projects"
      title="Projects"
      columns={columns}
    />
  );
};
