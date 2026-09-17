/* 共享 UI 组件 + P00 登录注册 + 页面目录 */
import React, { useState, useRef, useEffect } from 'react'
import { useStore, actions } from './store.jsx'
import { DB } from './data.js'

/* ---------- 基础组件 ---------- */
export function PageHead({ title, sub, right }) {
  return (
    <div className="spread mb">
      <div>
        <div className="page-title">{title}</div>
        {sub && <div className="page-sub" style={{ marginBottom: 0 }}>{sub}</div>}
      </div>
      {right}
    </div>
  )
}

export function StatusTag({ status }) {
  const map = {
    pending: ['待审核', 'tag-orange'], approved: ['已上架', 'tag-green'],
    rejected: ['已驳回', 'tag-red'], offlined: ['已下架', 'tag-gray'],
    draft: ['待签约', 'tag-blue'], signed: ['已签约', 'tag-green']
  }
  const appt = {
    pending: ['待确认', 'tag-orange'], confirmed: ['已确认', 'tag-blue'],
    rejected: ['已拒绝', 'tag-red'], completed: ['已完成', 'tag-green']
  }
  const lease = {
    active: ['在租', 'tag-green'], completed: ['已完成', 'tag-gray'],
    ended: ['已退租', 'tag-gray']
  }
  const all = { ...map, ...appt, ...lease }
  const [t, c] = all[status] || [status, 'tag-gray']
  return <span className={'tag ' + c}>{t}</span>
}

export function Stars({ value = 0, onChange }) {
  return (
    <span>
      {[1, 2, 3, 4, 5].map(i => (
        <span key={i}
          className={'star' + (i <= value ? ' on' : '')}
          style={onChange ? { cursor: 'pointer', fontSize: 20 } : undefined}
          onClick={onChange ? () => onChange(i) : undefined}>★</span>
      ))}
    </span>
  )
}

export function Field({ label, required, children }) {
  return (
    <div className="field">
      <label>{label}{required && <b className="req">*</b>}</label>
      {children}
    </div>
  )
}

export function Modal({ title, onClose, children, width }) {
  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" style={width ? { width } : undefined} onClick={e => e.stopPropagation()}>
        <div className="spread">
          <h3>{title}</h3>
          <button className="btn btn-sm" onClick={onClose}>✕</button>
        </div>
        <div className="hr" style={{ margin: '10px 0 14px' }} />
        {children}
      </div>
    </div>
  )
}

export function Empty({ text }) {
  return <div className="card" style={{ textAlign: 'center', color: 'var(--text-2)', padding: '40px 20px' }}>{text}</div>
}

export function NeedLogin({ role }) {
  const { navigate } = useStore()
  const names = { tenant: '租客', landlord: '房东', admin: '管理员' }
  return (
    <div className="auth-wrap">
      <div className="auth-card" style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 40 }}>🔒</div>
        <h3 style={{ margin: '10px 0' }}>请先以「{names[role]}」身份登录</h3>
        <p className="muted mb">该页面需要相应角色权限（RBAC 权限隔离演示，对应 NFR-03）。</p>
        <button className="btn btn-primary" onClick={() => navigate('p00')}>去登录</button>
      </div>
    </div>
  )
}

export function ListingCard({ l, showFav, reason }) {
  const { user, navigate, bump, toast, curListingId, setCurListingId } = useStore()
  const fav = user && actions.isFav(user.id, l.id)
  const open = () => { setCurListingId(l.id); navigate('p03') }
  return (
    <div className="lcard">
      <div className={`img g${(Number(l.id.replace(/\D/g, '')) % 6) + 1}`} onClick={open}>
        <span>房源图片 · {l.community}</span>
        {showFav && (
          <span className={'fav' + (fav ? ' on' : '')} onClick={e => {
            e.stopPropagation()
            if (!user) return toast('请先登录', 'err')
            const on = actions.toggleFav(user.id, l.id)
            bump(); toast(on ? '已收藏（FR-25）' : '已取消收藏', 'ok')
          }}>{fav ? '❤' : '♡'}</span>
        )}
      </div>
      <div className="bd" onClick={open}>
        <div className="tt">{l.title}</div>
        <div className="info">{l.layout} · {l.area}㎡ · {l.orientation} · {l.floor}</div>
        <div className="info">{l.region} · {l.community}{l.subway ? ' · 近地铁' : ''}</div>
        {reason && <div className="info" style={{ color: 'var(--primary)' }}>🤖 {reason}</div>}
        <div className="spread">
          <span className="price">{l.price}<small> 元/月</small></span>
          <span className="muted">{l.deposit}</span>
        </div>
      </div>
    </div>
  )
}

