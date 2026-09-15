import React, { useEffect, useState } from 'react'
import { Button, Input, message, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'

const ROLE: Record<number, string> = { 1: '租客', 2: '房东', 3: '管理员' }

export default function AdminUsersView() {
  const [list, setList] = useState<any[]>([])
  const [keyword, setKeyword] = useState('')

  async function load(kw = keyword) {
    const p = await http.get('/admin/users', { params: { keyword: kw || undefined, size: 50 } })
    setList(p.list.map((u: any) => ({ ...u, key: u.id })))
  }

  async function toggle(row: any) {
    await http.patch(`/admin/users/${row.id}/status?enabled=${row.status === 0}`)
    message.success(row.status === 0 ? '已启用' : '已禁用（该用户立即无法访问）')
    load()
  }

  const columns: ColumnsType<any> = [
    { title: 'ID', width: 70, dataIndex: 'id' },
    { title: '昵称', dataIndex: 'nickname' },
    { title: '账号', dataIndex: 'username' },
    { title: '手机号', dataIndex: 'phone' },
    { title: '角色', width: 90, render: (_, u) => <Tag>{ROLE[u.role]}</Tag> },
    {
      title: '状态', width: 90,
      render: (_, u) => <Tag color={u.status === 1 ? 'green' : 'red'}>{u.status === 1 ? '正常' : '已禁用'}</Tag>
    },
    {
      title: '操作', width: 110,
      render: (_, u) => u.role === 3
        ? <span style={{ color: '#c0c4cc' }}>管理员不可禁用</span>
        : (
          <Button size="small" danger={u.status === 1} onClick={() => toggle(u)}>
            {u.status === 1 ? '禁用' : '启用'}
          </Button>
        )
    }
  ]

  return (
    <div className="page">
      <h2 className="page-title">用户管理（FR-22）</h2>
      <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
        <Input style={{ width: 260 }} placeholder="昵称 / 手机号 / 账号" value={keyword}
          onChange={e => setKeyword(e.target.value)} onPressEnter={() => load()} />
        <Button type="primary" onClick={() => load()}>查询</Button>
      </div>
      <Table
        rowKey="id"
        dataSource={list}
        pagination={{ pageSize: 10 }}
        columns={columns}
      />
    </div>
  )
}
