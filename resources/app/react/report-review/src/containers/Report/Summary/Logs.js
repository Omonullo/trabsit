import { Table } from 'antd';
import React, { useMemo, useState } from 'react';

const Mapper = props => {
  const [data, setData] = useState([
    {
      id: 1,
      user: 'Hello man',
      createdAt: new Date(),
      ip: '233.14.78.1',
      action: 'Updated'
    },
    {
      id: 1,
      user: 'Hello man',
      createdAt: new Date(),
      ip: '233.14.78.1',
      action: 'Updated'
    },
    {
      id: 1,
      user: 'Hello man',
      createdAt: new Date(),
      ip: '233.14.78.1',
      action: 'Updated'
    },
  ]);

  const columns = useMemo(() => [
    {
      dataIndex: 'user',
      title: 'User',
    },
    {
      dataIndex: 'action',
      title: 'Action',
    },
    {
      dataIndex: 'ip',
      title: 'IP address',
    },
    {
      dataIndex: 'createdAt',
      title: 'Date',
    },
  ], []);

  return (
    <div>
      <Table
        dataSource={data}
        columns={columns}
      />
    </div>
  );
}

export default Mapper;