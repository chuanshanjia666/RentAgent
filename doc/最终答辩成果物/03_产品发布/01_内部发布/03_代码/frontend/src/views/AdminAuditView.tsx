import { useState } from 'react'
import { Button, Descriptions, Empty, Input, message, Modal, Spin, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { IconBot } from '../components/icons'
import { fmtMoney, HOUSE_STATUS, HOUSE_STATUS_TYPE } from '../constants'
import type { DetectVO, House } from '../types'
import { usePagedList } from '../usePagedList'

export default function AdminAuditView() {
  const list = usePagedList<House>('/admin/houses/pending')
  const [rejectRow, setRejectRow] = useState<House | null>(null)
  const [reason, setReason] = useState('房源信息不完整，请补充后重新提交')
  const [detectRow, setDetectRow] = useState<House | null>(null)
  const [detectLoading, setDetectLoading] = useState(false)
  const [detectResult, setDetectResult] = useState<DetectVO | null>(null)

  async function audit(row: House, pass: boolean) {
    await http.patch(`/admin/houses/${row.id}/audit`, { pass, reason: pass ? '' : reason })
    message.success(pass ? '已通过审核' : '已驳回并通知房东')
    setRejectRow(null)
    list.reload()
  }

  async function runDetect(row: House) {
    setDetectRow(row)
    setDetectResult(null)
    setDetectLoading(true)
    try {
      const vo = await http.post<DetectVO>(`/admin/houses/${row.id}/fake-detect`)
      setDetectResult(vo)
    } catch {
      // 错误由 http 拦截器弹出 message.error，此处保留弹窗支持重试
    } finally {
      setDetectLoading(false)
    }
  }

  function handleCloseDetect() {
    setDetectRow(null)
    setDetectResult(null)
    setDetectLoading(false)
  }

  const riskColor = (s: number) => (s >= 70 ? '#bc4a1d' : s >= 40 ? '#d98a1f' : '#3d8f63')

  const columns: ColumnsType<House> = [
    {
      title: '房源',
      render: (_, row) => (
        <>
          <b>{row.title}</b>
          <div style={{ color: '#82948e', fontSize: 12 }}>
            {row.district} · {row.community} · {row.layout} · {row.area}㎡
          </div>
          <div style={{ color: '#4a5c56', fontSize: 12 }}>
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
          <Button
            size="small"
            style={{ marginLeft: 6 }}
            icon={<IconBot size={13} />}
            loading={detectLoading && detectRow?.id === row.id}
            onClick={() => runDetect(row)}
          >
            虚假检测
          </Button>
        </>
      )
    }
  ]

  return (
    <div className="page">
      <h2 className="page-title">审核工作台</h2>
      <div className="card-panel">
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
          locale={{ emptyText: <Empty description="没有待审核房源" /> }}
          columns={columns}
        />
      </div>

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
        title="虚假房源检测（辅助审核）"
        open={!!detectRow}
        footer={null}
        width={540}
        onCancel={handleCloseDetect}
      >
        {detectRow && (
          <div>
            <div
              style={{
                padding: '8px 12px',
                marginBottom: 16,
                background: '#f6f9f8',
                borderRadius: 6,
                border: '1px solid #e2eae7',
                fontSize: 13
              }}
            >
              <div style={{ fontWeight: 600, color: '#1f2e29' }}>{detectRow.title}</div>
              <div style={{ color: '#82948e', fontSize: 12, marginTop: 2 }}>
                {detectRow.district} · {detectRow.community} · {detectRow.layout} · {detectRow.area}
                ㎡ · 租金 {fmtMoney(detectRow.rent)}
              </div>
            </div>

            {detectLoading ? (
              <div style={{ textAlign: 'center', padding: '36px 0 28px' }}>
                <Spin size="large" />
                <div style={{ marginTop: 16, fontWeight: 500, fontSize: 14, color: '#2b3833' }}>
                  AI 正在多维度分析房源真实性…
                </div>
                <div style={{ marginTop: 6, color: '#82948e', fontSize: 12 }}>
                  结合租金偏离度、配套描述与疑似虚假疑点进行综合研判，请稍候
                </div>
              </div>
            ) : detectResult ? (
              <>
                <div style={{ textAlign: 'center', margin: '4px 0 16px' }}>
                  <span
                    style={{
                      fontSize: 42,
                      fontWeight: 700,
                      lineHeight: 1.1,
                      color: riskColor(detectResult.riskScore)
                    }}
                  >
                    {detectResult.riskScore}
                  </span>
                  <div style={{ color: '#82948e', fontSize: 12, marginTop: 4 }}>
                    风险分（0~100，分数越高风险越大）
                  </div>
                </div>

                <div style={{ marginBottom: 16 }}>
                  <div style={{ fontWeight: 600, marginBottom: 8, fontSize: 13, color: '#2b3833' }}>
                    疑似疑点分析：
                  </div>
                  {detectResult.suspicions && detectResult.suspicions.length > 0 ? (
                    <ul style={{ paddingLeft: 20, margin: 0, lineHeight: 1.8, color: '#4a5c56' }}>
                      {detectResult.suspicions.map((s: string, i: number) => (
                        <li key={i}>{s}</li>
                      ))}
                    </ul>
                  ) : (
                    <div style={{ color: '#3d8f63', fontSize: 13 }}>
                      ✓ 未发现明显异常或虚假疑点。
                    </div>
                  )}
                </div>

                <Descriptions bordered size="small" column={1}>
                  <Descriptions.Item label="处理建议">{detectResult.suggestion}</Descriptions.Item>
                  {detectResult.model && (
                    <Descriptions.Item label="研判模型">{detectResult.model}</Descriptions.Item>
                  )}
                </Descriptions>

                <p style={{ color: '#82948e', fontSize: 12, marginTop: 12, marginBottom: 0 }}>
                  AI 输出仅辅助排序与提示，管理员保留最终裁决权。
                </p>
              </>
            ) : (
              <div style={{ textAlign: 'center', padding: '24px 0' }}>
                <p style={{ color: '#bc4a1d', marginBottom: 12 }}>检测未能成功返回结果</p>
                <Button type="primary" onClick={() => runDetect(detectRow)}>
                  重新检测
                </Button>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
