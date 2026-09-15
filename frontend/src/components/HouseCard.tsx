import React from 'react'
import { Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import { fmtMoney } from '../constants'
import type { House } from '../types'

function parseFacilities(f: string | string[] | undefined): string[] {
  try {
    const arr = typeof f === 'string' ? JSON.parse(f || '[]') : f || []
    return arr.slice(0, 3)
  } catch (e) {
    return []
  }
}

export default function HouseCard({ house }: { house: House }) {
  const nav = useNavigate()
  const facilities = parseFacilities(house.facilities)
  return (
    <div className="house-card" onClick={() => nav('/app/houses/' + house.id)}>
      <div className="house-cover">
        {house.coverUrl ? <img src={house.coverUrl} alt="" /> : <span>🏠</span>}
      </div>
      <div className="house-body">
        <div className="house-title">{house.title}</div>
        <div className="house-sub">
          {house.district} · {house.community} · {house.layout} · {house.area}㎡
        </div>
        <div style={{ marginTop: 6 }}>
          {facilities.map(f => (
            <Tag key={f} style={{ marginRight: 4 }}>{f}</Tag>
          ))}
          {house.avgScore ? <Tag color="gold">⭐ {house.avgScore}</Tag> : null}
        </div>
        <div className="house-rent">
          {fmtMoney(house.rent)}<small> /月</small>
          <span style={{ float: 'right', fontSize: 12, color: '#c0c4cc' }}>{house.viewCount || 0} 次浏览</span>
        </div>
      </div>
    </div>
  )
}
