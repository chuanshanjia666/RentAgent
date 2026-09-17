/* 房东端页面：P10 房源发布（含未实名拦截 + P12 定价弹窗）/ P11 房源管理 / P13 预约订单 / P14 实名认证 */
import React, { useState } from 'react'
import { useStore, actions } from './store.jsx'
import { DB, REGIONS, LAYOUTS, ORIENTS, DEPOSITS, FACILITY_OPTIONS } from './data.js'
import { PageHead, StatusTag, Field, Empty } from './ui.jsx'

/* ---------- P14 实名认证（FR-03） ---------- */
export function Verify() {
  const { user, bump, toast } = useStore()
  const [name, setName] = useState('')
  const [idc, setIdc] = useState('')
  const [checking, setChecking] = useState(false)

  if (user.verified) {
    return (
      <div>
        <PageHead title="实名认证" sub="房东发布房源前须完成实名认证（FR-03）· 认证信息加密存储（NFR-04）" />
        <div className="card" style={{ textAlign: 'center', padding: '40px 20px' }}>
          <div style={{ fontSize: 40 }}>✅</div>
          <h3 style={{ margin: '10px 0' }}>已完成实名认证</h3>
          <p className="muted">认证姓名：{user.name}（证件号已加密存储，展示脱敏：3301**********XXXX）</p>
        </div>
      </div>
    )
  }

  const submit = () => {
    if (!name.trim()) return toast('请填写姓名', 'err')
    if (!/^\d{17}[\dXx]$/.test(idc.trim())) return toast('请填写 18 位身份证号', 'err')
    setChecking(true)
    setTimeout(() => {
      actions.verifyOk(user.id); bump(); setChecking(false)
      toast('实名认证审核通过（模拟即时审核）', 'ok')
    }, 1400)
  }

  return (
    <div>
      <PageHead title="实名认证" sub="房东发布房源前须完成实名认证（FR-03）· 未认证无法进入房源发布页" />
      <div className="card" style={{ maxWidth: 480 }}>
        <Field label="真实姓名" required><input className="input" placeholder="与身份证一致" value={name} onChange={e => setName(e.target.value)} /></Field>
        <Field label="身份证号" required><input className="input" placeholder="18 位身份证号码" value={idc} onChange={e => setIdc(e.target.value)} /></Field>
        <div className="banner banner-info">隐私说明：认证信息仅用于身份核验，加密存储（NFR-04），不会向其他用户展示。</div>
        <button className="btn btn-primary btn-block" disabled={checking} onClick={submit}>
          {checking ? '审核中…（模拟 1.4s）' : '提交认证'}
        </button>
      </div>
    </div>
  )
}

/* ---------- P10 房源发布（FR-05/08/15）+ 未实名拦截 + P12 定价弹窗 ---------- */
const emptyForm = {
  title: '', community: '', region: '城东区', layout: '两居', area: '', price: '',
  deposit: '押一付三', orientation: '朝南', floor: '', desc: '', facs: []
}

