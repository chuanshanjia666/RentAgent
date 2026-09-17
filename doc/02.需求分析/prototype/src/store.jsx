/* 全局状态：会话 / 路由 / Toast / 共享数据操作（直接变更 DB 后 bump 触发重渲染） */
import React, { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { DB } from './data.js'

const Ctx = createContext(null)
export const useStore = () => useContext(Ctx)

function currentRoute() {
  return (location.hash || '#/p00').replace(/^#\//, '') || 'p00'
}

function fmtTime() {
  const d = new Date()
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

let toastSeq = 0

export function StoreProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      const id = localStorage.getItem('ra_user')
      return DB.users.find(u => u.id === id) || null
    } catch { return null }
  })
  const [version, setVersion] = useState(0)
  const [route, setRoute] = useState(currentRoute)
  const [curListingId, setCurListingId] = useState('l1')
  const [editListingId, setEditListingId] = useState(null)
  const [kw, setKw] = useState('')
  const [toasts, setToasts] = useState([])

  const bump = useCallback(() => setVersion(v => v + 1), [])
  const navigate = useCallback(r => { location.hash = '#/' + r }, [])
  const toast = useCallback((msg, type = '') => {
    const id = ++toastSeq
    setToasts(ts => [...ts, { id, msg, type }])
    setTimeout(() => setToasts(ts => ts.filter(t => t.id !== id)), 2600)
  }, [])

  useEffect(() => {
    const onHash = () => setRoute(currentRoute())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  const login = useCallback(u => {
    setUser(u)
    try { localStorage.setItem('ra_user', u.id) } catch { /* 忽略 */ }
  }, [])
  const logout = useCallback(() => {
    setUser(null)
    try { localStorage.removeItem('ra_user') } catch { /* 忽略 */ }
    location.hash = '#/p00'
  }, [])

  const value = { user, login, logout, version, bump, route, navigate, toast, toasts, curListingId, setCurListingId, editListingId, setEditListingId, kw, setKw }
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

/* ---------- 数据操作（模块级，调用后需 bump()） ---------- */
export const actions = {
  isFav(userId, listingId) {
    return DB.favorites.some(f => f.userId === userId && f.listingId === listingId)
  },
  toggleFav(userId, listingId) {
    const i = DB.favorites.findIndex(f => f.userId === userId && f.listingId === listingId)
    if (i >= 0) DB.favorites.splice(i, 1)
    else DB.favorites.push({ userId, listingId })
    return i < 0
  },
  notify(userId, text) {
    DB.notifications.unshift({ id: 'n' + Date.now() + Math.random().toString(36).slice(2, 5), userId, text, time: fmtTime(), read: false })
  },
  unread(userId) {
    return DB.notifications.filter(n => n.userId === userId && !n.read).length
  },
  markRead(id) {
    const n = DB.notifications.find(x => x.id === id)
    if (n) n.read = true
  },
  markAllRead(userId) {
    DB.notifications.forEach(n => { if (n.userId === userId) n.read = true })
  },
  addAppointment({ listingId, tenantId, landlordId, date, slot }) {
    const a = { id: 'a' + Date.now(), listingId, tenantId, landlordId, date, slot, status: 'pending' }
    DB.appointments.unshift(a)
    actions.notify(landlordId, `新预约待确认：${DB.listings.find(l => l.id === listingId)?.title || ''}（${date} ${slot}）`)
    return a
  },
  setApptStatus(id, status, reason) {
    const a = DB.appointments.find(x => x.id === id)
    if (!a) return
    a.status = status
    if (reason !== undefined) a.reason = reason
    const lt = DB.listings.find(l => l.id === a.listingId)?.title || ''
    const textMap = {
      confirmed: `你的预约已确认：${lt}（${a.date} ${a.slot}）`,
      rejected: `你的预约已被拒绝：${lt}${reason ? `（${reason}）` : ''}`,
      completed: `看房已完成：${lt}，欢迎对房源/房东进行评价`
    }
    if (textMap[status]) actions.notify(a.tenantId, textMap[status])
  },
  signAs(contractId, who) {
    const c = DB.contracts.find(x => x.id === contractId)
    if (!c) return
    if (who === 'tenant') c.tenantSigned = true
    if (who === 'landlord') c.landlordSigned = true
    if (c.tenantSigned && c.landlordSigned) {
      c.status = 'signed'
      actions.notify(c.tenantId, '合同已完成双方签约，可在个人中心查看合同副本与租金计划')
      actions.notify(c.landlordId, '合同已完成双方签约，可在预约/订单页管理租约')
    }
  },
  addListing(data, landlordId) {
    const l = {
      id: 'l' + Date.now(), status: 'pending', risk: 20, riskPoints: ['新发布房源，待人工复核'], landlordId,
      mapX: 20 + Math.round(Math.random() * 60), mapY: 20 + Math.round(Math.random() * 60), subway: false, ...data
    }
    DB.listings.unshift(l)
    DB.users.filter(u => u.role === 'admin').forEach(u => actions.notify(u.id, `新房源待审核：${l.title}`))
    return l
  },
  updateListing(id, data) {
    const l = DB.listings.find(x => x.id === id)
    if (!l) return
    Object.assign(l, data, { status: 'pending', rejectReason: undefined })
    DB.users.filter(u => u.role === 'admin').forEach(u => actions.notify(u.id, `房源已编辑，重新进入待审核：${l.title}`))
  },
  toggleOnline(id) {
    const l = DB.listings.find(x => x.id === id)
    if (!l) return null
    l.status = l.status === 'approved' ? 'offlined' : 'approved'
    return l
  },
  reviewListing(id, pass, reason) {
    const l = DB.listings.find(x => x.id === id)
    if (!l) return
    l.status = pass ? 'approved' : 'rejected'
    if (!pass) l.rejectReason = reason
    actions.notify(l.landlordId, pass ? `房源审核通过并上架：${l.title}` : `房源审核未通过：${l.title}（${reason}）`)
  },
  verifyOk(userId) {
    const u = DB.users.find(x => x.id === userId)
    if (u) {
      u.verified = true
      actions.notify(userId, '实名认证审核通过，现在可以发布房源了')
    }
  },
  setDisabled(userId, disabled) {
    const u = DB.users.find(x => x.id === userId)
    if (u) {
      u.disabled = disabled
      if (disabled) actions.notify(userId, '你的账号已被管理员禁用，如有疑问请联系平台')
    }
  },
  saveFaq(f) {
    if (f.id) {
      const i = DB.faqs.findIndex(x => x.id === f.id)
      if (i >= 0) DB.faqs[i] = f
    } else {
      f.id = 'f' + Date.now()
      DB.faqs.unshift(f)
    }
  },
  delFaq(id) {
    const i = DB.faqs.findIndex(x => x.id === id)
    if (i >= 0) DB.faqs.splice(i, 1)
  },
  addReview({ listingId, byUserId, tenantName, stars, text }) {
    const d = new Date()
    DB.reviews.unshift({ id: 'r' + Date.now(), listingId, byUserId, tenantName, stars, text, date: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` })
    const l = DB.listings.find(x => x.id === listingId)
    if (l) actions.notify(l.landlordId, `收到新评价：${l.title}（${stars} 星）`)
  },
  hasReviewed(userId, listingId) {
    return DB.reviews.some(r => r.byUserId === userId && r.listingId === listingId)
  },
  recordView(userId, listingId) {
    const h = (DB.history[userId] ||= { viewed: [], searches: [] })
    h.viewed = [listingId, ...h.viewed.filter(x => x !== listingId)].slice(0, 10)
  },
  recordSearch(userId, text) {
    if (!text) return
    const h = (DB.history[userId] ||= { viewed: [], searches: [] })
    h.searches = [text, ...h.searches.filter(x => x !== text)].slice(0, 8)
  },
  applyLease(leaseId, type) {
    const le = DB.leases.find(x => x.id === leaseId)
    if (!le) return
    le.applyType = type
    const lt = DB.listings.find(l => l.id === le.listingId)?.title || ''
    actions.notify(le.landlordId, `租客${type === 'renew' ? '发起续租' : '发起退租'}申请：${lt}`)
  },
  resolveLease(leaseId, agree) {
    const le = DB.leases.find(x => x.id === leaseId)
    if (!le || !le.applyType) return
    const lt = DB.listings.find(l => l.id === le.listingId)?.title || ''
    const type = le.applyType
    if (agree && type === 'terminate') {
      le.status = 'ended'
      const l = DB.listings.find(x => x.id === le.listingId)
      if (l && l.status === 'approved') { /* 房源保持可租状态 */ }
    }
    le.applyType = null
    actions.notify(le.tenantId, `你的${type === 'renew' ? '续租' : '退租'}申请${agree ? '已通过' : '被拒绝'}：${lt}`)
  },
  resolveReport(id, takeDown) {
    const rp = DB.reports.find(x => x.id === id)
    if (!rp) return
    rp.status = 'resolved'
    const l = DB.listings.find(x => x.id === rp.listingId)
    if (takeDown && l) {
      l.status = 'offlined'
      actions.notify(l.landlordId, `房源因举报核实被下架：${l.title}，如有异议请联系平台`)
    }
  }
}
