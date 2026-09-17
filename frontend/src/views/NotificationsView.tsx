import { useEffect, useState } from 'react'
import { Badge, Empty, Pagination, Tabs, Tag } from 'antd'
import http from '../api'
import { fmtTime, NOTIFY_TYPE_NAME } from '../constants'

export default function NotificationsView() {
  const [tab, setTab] = useState('all')
  const [list, setList] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const size = 15

  async function load(p = page, t = tab) {
    const data = await http.get('/notifications', {
      params: { onlyUnread: t === 'unread', page: p, size }
    })
    setList(data.list)
    setTotal(data.total)
  }

  async function read(n: any) {
    if (n.isRead) return
    await http.patch(`/notifications/${n.id}/read`)
    // 必须返回新数组：原地改 n.isRead 改的是 state 里的同一个对象，React 判定引用未变而不重渲染
    setList(prev => prev.map(it => (it.id === n.id ? { ...it, isRead: 1 } : it)))
  }

  useEffect(() => {
    load(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 首屏只加载一次；切 Tab 由 Tabs 的 onChange 显式触发 load
  }, [])

  return (
    <div className="page">
      <h2 className="page-title">站内消息</h2>
      <Tabs
        activeKey={tab}
        onChange={k => {
          setTab(k)
          setPage(1)
          load(1, k)
        }}
        items={[
          { key: 'all', label: '全部' },
          { key: 'unread', label: '未读' }
        ]}
      />
      {list.map(n => (
        <div key={n.id} className={'notif' + (n.isRead ? '' : ' unread')} onClick={() => read(n)}>
          <Tag color={n.type === 2 || n.type === 5 ? 'orange' : 'blue'}>
            {NOTIFY_TYPE_NAME[n.type] || '消息'}
          </Tag>
          <div style={{ flex: 1 }}>
            <b>{n.title}</b>
            <div style={{ color: '#606266', fontSize: 13, marginTop: 2 }}>{n.content}</div>
          </div>
          <span style={{ color: '#c0c4cc', fontSize: 12 }}>{fmtTime(n.createdAt)}</span>
          {!n.isRead && <Badge dot />}
        </div>
      ))}
      {list.length === 0 && <Empty description="暂无消息" />}
      {total > size && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
          <Pagination
            total={total}
            pageSize={size}
            current={page}
            onChange={p => {
              setPage(p)
              load(p)
            }}
          />
        </div>
      )}
    </div>
  )
}
