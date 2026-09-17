import { Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import { fmtMoney, parseJsonList } from '../constants'
import { assetUrl } from '../runtime'
import type { House } from '../types'

export default function HouseCard({ house }: { house: House }) {
  const nav = useNavigate()
  // 卡片只展示前 3 个设施标签，避免撑破卡片高度（详情页展示全部）
  const facilities = parseJsonList(house.facilities).slice(0, 3)
  return (
    <div className="house-card" onClick={() => nav(`/app/houses/${house.id}`)}>
      <div className="house-cover">
        {house.coverUrl ? <img src={assetUrl(house.coverUrl)} alt="" /> : <span>🏠</span>}
      </div>
      <div className="house-body">
        <div className="house-title">{house.title}</div>
        <div className="house-sub">
          {house.district} · {house.community} · {house.layout} · {house.area}㎡
        </div>
        <div style={{ marginTop: 6 }}>
          {facilities.map(f => (
            <Tag key={f} style={{ marginRight: 4 }}>
              {f}
            </Tag>
          ))}
          {house.avgScore ? <Tag color="gold">⭐ {house.avgScore}</Tag> : null}
        </div>
        <div className="house-rent">
          {fmtMoney(house.rent)}
          <small> /月</small>
          <span style={{ float: 'right', fontSize: 12, color: '#c0c4cc' }}>
            {house.viewCount || 0} 次浏览
          </span>
        </div>
      </div>
    </div>
  )
}
