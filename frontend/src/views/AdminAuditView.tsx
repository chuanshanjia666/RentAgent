import React, { useEffect, useState } from 'react'
import { Button, Descriptions, Empty, Input, message, Modal, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { fmtMoney, HOUSE_STATUS } from '../constants'

export default function AdminAuditView() {
  const [list, setList] = useState<any[]>([])
  const [rejectRow, setRejectRow] = useState<any>(null)
  const [reason, setReason] = useState('房源信息不完整，请补充后重新提交')
  const [detect, setDetect] = useState<any>(null)

  async function load() {
    const p = await http.get('/admin/houses/pending', { params: { size: 50 } })
    setList(p.list.map((h: any) => ({ ...h, key: h.id })))
  }

  async function audit(row: any, pass: boolean) {
    await http.patch(`/admin/houses/${row.id}/audit`, { pass, reason: pass ? '' : reason })
    message.success(pass ? '已通过审核' : '已驳回并通知房东')
    setRejectRow(null)
    load()
  }

  async function fakeDetect(row: any) {
    const vo = await http.post(`/admin/houses/${row.id}/fake-detect`)
    setDetect(vo)
  }

  useEffect(() => { load() }, [])

  const riskColor = (s: number) => (s >= 70 ? '#e6392f' : s >= 40 ? '#fa8c16' : '#52c41a')

  const columns: ColumnsType<any> = [
    {
      title: '房源', render: (_, row) => (
        <>
          <b>{row.title}</b>
          <div style={{ color: '#909399', fontSize: 12 }}>
            {row.district} · {row.community} · {row.layout} · {row.area}㎡
          </div>
          <div style={{ color: '#606266', fontSize: 12 }}>{(row.description || '').slice(0, 60)}…</div>
        </>
      )
    },
    { title: '租金', width: 110, render: (_, row) => fmtMoney(row.rent) },
    { title: '状态', width: 90, render: (_, row) => <Tag>{HOUSE_STATUS[row.status]}</Tag> },
    {
      title: '操作', width: 330,
      render: (_, row) => (
        <>
          <Button size="small" type="primary" onClick={() => audit(row, true)}>通过</Button>
          <Button size="small" danger style={{ marginLeft: 6 }}
            onClick={() => { setRejectRow(row); setReason('房源信息不完整，请补充后重新提交') }}>驳回</Button>
          <Button size="small" style={{ marginLeft: 6 }}
            onClick={() => fakeDetect(row)}>🤖 虚假检测</Button>
        </>
      )
    }
  ]

  return (
    <div className="page">
      <h2 className="page-title">审核工作台（FR-07 · AI 辅助 FR-16）</h2>
      <Table
        rowKey="id"
        dataSource={list}
        pagination={{ pageSize: 10 }}
        locale={{ emptyText: <Empty description="没有待审核房源 🎉" /> }}
        columns={columns}
      />

      <Modal title="驳回房源" open={!!rejectRow} onOk={() => rejectRow && audit(rejectRow, false)}
        okText="确认驳回" okButtonProps={{ danger: true }} onCancel={() => setRejectRow(null)}>
        <p>驳回理由将通知房东：</p>
        <Input.TextArea rows={2} value={reason} onChange={e => setReason(e.target.value)} />
      </Modal>

      <Modal title="🤖 虚假房源检测（FR-16 · 辅助审核）" open={!!detect} footer={null} width={520}
        onCancel={() => setDetect(null)}>
        {detect && (
          <>
            <div style={{ textAlign: 'center', margin: '8px 0 14px' }}>
              <span style={{ fontSize: 40, fontWeight: 700, color: riskColor(detect.riskScore) }}>
                {detect.riskScore}
              </span>
              <div style={{ color: '#909399' }}>风险分（0~100）</div>
            </div>
            {detect.suspicions.length ? (
              <ul style={{ paddingLeft: 20, lineHeight: 2 }}>
                {detect.suspicions.map((s: string, i: number) => <li key={i}>{s}</li>)}
              </ul>
            ) : (
              <p>未发现明显疑点。</p>
            )}
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="处理建议">{detect.suggestion}</Descriptions.Item>
            </Descriptions>
            <p style={{ color: '#909399', fontSize: 12, marginTop: 8 }}>
              AI 输出仅辅助排序与提示，管理员保留最终裁决权（FR-16 验收标准）。
            </p>
          </>
        )}
      </Modal>
    </div>
  )
}
