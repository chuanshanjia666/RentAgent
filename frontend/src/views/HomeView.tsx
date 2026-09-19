import { useEffect, useMemo, useState } from 'react'
import { Button, Empty, Input, Pagination, Select, Switch } from 'antd'
import { useNavigate } from 'react-router-dom'
import http from '../api'
import HouseCard from '../components/HouseCard'
import { FloorPlanArt, IconBot, IconSearch } from '../components/icons'
import { DISTRICTS, LAYOUTS } from '../constants'
import type { House, PageResult } from '../types'

const MAP_W = 1000
const MAP_H = 460

/** 首页筛选条件 */
interface HouseQuery {
  keyword: string
  district?: string
  layout?: string
  rentMax: string
  sort: string
}

interface MapPoint {
  id: number
  rent: number
  district?: string
  x: number
  y: number
}

export default function HomeView() {
  const nav = useNavigate()
  const [q, setQ] = useState<HouseQuery>({
    keyword: '',
    district: undefined,
    layout: undefined,
    rentMax: '',
    sort: 'new'
  })
  const [list, setList] = useState<House[]>([])
  const [recommend, setRecommend] = useState<House[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [searched, setSearched] = useState(false)
  const [mapMode, setMapMode] = useState(false)
  const [mapList, setMapList] = useState<House[]>([])

  const size = 12

  /** 筛选参数：列表与地图共用同一套条件 */
  const searchParams = (query: HouseQuery, extra: Record<string, unknown> = {}) => ({
    keyword: query.keyword || undefined,
    district: query.district || undefined,
    layout: query.layout || undefined,
    rentMax: query.rentMax || undefined,
    sort: query.sort,
    ...extra
  })

  async function load(p: number = page, query: HouseQuery = q) {
    const data = await http.get<PageResult<House>>('/houses', {
      params: searchParams(query, { page: p, size })
    })
    setList(data.list)
    setTotal(data.total)
  }

  function search() {
    setPage(1)
    setSearched(true)
    setMapMode(false)
    load(1, q)
  }

  /**
   * 地图模式取 /houses/map：它按当前筛选条件返回全部点位（上限 300），
   * 而列表只有当前一页数据——直接拿列表画图会永远只有十几个点，数量文案也跟着错。
   */
  async function toggleMap(on: boolean) {
    setMapMode(on)
    if (!on) return
    const points = await http.get<House[]>('/houses/map', { params: searchParams(q) })
    setMapList(points)
  }

  useEffect(() => {
    load()
    http
      .get<House[]>('/recommendations', { params: { limit: 6 } })
      .then(setRecommend)
      .catch(() => {
        /* 推荐位失败不影响列表 */
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 首屏只跑一次；筛选条件变化由「搜索」按钮显式触发 load
  }, [])

  const mapPoints = useMemo<MapPoint[]>(() => {
    const pts = mapList.filter(h => h.lng && h.lat)
    if (!pts.length) return []
    const lons = pts.map(h => Number(h.lng))
    const lats = pts.map(h => Number(h.lat))
    const minLon = Math.min(...lons) - 0.01
    const maxLon = Math.max(...lons) + 0.01
    const minLat = Math.min(...lats) - 0.005
    const maxLat = Math.max(...lats) + 0.005
    return pts.map(h => ({
      id: h.id,
      rent: Number(h.rent),
      district: h.district,
      x: 40 + ((Number(h.lng) - minLon) / (maxLon - minLon)) * (MAP_W - 80),
      y: MAP_H - 50 - ((Number(h.lat) - minLat) / (maxLat - minLat)) * (MAP_H - 100)
    }))
  }, [mapList])

  const set = (k: keyof HouseQuery, v: any) => setQ(s => ({ ...s, [k]: v }) as HouseQuery)

  return (
    <div className="page">
      {/* Hero：一句话说清产品的核心用法（对话找房 + 自助筛选） */}
      <div className="hero">
        <div className="hero-art">
          <FloorPlanArt width={230} />
        </div>
        <h1>说说你想怎么住</h1>
        <p className="hero-sub">一句话让 AI 帮你找房，也可以自己按区域、预算、户型筛选</p>
        <div className="search-bar">
          <Input
            size="large"
            style={{ maxWidth: 420 }}
            placeholder="输入小区 / 标题 / 地址关键词，或试试 AI 助手"
            value={q.keyword}
            onChange={e => set('keyword', e.target.value)}
            onPressEnter={search}
          />
          <Select
            size="large"
            allowClear
            placeholder="区域"
            style={{ width: 130 }}
            value={q.district}
            options={DISTRICTS.map(d => ({ value: d, label: d }))}
            onChange={v => set('district', v)}
          />
          <Select
            size="large"
            allowClear
            placeholder="户型"
            style={{ width: 120 }}
            value={q.layout}
            options={LAYOUTS.map(l => ({ value: l, label: l }))}
            onChange={v => set('layout', v)}
          />
          <Input
            size="large"
            style={{ width: 110 }}
            type="number"
            placeholder="租金上限"
            value={q.rentMax}
            onChange={e => set('rentMax', e.target.value)}
          />
          <Select
            size="large"
            style={{ width: 140 }}
            value={q.sort}
            onChange={v => set('sort', v)}
            options={[
              { value: 'new', label: '默认排序' },
              { value: 'rent_asc', label: '租金从低到高' },
              { value: 'rent_desc', label: '租金从高到低' },
              { value: 'hot', label: '最热优先' }
            ]}
          />
          <Button type="primary" size="large" icon={<IconSearch />} onClick={search}>
            搜索房源
          </Button>
          <Button
            size="large"
            ghost
            type="primary"
            icon={<IconBot />}
            onClick={() => nav('/app/ai')}
          >
            问 AI 助手
          </Button>
        </div>
      </div>

      {!searched && recommend.length > 0 && (
        <>
          <h3 className="sec-title">
            为你推荐
            <span className="sec-note">基于你的收藏与看房记录</span>
            <Switch
              style={{ marginLeft: 'auto', fontWeight: 400 }}
              checked={mapMode}
              onChange={toggleMap}
              checkedChildren="地图"
              unCheckedChildren="列表"
            />
          </h3>
          <div className="house-grid" style={{ marginBottom: 24 }}>
            {recommend.map(h => (
              <HouseCard key={`rec-${h.id}`} house={h} />
            ))}
          </div>
        </>
      )}

      {mapMode ? (
        <div className="map-panel">
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
            <b>地图找房（演示版按经纬度散点展示）</b>
            <span style={{ color: '#82948e', fontSize: 12 }}>
              共 {mapPoints.length} 套在租房源，点击圆点查看详情
            </span>
          </div>
          <svg
            viewBox={`0 0 ${MAP_W} ${MAP_H}`}
            width="100%"
            height={460}
            style={{ background: '#eef4f2', borderRadius: 8 }}
          >
            {mapPoints.map(p => (
              <g
                key={p.id}
                style={{ cursor: 'pointer' }}
                onClick={() => nav(`/app/houses/${p.id}`)}
              >
                <circle cx={p.x} cy={p.y} r="10" fill="rgba(13,107,91,.16)" />
                <circle cx={p.x} cy={p.y} r="5" fill="#0d6b5b" />
                <text x={p.x + 11} y={p.y + 4} fontSize="12" fill="#1d2c28">
                  {p.rent}元
                </text>
              </g>
            ))}
          </svg>
        </div>
      ) : (
        <>
          <h3 className="sec-title">
            在租房源 <span className="sec-note">共 {total} 套</span>
            <Switch
              style={{ marginLeft: 'auto', fontWeight: 400 }}
              checked={mapMode}
              onChange={toggleMap}
              checkedChildren="地图"
              unCheckedChildren="列表"
            />
          </h3>
          {list.length > 0 ? (
            <div className="house-grid">
              {list.map(h => (
                <HouseCard key={h.id} house={h} />
              ))}
            </div>
          ) : (
            <Empty description="没有符合条件的房源，换个条件试试～" />
          )}
          {total > size && (
            <div style={{ display: 'flex', justifyContent: 'center', marginTop: 20 }}>
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
        </>
      )}
    </div>
  )
}