export function Publish({ priceOpen }) {
  const { user, editListingId, setEditListingId, bump, toast, navigate } = useStore()
  const editing = editListingId ? DB.listings.find(l => l.id === editListingId) : null
  const [f, setF] = useState(() => editing
    ? { ...emptyForm, ...editing, facs: [...editing.facilities] }
    : { ...emptyForm })
  const [aiState, setAiState] = useState('idle') // idle | running | done
  const [priceModal, setPriceModal] = useState(!!priceOpen)

  // FR-03 拦截态：未实名房东不可进入发布页
  if (!user.verified) {
    return (
      <div>
        <PageHead title="房源发布" sub="FR-05 · 发布前须完成实名认证（FR-03 拦截态演示）" />
        <div className="card" style={{ textAlign: 'center', padding: '46px 20px' }}>
          <div style={{ fontSize: 44 }}>🪪</div>
          <h3 style={{ margin: '10px 0' }}>尚未完成实名认证</h3>
          <p className="muted mb">未认证房东无法进入房源发布页（FR-03 验收标准）。完成认证后即可发布房源。</p>
          <button className="btn btn-primary" onClick={() => navigate('p14')}>去实名认证（P14）</button>
        </div>
      </div>
    )
  }

  const set = (k, v) => setF(s => ({ ...s, [k]: v }))
  const toggleFac = x => setF(s => ({ ...s, facs: s.facs.includes(x) ? s.facs.filter(y => y !== x) : [...s.facs, x] }))

  // FR-08 房源信息智能识别（模拟）：上传实拍图 → AI 识别 → 一键填充可修改的表单字段
  const aiFill = () => {
    setAiState('running')
    setTimeout(() => {
      setF(s => ({
        ...s,
        layout: '两居',
        area: s.area || 78,
        orientation: s.orientation || '朝南',
        floor: s.floor || '中楼层/18层',
        facs: Array.from(new Set([...s.facs, '独立卫浴', '精装修', '家电齐全'])),
        desc: `${s.community || '本小区'}精装两居，采光充足，家电齐全，距地铁口约 500 米，周边配套成熟，拎包入住。（由 AI 识别实拍图自动生成，可修改）`
      }))
      setAiState('done')
      toast('AI 识别完成，结果已填充且可人工修改（FR-08）', 'ok')
    }, 1200)
  }

  const submit = () => {
    if (!f.title.trim()) return toast('请填写房源标题', 'err')
    if (!f.community.trim()) return toast('请填写小区名称', 'err')
    if (!f.area || !f.price) return toast('请填写面积与租金', 'err')
    const data = {
      title: f.title.trim(), community: f.community.trim(), region: f.region, layout: f.layout,
      area: Number(f.area), price: Number(f.price), deposit: f.deposit, orientation: f.orientation,
      floor: f.floor || '中楼层', desc: f.desc || '房东暂未填写描述。', facilities: f.facs
    }
    if (editing) {
      actions.updateListing(editing.id, data)
      toast('已保存并重新进入待审核（FR-06）', 'ok')
      setEditListingId(null)
    } else {
      actions.addListing(data, user.id)
      toast('发布成功！当前状态：待审核（FR-05/FR-07）', 'ok')
    }
    bump()
    navigate('p11')
  }

  return (
    <div>
      <PageHead
        title={editing ? `编辑房源：${editing.title}` : '房源发布'}
        sub={editing ? '编辑后房源将重新进入待审核状态（FR-06 验收）' : '发布后进入「待审核」，管理员审核通过后租客可见（FR-05 / FR-07）'}
        right={!editing && <button className="btn" onClick={() => setPriceModal(true)}>💰 获取 AI 定价建议（FR-15）</button>} />
      {editing && <div className="banner banner-warn">编辑模式：当前房源状态为「{editing.status === 'rejected' ? '已驳回' : editing.status}」{editing.rejectReason ? `，驳回理由：${editing.rejectReason}` : ''}，保存后将重新提交审核。</div>}
      <div className="card" style={{ maxWidth: 720 }}>
        <div className="form-2col">
          <Field label="房源标题" required><input className="input" placeholder="如：云顶小区·精装两居·近地铁" value={f.title} onChange={e => set('title', e.target.value)} /></Field>
          <Field label="小区名称" required><input className="input" placeholder="小区名" value={f.community} onChange={e => set('community', e.target.value)} /></Field>
          <Field label="区域"><select className="input" value={f.region} onChange={e => set('region', e.target.value)}>{REGIONS.map(r => <option key={r}>{r}</option>)}</select></Field>
          <Field label="户型"><select className="input" value={f.layout} onChange={e => set('layout', e.target.value)}>{LAYOUTS.map(x => <option key={x}>{x}</option>)}</select></Field>
          <Field label="面积（㎡）" required><input className="input" type="number" placeholder="如 78" value={f.area} onChange={e => set('area', e.target.value)} /></Field>
          <Field label="月租金（元）" required><input className="input" type="number" placeholder="如 2600" value={f.price} onChange={e => set('price', e.target.value)} /></Field>
          <Field label="押付方式"><select className="input" value={f.deposit} onChange={e => set('deposit', e.target.value)}>{DEPOSITS.map(x => <option key={x}>{x}</option>)}</select></Field>
          <Field label="朝向"><select className="input" value={f.orientation} onChange={e => set('orientation', e.target.value)}>{ORIENTS.map(x => <option key={x}>{x}</option>)}</select></Field>
        </div>
        <Field label="楼层"><input className="input" placeholder="如：中楼层/18层" value={f.floor} onChange={e => set('floor', e.target.value)} /></Field>
        <Field label="房源设施">
          <div className="checks">{FACILITY_OPTIONS.map(x => (
            <span key={x} className={'ck' + (f.facs.includes(x) ? ' on' : '')} onClick={() => toggleFac(x)}>{x}</span>
          ))}</div>
        </Field>
        <Field label="房源图片">
          <div className="row">
            <div className="g1" style={{ width: 120, height: 70, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 12 }}>
              {aiState === 'running' ? '上传中…' : '实拍图 1（占位）'}
            </div>
            <button className="btn" disabled={aiState === 'running'} onClick={aiFill}>
              {aiState === 'running' ? '🤖 AI 识别中（模拟上传 + 识别）…' : '🤖 上传实拍图，AI 识别填充（FR-08 模拟）'}
            </button>
          </div>
          {aiState === 'done' && (
            <div className="banner banner-ok mt" style={{ marginTop: 10, marginBottom: 0 }}>
              <b>AI 识别结果（综合置信度 92%）：户型 两居 · 面积约 78㎡ · 朝向 朝南 · 装修 精装 · 设施 独立卫浴 / 家电齐全</b>
              <div>识别结果已一键填充上方表单，<b>均可人工修改</b>（FR-08 验收标准）；正式版将基于真实图片做多模态识别，建议发布前替换为实拍图。</div>
            </div>
          )}
        </Field>
        <Field label="房源描述"><textarea className="input" rows={4} placeholder="介绍房源亮点、周边配套…" value={f.desc} onChange={e => set('desc', e.target.value)} /></Field>
        <div className="row">
          <button className="btn btn-primary" onClick={submit}>{editing ? '保存并重新提交审核' : '提交发布（进入待审核）'}</button>
          {editing && <button className="btn" onClick={() => { setEditListingId(null); navigate('p11') }}>取消编辑</button>}
        </div>
      </div>

      {priceModal && <PriceModal f={f} onClose={() => setPriceModal(false)} apply={p => { set('price', p); setPriceModal(false); toast('已应用建议价', 'ok') }} />}
    </div>
  )
}

