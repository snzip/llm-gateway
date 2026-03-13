import { List, Table } from "antd";
import type { TableProps } from "antd";
import type { ReactNode } from "react";
import type { TableColumnsType } from "antd";
import { useTable } from "@refinedev/core";

type StandardListProps<T extends Record<string, unknown>> = {
  resource: string;
  title: string;
  columns: TableColumnsType<T>;
  children?: ReactNode;
  meta?: Record<string, unknown>;
} & Omit<TableProps<T>, "columns">;

export const StandardList = <T extends Record<string, unknown>>({
  resource,
  title,
  columns,
  children,
  meta,
  ...rest
}: StandardListProps<T>) => {
  const { tableProps } = useTable<T>({
    resource,
    initialPageSize: 10,
    meta
  });

  return (
    <List title={title}>
      {children}
      <Table {...tableProps} columns={columns} rowKey="id" {...rest} />
    </List>
  );
};
