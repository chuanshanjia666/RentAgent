/* AI 智能体模拟：规则匹配实现，仅供原型演示（不连接真实大模型） */
import { DB, REGIONS } from './data.js'

// FR-12 AI 找房助手：解析自然语言 → 结构化条件 → 匹配房源
export function aiHouseReply(text, prev) {
  const f = { ...(prev || { maxPrice: null, subway: false, layout: null, region: null }) }
  const m = text.match(/(\d{3,4})\s*(元|块|每月|一个月|\/月)?/)
  if (/便宜|低点|降一点|再低/.test(text) && f.maxPrice) {
    f.maxPrice = Math.max(800, Math.round((f.maxPrice * 0.9) / 50) * 50)
  } else if (m) {
    f.maxPrice = parseInt(m[1], 10)
  }
  if (/地铁/.test(text)) f.subway = true
  if (/一居|一室|单间|单身/.test(text)) f.layout = '一居'
  if (/两居|两室/.test(text)) f.layout = '两居'
  if (/三居|三室/.test(text)) f.layout = '三居'
  const region = REGIONS.find(r => text.includes(r.slice(0, 2)))
  if (region) f.region = region

  let pool = DB.listings.filter(l => l.status === 'approved')
  if (f.maxPrice) pool = pool.filter(l => l.price <= f.maxPrice)
  if (f.subway) pool = pool.filter(l => l.subway)
  if (f.layout) pool = pool.filter(l => l.layout === f.layout)
  if (f.region) pool = pool.filter(l => l.region === f.region)
  pool.sort((a, b) => a.price - b.price)
  const top = pool.slice(0, 3)

  const cond = [
    f.maxPrice ? `预算 ≤ ${f.maxPrice} 元/月` : '',
    f.layout || '',
    f.region || '',
    f.subway ? '近地铁' : ''
  ].filter(Boolean)
  const head = `已解析你的需求：${cond.join('、') || '暂无明确条件'}。为你匹配到 ${pool.length} 套在架房源` +
    (top.length ? '，重点推荐（附推荐理由）：' : '。暂时没有完全匹配的房源，可以试着放宽预算或位置条件～')
  const reasons = {}
  top.forEach(l => {
    const rs = []
    if (l.subway) rs.push('近地铁通勤方便')
    if (f.maxPrice && l.price <= f.maxPrice * 0.85) rs.push('价格低于你的预算上限，性价比高')
    if (l.facilities.includes('精装修')) rs.push('精装拎包入住')
    reasons[l.id] = rs.length ? rs.join('、') : '综合条件与你的需求匹配度高'
  })
  return { text: head, cards: top, filters: f, reasons }
}

// FR-11 个性化推荐：根据用户历史行为（浏览/收藏/搜索）实时打分推荐
export function recommendFor(userId) {
  const h = (DB.history && DB.history[userId]) || { viewed: [], searches: [] }
  const favIds = DB.favorites.filter(f => f.userId === userId).map(f => f.listingId)
  const seenList = [...h.viewed, ...favIds]
    .map(id => DB.listings.find(l => l.id === id))
    .filter(Boolean)

  const regionCount = {}
  const layoutCount = {}
  let priceSum = 0
  let subwayBias = 0
  seenList.forEach(l => {
    regionCount[l.region] = (regionCount[l.region] || 0) + 1
    layoutCount[l.layout] = (layoutCount[l.layout] || 0) + 1
    priceSum += l.price
    if (l.subway) subwayBias++
  })
  const topOf = obj => Object.entries(obj).sort((a, b) => b[1] - a[1])[0]?.[0] || null
  const topRegion = topOf(regionCount)
  const topLayout = topOf(layoutCount)
  const avgPrice = seenList.length ? Math.round(priceSum / seenList.length) : 0
  const budget = avgPrice ? Math.round((avgPrice * 1.25) / 50) * 50 : null
  const preferSubway = seenList.length > 0 && subwayBias / seenList.length >= 0.5

  const list = DB.listings
    .filter(l => l.status === 'approved')
    .map(l => {
      let s = 0
      const why = []
      if (topRegion && l.region === topRegion) { s += 3; why.push(`与你常看的「${topRegion}」一致`) }
      if (topLayout && l.layout === topLayout) { s += 2; why.push(`${topLayout}户型符合你的偏好`) }
      if (budget && l.price <= budget) { s += 2; why.push(`价格在你可接受范围（历史均价参考 ${avgPrice} 元）`) }
      if (preferSubway && l.subway) { s += 1; why.push('近地铁，符合你的出行偏好') }
      if (!h.viewed.includes(l.id)) s += 1
      if (favIds.includes(l.id)) s -= 5 // 已收藏的不再重复推荐
      return { l, reason: why.join(' · ') || '平台热门优质房源' }
    })
    .sort((a, b) => b.s - a.s)
    .slice(0, 6)

  return {
    list,
    viewedListings: h.viewed.map(id => DB.listings.find(l => l.id === id)).filter(Boolean),
    stat: { viewed: h.viewed.length, favs: favIds.length, lastSearch: h.searches[0] || '暂无' },
    profile: [topRegion, topLayout].filter(Boolean).join(' · ') || '综合热门'
  }
}

// FR-13 智能客服：RAG 知识库命中模拟（关键词匹配 + 来源引用）
export function aiFaqReply(text, faqs) {
  let best = null
  let bestScore = 0
  for (const f of faqs) {
    const s = (f.keywords || []).filter(k => text.includes(k)).length
    if (s > bestScore) { best = f; bestScore = s }
  }
  if (best) {
    return { text: `${best.a}\n（来源：${best.source}）`, hit: true }
  }
  return {
    text: '抱歉，该问题暂未命中知识库，已为你转接人工客服（工作时间 9:00–21:00）。\n你也可以换个问法试试，例如"押金什么时候退？"',
    transfer: true
  }
}
