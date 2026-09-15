import React, { useEffect, useRef, useState } from 'react'
import { Card, Col, Radio, Row, Statistic } from 'antd'
import * as echarts from 'echarts'
import http from '../api'

export default function AdminDashboardView() {
  const [data, setData] = useState<any>(null)
  const [granularity, setGranularity] = useState<string>('day')
  const chartEl = useRef<HTMLDivElement | null>(null)

  async function load(g: string = granularity) {
    setData(await http.get('/admin/dashboard', { params: { granularity: g } }))
  }

  useEffect(() => { load() }, [])

  useEffect(() => {
    if (!data || !chartEl.current) return
    const chart = echarts.init(chartEl.current)
    const periods = data.userTrend.map((t: any) => t.period)
    const mk = (key: string) => data[key].map((t: any) => Number(t.cnt))
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['新增用户', '新增房源', '新增订单'] },
      grid: { left: 40, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: periods },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '新增用户', type: 'line', smooth: true, data: mk('userTrend'), itemStyle: { color: '#1f6feb' } },
        { name: '新增房源', type: 'line', smooth: true, data: mk('houseTrend'), itemStyle: { color: '#52c41a' } },
        { name: '新增订单', type: 'line', smooth: true, data: mk('orderTrend'), itemStyle: { color: '#fa8c16' } }
      ]
    })
    const onResize = () => chart.resize()
    window.addEventListener('resize', onResize)
    return () => {
      window.removeEventListener('resize', onResize)
      chart.dispose()
    }
  }, [data])

  return (
    <div className="page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 className="page-title">数据统计看板（FR-24）</h2>
        <Radio.Group value={granularity} buttonStyle="solid"
          onChange={e => { setGranularity(e.target.value); load(e.target.value) }}
          options={[{ value: 'day', label: '按日' }, { value: 'week', label: '按周' }, { value: 'month', label: '按月' }]} />
      </div>

      <Row gutter={16}>
        <Col span={6}><Card><Statistic title="租客数" value={data ? data.userCount : '-'} /></Card></Col>
        <Col span={6}><Card><Statistic title="房东数" value={data ? data.landlordCount : '-'} /></Card></Col>
        <Col span={6}><Card><Statistic title="房源总数" value={data ? data.houseCount : '-'} suffix={`/ 在租 ${data ? data.onlineCount : '-'}`} /></Card></Col>
        <Col span={6}><Card><Statistic title="订单总数" value={data ? data.orderCount : '-'} suffix={`/ 在租 ${data ? data.rentedCount : '-'}`} /></Card></Col>
      </Row>

      <Card title="增长趋势" style={{ marginTop: 16 }}>
        <div ref={chartEl} style={{ width: '100%', height: 380 }} />
      </Card>
      <Card style={{ marginTop: 16 }}>
        <Statistic title="待审核房源" value={data ? data.pendingCount : '-'}
          suffix={data && data.pendingCount > 0 ? '（去审核工作台处理）' : ''} />
      </Card>
    </div>
  )
}
