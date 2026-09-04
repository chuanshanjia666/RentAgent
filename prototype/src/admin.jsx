/* 管理端页面：P20 数据看板 / P21 审核工作台 / P22 用户管理 / P23 客服知识库 */
import React, { useState } from 'react'
import { useStore, actions } from './store.jsx'
import { DB } from './data.js'
import { PageHead, StatusTag, Field, Modal, Empty } from './ui.jsx'

/* ---------- P20 数据统计看板（FR-24） ---------- */
function Bars({ labels, a, b, aName, bName }) {
  const max = Math.max(...a, ...b, 1)
  return (
    <div>
      <div className="chart-legend">
        <span className="li">{aName}</span>
        <span className="li alt">{bName}</span>
      </div>
      <div className="bars">
        {labels.map((lb, i) => (
          <div key={lb} className="b">
            <div className="bar" style={{ height: `${(a[i] / max) * 100}%` }} data-v={`${aName} ${a[i]}`} />
            <div className="bar alt" style={{ height: `${(b[i] / max) * 100}%` }} data-v={`${bName} ${b[i]}`} />
          </div>
        ))}
      </div>
      <div className="bar-labels">{labels.map(lb => <div key={lb} className="bl">{lb}</div>)}</div>
    </div>
  )
}

export function Dashboard() {
  const [dim, setDim] = useState('daily')
  const s = DB.stats[dim]
  const c = DB.stats.cards
  return (
    <div>
      <PageHead title="数据统计看板" sub="用户量、房源量、成交量、AI 对话量可视化（FR-24）· 支持按日 / 周 / 月切换"
        right={
          <div className="seg">
            {[['daily', '日'], ['weekly', '周'], ['monthly', '月']].map(([k, t]) => (
              <span key={k} className={'s' + (dim === k ? ' on' : '')} onClick={() => setDim(k)}>{t}</span>
            ))}
          </div>
        } />
      <div className="metric-grid">
        <div className="metric"><div className="k">注册用户</div><div className="v">{c.users}</div><div className="d up">↑ 12% 较上期</div></div>
        <div className="metric"><div className="k">在架房源</div><div className="v">{c.listings}</div><div className="d up">↑ 8% 较上期</div></div>
        <div className="metric"><div className="k">累计成交</div><div className="v">{c.deals}</div><div className="d up">↑ 15% 较上期</div></div>
        <div className="metric"><div className="k">AI 对话量</div><div className="v">{c.ai}</div><div className="d up">↑ 21% 较上期</div></div>
      </div>
      <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
        <div className="card">
          <b className="mb" style={{ display: 'block' }}>用户与房源增长</b>
          <Bars labels={s.labels} a={s.users} b={s.listings} aName="新增用户" bName="新增房源" />
        </div>
        <div className="card">
          <b className="mb" style={{ display: 'block' }}>成交与 AI 对话</b>
          <Bars labels={s.labels} a={s.deals} b={s.ai} aName="成交量" bName="AI 对话量" />
        </div>
      </div>
      <div className="muted mt" style={{ marginTop: 12 }}>图表正确反映库内数据（FR-24 验收）；真实实现采用 ECharts（确认书技术栈）。</div>
    </div>
  )
}