/* ---------- P00 登录 / 注册 ---------- */
export function Auth() {
  const { login, navigate, toast } = useStore()
  const [mode, setMode] = useState('login')            // login | register
  const [loginType, setLoginType] = useState('pwd')    // pwd | captcha
  const [phone, setPhone] = useState('')
  const [pwd, setPwd] = useState('')
  const [captcha, setCaptcha] = useState('')
  const [regRole, setRegRole] = useState(null)
  const [attempts, setAttempts] = useState({})
  const [lockLeft, setLockLeft] = useState(0)

  useEffect(() => {
    if (lockLeft <= 0) return
    const t = setInterval(() => setLockLeft(s => (s > 0 ? s - 1 : 0)), 1000)
    return () => clearInterval(t)
  }, [lockLeft > 0])

  const goHome = u => {
    toast(`欢迎回来，${u.name}`, 'ok')
    navigate(u.role === 'tenant' ? 'p01' : u.role === 'landlord' ? 'p11' : 'p20')
  }

  const tryLogin = () => {
    if (lockLeft > 0) return
    const u = DB.users.find(x => x.phone === phone.trim())
    if (!u) return toast('该手机号尚未注册', 'err')
    if (u.disabled) return toast('该账号已被禁用，请联系平台管理员（FR-22）', 'err')
    if (loginType === 'pwd' && pwd !== u.password) {
      const n = (attempts[phone] || 0) + 1
      setAttempts({ ...attempts, [phone]: n })
      if (n >= 5) {
        setLockLeft(600)
        return toast('密码错误 5 次，账号已锁定 10 分钟（FR-02）', 'err')
      }
      return toast(`密码错误，还可尝试 ${5 - n} 次`, 'err')
    }
    if (loginType === 'captcha') {
      if (captcha.trim() !== DB.demoCaptcha) return toast('验证码错误（演示验证码：246810）', 'err')
    }
    setAttempts({ ...attempts, [phone]: 0 })
    login(u); goHome(u)
  }

  const tryRegister = () => {
    if (!/^1\d{10}$/.test(phone.trim())) return toast('请输入 11 位手机号', 'err')
    if (DB.users.some(u => u.phone === phone.trim())) return toast('该手机号已注册，请直接登录', 'err')
    if (captcha.trim() !== DB.demoCaptcha) return toast('验证码错误（演示验证码：246810）', 'err')
    if (pwd.length < 6) return toast('密码至少 6 位', 'err')
    if (!regRole) return toast('请选择注册角色', 'err')
    const u = {
      id: 'u' + Date.now(), role: regRole, name: regRole === 'landlord' ? '新房东' : '新租客',
      phone: phone.trim(), password: pwd, verified: false, disabled: false
    }
    DB.users.push(u)
    login(u)
    toast('注册成功，已自动登录（FR-01）', 'ok')
    navigate(regRole === 'landlord' ? 'p11' : 'p01')
  }

  const quick = u => { login(u); goHome(u) }

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="auth-tabs">
          <div className={'t' + (mode === 'login' ? ' on' : '')} onClick={() => setMode('login')}>登录</div>
          <div className={'t' + (mode === 'register' ? ' on' : '')} onClick={() => setMode('register')}>注册</div>
        </div>

        {lockLeft > 0 && (
          <div className="banner banner-error">
            登录已锁定（FR-02 验收演示）：密码连续错误 5 次，剩余 {Math.floor(lockLeft / 60)}:{String(lockLeft % 60).padStart(2, '0')}
          </div>
        )}

        {mode === 'login' ? (
          <>
            <div className="seg mb" style={{ marginBottom: 16 }}>
              <span className={'s' + (loginType === 'pwd' ? ' on' : '')} onClick={() => setLoginType('pwd')}>账号密码登录</span>
              <span className={'s' + (loginType === 'captcha' ? ' on' : '')} onClick={() => setLoginType('captcha')}>验证码登录</span>
            </div>
            <Field label="手机号" required>
              <input className="input" placeholder="请输入手机号" value={phone} onChange={e => setPhone(e.target.value)} />
            </Field>
            {loginType === 'pwd' ? (
              <Field label="密码" required>
                <input className="input" type="password" placeholder="请输入密码" value={pwd} onChange={e => setPwd(e.target.value)} />
              </Field>
            ) : (
              <Field label="短信验证码" required>
                <div className="captcha-row">
                  <input className="input" placeholder="请输入验证码" value={captcha} onChange={e => setCaptcha(e.target.value)} />
                  <div className="captcha-code">{DB.demoCaptcha}</div>
                </div>
                <div className="muted mt" style={{ marginTop: 6 }}>演示说明：开发期验证码为 Mock 通道（FR-01 v1.1），右侧为固定演示码。</div>
              </Field>
            )}
            <button className="btn btn-primary btn-block mt" disabled={lockLeft > 0} onClick={tryLogin}>登 录</button>
          </>
        ) : (
          <>
            <Field label="注册角色" required>
              <div className="role-pick">
                <div className={'rp' + (regRole === 'tenant' ? ' on' : '')} onClick={() => setRegRole('tenant')}>
                  <div className="ico">🧑‍💼</div><div className="nm">我是租客</div>
                </div>
                <div className={'rp' + (regRole === 'landlord' ? ' on' : '')} onClick={() => setRegRole('landlord')}>
                  <div className="ico">🏠</div><div className="nm">我是房东</div>
                </div>
              </div>
            </Field>
            <Field label="手机号" required>
              <input className="input" placeholder="请输入手机号" value={phone} onChange={e => setPhone(e.target.value)} />
            </Field>
            <Field label="验证码" required>
              <div className="captcha-row">
                <input className="input" placeholder="请输入验证码" value={captcha} onChange={e => setCaptcha(e.target.value)} />
                <div className="captcha-code">{DB.demoCaptcha}</div>
              </div>
            </Field>
            <Field label="设置密码" required>
              <input className="input" type="password" placeholder="至少 6 位" value={pwd} onChange={e => setPwd(e.target.value)} />
            </Field>
            <button className="btn btn-primary btn-block mt" onClick={tryRegister}>注 册 并 登 录</button>
          </>
        )}

        <div className="demo-accounts">
          <div className="muted mb" style={{ marginBottom: 6 }}>演示快捷入口（密码均为 123456）：</div>
          <button className="btn btn-sm da" onClick={() => quick(DB.users[0])}>🧑 租客·小陈</button>
          <button className="btn btn-sm da" onClick={() => quick(DB.users[1])}>🏠 房东·王房东（未实名）</button>
          <button className="btn btn-sm da" onClick={() => quick(DB.users[2])}>🏠 房东·李房东（已实名）</button>
          <button className="btn btn-sm da" onClick={() => quick(DB.users[3])}>🛡️ 管理员</button>
        </div>
      </div>
    </div>
  )
}

