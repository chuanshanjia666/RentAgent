import { useState } from 'react'
import { Button, Descriptions, Empty, Input, message, Modal, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { fmtMoney, HOUSE_STATUS, HOUSE_STATUS_TYPE } from '../constants'
import type { House } from '../types'
import { usePagedList } from '../usePagedList'

export default function AdminAuditView() {
  const list = usePagedList<House>('/admin/houses/pending')
  const [rejectRow, setRejectRow] = useState<House | null>(null)
  const [reason, setReason] = useState('房源信息不完整，请补充后重新提交')
  const [detect, setDetect] = useState<any>(null)

  async function audit(row: House, pass: boolean) {
    await http.patch(`/admin/houses/${row.id}/audit`, { pass, reason: pass ? '' : reason })
    message.success(pass ? '已通过审核' : '已驳回并通知房东')
    setRejectRow(null)
    list.reload()
  }

  async function fakeDetect(row: House) {
    const vo = await http.post(`/admin/houses/${row.id}/fake-detect`)
    setDetect(vo)
  }

  const riskColor = (s: number) => (s >= 70 ? '#e6392f' : s >= 40 ? '#fa8c16' : '#52c41a')

  const columns: ColumnsType<House> = [
    {
      title: '房源',
      render: (_, row) => (
        <>
          <b>{row.title}</b>
          <div style={{ color: '#909399', fontSize: 12 }}>
            {row.district} · {row.community} · {row.layout} · {row.area}㎡
          </div>
          <div style={{ color: '#606266', fontSize: 12 }}>
            {(row.description || '').slice(0, 60)}…
          </div>
        </>
      )
    },
    { title: '租金', width: 110, render: (_, row) => fmtMoney(row.rent) },
    {
      title: '状态',
      width: 90,
      render: (_, row) => (
        <Tag color={HOUSE_STATUS_TYPE[row.status ?? 0]}>{HOUSE_STATUS[row.status ?? 0]}</Tag>
      )
    },
    {
      title: '操作',
      width: 330,
      render: (_, row) => (
        <>
          <Button size="small" type="primary" onClick={() => audit(row, true)}>
            通过
          </Button>
          <Button
            size="small"
            danger
            style={{ marginLeft: 6 }}
            onClick={() => {
              setRejectRow(row)
              setReason('房源信息不完整，请补充后重新提交')
            }}
          >
            驳回
          </Button>
          <Button size="small" style={{ marginLeft: 6 }} onClick={() => fakeDetect(row)}>
            🤖 虚假检测
          </Button>
        </>
      )
    }
  ]

  return (
    <div className="page">
      <h2 className="page-title">审核工作台</h2>
      <Table
        rowKey="id"
        dataSource={list.rows}
        loading={list.loading}
        pagination={{
          current: list.page,
          pageSize: list.size,
          total: list.total,
          onChange: p => list.reload(p)
        }}
        locale={{ emptyText: <Empty description="没有待审核房源 🎉" /> }}
        columns={columns}
      />

      <Modal
        title="驳回房源"
        open={!!rejectRow}
        onOk={() => rejectRow && audit(rejectRow, false)}
        okText="确认驳回"
        okButtonProps={{ danger: true }}
        onCancel={() => setRejectRow(null)}
      >
        <p>驳回理由将通知房东：</p>
        <Input.TextArea rows={2} value={reason} onChange={e => setReason(e.target.value)} />
      </Modal>

      <Modal
        title="🤖 虚假房源检测（辅助审核）"
        open={!!detect}
        footer={null}
        width={520}
        onCancel={() => setDetect(null)}
      >
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
                {detect.suspicions.map((s: string, i: number) => (
                  <li key={i}>{s}</li>
                ))}
              </ul>
            ) : (
              <p>未发现明显疑点。</p>
            )}
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="处理建议">{detect.suggestion}</Descriptions.Item>
            </Descriptions>
            <p style={{ color: '#909399', fontSize: 12, marginTop: 8 }}>
              AI 输出仅辅助排序与提示，管理员保留最终裁决权。
            </p>
          </>
        )}
      </Modal>
    </div>
  )
}
