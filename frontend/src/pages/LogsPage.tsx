import { ColumnType } from "antd";
import { StandardList } from "../shared/components/StandardList";

type LogEntry = {
  id: string;
  request_id: string;
  path: string;
  provider_id: string;
  http_status: number;
  latency_ms: number;
  estimated_cost_micros_usd: number;
};

export const LogsPage = () => {
  const columns: ColumnType<LogEntry>[] = [
    { title: "Request ID", dataIndex: "request_id" },
    { title: "Path", dataIndex: "path" },
    { title: "Provider", dataIndex: "provider_id" },
    { title: "Status", dataIndex: "http_status" },
    { title: "Latency (ms)", dataIndex: "latency_ms" },
    { title: "Estimated Cost (µ$)", dataIndex: "estimated_cost_micros_usd" }
  ];

  return (
    <StandardList<LogEntry>
      resource="logs"
      title="Request Logs"
      columns={columns}
    />
  );
};