/* P12 智能定价建议弹窗（FR-15） */
function PriceModal({ f, onClose, apply }) {
  const [noSample, setNoSample] = useState(false)
  const approved = DB.listings.filter(l => l.status === 'approved' && l.region === f.region)
  const avg = approved.length ? Math.round(approved.reduce((s, l) => s + l.price, 0) / approved.length) : 2400
  const low = Math.round((avg * 0.92) / 50) * 50
  const high = Math.round((avg * 1.1) / 50) * 50
  const suggest = Math.round(avg / 50) * 50

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="spread">
          <h3>💰 AI 智能定价建议（FR-15）</h3>
          <button className="btn btn-sm" onClick={onClose}>✕</button>
        </div>
        <div className="hr" style={{ margin: '10px 0 14px' }} />
        {noSample ? (
          <>
            <div className="banner banner-warn">
              该小区 / 户型暂无同类样本数据，无法给出可靠定价区间。建议参考同区域在租均价 <b>{avg} 元/月</b>，或先发布后根据带看反馈调整。（FR-15 验收：无同类样本时明确告知数据不足）
            </div>
            <button className="btn btn-block" onClick={() => setNoSample(false)}>返回有样本演示</button>
          </>
        ) : (
          <>
            <div style={{ textAlign: 'center', padding: '10px 0 18px' }}>
              <div className="muted">建议定价区间</div>
              <div style={{ fontSize: 30, fontWeight: 700, color: 'var(--primary)', margin: '6px 0' }}>{low} ~ {high} <span style={{ fontSize: 14, fontWeight: 400 }}>元/月</span></div>
              <div className="muted">推荐定价：<b style={{ color: 'var(--danger)', fontSize: 16 }}>{suggest} 元/月</b></div>
            </div>
            <table className="param-table mb">
              <tbody>
                <tr><td>分析依据</td><td>同区域（{f.region}）在租房源 {approved.length} 套 · 同户型样本 8 套 · 近 90 天带看转化数据</td></tr>
                <tr><td>数据样本量</td><td>{approved.length + 8} 条（FR-15 验收：建议需包含区间、依据与样本量）</td></tr>
                <tr><td>当前填写价</td><td>{f.price ? `${f.price} 元/月` : '尚未填写'}{f.price && Number(f.price) > high ? <span className="tag tag-red" style={{ marginLeft: 6 }}>高于建议区间，可能延长空置</span> : f.price && Number(f.price) < low ? <span className="tag tag-orange" style={{ marginLeft: 6 }}>低于建议区间</span> : <span className="tag tag-green" style={{ marginLeft: 6 }}>处于合理区间</span>}</td></tr>
              </tbody>
            </table>
            <div className="row">
              <button className="btn btn-primary" onClick={() => apply(suggest)}>应用建议价 {suggest} 元</button>
              <button className="btn" onClick={() => setNoSample(true)}>演示：无同类样本情形</button>
            </div>
            <div className="muted mt" style={{ marginTop: 10 }}>AI 生成，仅供参考（NFR-05）</div>
          </>
        )}
      </div>
    </div>
  )
}

