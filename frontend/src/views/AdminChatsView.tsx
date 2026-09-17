import React, { useEffect, useState } from 'react'
import { Button, Collapse, Descriptions, Drawer, Empty, Input, Select, Space, Table, Tag, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { fmtTime } from '../constants'
import { SCENE_COLOR, USER_ROLE } from '../types'
import type { AdminChatDetail, AdminChatMessage, AdminChatSession } from '../types'

/**
 * AI 对话审计（NFR-05 对话日志可追溯 · FR-24 AI 对话量）。
 * 管理员查看全站会话，并可回放单会话完整轨迹：归属用户（人物）/ 多轮对话 / 工具调用（入参·返回·耗时）
 * / RAG 引用 / token 用量，形态对齐主流 Agent 工具的 Trace 视图。只读，不提供修改与删除。
 */
export default function AdminChatsView() {
  const [list, setList] = useState<AdminChatSession[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [scene, setScene] = useState<number | undefined>()
  const [transferred, setTransferred] = useState<boolean | undefined>()
  const [detail, setDetail] = useState<AdminChatDetail | null>(null)
  const [loading, setLoading] = useState(false)

  async function load(p = page, s = size) {
    setLoading(true)
    try {
      const res = await http.get('/admin/chats', {
        params: { keyword: keyword || undefined, scene, transferred, page: p, size: s }
      })
      setList(res.list)
      setTotal(res.total)
      setPage(res.page)
      setSize(res.size)
    } finally {
      setLoading(false)
    }
  }

  async function openTrace(row: AdminChatSession) {
    setDetail(await http.get(`/admin/chats/${row.id}`))
  }

  useEffect(() => { load(1) }, [])

  const columns: ColumnsType<AdminChatSession> = [
    {
      title: '会话', minWidth: 240,
      render: (_, r) => (
        <>
          <div style={{ fontWeight: 600, marginBottom: 2 }}>
            {r.toolCallCount > 0 && <span title="含工具调用">🔧 </span>}
            {r.title || '（无标题）'}
          </div>
          <Space size={4} wrap>
            <Tag color={SCENE_COLOR[r.scene]}>{r.sceneName}</Tag>
            <span style={{ color: '#909399', fontSize: 12 }}>#{r.id}</span>
            {r.isTransferred === 1 && <Tag color="volcano">转人工</Tag>}
          </Space>
        </>
      )
    },
    {
      title: '人物', width: 190,
      render: (_, r) => (
        <>
          <div>
            {r.nickname || '-'}
            {r.userRole != null && <Tag style={{ marginLeft: 6 }}>{USER_ROLE[r.userRole] || r.userRole}</Tag>}
          </div>
          <div style={{ color: '#909399', fontSize: 12 }}>
            {r.username} · {r.phone} · uid={r.userId}
          </div>
        </>
      )
    },
    {
      title: '对话规模', width: 168,
      render: (_, r) => (
        <Space size={4} wrap>
          <Tooltip title="用户提问轮数">
            <Tag color="blue">{r.roundCount} 轮</Tag>
          </Tooltip>
          <Tag>{r.messageCount} 条</Tag>
          <Tooltip title="工具调用次数">
            <Tag color={r.toolCallCount > 0 ? 'orange' : 'default'}>🔧 {r.toolCallCount}</Tag>
          </Tooltip>
        </Space>
      )
    },
    { title: 'Tokens', width: 90, render: (_, r) => (r.totalTokens ? r.totalTokens : '-') },
    {
      title: '最后一条', width: 240,
      render: (_, r) => (
        <span style={{ color: '#606266', fontSize: 12 }}>{r.lastMessage || '（暂无消息）'}</span>
      )
    },
    { title: '更新时间', width: 140, render: (_, r) => <span style={{ fontSize: 12 }}>{fmtTime(r.updatedAt)}</span> },
    {
      title: '操作', width: 100, fixed: 'right',
      render: (_, r) => <Button size="small" type="primary" ghost onClick={() => openTrace(r)}>查看轨迹</Button>
    }
  ]

  return (
    <div className="page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 className="page-title">AI 对话审计（NFR-05 可追溯 · FR-24 AI 对话量）</h2>
        <span style={{ color: '#909399', fontSize: 12 }}>共 {total} 个会话 · 日志只读</span>
      </div>

      <div className="trace-filter">
        <Select style={{ width: 150 }} placeholder="全部场景" allowClear value={scene}
          onChange={v => { setScene(v); load(1) }}
          options={[{ value: 1, label: '找房助手' }, { value: 2, label: '智能客服' }, { value: 3, label: '合同解读' }]} />
        <Select style={{ width: 150 }} placeholder="全部状态" allowClear value={transferred}
          onChange={v => { setTransferred(v); load(1) }}
          options={[{ value: true, label: '含转人工' }, { value: false, label: '未转人工' }]} />
        <Input style={{ width: 280 }} placeholder="会话标题 / 用户昵称 / 账号 / 手机号" value={keyword}
          onChange={e => setKeyword(e.target.value)} onPressEnter={() => load(1)} allowClear />
        <Button type="primary" onClick={() => load(1)}>查询</Button>
        <Button onClick={() => { setKeyword(''); setScene(undefined); setTransferred(undefined); load(1) }}>重置</Button>
      </div>

      <Table
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={list}
        columns={columns}
        scroll={{ x: 1120 }}
        locale={{ emptyText: <Empty description="暂无 AI 会话" /> }}
        pagination={{
          current: page, pageSize: size, total, showSizeChanger: true,
          onChange: (p, s) => load(p, s)
        }}
      />

      <Drawer
        title="对话轨迹（Trace）"
        width={980}
        open={!!detail}
        onClose={() => setDetail(null)}
        styles={{ body: { background: '#f7f9fc' } }}
      >
        {detail && <TracePanel detail={detail} />}
      </Drawer>
    </div>
  )
}

/** 单会话轨迹：人物 / 智能体设定 / 规模统计 / 逐步消息 */
function TracePanel({ detail }: { detail: AdminChatDetail }) {
  const { session: s, stats } = detail
  return (
    <>
      <div className="trace-meta">
        <Descriptions title="会话与人物" size="small" column={2} bordered>
          <Descriptions.Item label="会话 ID">#{s.id}</Descriptions.Item>
          <Descriptions.Item label="场景"><Tag color={SCENE_COLOR[s.scene]}>{s.sceneName}</Tag></Descriptions.Item>
          <Descriptions.Item label="标题" span={2}>{s.title || '（无标题）'}</Descriptions.Item>
          <Descriptions.Item label="用户">
            {s.nickname || '（用户已注销）'}
            {s.userRole != null && <Tag style={{ marginLeft: 6 }}>{USER_ROLE[s.userRole] || s.userRole}</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="账号 / 手机">{s.username} · {s.phone}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{fmtTime(s.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{fmtTime(s.updatedAt)}</Descriptions.Item>
          <Descriptions.Item label="客服转人工" span={2}>
            {s.isTransferred === 1
              ? <Tag color="volcano">已转人工（知识库未命中）</Tag>
              : <Tag>未触发</Tag>}
          </Descriptions.Item>
        </Descriptions>
      </div>

      <div className="trace-meta">
        <Descriptions title="智能体设定" size="small" column={1} bordered>
          <Descriptions.Item label="当前生效引擎">
            <Tag color="geekblue">{detail.currentEngine}</Tag>
            <span style={{ color: '#909399', fontSize: 12, marginLeft: 8 }}>
              引擎为查看时刻生效值，历史消息未逐条快照
            </span>
          </Descriptions.Item>
          <Descriptions.Item label="角色设定">
            <Collapse ghost size="small" items={[{
              key: 'prompt', label: '展开 System Prompt',
              children: <pre className="trace-json">{detail.systemPrompt}</pre>
            }]} />
          </Descriptions.Item>
        </Descriptions>
      </div>

      <div className="trace-meta">
        <Space size={28} wrap style={{ marginBottom: 10 }}>
          <StatLine label="对话轮次" value={`${stats.roundCount} 轮`} />
          <StatLine label="消息条数" value={`${s.messageCount} 条`} />
          <StatLine label="工具调用" value={`${stats.toolCallCount} 次`} />
          <StatLine label="Tokens" value={stats.totalTokens ? String(stats.totalTokens) : '-'} />
          <StatLine label="平均回答时延" value={`${stats.avgLatencyMs} ms`} />
        </Space>
        <div>
          <span style={{ color: '#8492a6', fontSize: 12, marginRight: 8 }}>工具使用：</span>
          {stats.tools.length === 0
            ? <span style={{ color: '#909399', fontSize: 12 }}>本会话未调用工具</span>
            : stats.tools.map(t => (
              <Tooltip key={t.name} title={`调用 ${t.count} 次 · 平均耗时 ${t.avgLatencyMs} ms`}>
                <Tag color="orange" style={{ marginBottom: 4 }}>
                  🔧 {t.name} ×{t.count}
                </Tag>
              </Tooltip>
            ))}
        </div>
      </div>

      <h4 style={{ margin: '16px 0 10px', color: '#303133' }}>
        多轮对话与工具调用（按时间顺序）
      </h4>
      {detail.messages.length === 0
        ? <Empty description="该会话尚无消息" />
        : detail.messages.map(m => <TraceStep key={m.id} m={m} />)}
    </>
  )
}

function StatLine({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div style={{ color: '#8492a6', fontSize: 12 }}>{label}</div>
      <div style={{ fontSize: 16, fontWeight: 600, color: '#1f2f3d' }}>{value}</div>
    </div>
  )
}

/** 单步轨迹：用户 / 助手 / 工具调用三种形态 */
function TraceStep({ m }: { m: AdminChatMessage }) {
  if (m.role === 3) {
    return (
      <div className="trace-step">
        <span className="trace-dot tool">🔧</span>
        <div className="trace-role">
          <b style={{ color: '#d48806' }}>工具调用</b>
          <Tag color="orange">{m.toolName}</Tag>
          {m.latencyMs != null && <span>耗时 {m.latencyMs} ms</span>}
          <span>#{m.id}</span>
        </div>
        <div className="trace-tool">
          <Collapse ghost size="small" defaultActiveKey={['result']} items={[
            {
              key: 'args', label: `入参（tool_args）`,
              children: <pre className="trace-json">{pretty(m.toolArgs)}</pre>
            },
            {
              key: 'result', label: `返回（tool_result）`,
              children: <pre className="trace-json">{pretty(m.toolResult)}</pre>
            }
          ]} />
        </div>
      </div>
    )
  }

  const isUser = m.role === 1
  return (
    <div className="trace-step">
      <span className={'trace-dot ' + (isUser ? 'user' : 'ai')}>{isUser ? '🙋' : '🤖'}</span>
      <div className="trace-role">
        <b style={{ color: isUser ? '#1f6feb' : '#303133' }}>{m.roleName}</b>
        {m.latencyMs ? <span>回答耗时 {m.latencyMs} ms</span> : null}
        {m.tokenCount ? <span>tokens {m.tokenCount}</span> : null}
        <span>#{m.id}</span>
      </div>
      <div className={'trace-bubble ' + (isUser ? 'user' : 'ai')}>
        {m.content || '（空）'}
        {m.citations && m.citations.length > 0 && (
          <div className="cite-tags">
            {m.citations.map((c, i) => (
              <Tooltip key={i} title={c.snippet}>
                <Tag color="green">来源：{c.title}</Tag>
              </Tooltip>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/** 后端已把 JSON 列解析为对象；兜底处理字符串与空值 */
function pretty(v: unknown): string {
  if (v === null || v === undefined || v === '') return '（无）'
  if (typeof v === 'string') {
    try {
      return JSON.stringify(JSON.parse(v), null, 2)
    } catch {
      return v
    }
  }
  return JSON.stringify(v, null, 2)
}
