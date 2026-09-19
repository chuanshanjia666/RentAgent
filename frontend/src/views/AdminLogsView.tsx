import { Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { fmtTime } from '../constants'
import type { AuditLogRow } from '../types'
import { usePagedList } from '../usePagedList'

/** 操作留痕的动作字典（后端各处以 auditLogService.log 写入的 action 值） */
const ACTION_NAME: Record<string, string> = {
  HOUSE_AUDIT: '房源审核',
  USER_BAN: '禁用用户',
  USER_ENABLE: '启用用户',
  REPORT_HANDLE: '处理举报'
}

/**
 * 操作留痕（FR-07/22/23 验收要求）。
 * 早先只有写入没有查看入口：留痕表只写不读，"可追溯"就无从验证；
 * 这里按时间倒序只读展示，不做修改与删除（与 AI 对话审计同口径）。
 */
export default function AdminLogsView() {
  const list = usePagedList<AuditLogRow>('/admin/audits', {}, 20)

  const columns: ColumnsType<AuditLogRow> = [
    { title: '时间', width: 160, render: (_, r) => fmtTime(r.createdAt) },
    {
      title: '动作',
      width: 120,
      render: (_, r) => <Tag color="blue">{ACTION_NAME[r.action ?? ''] || r.action}</Tag>
    },
    {
      title: '对象',
      width: 160,
      render: (_, r) => `${r.targetType ?? '-'} #${r.targetId ?? '-'}`
    },
    { title: '操作人', width: 110, render: (_, r) => `uid=${r.operatorId ?? '-'}` },
    {
      title: '明细',
      render: (_, r) => <span style={{ color: '#606266', fontSize: 12 }}>{r.detail || '-'}</span>
    }
  ]

  return (
    <div className="page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 className="page-title">操作留痕</h2>
        <span style={{ color: '#909399', fontSize: 12 }}>共 {list.total} 条 · 日志只读</span>
      </div>
      <Table
        rowKey="id"
        size="middle"
        dataSource={list.rows}
        loading={list.loading}
        columns={columns}
        pagination={{
          current: list.page,
          pageSize: list.size,
          total: list.total,
          onChange: p => list.reload(p)
        }}
      />
    </div>
  )
}