/* ---------- P11 房源管理（FR-06 / FR-07） ---------- */
export function Manage() {
  const { user, bump, toast, navigate, setEditListingId } = useStore()
  const mine = DB.listings.filter(l => l.landlordId === user.id)

  return (
    <div>
      <PageHead title="房源管理" sub="编辑与上下架（FR-06）· 审核状态跟踪（FR-07）· 下架房源对租客不可见"
        right={<button className="btn btn-primary" onClick={() => { setEditListingId(null); navigate('p10') }}>＋ 发布新房源</button>} />
      <div className="tbl-wrap">
        <table className="table">
          <thead><tr><th>房源</th><th>租金</th><th>状态</th><th>说明</th><th style={{ width: 240 }}>操作</th></tr></thead>
          <tbody>
            {mine.length === 0 && <tr><td colSpan={5} className="muted">暂无房源，点击右上角发布（演示建议用"李房东"账号查看完整数据）</td></tr>}
            {mine.map(l => (
              <tr key={l.id}>
                <td><b>{l.title}</b><div className="muted">{l.region} · {l.community} · {l.layout} · {l.area}㎡</div></td>
                <td>{l.price} 元/月</td>
                <td><StatusTag status={l.status} /></td>
                <td className="muted" style={{ maxWidth: 220 }}>
                  {l.status === 'rejected' ? <span style={{ color: 'var(--danger)' }}>驳回理由：{l.rejectReason}</span>
                    : l.status === 'pending' ? '等待管理员审核'
                    : l.status === 'offlined' ? '已下架，租客不可见'
                    : '已上架，租客可检索'}
                </td>
                <td>
                  <button className="btn btn-sm" onClick={() => { setEditListingId(l.id); navigate('p10') }}>编辑</button>{' '}
                  {l.status === 'approved' && <button className="btn btn-sm" onClick={() => { actions.toggleOnline(l.id); bump(); toast('已下架，租客不可见（FR-06）', 'ok') }}>下架</button>}
                  {l.status === 'offlined' && (
                    <>
                      <button className="btn btn-sm" onClick={() => { actions.toggleOnline(l.id); bump(); toast('已重新上架（已审核通过的房源）', 'ok') }}>上架</button>{' '}
                      <button className="btn btn-sm" onClick={() => navigate('p03a')}>预览已下架页</button>
                    </>
                  )}
                  {l.status === 'rejected' && <button className="btn btn-sm" onClick={() => { setEditListingId(l.id); navigate('p10') }}>修改后重新提交</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="banner banner-info mt">提示：房源状态流转「待审核 → 已通过/已驳回 → 已上架/已下架」；被驳回后修改重新提交会回到待审核态（FR-06/FR-07）。</div>
    </div>
  )
}

/* ---------- P13 预约 / 订单管理（FR-17 / FR-19） ---------- */
export function Orders() {
  const { user, bump, toast } = useStore()
  const [rejecting, setRejecting] = useState(null)
  const [reason, setReason] = useState('')

  const appts = DB.appointments.filter(a => a.landlordId === user.id)
  const leases = DB.leases.filter(le => le.landlordId === user.id)

  const act = (id, status, why) => {
    actions.setApptStatus(id, status, why); bump()
    toast(status === 'confirmed' ? '已确认预约，租客将收到通知' : '已拒绝预约，租客将收到通知', 'ok')
    setRejecting(null); setReason('')
  }

  return (
    <div>
      <PageHead title="预约 / 订单管理" sub="看房预约确认（FR-17）· 租约与续租/退租处理（FR-19）" />
      <b>看房预约</b>
      <div className="tbl-wrap" style={{ marginTop: 10 }}>
        <table className="table">
          <thead><tr><th>租客</th><th>房源</th><th>时间</th><th>状态</th><th style={{ width: 280 }}>操作</th></tr></thead>
          <tbody>
            {appts.length === 0 && <tr><td colSpan={5} className="muted">暂无预约</td></tr>}
            {appts.map(a => (
              <tr key={a.id}>
                <td>{DB.users.find(u => u.id === a.tenantId)?.name}</td>
                <td>{DB.listings.find(l => l.id === a.listingId)?.title}<div className="muted">{a.date} {a.slot}</div></td>
                <td>{a.date} {a.slot}</td>
                <td><StatusTag status={a.status} />{a.reason && <div className="muted">{a.reason}</div>}</td>
                <td>
                  {a.status === 'pending' && (
                    rejecting === a.id ? (
                      <div className="row">
                        <input className="input" style={{ width: 140 }} placeholder="拒绝理由" value={reason} onChange={e => setReason(e.target.value)} />
                        <button className="btn btn-danger btn-sm" onClick={() => act(a.id, 'rejected', reason || '时间不合适')}>确认拒绝</button>
                        <button className="btn btn-sm" onClick={() => setRejecting(null)}>取消</button>
                      </div>
                    ) : (
                      <>
                        <button className="btn btn-primary btn-sm" onClick={() => act(a.id, 'confirmed')}>确认预约</button>{' '}
                        <button className="btn btn-danger btn-sm" onClick={() => setRejecting(a.id)}>拒绝</button>
                      </>
                    )
                  )}
                  {a.status === 'confirmed' && <button className="btn btn-sm" onClick={() => { actions.setApptStatus(a.id, 'completed'); bump(); toast('已标记看房完成') }}>标记完成</button>}
                  {['rejected', 'completed'].includes(a.status) && <span className="muted">—</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="hr" />
      <b>租约管理（FR-19：续租 / 退租申请处理）</b>
      <div className="tbl-wrap" style={{ marginTop: 10 }}>
        <table className="table">
          <thead><tr><th>房源</th><th>租客</th><th>租期</th><th>状态</th><th style={{ width: 220 }}>操作</th></tr></thead>
          <tbody>
            {leases.length === 0 && <tr><td colSpan={5} className="muted">暂无租约</td></tr>}
            {leases.map(le => (
              <tr key={le.id}>
                <td>{DB.listings.find(l => l.id === le.listingId)?.title}</td>
                <td>{DB.users.find(u => u.id === le.tenantId)?.name}</td>
                <td>{le.start} 起 · {le.months} 期</td>
                <td>
                  <StatusTag status={le.status} />
                  {le.applyType && <span className="tag tag-blue" style={{ marginLeft: 6 }}>{le.applyType === 'renew' ? '续租申请' : '退租申请'}</span>}
                </td>
                <td>
                  {le.applyType ? (
                    <>
                      <button className="btn btn-primary btn-sm" onClick={() => { actions.resolveLease(le.id, true); bump(); toast('已同意申请，租客将收到通知', 'ok') }}>同意</button>{' '}
                      <button className="btn btn-danger btn-sm" onClick={() => { actions.resolveLease(le.id, false); bump(); toast('已拒绝申请，租客将收到通知') }}>拒绝</button>
                    </>
                  ) : <span className="muted">—</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
