import { useState } from 'react'
import { Button, Empty, Input, message, Modal, Segmented, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { fmtTime } from '../constants'
import type { RealnameRow } from '../types'
import { usePagedList } from '../usePagedList'

const STATUS: Record<number, { text: string; color: string }> = {
  0: { text: '待审核', color: 'orange' },
  1: { text: '已通过', color: 'green' },
  2: { text: '已驳回', color: 'red' }
}

const DEFAULT_REJECT = '证件信息不清晰，请重新提交'

/** 实名认证审核（FR-03/07）：通过后房东才能发布房源；处置留痕与通知由后端服务层完成 */
export default function AdminRealnamesView() {
  const [tab, setTab] = useState(0)
  const [target, setTarget] = useState<RealnameRow | null>(null)
  const [pass, setPass] = useState(true)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const list = usePagedList<RealnameRow>('/admin/realnames', { status: tab })

  function open(row: RealnameRow, approved: boolean) {
    setTarget(row)
    setPass(approved)
    setReason(approved ? '' : DEFAULT_REJECT)
  }

  async function submit() {
    if (!target) return
    if (!pass && !reason.trim()) {
      message.warning('驳回必须填写理由')
      return
    }
    setSubmitting(true)
    try {
      await http.patch(`/admin/realnames/${target.id}/audit`, { pass, reason: reason.trim() })
      message.success(pass ? '已通过实名认证，该用户现在可以发布房源' : '已驳回并通知申请人')
      setTarget(null)
      list.reload()
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ColumnsType<RealnameRow> = [
    { title: '昵称', width: 120, render: (_, r) => r.nickname || '-' },
    { title: '手机号', width: 130, render: (_, r) => r.phone || '-' },
    { title: '真实姓名', width: 100, render: (_, r) => r.realName || '-' },
    { title: '身份证号', width: 190, render: (_, r) => r.maskedIdCard || '-' },
    {
      title: '状态',
      width: 90,
      render: (_, r) => {
        const s = STATUS[r.status] ?? { text: '未知', color: 'default' }
        return <Tag color={s.color}>{s.text}</Tag>
      }
    },
    { title: '驳回理由', width: 160, render: (_, r) => r.rejectReason || '-' },
    { title: '提交时间', width: 170, render: (_, r) => fmtTime(r.createdAt) },
    {
      title: '操作',
      width: 150,
      render: (_, r) =>
        r.status === 0 ? (
          <>
            <Button size="small" type="primary" onClick={() => open(r, true)}>
              通过
            </Button>
            <Button size="small" danger style={{ marginLeft: 8 }} onClick={() => open(r, false)}>
              驳回
            </Button>
          </>
        ) : null
    }
  ]

  return (
    <div className="page">
      <h2 className="page-title">实名认证审核</h2>
      <Segmented
        style={{ marginBottom: 14 }}
        value={tab}
        onChange={k => setTab(k as number)}
        options={[
          { label: '待审核', value: 0 },
          { label: '已通过', value: 1 },
          { label: '已驳回', value: 2 },
          { label: '全部', value: -1 }
        ]}
      />
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
        locale={{ emptyText: <Empty description="暂无实名认证申请" /> }}
        columns={columns}
        scroll={{ x: 1000 }}
      />

      <Modal
        title={pass ? '通过实名认证' : '驳回实名认证'}
        open={!!target}
        onOk={submit}
        okText={pass ? '确认通过' : '确认驳回'}
        okButtonProps={{ danger: !pass }}
        confirmLoading={submitting}
        onCancel={() => setTarget(null)}
      >
        <p>
          {target?.nickname}（{target?.realName} / {target?.maskedIdCard}）
        </p>
        {pass ? (
          <p>通过后该账号即完成实名认证，可以发布与管理房源。</p>
        ) : (
          <>
            <p>驳回理由（将通知申请人，申请人可修改后重新提交）：</p>
            <Input.TextArea rows={2} value={reason} onChange={e => setReason(e.target.value)} />
          </>
        )}
      </Modal>
    </div>
  )
}
