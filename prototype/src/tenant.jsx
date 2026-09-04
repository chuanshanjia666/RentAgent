/* 租客端页面：P01 首页 / P01M 移动示意 / P02 列表 / P03 详情 / P04 AI助手 / P05 预约 / P06 签约 / P07 个人中心 */
import React, { useState, useRef, useEffect } from 'react'
import { useStore, actions } from './store.jsx'
import { DB, REGIONS, LAYOUTS, SLOTS } from './data.js'
import { aiHouseReply, aiFaqReply, recommendFor } from './ai.js'
import { PageHead, StatusTag, Stars, Field, Empty, ListingCard } from './ui.jsx'

/* ---------- P01 首页 ---------- */
export function Home() {
  const { setKw, navigate, user, bump, setCurListingId } = useStore()
  const [text, setText] = useState('')
  const rec = user ? recommendFor(user.id) : null
  const recList = rec ? rec.list : DB.listings.filter(l => l.status === 'approved').slice(0, 6).map(l => ({ l, reason: null }))
  const doSearch = () => {
    const kw = text.trim()
    setKw(kw)
    if (user && kw) { actions.recordSearch(user.id, kw); bump() }
    navigate('p02')
  }
  return (
    <div>
      <div className="card mb" style={{ background: 'linear-gradient(120deg, #1F6FEB, #5A8DF5)', color: '#fff', padding: '34px 30px' }}>
        <div style={{ fontSize: 24, fontWeight: 700, marginBottom: 6 }}>找个家，说句话就行</div>
        <div style={{ fontSize: 13, opacity: .9, marginBottom: 16 }}>AI 找房助手 · 智能客服 · 合同解读 · 智能定价 —— 对话即服务</div>
        <div className="row">
          <input className="input" style={{ flex: 1, maxWidth: 520 }} placeholder="试试：小区名 / 区域，或让 AI 帮你找（预算 2500 两居近地铁）"
            value={text} onChange={e => setText(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && doSearch()} />
          <button className="btn" style={{ background: '#fff' }} onClick={doSearch}>搜索房源</button>
          <button className="btn" style={{ background: 'rgba(255,255,255,.18)', color: '#fff', borderColor: 'rgba(255,255,255,.6)' }}
            onClick={() => navigate('p04')}>🤖 AI 找房</button>
        </div>
      </div>

      <div className="spread mb">
        <div className="page-title" style={{ fontSize: 17 }}>为你推荐</div>
        <span className="muted">FR-11 个性化推荐 · 根据历史行为实时计算 · {user ? user.name : '游客'}，欢迎回来</span>
      </div>
      {rec && (
        <div className="banner banner-info" style={{ paddingTop: 8, paddingBottom: 8 }}>
          🤖 AI 已分析你的历史行为：浏览 <b>{rec.stat.viewed}</b> 套 · 收藏 <b>{rec.stat.favs}</b> 套 · 最近搜索「{rec.stat.lastSearch}」，判断你偏好 <b>{rec.profile}</b>。以下为匹配结果——多看几套或改搜别的，推荐会随之变化。
        </div>
      )}
      <div className="listing-grid">
        {recList.map(({ l, reason }) => <ListingCard key={l.id} l={l} showFav reason={reason} />)}
      </div>
      {rec && rec.viewedListings.length > 0 && (
        <>
          <div className="hr" />
          <b style={{ fontSize: 14 }}>🕐 最近浏览（历史记录实时驱动上方推荐）</b>
          <div className="row mt" style={{ flexWrap: 'wrap' }}>
            {rec.viewedListings.map(l => (
              <span key={l.id} className="tag tag-blue" style={{ cursor: 'pointer', padding: '6px 12px', fontSize: 13 }}
                onClick={() => { setCurListingId(l.id); navigate('p03') }}>
                {l.title.length > 16 ? l.title.slice(0, 16) + '…' : l.title} · {l.price}元
              </span>
            ))}
          </div>
        </>
      )}
      <div className="proto-footer">RentAgent 高保真原型 · 纯前端 Mock 演示（React 实现） · 对应《04-原型工具使用方法》v1.1 页面清单</div>
    </div>
  )
}

/* ---------- P01M 移动端示意（375px） ---------- */
export function HomeMobile() {
  const { navigate, setKw } = useStore()
  const rec = DB.listings.filter(l => l.status === 'approved').slice(0, 3)
  return (
    <div>
      <PageHead title="P01M 移动端示意页" sub="375px 宽度响应式基准演示（对应 NFR-06）：完整响应式适配在开发阶段按 NFR-06 验证" />
      <div className="phone-wrap">
        <div className="phone-frame">
          <div className="pf-head">
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
              <b style={{ color: 'var(--primary)' }}>RentAgent</b>
              <span className="muted" style={{ cursor: 'pointer' }} onClick={() => navigate('p04')}>🤖 AI 助手</span>
            </div>
            <div className="search" onClick={() => { setKw(''); navigate('p02') }}>🔍 搜索小区 / 区域 / 预算…</div>
          </div>
          <div className="pf-body">
            <img alt="" style={{ width: '100%', borderRadius: 10, height: 120, objectFit: 'cover', background: 'linear-gradient(120deg,#1F6FEB,#5A8DF5)' }}
              src="data:image/svg+xml;utf8,%3Csvg xmlns='http://www.w3.org/2000/svg'/%3E" />
            <div className="muted" style={{ margin: '10px 2px' }}>为你推荐</div>
            <div className="pf-cards">
              {rec.map(l => (
                <div key={l.id} className="card" style={{ padding: 12 }} onClick={() => { navigate('p03') }}>
                  <div className="row" style={{ justifyContent: 'space-between' }}>
                    <b style={{ fontSize: 13 }}>{l.title}</b>
                    <span className="price" style={{ fontSize: 15 }}>{l.price}<small> 元/月</small></span>
                  </div>
                  <div className="muted" style={{ marginTop: 4 }}>{l.layout} · {l.area}㎡ · {l.region}{l.subway ? ' · 近地铁' : ''}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ---------- P02 房源列表（筛选 + 地图找房） ---------- */
export function ListPage() {
  const { kw, navigate, setCurListingId } = useStore()
  const [mode, setMode] = useState('list')
  const [sort, setSort] = useState('default')
  const [ft, setFt] = useState({ region: '', price: '', layout: '', area: '', orient: '', facs: [] })
  const [popId, setPopId] = useState(null)

  const set = (k, v) => setFt(s => ({ ...s, [k]: v }))
  const toggleFac = f => setFt(s => ({
    ...s, facs: s.facs.includes(f) ? s.facs.filter(x => x !== f) : [...s.facs, f]
  }))

  let list = DB.listings.filter(l => l.status === 'approved')
  if (kw) list = list.filter(l => (l.title + l.community + l.region + l.desc).includes(kw))
  if (ft.region) list = list.filter(l => l.region === ft.region)
  if (ft.layout) list = list.filter(l => l.layout === ft.layout)
  if (ft.orient) list = list.filter(l => l.orientation === ft.orient)
  if (ft.price === 'a') list = list.filter(l => l.price <= 1500)
  if (ft.price === 'b') list = list.filter(l => l.price > 1500 && l.price <= 2500)
  if (ft.price === 'c') list = list.filter(l => l.price > 2500 && l.price <= 3500)
  if (ft.price === 'd') list = list.filter(l => l.price > 3500)
  if (ft.area === 'a') list = list.filter(l => l.area <= 50)
  if (ft.area === 'b') list = list.filter(l => l.area > 50 && l.area <= 80)
  if (ft.area === 'c') list = list.filter(l => l.area > 80 && l.area <= 100)
  if (ft.area === 'd') list = list.filter(l => l.area > 100)
  if (ft.facs.length) list = list.filter(l => ft.facs.every(f => l.facilities.includes(f)))
  if (sort === 'priceAsc') list = [...list].sort((a, b) => a.price - b.price)
  if (sort === 'priceDesc') list = [...list].sort((a, b) => b.price - a.price)
  if (sort === 'areaDesc') list = [...list].sort((a, b) => b.area - a.area)

  const openDetail = l => { setCurListingId(l.id); navigate('p03') }
  const pop = list.find(l => l.id === popId)

  return (
    <div>
      <PageHead title="房源列表"
        sub={kw ? `关键词“${kw}”的搜索结果（FR-09 关键词搜索 + 多条件筛选）` : '多条件筛选 + 排序（FR-09） · 支持地图找房（FR-10）'}
        right={
          <div className="row">
            <select className="input" style={{ width: 130 }} value={sort} onChange={e => setSort(e.target.value)}>
              <option value="default">默认排序</option>
              <option value="priceAsc">价格从低到高</option>
              <option value="priceDesc">价格从高到低</option>
              <option value="areaDesc">面积从大到小</option>
            </select>
            <div className="seg">
              <span className={'s' + (mode === 'list' ? ' on' : '')} onClick={() => setMode('list')}>☰ 列表</span>
              <span className={'s' + (mode === 'map' ? ' on' : '')} onClick={() => setMode('map')}>🗺️ 地图</span>
            </div>
          </div>
        } />
      <div className="list-page">
        <div className="card filter-panel">
          <b>筛选条件</b>
          <div className="hr" />
          <Field label="区域"><select className="input" value={ft.region} onChange={e => set('region', e.target.value)}>
            <option value="">全部区域</option>{REGIONS.map(r => <option key={r}>{r}</option>)}
          </select></Field>
          <Field label="租金区间（元/月）"><select className="input" value={ft.price} onChange={e => set('price', e.target.value)}>
            <option value="">不限</option><option value="a">1500 及以下</option><option value="b">1500 - 2500</option>
            <option value="c">2500 - 3500</option><option value="d">3500 以上</option>
          </select></Field>
          <Field label="户型"><select className="input" value={ft.layout} onChange={e => set('layout', e.target.value)}>
            <option value="">不限</option>{LAYOUTS.map(x => <option key={x}>{x}</option>)}
          </select></Field>
          <Field label="面积"><select className="input" value={ft.area} onChange={e => set('area', e.target.value)}>
            <option value="">不限</option><option value="a">50㎡ 及以下</option><option value="b">50 - 80㎡</option>
            <option value="c">80 - 100㎡</option><option value="d">100㎡ 以上</option>
          </select></Field>
          <Field label="朝向"><select className="input" value={ft.orient} onChange={e => set('orient', e.target.value)}>
            <option value="">不限</option><option>朝南</option><option>朝北</option><option>朝东</option><option>朝西</option><option>南北</option>
          </select></Field>
          <Field label="设施（可多选）">
            <div className="checks">
              {['近地铁', '独立卫浴', '精装修', '可短租'].map(f => (
                <span key={f} className={'ck' + (ft.facs.includes(f) ? ' on' : '')} onClick={() => toggleFac(f)}>{f}</span>
              ))}
            </div>
          </Field>
          <button className="btn btn-block" onClick={() => setFt({ region: '', price: '', layout: '', area: '', orient: '', facs: [] })}>重置筛选</button>
        </div>

        <div>
          <div className="muted mb" style={{ marginBottom: 10 }}>共找到 <b style={{ color: 'var(--primary)' }}>{list.length}</b> 套符合条件（仅展示"已通过审核"的房源，FR-07）</div>
          {mode === 'list' ? (
            list.length ? (
              <div className="listing-grid">
                {list.map(l => <ListingCard key={l.id} l={l} showFav />)}
              </div>
            ) : <Empty text="没有符合条件的房源，试试放宽筛选条件～" />
          ) : (
            <div className="map-canvas">
              <div className="road" style={{ left: 0, right: 0, top: '38%', height: 14 }} />
              <div className="road" style={{ left: 0, right: 0, top: '72%', height: 10 }} />
              <div className="road" style={{ top: 0, bottom: 0, left: '30%', width: 12 }} />
              <div className="road" style={{ top: 0, bottom: 0, left: '64%', width: 8 }} />
              {list.map(l => (
                <div key={l.id} className={'pin' + (l.price <= 1600 ? ' hot' : '')}
                  style={{ left: l.mapX + '%', top: l.mapY + '%' }}
                  onClick={() => setPopId(popId === l.id ? null : l.id)}>
                  <span className="b">{l.price}元 · {l.layout}</span>
                </div>
              ))}
              {pop && (
                <div className="pin-pop" style={{ left: `calc(${pop.mapX}% + 12px)`, top: `calc(${pop.mapY}% - 20px)` }}>
                  <b style={{ fontSize: 13 }}>{pop.title}</b>
                  <div className="muted" style={{ margin: '6px 0' }}>{pop.layout} · {pop.area}㎡ · {pop.region}</div>
                  <div className="spread">
                    <span className="price" style={{ fontSize: 16 }}>{pop.price}<small> 元/月</small></span>
                    <button className="btn btn-primary btn-sm" onClick={() => openDetail(pop)}>查看详情</button>
                  </div>
                </div>
              )}
              <div className="map-legend">🗺️ 地图找房（FR-10）：拖动/缩放联动刷新为开发态交互，此处演示标记点点击查看房源卡片</div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/* ---------- P03 房源详情（P03A：已下架状态） ---------- */
export function Detail({ offlined }) {
  const { user, curListingId, navigate, bump, toast, setCurListingId } = useStore()
  const l = offlined
    ? DB.listings.find(x => x.id === 'l14')
    : (DB.listings.find(x => x.id === curListingId) || DB.listings[0])
  const reviews = DB.reviews.filter(r => r.listingId === l.id)
  const avg = reviews.length ? Math.round(reviews.reduce((s, r) => s + r.stars, 0) / reviews.length) : 0
  const fav = user && actions.isFav(user.id, l.id)
  const landlord = DB.users.find(u => u.id === l.landlordId)

  // 记录浏览历史 → 驱动 FR-11 个性化推荐
  useEffect(() => {
    if (user && !offlined) {
      actions.recordView(user.id, l.id)
      bump()
    }
  }, [l.id])

  return (
    <div>
      {offlined && <div className="banner banner-warn">状态演示页 P03A「房源详情-已下架」：已下架房源对租客不可见（FR-06 验收），以下为管理员/房东视角回看效果。</div>}
      <div className="detail-grid">
        <div className="card">
          <div className="gallery mb">
            <div className="g main g1">房源实拍图 1（{l.community}）</div>
            <div className="g g2">实拍图 2</div>
            <div className="g g3">实拍图 3</div>
          </div>
          <h2 style={{ fontSize: 19, marginBottom: 8 }}>{l.title}</h2>
          <div style={{ marginBottom: 12 }}>
            {l.facilities.map(f => <span key={f} className="tag tag-blue">{f}</span>)}
          </div>
          <table className="param-table mb">
            <tbody>
              <tr><td>租金</td><td><b className="big-price" style={{ fontSize: 20 }}>{l.price}</b> 元/月（{l.deposit}）</td></tr>
              <tr><td>户型 / 面积</td><td>{l.layout} · {l.area}㎡ · {l.orientation} · {l.floor}</td></tr>
              <tr><td>位置</td><td>{l.region} · {l.community}{l.subway ? ' · 距地铁 300 米' : ''}</td></tr>
              <tr><td>房东</td><td>{landlord?.name}{landlord?.verified ? <span className="tag tag-green" style={{ marginLeft: 6 }}>已实名认证（FR-03）</span> : <span className="tag" style={{ marginLeft: 6 }}>未实名</span>}</td></tr>
              <tr><td>房源描述</td><td style={{ lineHeight: 1.8 }}>{l.desc}</td></tr>
            </tbody>
          </table>

          <div className="spread mb" style={{ marginTop: 18 }}>
            <b>租客评价（{reviews.length}）</b>
            {reviews.length > 0 && <span><Stars value={avg} /> <b>{avg}.0</b></span>}
          </div>
          {reviews.length === 0 && <div className="muted">暂无评价（仅完成合同的用户可评价，FR-20）</div>}
          {reviews.map(r => (
            <div key={r.id} className="review-item">
              <div className="row spread">
                <b>{r.tenantName}</b>
                <span className="muted">{r.date}</span>
              </div>
              <div style={{ margin: '4px 0' }}><Stars value={r.stars} /></div>
              <div>{r.text}</div>
            </div>
          ))}
        </div>

        <div className="side-panel">
          <div className="card">
            <div className="big-price">{l.price}<small style={{ fontSize: 13, color: 'var(--text-2)', fontWeight: 400 }}> 元/月</small></div>
            <div className="muted mb">{l.deposit} · {l.layout} · {l.area}㎡</div>
            {offlined ? (
              <>
                <button className="btn btn-block mb" disabled>❤ 已下架，不可收藏</button>
                <button className="btn btn-primary btn-block mb" disabled>预约看房（不可用）</button>
                <button className="btn btn-block" disabled>咨询 AI（不可用）</button>
              </>
            ) : (
              <>
                <button className="btn btn-block mb" onClick={() => {
                  if (!user) return toast('请先登录', 'err')
                  const on = actions.toggleFav(user.id, l.id); bump()
                  toast(on ? '已收藏（FR-25）' : '已取消收藏', 'ok')
                }}>{fav ? '❤ 已收藏，点击取消' : '♡ 收藏房源（FR-25）'}</button>
                <button className="btn btn-primary btn-block mb" onClick={() => { setCurListingId(l.id); navigate('p05') }}>📅 预约看房（FR-17）</button>
                <button className="btn btn-block" onClick={() => navigate('p04')}>🤖 咨询 AI（FR-13）</button>
                <div className="hr" />
                <button className="btn btn-ghost btn-sm" onClick={() => toast('已提交举报，管理员将结合 AI 风险识别核实（FR-23/FR-16）')}>⚠ 举报该房源</button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

/* ---------- P04 AI 助手（找房 + 智能客服） / P04A 超时状态 ---------- */
function BotBubble({ m }) {
  return (
    <div className="msg bot">
      <div className="avatar">🤖</div>
      <div>
        <div className={'bubble' + (m.err ? ' err-bubble' : '')}>{m.text}</div>
        {m.cards && m.cards.length > 0 && m.done && (
          <div className="chat-cards">
            {m.cards.map(c => (
              <MiniCard key={c.id} l={c} reason={m.reasons?.[c.id]} />
            ))}
          </div>
        )}
        {!m.err && <span className="ai-note">AI 生成，仅供参考（NFR-05）</span>}
        {m.transfer && <span className="ai-note" style={{ color: 'var(--warn)', borderColor: '#F0D8A8' }}>已转人工 · 保留对话日志</span>}
      </div>
    </div>
  )
}

function MiniCard({ l, reason, onClick }) {
  const { setCurListingId, navigate } = useStore()
  return (
    <div className="mini-card" onClick={onClick || (() => { setCurListingId(l.id); navigate('p03') })}>
      <div>
        <div className="t">{l.title}</div>
        <div className="s">{l.layout} · {l.area}㎡ · {l.region}{reason ? ` · 推荐理由：${reason}` : ''}</div>
      </div>
      <div style={{ whiteSpace: 'nowrap' }}>
        <b className="price" style={{ fontSize: 15 }}>{l.price}</b><span className="muted"> 元/月</span>
      </div>
    </div>
  )
}

export function AiChat() {
  const { bump, user } = useStore()
  const [scene, setScene] = useState('house') // house | cs
  const [input, setInput] = useState('')
  const [typing, setTyping] = useState(false)
  const filtersRef = useRef(null)
  const [msgs, setMsgs] = useState({
    house: [{ role: 'bot', text: '你好，我是 AI 找房助手 🏠\n直接用一句话告诉我你的需求，例如"预算 3000，要两居，最好近地铁"，我帮你匹配房源；也可以随时说"再便宜一点"调整条件。', done: true }],
    cs: [{ role: 'bot', text: '你好，我是 RentAgent 智能客服 💬\n押金、退租、维修、违约等问题都可以问我，回答均引用平台知识库；未命中时会为你转接人工。', done: true }]
  })
  const boxRef = useRef(null)

  useEffect(() => {
    if (boxRef.current) boxRef.current.scrollTop = boxRef.current.scrollHeight
  }, [msgs, typing])

  const push = (key, m) => setMsgs(s => ({ ...s, [key]: [...s[key], m] }))
  const patchLast = (key, patch) => setMsgs(s => {
    const arr = [...s[key]]
    arr[arr.length - 1] = { ...arr[arr.length - 1], ...patch }
    return { ...s, [key]: arr }
  })

  const send = raw => {
    const text = (raw ?? input).trim()
    if (!text || typing) return
    setInput('')
    push(scene, { role: 'me', text, done: true })
    setTyping(true)
    setTimeout(() => {
      const reply = scene === 'house'
        ? aiHouseReply(text, filtersRef.current)
        : aiFaqReply(text, DB.faqs)
      if (scene === 'house') {
        filtersRef.current = reply.filters
        if (user) actions.recordSearch(user.id, text) // 记录查询历史 → 驱动 FR-11 推荐
      }
      push(scene, { role: 'bot', text: '', full: reply.text, cards: reply.cards || null, reasons: reply.reasons, transfer: reply.transfer, done: false })
      setTyping(false)
      let i = 0
      const t = setInterval(() => {
        i += 2
        if (i >= reply.text.length) {
          patchLast(scene, { text: reply.text, done: true })
          clearInterval(t)
          bump()
        } else {
          patchLast(scene, { text: reply.text.slice(0, i) })
        }
      }, 16)
    }, 600)
  }

  const quicks = scene === 'house'
    ? ['预算 3000，要两居，最好近地铁', '大学城 1500 以内的单间', '再便宜一点']
    : ['押金什么时候退？', '提前退租怎么处理？', '房子水管坏了谁负责维修？']

  return (
    <div>
      <PageHead title="AI 助手" sub="找房助手（FR-12）+ 智能客服（FR-13）· 对话即服务；规则匹配模拟，正式版接入大模型 API（Function Calling + RAG）" />
      <div className="chat-shell">
        <div className="chat-box">
          <div className="chat-head spread">
            <div className="seg">
              <span className={'s' + (scene === 'house' ? ' on' : '')} onClick={() => setScene('house')}>🏠 找房助手</span>
              <span className={'s' + (scene === 'cs' ? ' on' : '')} onClick={() => setScene('cs')}>💬 智能客服</span>
            </div>
            <span className="muted">{scene === 'house' ? '多轮对话 · 增量修改条件' : `知识库 ${DB.faqs.length} 条 · 回答附来源`}</span>
          </div>
          <div className="chat-msgs" ref={boxRef}>
            {msgs[scene].map((m, i) => m.role === 'me'
              ? <div key={i} className="msg me"><div className="avatar">🧑</div><div className="bubble">{m.text}</div></div>
              : <BotBubble key={i} m={m} />)}
            {typing && <div className="msg bot"><div className="avatar">🤖</div><div className="bubble"><span className="typing"><i /><i /><i /></span></div></div>}
          </div>
          <div className="chat-input">
            <input className="input" placeholder={scene === 'house' ? '描述你的找房需求…' : '输入你的问题…'}
              value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => e.key === 'Enter' && send()} />
            <button className="btn btn-primary" onClick={() => send()}>发送</button>
          </div>
        </div>
        <div className="chat-side">
          <div className="card">
            <b>试试这样问</b>
            <div className="hr" />
            {quicks.map(q => <button key={q} className="q" onClick={() => send(q)}>{q}</button>)}
          </div>
          <div className="card mt" style={{ paddingTop: 14 }}>
            <b style={{ fontSize: 13 }}>能力说明</b>
            <div className="muted mt" style={{ marginTop: 8, lineHeight: 1.9 }}>
              · 找房助手：自然语言 → 结构化条件 → 房源检索工具（模拟 Function Calling）<br />
              · 智能客服：命中知识库附带来源，未命中转人工（NFR-05）<br />
              · 合同/资金类问题强制引用知识库并声明 AI 生成
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export function AiChatError() {
  return (
    <div>
      <div className="banner banner-warn">状态演示页 P04A「AI 对话-超时/转人工」：对应《项目最终确认书》风险 R2（AI 不可用降级）与 NFR-05（内容合规、日志留痕）。</div>
      <div className="chat-shell">
        <div className="chat-box">
          <div className="chat-head"><b>🤖 AI 助手（异常状态演示）</b></div>
          <div className="chat-msgs">
            <div className="msg me"><div className="avatar">🧑</div><div className="bubble">合同里违约金 200% 合理吗？</div></div>
            <div className="msg bot">
              <div className="avatar">🤖</div>
              <div>
                <div className="bubble err-bubble">⚠ AI 服务暂时不可用（模拟请求超时）。<br />你的问题已记录，可点击"重试"或转接人工客服；涉及合同与资金的问题建议直接咨询人工。</div>
                <span className="ai-note" style={{ color: 'var(--warn)', borderColor: '#F0D8A8' }}>对话日志已留存（NFR-05）</span>
              </div>
            </div>
          </div>
          <div className="chat-input">
            <input className="input" placeholder="输入已禁用（演示状态）" disabled />
            <button className="btn" onClick={() => location.reload()}>🔄 重试</button>
            <button className="btn btn-primary" onClick={() => alert('演示：已转接人工客服，排队第 2 位')}>转人工客服</button>
          </div>
        </div>
        <div className="chat-side">
          <div className="card">
            <b>降级策略（对应风险 R2）</b>
            <div className="muted mt" style={{ marginTop: 8, lineHeight: 1.9 }}>
              · 超时/异常 → 友好提示 + 一键重试<br />
              · 合同/资金类 → 强制建议转人工<br />
              · 全部对话留存日志可追溯（NFR-05）
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ---------- P05 预约看房（P05A 已拒绝 / P05B 已完成） ---------- */
function nextDays(n) {
  const arr = []
  const wk = ['日', '一', '二', '三', '四', '五', '六']
  for (let i = 1; i <= n; i++) {
    const d = new Date(Date.now() + i * 86400000)
    arr.push({ value: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`, label: `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 周${wk[d.getDay()]}` })
  }
  return arr
}

export function Booking({ demoStatus }) {
  const { user, curListingId, bump, toast, navigate } = useStore()
  if (demoStatus) return <BookingDemo status={demoStatus} />
  const l = DB.listings.find(x => x.id === curListingId) || DB.listings[0]
  const days = nextDays(7)
  const [date, setDate] = useState(days[0].value)
  const [slot, setSlot] = useState(null)
  const [done, setDone] = useState(null)
  const mine = DB.appointments.filter(a => a.listingId === l.id && a.tenantId === user?.id)

  const conflict = date && slot && DB.appointments.some(a =>
    a.listingId === l.id && a.date === date && a.slot === slot && ['pending', 'confirmed'].includes(a.status))

  const submit = () => {
    if (!date || !slot) return toast('请选择日期与时段', 'err')
    if (conflict) return toast('该时段已被预约，请选择其他时段（FR-17 冲突校验）', 'err')
    const a = actions.addAppointment({ listingId: l.id, tenantId: user.id, landlordId: l.landlordId, date, slot })
    bump(); setDone(a); toast('预约提交成功，等待房东确认', 'ok')
  }

  return (
    <div>
      <PageHead title="预约看房" sub={`房源：${l.title}（FR-17）· 状态流转：待确认 → 已确认/已拒绝 → 已完成`} />
      {done ? (
        <div className="card" style={{ textAlign: 'center', padding: '40px 20px' }}>
          <div style={{ fontSize: 40 }}>✅</div>
          <h3 style={{ margin: '10px 0' }}>预约提交成功</h3>
          <p className="muted mb">{l.title} · {done.date} {done.slot} · 当前状态：待房东确认</p>
          <div className="row" style={{ justifyContent: 'center' }}>
            <button className="btn btn-primary" onClick={() => navigate('p07')}>查看我的预约</button>
            <button className="btn" onClick={() => setDone(null)}>继续预约其他时段</button>
          </div>
        </div>
      ) : (
        <div className="card">
          <Field label="选择日期" required>
            <div className="checks">
              {days.map(d => (
                <span key={d.value} className={'ck' + (date === d.value ? ' on' : '')} onClick={() => setDate(d.value)}>{d.label}</span>
              ))}
            </div>
          </Field>
          <Field label="选择时段" required>
            <div className="checks">
              {SLOTS.map(s => (
                <span key={s} className={'ck' + (slot === s ? ' on' : '')} onClick={() => setSlot(s)}>{s}</span>
              ))}
            </div>
          </Field>
          {conflict && <div className="banner banner-error">⚠ 该日期时段已存在待确认/已确认的预约，禁止重复预约（FR-17 验收标准）。</div>}
          <button className="btn btn-primary" onClick={submit}>提交预约申请</button>
          <div className="hr" />
          <b>我对该房源的预约记录</b>
          <table className="table mt" style={{ marginTop: 10 }}>
            <thead><tr><th>日期</th><th>时段</th><th>状态</th><th>说明</th></tr></thead>
            <tbody>
              {mine.length === 0 && <tr><td colSpan={4} className="muted">暂无预约记录</td></tr>}
              {mine.map(a => (
                <tr key={a.id}>
                  <td>{a.date}</td><td>{a.slot}</td><td><StatusTag status={a.status} /></td>
                  <td className="muted">{a.reason || (a.status === 'pending' ? '等待房东确认' : '')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function BookingDemo({ status }) {
  const id = status === 'rejected' ? 'a2' : 'a3'
  const a = DB.appointments.find(x => x.id === id)
  const l = DB.listings.find(x => x.id === a.listingId)
  const step = status === 'rejected' ? 1 : 2
  return (
    <div>
      <div className="banner banner-info">状态演示页 {status === 'rejected' ? 'P05A「预约-已拒绝」' : 'P05B「预约-已完成」'}：用于答辩演示预约流转的边界状态（FR-17）。</div>
      <div className="card">
        <div className="spread">
          <h3>{l.title}</h3>
          <StatusTag status={a.status} />
        </div>
        <div className="muted mb">看房时间：{a.date} {a.slot}</div>
        <div className="sign-step">
          <span className={'st' + (step >= 0 ? ' done' : '')}>提交预约</span>
          <span className={'st' + (step >= 1 ? ' done' : '')}>{status === 'rejected' ? '已拒绝' : '房东已确认'}</span>
          <span className={'st' + (step >= 2 ? ' done' : '')}>看房完成</span>
        </div>
        {a.reason && <div className="banner banner-error">拒绝理由：{a.reason}</div>}
        {status === 'completed' && <div className="banner banner-ok">看房已完成，可前往个人中心对房源与房东进行评价（FR-20）。</div>}
      </div>
    </div>
  )
}

/* ---------- P06 签约页（合同预览 + AI 解读）/ P06A 风险条款示例 ---------- */
export function ContractPage({ riskDemo }) {
  const { user, bump, toast } = useStore()
  const c = DB.contracts[0]
  const l = DB.listings.find(x => x.id === c.listingId)
  const [sel, setSel] = useState(riskDemo ? 'k3' : 'k1')
  const clause = c.clauses.find(x => x.id === sel)

  const sign = who => {
    actions.signAs(c.id, who); bump()
    toast(who === 'tenant' ? '租客已确认签约' : '房东已确认签约（演示代签）', 'ok')
  }

  return (
    <div>
      {riskDemo && <div className="banner banner-warn">状态演示页 P06A「合同解读-风险条款示例」：AI 已自动定位并标红 3 处风险条款（FR-14 验收：3 处风险样例全部命中）。</div>}
      <PageHead title="在线签约" sub={`${l.title} · 月租 ${c.rent} 元 · 押金 ${c.deposit} 元 · 租期 ${c.months} 个月（FR-18）`} />
      <div className="contract-grid">
        <div className="card">
          <div className="spread mb">
            <b>📜 房屋租赁合同（模板自动生成）</b>
            <StatusTag status={c.status} />
          </div>
          <div className="muted mb">出租方：李房东（已实名） · 承租方：{user?.name || '小陈'} · 起租日 {c.start}</div>
          <div className="muted mb" style={{ marginTop: -6 }}>💡 点击任意条款，右侧 AI 解读将给出通俗化解释；红色条款为 AI 识别的风险条款（FR-14）。合同涉及重大权益，AI 解读仅供参考。</div>
          {c.clauses.map(k => (
            <div key={k.id} className={'clause' + (k.risk ? ' risk' : '') + (sel === k.id ? ' on' : '')} onClick={() => setSel(k.id)}>
              {k.risk && <span className="rk">⚠ 风险条款</span>}
              {k.text}
            </div>
          ))}
          <div className="hr" />
          <div className="sign-step">
            <span className={'st' + (c.landlordSigned ? ' done' : '')}>房东确认</span>
            <span className={'st' + (c.tenantSigned ? ' done' : '')}>租客确认</span>
            <span className={'st' + (c.status === 'signed' ? ' done' : '')}>签约完成</span>
          </div>
          {c.status === 'signed'
            ? <div className="banner banner-ok">双方已完成签约，合同副本可在个人中心查看（演示环境不含 CA 电子签章，见确认书范围外清单）。</div>
            : (
              <div className="row">
                <button className="btn btn-primary" disabled={c.tenantSigned} onClick={() => sign('tenant')}>{c.tenantSigned ? '✓ 租客已确认' : '租客确认签约'}</button>
                <button className="btn" disabled={c.landlordSigned} onClick={() => sign('landlord')}>{c.landlordSigned ? '✓ 房东已确认' : '模拟房东确认'}</button>
              </div>
            )}
        </div>
        <div className="side-panel card">
          <div className="head">{clause?.risk ? '⚠ AI 风险解读' : '🤖 AI 条款解读'}</div>
          <div className="muted mb">你正在查看第 {c.clauses.indexOf(clause) + 1} 条</div>
          <div className="banner" style={{ background: clause?.risk ? '#FDEBEC' : 'var(--primary-light)', color: clause?.risk ? '#A02C30' : 'var(--primary-dark)', lineHeight: 1.9 }}>
            {clause?.explain}
          </div>
          <span className="ai-note">AI 生成，仅供参考，不构成法律意见（FR-14 / NFR-05）</span>
          <div className="hr" />
          <b style={{ fontSize: 13 }}>本次合同 AI 识别结果</b>
          <div className="muted mt" style={{ marginTop: 6, lineHeight: 2 }}>
            共 {c.clauses.length} 条条款，其中 <b style={{ color: 'var(--danger)' }}>{c.clauses.filter(x => x.risk).length} 条存在风险</b>：
            <br />· 违约金过高（200% 月租）
            <br />· 租期内单方涨租
            <br />· 押金不予退还条款
          </div>
        </div>
      </div>
    </div>
  )
}

/* ---------- P07 个人中心 ---------- */
export function Profile() {
  const { user, bump, toast, logout, navigate } = useStore()
  const [tab, setTab] = useState('fav')
  const [name, setName] = useState(user.name)
  const [phone, setPhone] = useState(user.phone)

  const favs = DB.favorites.filter(f => f.userId === user.id).map(f => DB.listings.find(l => l.id === f.listingId)).filter(Boolean)
  const appts = DB.appointments.filter(a => a.tenantId === user.id)
  const leases = DB.leases.filter(le => le.tenantId === user.id)
  const doneLeases = leases.filter(le => le.status === 'completed')
  const myNotis = DB.notifications.filter(n => n.userId === user.id)
  const unread = myNotis.filter(n => !n.read).length
  const myReviews = DB.reviews.filter(r => r.byUserId === user.id)

  const tabs = [
    ['fav', `🏠 我的收藏（${favs.length}）`], ['order', '📋 预约与租约'],
    ['review', '⭐ 我的评价'], ['msg', `🔔 消息${unread ? `（${unread}）` : ''}`], ['me', '👤 个人信息']
  ]

  return (
    <div>
      <PageHead title="个人中心" sub="收藏（FR-25）· 预约与租约（FR-17/19）· 评价（FR-20）· 消息（FR-21）· 个人信息（FR-04）" />
      <div className="pc-grid">
        <div className="pc-menu">
          <div className="card" style={{ textAlign: 'center', borderRadius: 0, borderBottom: '1px solid var(--line)' }}>
            <div style={{ fontSize: 34 }}>🧑</div>
            <b>{user.name}</b>
            <div className="muted">租客 · {user.phone}</div>
          </div>
          {tabs.map(([k, t]) => (
            <div key={k} className={'mi' + (tab === k ? ' on' : '')} onClick={() => setTab(k)}>{t}</div>
          ))}
          <div className="mi" style={{ color: 'var(--danger)' }} onClick={logout}>🚪 退出登录</div>
        </div>

        <div className="card">
          {tab === 'fav' && (
            favs.length ? <div className="listing-grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
              {favs.map(l => <ListingCard key={l.id} l={l} showFav />)}
            </div> : <Empty text="还没有收藏房源，去列表页看看吧～（FR-25 收藏管理）" />
          )}

          {tab === 'order' && (
            <>
              <b>我的预约（FR-17）</b>
              <div className="tbl-wrap mt" style={{ marginTop: 10 }}>
                <table className="table">
                  <thead><tr><th>房源</th><th>时间</th><th>状态</th><th>说明</th></tr></thead>
                  <tbody>
                    {appts.length === 0 && <tr><td colSpan={4} className="muted">暂无预约</td></tr>}
                    {appts.map(a => (
                      <tr key={a.id}>
                        <td>{DB.listings.find(l => l.id === a.listingId)?.title}</td>
                        <td>{a.date} {a.slot}</td>
                        <td><StatusTag status={a.status} /></td>
                        <td className="muted">{a.reason || ''}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="hr" />
              <b>我的租约与租金计划（FR-19）</b>
              {leases.length === 0 && <div className="muted mt">暂无租约</div>}
              {leases.map(le => (
                <div key={le.id} className="card mt" style={{ padding: 14 }}>
                  <div className="spread">
                    <div>
                      <b>{DB.listings.find(l => l.id === le.listingId)?.title}</b>
                      <span className="muted" style={{ marginLeft: 8 }}>起租 {le.start} · 共 {le.months} 期</span>
                    </div>
                    <div className="row">
                      <StatusTag status={le.applyType === 'renew' ? 'confirmed' : le.applyType === 'terminate' ? 'pending' : le.status} />
                      {le.applyType && <span className="muted">{le.applyType === 'renew' ? '续租申请处理中' : '退租申请处理中'}</span>}
                      {le.status === 'active' && !le.applyType && (
                        <>
                          <button className="btn btn-sm" onClick={() => { actions.applyLease(le.id, 'renew'); bump(); toast('续租申请已提交（FR-19）', 'ok') }}>申请续租</button>
                          <button className="btn btn-danger btn-sm" onClick={() => { actions.applyLease(le.id, 'terminate'); bump(); toast('退租申请已提交，等待房东确认', 'ok') }}>申请退租</button>
                        </>
                      )}
                    </div>
                  </div>
                  <table className="table mt" style={{ marginTop: 10 }}>
                    <thead><tr><th>期数</th><th>金额</th><th>支付状态</th></tr></thead>
                    <tbody>
                      {le.plan.map(p => (
                        <tr key={p.period}>
                          <td>{p.period}</td><td>{p.amount} 元</td>
                          <td>{p.status === 'paid' ? <span className="tag tag-green">已支付</span> : <span className="tag tag-orange">待支付（记录，不对接支付）</span>}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ))}
            </>
          )}

          {tab === 'review' && (
            <>
              <b>待评价（仅已完成合同可评价，FR-20）</b>
              {doneLeases.length === 0 && <div className="muted mt">暂无可评价的已完成合同</div>}
              {doneLeases.map(le => {
                const l = DB.listings.find(x => x.id === le.listingId)
                const reviewed = actions.hasReviewed(user.id, le.listingId)
                return <ReviewForm key={le.id} l={l} reviewed={reviewed} user={user} bump={bump} toast={toast} />
              })}
              <div className="hr" />
              <b>我的评价（{myReviews.length}）</b>
              {myReviews.length === 0 && <div className="muted mt">暂无已发表评价</div>}
              {myReviews.map(r => (
                <div key={r.id} className="review-item">
                  <div className="row spread">
                    <b>{DB.listings.find(l => l.id === r.listingId)?.title}</b>
                    <span className="muted">{r.date}</span>
                  </div>
                  <Stars value={r.stars} />
                  <div>{r.text}</div>
                </div>
              ))}
            </>
          )}

          {tab === 'msg' && (
            <>
              <div className="spread mb">
                <b>站内消息（FR-21：关键事件 5 秒内提醒）</b>
                <button className="btn btn-sm" onClick={() => { actions.markAllRead(user.id); bump(); toast('已全部标记为已读') }}>全部已读</button>
              </div>
              {myNotis.length === 0 && <Empty text="暂无消息" />}
              {myNotis.map(n => (
                <div key={n.id} className="review-item" style={{ cursor: n.read ? 'default' : 'pointer', fontWeight: n.read ? 400 : 700 }}
                  onClick={() => { if (!n.read) { actions.markRead(n.id); bump() } }}>
                  <div className="row spread">
                    <span>{!n.read && <span className="dot" style={{ background: 'var(--danger)' }} />}{n.text}</span>
                    <span className="muted" style={{ fontWeight: 400 }}>{n.time}</span>
                  </div>
                </div>
              ))}
            </>
          )}

          {tab === 'me' && (
            <div style={{ maxWidth: 420 }}>
              <Field label="昵称"><input className="input" value={name} onChange={e => setName(e.target.value)} /></Field>
              <Field label="手机号"><input className="input" value={phone} onChange={e => setPhone(e.target.value)} /></Field>
              <Field label="头像"><div className="muted">演示环境使用固定头像 🧑（图片上传为开发态能力）</div></Field>
              <div className="row">
                <button className="btn btn-primary" onClick={() => {
                  if (!name.trim()) return toast('昵称不能为空', 'err')
                  user.name = name.trim(); user.phone = phone.trim(); bump()
                  toast('个人信息已更新，即时生效（FR-04）', 'ok')
                }}>保存修改</button>
                <button className="btn" onClick={() => { toast('密码修改成功，请重新登录（FR-04）', 'ok'); setTimeout(logout, 800) }}>修改密码</button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function ReviewForm({ l, reviewed, user, bump, toast }) {
  const [stars, setStars] = useState(0)
  const [text, setText] = useState('')
  if (reviewed) {
    return <div className="card mt" style={{ padding: 14 }}>✅ 已评价：{l.title}（感谢你的反馈）</div>
  }
  return (
    <div className="card mt" style={{ padding: 14 }}>
      <div className="spread mb">
        <b style={{ fontSize: 13 }}>{l.title}</b>
        <Stars value={stars} onChange={setStars} />
      </div>
      <textarea className="input" rows={2} placeholder="说说你的租住体验…" value={text} onChange={e => setText(e.target.value)} />
      <div style={{ marginTop: 8 }}>
        <button className="btn btn-primary btn-sm" onClick={() => {
          if (!stars) return toast('请先打星', 'err')
          if (!text.trim()) return toast('请填写评价内容', 'err')
          actions.addReview({ listingId: l.id, byUserId: user.id, tenantName: user.name, stars, text: text.trim() })
          bump(); toast('评价发表成功，已展示于房源详情页（FR-20）', 'ok')
        }}>发表评价</button>
      </div>
    </div>
  )
}
