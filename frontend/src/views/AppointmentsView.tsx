import { useState } from 'react'
import { Button, Empty, message, Modal, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'
import http from '../api'
import { APPT_STATUS, APPT_STATUS_TYPE, fmtMoney, fmtTime } from '../constants'
import type { AppointmentRow } from '../types'
import { usePagedList } from '../usePagedList'

export default function AppointmentsView({ landlord = false }: { landlord?: boolean }) {
  const [rejectRow, setRejectRow] = useState<AppointmentRow | null>(null)
  const [rejectReason, setRejectReason] = useState('时间不合适')
  // 角色切换（租客/房东两个入口复用）时 url 变化，hook 会重新从第 1 页拉取
  const { rows, total, page, size, loading, reload } = usePagedList<AppointmentRow>(
    landlord ? '/landlord/appointments' : '/appointments/mine'
  )

  async function act(row: AppointmentRow, action: string, reason?: string) {
    await http.patch(`/appointments/${row.appointment.id}`, { action, reason })
    message.success('操作成功')
    reload()
  }

  async function doReject() {
    if (!rejectRow) return
    await act(rejectRow, 'reject', rejectReason)
    setRejectRow(null)
  }

  const columns: ColumnsType<AppointmentRow> = [
    {
      title: '房源',
      render: (_, row) => (
        <Link to={`/app/houses/${row.appointment.houseId}`}>{row.houseTitle}</Link>
      )
    },
    {
      title: landlord ? '租客' : '看房时间',
      render: (_, row) =>
        landlord ? row.tenantName || '-' : fmtTime(row.appointment.appointmentTime)
    },
    {
      title: landlord ? '看房时间' : '租金',
      width: landlord ? 170 : 110,
      render: (_, row) =>
        landlord ? fmtTime(row.appointment.appointmentTime) : fmtMoney(row.rent) + '/月'
    },
    {
      title: '状态',
      width: 100,
      render: (_, row) => (
        <Tag color={APPT_STATUS_TYPE[row.appointment.status]}>
          {APPT_STATUS[row.appointment.status]}
        </Tag>
      )
    },
    {
      title: '留言/理由',
      render: (_, row) => row.appointment.remark || row.appointment.rejectReason || '-'
    },
    {
      title: '操作',
      width: 230,
      render: (_, row) => {
        const s = row.appointment.status
        if (landlord) {
          return (
            <>
              {s === 0 && (
                <Button size="small" type="primary" onClick={() => act(row, 'confirm')}>
                  确认
                </Button>
              )}
              {s === 0 && (
                <Button
                  size="small"
                  danger
                  style={{ marginLeft: 6 }}
                  onClick={() => {
                    setRejectRow(row)
                    setRejectReason('时间不合适')
                  }}
                >
                  拒绝
                </Button>
              )}
              {s === 1 && (
                <Button
                  size="small"
                  type="primary"
                  ghost
                  style={{ marginLeft: 6 }}
                  onClick={() => act(row, 'complete')}
                >
                  完成看房
                </Button>
              )}
            </>
          )
        }
        return [0, 1].includes(s) ? (
          <Button size="small" onClick={() => act(row, 'cancel')}>
            取消预约
          </Button>
        ) : null
      }
    }
  ]

  return (
    <div className="page">
      <h2 className="page-title">{landlord ? '收到的预约（房东）' : '我的预约'}</h2>
      <Table
        rowKey={r => r.appointment.id}
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{
          current: page,
          pageSize: size,
          total,
          onChange: p => reload(p)
        }}
        locale={{ emptyText: <Empty description="暂无预约" /> }}
      />

      <Modal
        title="拒绝预约"
        open={!!rejectRow}
        onOk={doReject}
        okText="确认拒绝"
        okButtonProps={{ danger: true }}
        onCancel={() => setRejectRow(null)}
      >
        <p>拒绝理由将通知租客：</p>
        <input
          style={{ width: '100%', padding: 6 }}
          value={rejectReason}
          onChange={e => setRejectReason(e.target.value)}
        />
      </Modal>
    </div>
  )
}
