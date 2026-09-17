import { useEffect, useState } from 'react'
import { Button, Empty, message } from 'antd'
import http from '../api'
import HouseCard from '../components/HouseCard'
import type { House } from '../types'

export default function FavoritesView() {
  const [list, setList] = useState<House[]>([])

  async function load() {
    const p = await http.get('/favorites', { params: { size: 50 } })
    setList(p.list)
  }

  async function remove(id: number) {
    await http.delete(`/favorites/${id}`)
    message.success('已取消收藏')
    load()
  }

  useEffect(() => {
    load()
  }, [])

  return (
    <div className="page">
      <h2 className="page-title">我的收藏</h2>
      {list.length > 0 ? (
        <div className="house-grid">
          {list.map(h => (
            <div key={h.id} style={{ position: 'relative' }}>
              <HouseCard house={h} />
              <Button
                size="small"
                type="primary"
                danger
                ghost
                style={{ position: 'absolute', top: 8, right: 8 }}
                onClick={() => remove(h.id)}
              >
                取消收藏
              </Button>
            </div>
          ))}
        </div>
      ) : (
        <Empty description="还没有收藏房源，去首页逛逛吧" />
      )}
    </div>
  )
}