/* ---------- P21 房源审核工作台（FR-07 / FR-16 / FR-23） ---------- */
export function Review() {
  const { bump, toast } = useStore()
  const [tab, setTab] = useState('listing')
  const [expand, setExpand] = useState(null)
  const [rejecting, setRejecting] = useState(null)
  const [reason, setReason] = useState('')
  const pending = DB.listings.filter(l => l.status === 'pending').sort((a, b) => b.risk - a.risk)

  const review = (l, pass, why) => {
    actions.reviewListing(l.id, pass, why); bump()
    toast(pass ? '已通过并上架，房东已收到通知' : '已驳回，驳回理由已通知房东', pass ? 'ok' : '')
    setRejecting(null); setReason('')
  }

  return (
    <div>
      <PageHead title="房源审核工作台"
        sub="人工审核（FR-07）+ AI 虚假房源风险识别（FR-16，P2）· 风险分 ≥ 70 自动置顶，管理员保留最终裁决权"
        right={
          <div className="tabs" style={{ marginBottom: 0 }}>
            <span className={'tb' + (tab === 'listing' ? ' on' : '')} onClick={() => setTab('listing')}>房源审核（{pending.length}）</span>
            <span className={'tb' + (tab === 'report' ? ' on' : '')} onClick={() => setTab('report')}>评价举报处理（FR-23）</span>
          </div>
        } />
      {tab === 'listing' && (
        pending.length === 0 ? <Empty text="暂无待审核房源 🎉" /> : (
          <div className="tbl-wrap">
            <table className="table">
              <thead><tr><th>房源</th><th>AI 风险分</th><th>状态</th><th style={{ width: 300 }}>操作</th></tr></thead>
              <tbody>
                {pending.map(l => (
                  <tr key={l.id} className={l.risk >= 70 ? 'pinned' : ''}>
                    <td>
                      {l.risk >= 70 && <span className="tag tag-red">高风险 · 置顶</span>}
                      <b>{l.title}</b>
                      <div className="muted">{l.region} · {l.community} · {l.layout} · {l.price} 元/月</div>
                      {expand === l.id && (
                        <div className="banner banner-error" style={{ marginTop: 8, marginBottom: 0 }}>
                          <b>AI 疑点列表（FR-16）：</b>
                          <div style={{ marginTop: 4 }}>
                            {(l.riskPoints || ['暂无明显疑点']).map((p, i) => <div key={i}>· {p}</div>)}
                          </div>
                        </div>
                      )}
                    </td>
                    <td>
                      <b style={{ color: l.risk >= 70 ? 'var(--danger)' : l.risk >= 40 ? '#C97A0A' : 'var(--ok)', fontSize: 16 }}>{l.risk}</b>
                      <div className="muted">阈值 70</div>
                    </td>
                    <td><StatusTag status={l.status} /></td>
                    <td>
                      <button className="btn btn-sm" onClick={() => setExpand(expand === l.id ? null : l.id)}>{expand === l.id ? '收起疑点' : '查看 AI 疑点'}</button>{' '}
                      {rejecting === l.id ? (
                        <span className="row" style={{ display: 'inline-flex' }}>
                          <input className="input" style={{ width: 150 }} placeholder="驳回理由（必填）" value={reason} onChange={e => setReason(e.target.value)} />
                          <button className="btn btn-danger btn-sm" onClick={() => reason.trim() ? review(l, false, reason.trim()) : toast('请填写驳回理由（FR-07 验收）', 'err')}>确认驳回</button>
                          <button className="btn btn-sm" onClick={() => setRejecting(null)}>取消</button>
                        </span>
                      ) : (
                        <>
                          <button className="btn btn-primary btn-sm" onClick={() => review(l, true)}>通过</button>{' '}
                          <button className="btn btn-danger btn-sm" onClick={() => setRejecting(l.id)}>驳回</button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
      {tab === 'report' && (
        <div className="tbl-wrap">
          <table className="table">
            <thead><tr><th>举报内容</th><th>举报人</th><th>状态</th><th style={{ width: 240 }}>处理（FR-23：处理后通知双方）</th></tr></thead>
            <tbody>
              {DB.reports.length === 0 && <tr><td colSpan={4} className="muted">暂无举报</td></tr>}
              {DB.reports.map(rp => {
                const l = DB.listings.find(x => x.id === rp.listingId)
                return (
                  <tr key={rp.id}>
                    <td><b>{l?.title}</b><div className="muted">{rp.reason}</div></td>
                    <td>{rp.reporter}<div className="muted">{rp.time}</div></td>
                    <td>{rp.status === 'pending' ? <span className="tag tag-orange">待处理</span> : <span className="tag tag-green">已处理</span>}</td>
                    <td>
                      {rp.status === 'pending' ? (
                        <>
                          <button className="btn btn-danger btn-sm" onClick={() => { actions.resolveReport(rp.id, true); bump(); toast('房源已下架，已通知双方（FR-23）', 'ok') }}>核实属实：下架房源</button>{' '}
                          <button className="btn btn-sm" onClick={() => { actions.resolveReport(rp.id, false); bump(); toast('举报驳回，已通知双方') }}>举报不实</button>
                        </>
                      ) : <span className="muted">—</span>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

/* ---------- P22 用户管理（FR-22） ---------- */
export function UserAdmin() {
  const { user, bump, toast } = useStore()
  const [q, setQ] = useState('')
  const list = DB.users.filter(u => !q || u.name.includes(q) || u.phone.includes(q))
  return (
    <div>
      <PageHead title="用户管理" sub="查询、禁用 / 启用账号（FR-22）· 禁用后该用户立即无法登录"
        right={<input className="input" style={{ width: 240 }} placeholder="搜索昵称 / 手机号" value={q} onChange={e => setQ(e.target.value)} />} />
      <div className="tbl-wrap">
        <table className="table">
          <thead><tr><th>用户</th><th>角色</th><th>实名状态</th><th>账号状态</th><th style={{ width: 140 }}>操作</th></tr></thead>
          <tbody>
            {list.map(u => (
              <tr key={u.id}>
                <td><b style={u.disabled ? { color: 'var(--danger)' } : undefined}>{u.name}</b><div className="muted">{u.phone}</div></td>
                <td>{u.role === 'tenant' ? '租客' : u.role === 'landlord' ? '房东' : '管理员'}</td>
                <td>{u.verified ? <span className="tag tag-green">已实名</span> : <span className="tag tag-gray">未实名</span>}</td>
                <td>{u.disabled ? <span className="tag tag-red">已禁用</span> : <span className="tag tag-green">正常</span>}</td>
                <td>
                  {u.role === 'admin'
                    ? <span className="muted">管理员账号不可操作</span>
                    : u.disabled
                      ? <button className="btn btn-sm" onClick={() => { actions.setDisabled(u.id, false); bump(); toast('已启用该账号', 'ok') }}>启用</button>
                      : <button className="btn btn-danger btn-sm" onClick={() => { actions.setDisabled(u.id, true); bump(); toast('已禁用，该用户将无法登录（FR-22 验收）', 'ok') }}>禁用</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="banner banner-info mt">演示提示：禁用"历史用户（13800000005）"后，可到 P00 登录页尝试该账号，验证"禁用后立即无法登录"。</div>
    </div>
  )
}

/* ---------- P23 智能客服后台（FR-13 知识库维护） ---------- */
export function FaqAdmin() {
  const { bump, toast } = useStore()
  const [editing, setEditing] = useState(null) // null | {} | faq
  const [form, setForm] = useState({ q: '', a: '', keywords: '' })

  const openNew = () => { setForm({ q: '', a: '', keywords: '' }); setEditing({}) }
  const openEdit = f => { setForm({ q: f.q, a: f.a, keywords: (f.keywords || []).join(',') }); setEditing(f) }
  const save = () => {
    if (!form.q.trim() || !form.a.trim()) return toast('问题与答案不能为空', 'err')
    actions.saveFaq(editing?.id
      ? { ...editing, q: form.q.trim(), a: form.a.trim(), keywords: form.keywords.split(/[,，]/).map(s => s.trim()).filter(Boolean) }
      : { q: form.q.trim(), a: form.a.trim(), keywords: form.keywords.split(/[,，]/).map(s => s.trim()).filter(Boolean), source: '《RentAgent 平台规则 FAQ》（管理员新增）' })
    bump(); setEditing(null)
    toast('知识库已更新，P04 智能客服即时生效（FR-13）', 'ok')
  }

  return (
    <div>
      <PageHead title="智能客服后台 · 知识库维护" sub={`当前知识库 ${DB.faqs.length} 条（验收目标 ≥ 50 条，演示数据 12 条）（FR-13）· 增删改后即时作用于 AI 客服`}
        right={<button className="btn btn-primary" onClick={openNew}>＋ 新增 FAQ</button>} />
      <div className="tbl-wrap">
        <table className="table">
          <thead><tr><th style={{ width: 240 }}>问题</th><th>答案</th><th>命中关键词</th><th style={{ width: 130 }}>操作</th></tr></thead>
          <tbody>
            {DB.faqs.map(f => (
              <tr key={f.id}>
                <td><b>{f.q}</b><div className="muted">{f.source}</div></td>
                <td style={{ lineHeight: 1.7 }}>{f.a}</td>
                <td>{(f.keywords || []).map(k => <span key={k} className="tag tag-blue">{k}</span>)}</td>
                <td>
                  <button className="btn btn-sm" onClick={() => openEdit(f)}>编辑</button>{' '}
                  <button className="btn btn-danger btn-sm" onClick={() => { if (window.confirm('确认删除该 FAQ？')) { actions.delFaq(f.id); bump(); toast('已删除') } }}>删除</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <Modal title={editing.id ? '编辑 FAQ' : '新增 FAQ'} onClose={() => setEditing(null)}>
          <Field label="问题" required><input className="input" value={form.q} onChange={e => setForm({ ...form, q: e.target.value })} /></Field>
          <Field label="答案" required><textarea className="input" rows={3} value={form.a} onChange={e => setForm({ ...form, a: e.target.value })} /></Field>
          <Field label="命中关键词（逗号分隔）"><input className="input" placeholder="如：押金,退" value={form.keywords} onChange={e => setForm({ ...form, keywords: e.target.value })} /></Field>
          <button className="btn btn-primary" onClick={save}>保存</button>{' '}
          <button className="btn" onClick={() => setEditing(null)}>取消</button>
        </Modal>
      )}
    </div>
  )
}
