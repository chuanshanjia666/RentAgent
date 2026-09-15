import React, { useEffect, useState } from 'react'
import { Badge, Button, Dropdown, Menu, Space } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import http from '../api'
import { useAuth } from '../auth'

/** 各角色导航菜单：[路径, 标签] */
const MENUS: Record<number, [string, string][]> = {
  1: [
    ['/app', '找房'],
    ['/app/ai', '🤖 AI 助手'],
    ['/app/appointments', '我的预约'],
    ['/app/contracts', '我的合同'],
    ['/app/orders', '我的订单'],
    ['/app/favorites', '收藏']
  ],
  2: [
    ['/landlord/houses', '房源管理'],
    ['/landlord/appointments', '预约管理'],
    ['/landlord/contracts', '我的合同'],
    ['/landlord/orders', '订单管理']
  ],
  3: [
    ['/admin/audit', '审核工作台'],
    ['/admin/users', '用户管理'],
    ['/admin/reports', '举报处理'],
    ['/admin/dashboard', '数据看板']
  ]
}

export default function NavLayout() {
  const auth = useAuth()
  const nav = useNavigate()
  const loc = useLocation()
  const [unread, setUnread] = useState(0)

  const items = MENUS[auth.role] || MENUS[1]
  const active = '/' + loc.pathname.split('/').slice(1, 3).join('/')

  const base = auth.role === 3 ? '/admin' : auth.role === 2 ? '/landlord' : '/app'

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const n = await http.get<number>('/notifications/unread-count')
        if (alive) setUnread(n)
      } catch (e) { /* 忽略 */ }
    }
    load()
    const timer = setInterval(load, 30000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [loc.pathname])

  return (
    <div>
      <div className="nav-header">
        <div className="nav-inner">
          <div className="brand" onClick={() => nav(auth.home)}>
            🏠 <b>RentAgent</b>
            <span className="badge">AI 智能租房</span>
          </div>
          <Menu mode="horizontal" selectedKeys={[active]} style={{ flex: 1, borderBottom: 'none', minWidth: 0 }}>
            {items.map(([path, label]) => (
              <Menu.Item key={path} onClick={() => nav(path)}>{label}</Menu.Item>
            ))}
          </Menu>
          <Space size="middle">
            <Badge count={unread} size="small">
              <Button type="text" onClick={() => nav(`${base}/notifications`)}>🔔 消息</Button>
            </Badge>
            <Dropdown
              menu={{
                items: [
                  { key: 'profile', label: '个人中心' },
                  { type: 'divider' },
                  { key: 'logout', label: '退出登录', danger: true }
                ],
                onClick: ({ key }) => {
                  if (key === 'logout') {
                    auth.logout()
                    nav('/login')
                  } else {
                    nav(`${base}/profile`)
                  }
                }
              }}
            >
              <span className="nav-user">{auth.nickname || '用户'} ▾</span>
            </Dropdown>
          </Space>
        </div>
      </div>
      <div style={{ background: '#f5f7fa', minHeight: 'calc(100vh - 60px)' }}>
        <Outlet />
      </div>
    </div>
  )
}
