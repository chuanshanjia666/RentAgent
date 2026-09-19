import { useEffect, useRef, useState } from 'react'
import { Card, Col, Radio, Row, Statistic } from 'antd'
import * as echarts from 'echarts'
import http from '../api'
import type { DashboardData } from '../types'

/** 看板里的四组趋势字段（与 /admin/dashboard 的返回同名） */
type TrendKey = 'userTrend' | 'houseTrend' | 'orderTrend' | 'chatTrend'

export default function AdminDashboardView() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [granularity, setGranularity] = useState<string>('day')
  const chartEl = useRef<HTMLDivElement | null>(null)

  async function load(g: string = granularity) {
    setData(await http.get<DashboardData>('/admin/dashboard', { params: { granularity: g } }))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 首屏按默认粒度加载；切粒度由 Radio.Group 的 onChange 显式触发 load
  }, [])

  useEffect(() => {
    if (!data || !chartEl.current) return
    const chart = echarts.init(chartEl.current)
    // AI 会话趋势的周期可能不与用户趋势完全重合，取并集后缺失补 0（FR-24）
    // 只取趋势字段：keyof DashboardData 里还有一堆数值字段，收窄后 data[key] 才是数组
    const series: [TrendKey, string, string][] = [
      ['userTrend', '新增用户', '#0d6b5b'],
      ['houseTrend', '新增房源', '#4e7d8c'],
      ['orderTrend', '新增订单', '#c99a3a'],
      ['chatTrend', '新增 AI 会话', '#bc4a1d']
    ]
    const byKey: Record<string, Map<string, number>> = {}
    const periodSet = new Set<string>()
    for (const [key] of series) {
      const m = new Map<string, number>()
      for (const t of data[key] ?? []) {
        m.set(t.period, Number(t.cnt))
        periodSet.add(t.period)
      }
      byKey[key] = m
    }
    const periods = Array.from(periodSet).sort()
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: series.map(s => s[1]) },
      grid: { left: 40, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: periods },
      yAxis: { type: 'value', minInterval: 1 },
      series: series.map(([key, name, color]) => ({
        name,
        type: 'line',
        smooth: true,
        itemStyle: { color },
        data: periods.map(p => byKey[key].get(p) ?? 0)
      }))
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
        <h2 className="page-title">数据统计看板</h2>
        <Radio.Group
          value={granularity}
          buttonStyle="solid"
          onChange={e => {
            setGranularity(e.target.value)
            load(e.target.value)
          }}
          options={[
            { value: 'day', label: '按日' },
            { value: 'week', label: '按周' },
            { value: 'month', label: '按月' }
          ]}
        />
      </div>

      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="租客数" value={data ? data.userCount : '-'} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="房东数" value={data ? data.landlordCount : '-'} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="房源总数"
              value={data ? data.houseCount : '-'}
              suffix={`/ 在租 ${data ? data.onlineCount : '-'}`}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="订单总数"
              value={data ? data.orderCount : '-'}
              suffix={`/ 在租 ${data ? data.rentedCount : '-'}`}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={6}>
          <Card>
            <Statistic title="AI 会话总数" value={data ? data.chatCount : '-'} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="AI 消息 / 工具调用"
              value={data ? data.chatMessageCount : '-'}
              suffix={`/ ${data ? data.toolCallCount : '-'}`}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="待审核房源" value={data ? data.pendingCount : '-'} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="转人工会话"
              value={data ? data.transferredCount : '-'}
              suffix={data && data.transferredCount > 0 ? '（知识库未命中）' : ''}
            />
          </Card>
        </Col>
      </Row>

      <Card title="增长趋势（含 AI 对话量）" style={{ marginTop: 16 }}>
        <div ref={chartEl} style={{ width: '100%', height: 380 }} />
      </Card>
    </div>
  )
}
