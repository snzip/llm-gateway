import { ColumnType } from "antd";
import { StandardList } from "../shared/components/StandardList";

type Model = {
  id: string;
  name: string;
  family: string;
  free: boolean;
  context_window_tokens: number;
};

export const ModelsPage = () => {
  const columns: ColumnType<Model>[] = [
    { title: "ID", dataIndex: "id" },
    { title: "Name", dataIndex: "name" },
    { title: "Family", dataIndex: "family" },
    {
      title: "Context Window",
      dataIndex: "context_window_tokens"
    },
    {
      title: "Free Model",
      dataIndex: "free",
      render: (free: boolean) => (free ? "Yes" : "No")
    }
  ];

  return (
    <StandardList<Model>
      resource="models"
      title="Models"
      columns={columns}
      meta={{ path: "/v1/models" }}
    />
  );
};
