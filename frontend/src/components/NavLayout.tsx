import { useEffect, useState } from 'react'
import { Badge, Button, Dropdown, Menu, Space } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import http from '../api'
import { useAuth } from '../auth'
import {
  IconBell,
  IconBot,
  IconBuilding,
  IconCalendar,
  IconChart,
  IconChat,
  IconChevronDown,
  IconFile,
  IconFlag,
  IconHeart,
  IconHome,
  IconList,
  IconReceipt,
  IconShield,
  IconUsers
} from './icons'

/** 各角色导航菜单：[路径, 标签, 图标] */
const MENUS: Record<number, [string, string, ReactNode][]> = {
  1: [
    ['/app', '找房', <IconHome key="i" />],
    ['/app/ai', 'AI 助手', <IconBot key="i" />],
    ['/app/appointments', '我的预约', <IconCalendar key="i" />],
    ['/app/contracts', '我的合同', <IconFile key="i" />],
    ['/app/orders', '我的订单', <IconReceipt key="i" />],
    ['/app/favorites', '收藏', <IconHeart key="i" />]
  ],
  2: [
    ['/landlord/houses', '房源管理', <IconBuilding key="i" />],
    ['/landlord/appointments', '预约管理', <IconCalendar key="i" />],
    ['/landlord/contracts', '我的合同', <IconFile key="i" />],
    ['/landlord/orders', '订单管理', <IconReceipt key="i" />]
  ],
  3: [
    ['/admin/audit', '审核工作台', <IconShield key="i" />],
    ['/admin/users', '用户管理', <IconUsers key="i" />],
    ['/admin/reports', '举报处理', <IconFlag key="i" />],
    ['/admin/logs', '操作留痕', <IconList key="i" />],
    ['/admin/chats', 'AI 对话审计', <IconChat key="i" />],
    ['/admin/dashboard', '数据看板', <IconChart key="i" />]
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
      } catch {
        /* 忽略：未读数拉取失败不影响导航 */
      }
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
            <span className="seal">租</span>
            <span className="brand-name">RentAgent</span>
            <span className="brand-plate">AI 智能租房</span>
          </div>
          <Menu
            className="nav-menu"
            mode="horizontal"
            selectedKeys={[active]}
            style={{ flex: 1, borderBottom: 'none', minWidth: 0 }}
          >
            {items.map(([path, label, icon]) => (
              <Menu.Item key={path} icon={icon} onClick={() => nav(path)}>
                {label}
              </Menu.Item>
            ))}
          </Menu>
          <Space size="middle">
            <Badge count={unread} size="small">
              <Button type="text" icon={<IconBell />} onClick={() => nav(`${base}/notifications`)}>
                消息
              </Button>
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
              <span className="nav-user">
                <span className="nav-avatar">{(auth.nickname || '用').slice(0, 1)}</span>
                {auth.nickname || '用户'}
                <span className="nav-caret">
                  <IconChevronDown size={13} />
                </span>
              </span>
            </Dropdown>
          </Space>
        </div>
      </div>
      <div className="app-body">
        <Outlet />
      </div>
    </div>
  )
}