/* ---------- 页面目录 ---------- */
export function Directory() {
  const { navigate } = useStore()
  const groups = [
    { name: '全局', items: [
      ['p00', 'P00 登录/注册页', 'FR-01 / FR-02'],
      ['p01m', 'P01M 移动端示意（375px）', 'NFR-06']
    ]},
    { name: '租客端', items: [
      ['p01', 'P01 首页（搜索+推荐+AI入口）', 'FR-09 / FR-11'],
      ['p02', 'P02 房源列表（筛选+地图找房）', 'FR-09 / FR-10'],
      ['p03', 'P03 房源详情（图集+参数+评价）', 'FR-20 / FR-25'],
      ['p03a', 'P03A 状态演示：房源-已下架', 'FR-06'],
      ['p04', 'P04 AI 助手（找房+智能客服）', 'FR-12 / FR-13'],
      ['p04a', 'P04A 状态演示：AI 超时/转人工', 'NFR-05 / 风险R2'],
      ['p05', 'P05 预约看房', 'FR-17'],
      ['p05a', 'P05A 状态演示：预约-已拒绝', 'FR-17'],
      ['p05b', 'P05B 状态演示：预约-已完成', 'FR-17'],
      ['p06', 'P06 签约页（合同+AI解读）', 'FR-14 / FR-18'],
      ['p06a', 'P06A 状态演示：风险条款示例', 'FR-14'],
      ['p07', 'P07 个人中心（收藏/订单/消息）', 'FR-04 / 19 / 20 / 21 / 25']
    ]},
    { name: '房东端', items: [
      ['p10', 'P10 房源发布（AI识别+定价）', 'FR-05 / FR-08 / FR-15'],
      ['p11', 'P11 房源管理（上下架/驳回）', 'FR-06 / FR-07'],
      ['p12', 'P12 智能定价建议弹窗', 'FR-15'],
      ['p13', 'P13 预约/订单管理', 'FR-17 / FR-19'],
      ['p14', 'P14 实名认证', 'FR-03']
    ]},
    { name: '管理端', items: [
      ['p20', 'P20 数据统计看板', 'FR-24'],
      ['p21', 'P21 房源审核工作台（AI风险分）', 'FR-07 / FR-16 / FR-23'],
      ['p22', 'P22 用户管理', 'FR-22'],
      ['p23', 'P23 智能客服后台（知识库）', 'FR-13']
    ]}
  ]
  return (
    <div>
      <PageHead title="页面目录（22 页原型导航）" sub="与《04-原型工具使用方法》v1.1 页面规划清单一一对应，点击任意页面直接跳转体验" />
      {groups.map(g => (
        <div key={g.name} className="mb">
          <div className="row mb" style={{ marginBottom: 8 }}>
            <span className="tag tag-blue">{g.name}</span>
          </div>
          <div className="dir-grid">
            {g.items.map(([r, name, fr]) => (
              <div key={r} className="dir-item" onClick={() => navigate(r)}>
                <span>{name}</span>
                <span className="no">{fr}</span>
              </div>
            ))}
          </div>
        </div>
      ))}
      <div className="banner banner-info mt">演示提示：状态演示页（P03A/P04A/P05A/P05B/P06A）无需登录可直接访问；业务页面按角色权限隔离（RBAC）。</div>
    </div>
  )
}
