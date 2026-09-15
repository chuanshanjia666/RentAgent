import React, { useEffect, useState } from 'react'
import { Button, Empty, Input, message, Modal, Segmented, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'

const TARGET: Record<number, string> = { 1: '房源', 2: '评价', 3: '用户' }

export default function AdminReportsView() {
  const [tab, setTab] = useState(-1)
  const [list, setList] = useState<any[]>([])
  const [handleRow, setHandleRow] = useState<any>(null)
  const [remark, setRemark] = useState('已核实，按平台规则处理')

  async function load(t = tab) {
    const p = await http.get('/admin/reports', { params: { status: t, size: 50 } })
    setList(p.list)
  }

  async function handle() {
    await http.patch(`/admin/reports/${handleRow.report.id}/handle`, { remark })
    message.success('已处理并通知双方')
    setHandleRow(null)
    load()
  }

  const columns: ColumnsType<any> = [
    {
      title: '举报目标', width: 110,
      render: (_, r) => <Tag>{TARGET[r.report.targetType]}</Tag>
    },
    { title: '目标内容', render: (_, r) => r.targetTitle },
    { title: '理由', render: (_, r) => r.report.reason },
    {
      title: '状态', width: 90,
      render: (_, r) => <Tag color={r.report.status === 0 ? 'orange' : 'green'}>
        {r.report.status === 0 ? '待处理' : '已处理'}
      </Tag>
    },
    {
      title: '处理意见', width: 160,
      render: (_, r) => r.report.handleRemark || '-'
    },
    {
      title: '操作', width: 100,
      render: (_, r) => r.report.status === 0
        ? <Button size="small" type="primary" onClick={() => { setHandleRow(r); setRemark('已核实，按平台规则处理') }}>处理</Button>
        : null
    }
  ]

  return (
    <div className="page">
      <h2 className="page-title">举报处理（FR-23）</h2>
      <Segmented style={{ marginBottom: 14 }} value={tab} onChange={k => { setTab(k as number); load(k as number) }}
        options={[{ label: '全部', value: -1 }, { label: '待处理', value: 0 }, { label: '已处理', value: 1 }]} />
      <Table
        rowKey={r => r.report.id}
        dataSource={list}
        pagination={{ pageSize: 10 }}
        locale={{ emptyText: <Empty description="暂无举报" /> }}
        columns={columns}
      />

      <Modal title="处理举报" open={!!handleRow} onOk={handle} okText="提交处理" onCancel={() => setHandleRow(null)}>
        <p>处理意见（将通知举报人与被举报方；评价类举报处理后自动隐藏）：</p>
        <Input.TextArea rows={2} value={remark} onChange={e => setRemark(e.target.value)} />
      </Modal>
    </div>
  )
}
