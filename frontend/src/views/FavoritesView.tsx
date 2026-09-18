import { Button, Empty, message, Pagination } from 'antd'
import http from '../api'
import HouseCard from '../components/HouseCard'
import type { House } from '../types'
import { usePagedList } from '../usePagedList'

export default function FavoritesView() {
  const { rows, total, page, size, loading, reload } = usePagedList<House>('/favorites', {}, 12)

  async function remove(id: number) {
    await http.delete(`/favorites/${id}`)
    message.success('已取消收藏')
    reload()
  }

  return (
    <div className="page">
      <h2 className="page-title">我的收藏</h2>
      {rows.length > 0 ? (
        <div className="house-grid" style={{ opacity: loading ? 0.6 : 1 }}>
          {rows.map(h => (
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
      {total > size && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
          <Pagination total={total} pageSize={size} current={page} onChange={p => reload(p)} />
        </div>
      )}
    </div>
  )
}
