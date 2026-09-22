import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
// 大连市真实区界 GeoJSON（DataV.GeoAtlas，adcode 210200，10 个区县），?raw 以字符串内联，
// 避免让 tsc 推导百 KB 级 JSON 字面量类型；registerMap 原生接受 GeoJSON 字符串。
import dalianGeoRaw from '../assets/dalian-geo.json?raw'

/** 点位数据（由调用方从房源列表整理，缺坐标的房源不应传入） */
export interface DalianPoint {
  id: number
  title: string
  rent: number
  district: string
  lng: number
  lat: number
}

/** 地图配色沿用全站设计令牌（釉青 #0d6b5b × 瓷白 × 柿朱 #bc4a1d），与 styles.css :root 同源 */
const TOKEN = {
  land: '#e8f1ee',
  border: 'rgba(13,107,91,.45)',
  borderEmphasis: '#0d6b5b',
  landEmphasis: '#d7e8e3',
  label: '#7d918a',
  dot: '#bc4a1d'
}

let registered = false
function ensureMap() {
  if (!registered) {
    echarts.registerMap('dalian', dalianGeoRaw)
    registered = true
  }
}

/**
 * 大连市地图找房（ECharts geo + 真实区界底图）：
 * 点位按经纬度落在真实地理位置上，支持缩放拖拽，点圆点跳房源详情。
 */
export default function DalianMap({
  points,
  onPick
}: {
  points: DalianPoint[]
  onPick: (id: number) => void
}) {
  const el = useRef<HTMLDivElement | null>(null)
  const pickRef = useRef(onPick)
  pickRef.current = onPick

  useEffect(() => {
    if (!el.current) return
    ensureMap()
    const chart = echarts.init(el.current)
    chart.setOption({
      tooltip: {
        trigger: 'item',
        confine: true,
        backgroundColor: '#ffffffee',
        borderColor: 'rgba(13,107,91,.25)',
        textStyle: { color: '#1d2c28' },
        formatter: (p: any) => {
          const d = p?.data
          if (!d) return ''
          return (
            `<b>${d.name}</b><br/>` +
            `<span style="color:#7d918a">${d.district}</span><br/>` +
            `租金 <b style="color:#bc4a1d">¥${Number(d.rent).toLocaleString('zh-CN')}</b>/月<br/>` +
            '<span style="color:#9aa8a2;font-size:11px">点击圆点查看详情</span>'
          )
        }
      },
      geo: {
        map: 'dalian',
        roam: true,
        scaleLimit: { min: 0.7, max: 8 },
        // 初始聚焦主城区（房源密度最高），庄河/瓦房店等远郊滚轮缩小即可看到
        center: [121.66, 38.97],
        zoom: 1.5,
        itemStyle: { areaColor: TOKEN.land, borderColor: TOKEN.border, borderWidth: 1 },
        emphasis: {
          itemStyle: { areaColor: TOKEN.landEmphasis, borderColor: TOKEN.borderEmphasis },
          label: { show: false }
        },
        select: { itemStyle: { areaColor: TOKEN.land }, label: { show: false } },
        label: { show: true, color: TOKEN.label, fontSize: 11 }
      },
      series: [
        {
          name: '在租房源',
          type: 'scatter',
          coordinateSystem: 'geo',
          symbolSize: 13,
          itemStyle: { color: TOKEN.dot, borderColor: '#fff', borderWidth: 1.5 },
          emphasis: {
            scale: 1.5,
            itemStyle: { shadowBlur: 8, shadowColor: 'rgba(188,74,29,.45)' }
          },
          data: points.map(p => ({
            name: p.title,
            district: p.district,
            rent: p.rent,
            value: [p.lng, p.lat],
            id: p.id
          }))
        }
      ]
    })
    // 只认散点系列的点击；data 形状由上方 series.data 决定，any 规避 echarts 泛型对 null 联合的误报
    chart.on('click', (p: any) => {
      if (p?.componentType === 'series' && typeof p?.data?.id === 'number') {
        pickRef.current(p.data.id as number)
      }
    })
    const onResize = () => chart.resize()
    window.addEventListener('resize', onResize)
    return () => {
      window.removeEventListener('resize', onResize)
      chart.dispose()
    }
  }, [points])

  return <div ref={el} style={{ width: '100%', height: 480, borderRadius: 8 }} />
}
