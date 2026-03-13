import { Card, Descriptions, Spin, Typography } from "antd";
import { useGetIdentity } from "@refinedev/core";

const { Title } = Typography;

export const SettingsPage = () => {
  const { data, isLoading } = useGetIdentity();
  return (
    <Card>
      <Title level={4}>Platform Settings</Title>
      {isLoading ? (
        <Spin />
      ) : (
        <Descriptions column={1} bordered>
          <Descriptions.Item label="User ID">{data?.id ?? "Unknown"}</Descriptions.Item>
          <Descriptions.Item label="Name">{data?.fullName ?? "N/A"}</Descriptions.Item>
          <Descriptions.Item label="Email">{data?.email ?? "N/A"}</Descriptions.Item>
        </Descriptions>
      )}
    </Card>
  );
};
