import { useNavigate } from 'react-router-dom'
import { FloorPlanArt, IconEye, IconStar } from './icons'
import { fmtMoney } from '../constants'
import { assetUrl } from '../runtime'
import type { House } from '../types'

export default function HouseCard({ house }: { house: House }) {
  const nav = useNavigate()
  // 卡片只展示前 3 个设施标签，避免撑破卡片高度（详情页展示全部）
  const facilities = (house.facilities ?? []).slice(0, 3)
  return (
    <div className="house-card" onClick={() => nav(`/app/houses/${house.id}`)}>
      <div className="house-cover">
        {house.coverUrl ? (
          <img src={assetUrl(house.coverUrl)} alt="" />
        ) : (
          <FloorPlanArt width={92} />
        )}
      </div>
      <div className="house-body">
        <div className="house-title">{house.title}</div>
        <div className="house-sub">
          {house.district} · {house.community} · {house.layout} · {house.area}㎡
        </div>
        <div style={{ marginTop: 7, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
          {facilities.map(f => (
            <span key={f} className="chip">
              {f}
            </span>
          ))}
          {house.avgScore ? (
            <span className="chip chip-score">
              <IconStar size={11} />
              {house.avgScore}
            </span>
          ) : null}
        </div>
        <div className="house-rent">
          {fmtMoney(house.rent)}
          <small> /月</small>
          <span className="house-views">
            <IconEye size={13} />
            {house.viewCount || 0}
          </span>
        </div>
      </div>
    </div>
  )
}
