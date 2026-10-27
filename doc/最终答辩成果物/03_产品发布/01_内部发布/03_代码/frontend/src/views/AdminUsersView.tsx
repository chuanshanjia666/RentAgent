import { useState } from 'react'
import { Button, Input, message, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { USER_ROLE } from '../constants'
import type { AdminUserRow } from '../types'
import { usePagedList } from '../usePagedList'

export default function AdminUsersView() {
  const [keyword, setKeyword] = useState('')
  // 查询条件进 params：关键词变化时 hook 自动回到第 1 页重新拉取
  const list = usePagedList<AdminUserRow>('/admin/users', { keyword: keyword || undefined })

  async function toggle(row: AdminUserRow) {
    await http.patch(`/admin/users/${row.id}/status?enabled=${row.status === 0}`)
    message.success(row.status === 0 ? '已启用' : '已禁用（该用户立即无法访问）')
    list.reload()
  }

  const columns: ColumnsType<AdminUserRow> = [
    { title: 'ID', width: 70, dataIndex: 'id' },
    { title: '昵称', dataIndex: 'nickname' },
    { title: '账号', dataIndex: 'username' },
    { title: '手机号', dataIndex: 'phone' },
    { title: '角色', width: 90, render: (_, u) => <Tag>{USER_ROLE[u.role ?? 0]}</Tag> },
    {
      title: '状态',
      width: 90,
      render: (_, u) => (
        <Tag color={u.status === 1 ? 'green' : 'red'}>{u.status === 1 ? '正常' : '已禁用'}</Tag>
      )
    },
    {
      title: '操作',
      width: 110,
      render: (_, u) =>
        u.role === 3 ? (
          <span style={{ color: '#a5b3ae' }}>管理员不可禁用</span>
        ) : (
          <Button size="small" danger={u.status === 1} onClick={() => toggle(u)}>
            {u.status === 1 ? '禁用' : '启用'}
          </Button>
        )
    }
  ]

  return (
    <div className="page">
      <h2 className="page-title">用户管理</h2>
      <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
        <Input
          style={{ width: 260 }}
          placeholder="昵称 / 手机号 / 账号"
          value={keyword}
          onChange={e => setKeyword(e.target.value)}
          onPressEnter={() => list.reload(1)}
        />
        <Button type="primary" onClick={() => list.reload(1)}>
          查询
        </Button>
      </div>
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
        columns={columns}
      />
    </div>
  )
}
