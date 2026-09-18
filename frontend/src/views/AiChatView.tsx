import { useEffect, useRef, useState } from 'react'
import { Button, Input, message, Radio, Tag } from 'antd'
import http, { ssePost } from '../api'
import { fmtTime } from '../constants'
import type { ChatMsg } from '../types'

export default function AiChatView() {
  const [scene, setScene] = useState(1) // 1 找房 2 客服
  const [sessions, setSessions] = useState<any[]>([])
  const [current, setCurrent] = useState<number | null>(null)
  const [messages, setMessages] = useState<ChatMsg[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const msgsEl = useRef<HTMLDivElement | null>(null)
  /**
   * 在途流式请求的中断句柄。切换/新建会话时必须先中断：
   * 否则旧的增量回调会继续 updateLast，把上一个会话的回复追加到刚加载的新会话消息里。
   */
  const inFlight = useRef<AbortController | null>(null)

  const quickPrompts =
    scene === 1
      ? ['预算2500以内，要一居室，近地铁', '3000元两居室，沙河口区', '再便宜一点']
      : ['押金怎么退？', '提前退租有什么责任？', '房子东西坏了谁修？']
  const placeholder = scene === 1 ? '例：预算2500以内，要一居室，近地铁' : '例：押金怎么退？'

  const scrollBottom = () => {
    setTimeout(() => {
      if (msgsEl.current) msgsEl.current.scrollTop = msgsEl.current.scrollHeight
    }, 30)
  }

  /** 中断在途流并解除发送禁用。被中断的回调不会再触发，因此不会改动消息列表 */
  function abortStream() {
    inFlight.current?.abort()
    inFlight.current = null
    setStreaming(false)
  }

  async function loadSessions(): Promise<any[]> {
    const p = await http.get('/ai/sessions', { params: { size: 50 } })
    setSessions(p.list)
    return p.list
  }

  async function newSession(sc: number = scene) {
    abortStream()
    const s = await http.post('/ai/sessions', { scene: sc })
    setCurrent(s.id)
    setMessages([])
    await loadSessions()
  }

  /**
   * 打开某个历史会话。`sc` 由调用方传入而不再从 state 里找：
   * 侧栏点开的是任意场景的会话，场景单选必须跟着会话走，否则快捷提问与占位文案会串场景。
   */
  async function switchSession(id: number, sc?: number) {
    abortStream()
    setCurrent(id)
    const history = await http.get(`/ai/sessions/${id}/history`)
    setMessages(
      history.map((m: any) => ({ role: m.role, content: m.content, citations: m.citations || [] }))
    )
    if (typeof sc === 'number') setScene(sc)
    scrollBottom()
  }

  function updateLast(fn: (m: ChatMsg) => ChatMsg) {
    setMessages(prev => {
      // 会话被切换/清空后列表可能已空，此时没有"最后一条"可改
      if (!prev.length) return prev
      const next = [...prev]
      next[next.length - 1] = fn({ ...next[next.length - 1] })
      return next
    })
  }

  async function send(text?: string) {
    const content = (text ?? input).trim()
    if (!content || streaming) return
    let sessionId = current
    if (!sessionId) {
      const s = await http.post('/ai/sessions', { scene })
      sessionId = s.id
      setCurrent(sessionId)
    }
    setInput('')
    setMessages(prev => [
      ...prev,
      { role: 1, content },
      { role: 2, content: '', citations: [], typing: true }
    ])
    setStreaming(true)
    scrollBottom()
    const ac = new AbortController()
    inFlight.current = ac
    await ssePost(
      `/ai/sessions/${sessionId}/messages`,
      { content },
      {
        onDelta(d) {
          updateLast(m => ({ ...m, content: m.content + d }))
          scrollBottom()
        },
        onDone(d) {
          updateLast(m => ({
            ...m,
            typing: false,
            citations: (d.citations || []).map((c: any) => ({ title: c.title, snippet: c.snippet }))
          }))
          if (d.transferred) message.info('未命中知识库，已提示转人工')
          setStreaming(false)
          loadSessions()
          scrollBottom()
        },
        onError(msg) {
          updateLast(m => ({
            ...m,
            typing: false,
            content: m.content || msg || '抱歉，出了点问题，请稍后再试。'
          }))
          setStreaming(false)
        }
      },
      ac.signal
    )
    // 中途被 abortStream 换掉时 inFlight 已指向新的控制器，这里不要误清
    if (inFlight.current === ac) inFlight.current = null
  }

  useEffect(() => {
    // 一次拉取即可：列表已按更新时间倒序，首条就是最近会话，不必再为此单独请求一次 size=1
    loadSessions()
      .then(list => (list.length ? switchSession(list[0].id, list[0].scene) : newSession()))
      .catch(() => {
        /* 首屏定位最近会话失败不阻断，用户仍可手动新建 */
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅在进入页面时定位到最近一个会话
  }, [])

  // 组件卸载时中断在途流，避免回调对着已卸载的组件 setState
  useEffect(() => () => inFlight.current?.abort(), [])

  return (
    <div className="page">
      <div className="chat-wrap">
        <div className="chat-side">
          <Radio.Group
            value={scene}
            style={{ width: '100%', marginBottom: 10, display: 'flex' }}
            buttonStyle="solid"
            onChange={e => {
              setScene(e.target.value)
              newSession(e.target.value)
            }}
          >
            <Radio.Button value={1} style={{ width: '50%', textAlign: 'center' }}>
              找房助手
            </Radio.Button>
            <Radio.Button value={2} style={{ width: '50%', textAlign: 'center' }}>
              智能客服
            </Radio.Button>
          </Radio.Group>
          <Button style={{ width: '100%' }} onClick={() => newSession()}>
            ＋ 新会话
          </Button>
          {sessions.map(s => (
            <div
              key={s.id}
              className={'session-item' + (s.id === current ? ' active' : '')}
              onClick={() => switchSession(s.id, s.scene)}
            >
              <div className="session-title">{s.title || '新会话'}</div>
              <div className="session-time">{fmtTime(s.updatedAt)}</div>
            </div>
          ))}
        </div>

        <div className="chat-main">
          <div className="chat-msgs" ref={msgsEl}>
            {messages.length === 0 && (
              <div className="chat-empty">
                <div style={{ fontSize: 40 }}>🤖</div>
                <div style={{ color: '#8492a6', margin: '8px 0 16px' }}>
                  {scene === 1
                    ? '用一句话描述你的租房需求，我来帮你找'
                    : '押金、退租、维修、违约……有问题尽管问'}
                </div>
                {quickPrompts.map(p => (
                  <Button
                    key={p}
                    size="small"
                    shape="round"
                    style={{ margin: '0 6px 8px' }}
                    onClick={() => send(p)}
                  >
                    {p}
                  </Button>
                ))}
              </div>
            )}
            {messages.map((m, i) => (
              <div key={i} className={'msg-row ' + (m.role === 1 ? 'me' : 'ai')}>
                <div className="bubble">
                  {m.content}
                  {m.typing ? '▌' : ''}
                  {m.citations && m.citations.length > 0 && (
                    <div className="cite-tags">
                      {m.citations.map((c, ci) => (
                        <Tag key={ci} color="green" style={{ marginBottom: 4 }}>
                          来源：{c.title}
                        </Tag>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
          <div className="chat-input">
            <Input.TextArea
              rows={2}
              style={{ flex: 1, resize: 'none' }}
              placeholder={placeholder}
              value={input}
              onChange={e => setInput(e.target.value)}
              onPressEnter={e => {
                if (!e.shiftKey) {
                  e.preventDefault()
                  send()
                }
              }}
            />
            <Button
              type="primary"
              style={{ marginLeft: 10 }}
              disabled={streaming || !input.trim()}
              onClick={() => send()}
            >
              {streaming ? '回复中…' : '发送'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
