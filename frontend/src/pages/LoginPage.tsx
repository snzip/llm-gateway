import { useLogin } from "@refinedev/core";
import { Button, Card, Form, Input, Space, Typography, Alert } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

const { Title, Text } = Typography;

export const LoginPage = () => {
  const { mutateAsync, isLoading, error } = useLogin();
  const navigate = useNavigate();

  const onFinish = async (values: { email: string; password: string }) => {
    console.debug("LoginPage: submit", values);
    try {
      const result = await mutateAsync(values);
      console.debug("LoginPage: login success, result:", result);

      // 获取 redirectTo，如果没有则默认跳转到 dashboard
      const redirectTo = (result as any)?.redirectTo || "/dashboard";
      console.debug("LoginPage: navigating to", redirectTo);

      // 使用 React Router 的 navigate 进行 SPA 导航
      navigate(redirectTo, { replace: true });
    } catch (err) {
      console.debug("LoginPage: login failed", err);
      /* handled by error state */
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "24px",
        background: "var(--refine-sider-background, #f4f4f4)"
      }}
    >
      <Card style={{ width: 360, boxShadow: "0 10px 30px rgba(0,0,0,0.15)" }}>
        <Space direction="vertical" size="small" style={{ width: "100%" }}>
          <Title level={3}>Platform Sign In</Title>
          <Text type="secondary">Use your control-plane credentials to continue.</Text>
          {error && (
            <Alert
              type="error"
              message="Authentication failed"
              description="Please verify your email and password."
              showIcon
            />
          )}
        </Space>
        <Form layout="vertical" onFinish={onFinish} style={{ marginTop: 24 }}>
          <Form.Item
            label="Email"
            name="email"
            rules={[{ required: true, message: "Email is required" }]}
          >
            <Input prefix={<UserOutlined />} placeholder="admin@example.com" />
          </Form.Item>
          <Form.Item
            label="Password"
            name="password"
            rules={[{ required: true, message: "Password is required" }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="••••••••" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={isLoading}>
              Sign in
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};
