/* 应用外壳：路由 / 顶栏 / 角色 RBAC 守卫 / 悬浮 AI 按钮 / Toast */
import React, { useEffect } from 'react'
import { StoreProvider, useStore, actions } from './store.jsx'
import { Auth, Directory, NeedLogin } from './ui.jsx'
import { Home, HomeMobile, ListPage, Detail, AiChat, AiChatError, Booking, ContractPage, Profile } from './tenant.jsx'
import { Publish, Manage, Orders, Verify } from './landlord.jsx'
import { Dashboard, Review, UserAdmin, FaqAdmin } from './admin.jsx'

const ROUTES = {
  p00: { C: Auth },
  dir: { C: Directory },
  p01: { C: Home, role: 'tenant' },
  p01m: { C: HomeMobile },
  p02: { C: ListPage, role: 'tenant' },
  p03: { C: Detail, role: 'tenant' },
  p03a: { C: () => <Detail offlined /> },
  p04: { C: AiChat, role: 'tenant' },
  p04a: { C: AiChatError },
  p05: { C: Booking, role: 'tenant' },
  p05a: { C: () => <Booking demoStatus="rejected" /> },
  p05b: { C: () => <Booking demoStatus="completed" /> },
  p06: { C: ContractPage, role: 'tenant' },
  p06a: { C: () => <ContractPage riskDemo /> },
  p07: { C: Profile, role: 'tenant' },
  p10: { C: Publish, role: 'landlord' },
  p11: { C: Manage, role: 'landlord' },
  p12: { C: () => <Publish priceOpen />, role: 'landlord' },
  p13: { C: Orders, role: 'landlord' },
  p14: { C: Verify, role: 'landlord' },
  p20: { C: Dashboard, role: 'admin' },
  p21: { C: Review, role: 'admin' },
  p22: { C: UserAdmin, role: 'admin' },
  p23: { C: FaqAdmin, role: 'admin' }
}

const ROLE_NAME = { tenant: '租客', landlord: '房东', admin: '管理员' }

function Topbar() {
  const { user, route, navigate, logout } = useStore()
  const role = user?.role
  const navs = role === 'tenant'
    ? [['p01', '首页'], ['p02', '找房'], ['p04', 'AI 助手'], ['p07', '个人中心']]
    : role === 'landlord'
      ? [['p10', '房源发布'], ['p11', '房源管理'], ['p13', '预约/订单'], ['p14', '实名认证']]
      : role === 'admin'
        ? [['p20', '数据看板'], ['p21', '审核工作台'], ['p22', '用户管理'], ['p23', '知识库']]
        : []
  const unread = user ? 0 : 0
  return (
    <header id="topbar">
      <div className="logo" onClick={() => navigate(user ? (role === 'tenant' ? 'p01' : role === 'landlord' ? 'p11' : 'p20') : 'p00')}>
        🏠 RentAgent <span className="badge-proto">高保真原型</span>
      </div>
      <nav className="nav-links">
        {navs.map(([r, t]) => (
          <a key={r} href={'#/' + r} className={route === r ? 'active' : ''}>
            {t}
            {r === 'p07' && <MsgBadge />}
          </a>
        ))}
      </nav>
      <div className="nav-right">
        <a href="#/dir" className={'btn btn-sm' + (route === 'dir' ? ' btn-primary' : '')}>📄 页面目录</a>
        {user ? (
          <>
            <span className="role-chip">{ROLE_NAME[role]}</span>
            <span className="nav-user">{user.name}</span>
            <button className="btn btn-sm" onClick={logout}>退出</button>
          </>
        ) : (
          <a href="#/p00" className="btn btn-sm btn-primary">登录 / 注册</a>
        )}
      </div>
    </header>
  )
}

function MsgBadge() {
  const { user } = useStore()
  if (!user) return null
  const n = actions.unread(user.id)
  if (!n) return null
  return <span className="msg-badge">{n}</span>
}

function Shell() {
  const { route, user, navigate, toasts } = useStore()
  const def = ROUTES[route] || { C: Directory }
  let C = def.C
  if (def.role && (!user || user.role !== def.role)) C = () => <NeedLogin role={def.role} />

  useEffect(() => { window.scrollTo(0, 0) }, [route])
  const showFab = user?.role === 'tenant' && route !== 'p04'

  return (
    <>
      <Topbar />
      <main id="app" key={route + (user ? user.id : 'guest')}>
        <C />
      </main>
      {showFab && <div className="ai-fab" onClick={() => navigate('p04')}>🤖 AI 找房</div>}
      <div id="toast-wrap">
        {toasts.map(t => <div key={t.id} className={'toast ' + t.type}>{t.msg}</div>)}
      </div>
    </>
  )
}

export default function App() {
  return (
    <StoreProvider>
      <Shell />
    </StoreProvider>
  )
}
