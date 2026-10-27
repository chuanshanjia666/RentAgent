import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ssePost } from './api'

/**
 * AI 对话 SSE 客户端单测（FT-SSE-xx）：
 * 流式响应按行解析、半行缓冲、非 data 行与坏 JSON 容错、失败降级回调。
 * 后端 /ai/sessions/{id}/messages 为 text/event-stream，前端必须逐字渲染而不能等整包。
 */
function sseResponse(
  chunks: string[],
  init: { ok?: boolean; status?: number; body?: unknown; contentType?: string; json?: unknown } = {}
) {
  const encoder = new TextEncoder()
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    }
  })
  const contentType =
    init.contentType ?? (init.json === undefined ? 'text/event-stream' : 'application/json')
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    body: init.body === undefined ? (init.json === undefined ? stream : null) : init.body,
    headers: new Headers({ 'content-type': contentType }),
    json: async () => init.json
  }
}

let fetchMock: ReturnType<typeof vi.fn>

/** 内存版 Storage：vitest 的 jsdom 环境不注入 localStorage，测试内显式提供 */
function memoryStorage(): Storage {
  const map = new Map<string, string>()
  return {
    get length() {
      return map.size
    },
    clear: () => map.clear(),
    getItem: (k: string) => (map.has(k) ? map.get(k)! : null),
    key: (i: number) => Array.from(map.keys())[i] ?? null,
    removeItem: (k: string) => void map.delete(k),
    setItem: (k: string, v: string) => void map.set(k, String(v))
  } as Storage
}

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('localStorage', memoryStorage())
  localStorage.setItem('ra_token', 'jwt-token')
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('FT-SSE AI 对话流式解析', () => {
  it('FT-SSE-01 delta 事件逐字回调，done 事件收尾', async () => {
    fetchMock.mockResolvedValue(
      sseResponse([
        'data: {"delta":"预"}\n\n',
        'data: {"delta":"算"}\n\n',
        'data: {"delta":"2500"}\n\n',
        'data: {"messageId":12,"citations":[{"docId":"1","title":"押金规则"}],"transferred":false,"latencyMs":860}\n\n'
      ])
    )
    const deltas: string[] = []
    let done: Record<string, any> | undefined

    await ssePost(
      '/ai/sessions/1/messages',
      { content: '预算 2500 以内' },
      {
        onDelta: d => deltas.push(d),
        onDone: d => (done = d)
      }
    )

    expect(deltas.join('')).toBe('预算2500')
    expect(done?.messageId).toBe(12)
    expect(done?.transferred).toBe(false)
    expect(done?.latencyMs).toBe(860)
  })

  it('FT-SSE-02 单条事件被拆到多个网络分片时仍能正确拼装（半行缓冲）', async () => {
    fetchMock.mockResolvedValue(
      sseResponse(['data: {"del', 'ta":"你', '好"}\n\ndata: {"messageId":2}\n', '\n'])
    )
    const deltas: string[] = []
    let done: Record<string, any> | undefined

    await ssePost(
      '/ai/sessions/1/messages',
      { content: '你好' },
      {
        onDelta: d => deltas.push(d),
        onDone: d => (done = d)
      }
    )

    expect(deltas).toEqual(['你好'])
    expect(done?.messageId).toBe(2)
  })

  it('FT-SSE-03 注释行、非 data 行与非法 JSON 行被忽略且不中断流', async () => {
    fetchMock.mockResolvedValue(
      sseResponse([
        ': keep-alive\n\n',
        'event: ping\n',
        'data: 不是JSON\n\n',
        'data: {"delta":"正常"}\n\n',
        'data: {"messageId":3}\n\n'
      ])
    )
    const deltas: string[] = []
    let done: Record<string, any> | undefined

    await ssePost(
      '/ai/sessions/1/messages',
      { content: '你好' },
      {
        onDelta: d => deltas.push(d),
        onDone: d => (done = d)
      }
    )

    expect(deltas).toEqual(['正常'])
    expect(done?.messageId).toBe(3)
  })

  it('FT-SSE-04 后端返回业务错误（非事件流）时透出后端原因', async () => {
    // 典型场景：未配置模型时 /ai/sessions/{id}/messages 返回 {code:4001,message:...}
    fetchMock.mockResolvedValue(
      sseResponse([], {
        ok: false,
        status: 200,
        json: {
          code: 4001,
          message:
            '未配置大模型服务：请注入模型凭据（AI_API_KEY / AGGREGATOR_API_KEY 等环境变量）后使用 AI 能力'
        }
      })
    )
    const onError = vi.fn()
    const onDelta = vi.fn()

    await ssePost('/ai/sessions/1/messages', { content: '你好' }, { onDelta, onError })

    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError.mock.calls[0][0]).toContain('未配置大模型服务')
    expect(onDelta).not.toHaveBeenCalled()
  })

  it('FT-SSE-04b 响应不是事件流且响应体非 JSON 时退回状态码提示', async () => {
    fetchMock.mockResolvedValue(
      sseResponse([], {
        ok: false,
        status: 502,
        contentType: 'text/html',
        json: undefined,
        body: null
      })
    )
    const onError = vi.fn()

    await ssePost('/ai/sessions/1/messages', { content: '你好' }, { onError })

    expect(onError).toHaveBeenCalledWith('连接智能助手失败（502）')
  })

  it('FT-SSE-04c 流中出现 error 载荷时回调错误原因（模型调用失败不再伪造回答）', async () => {
    fetchMock.mockResolvedValue(sseResponse(['data: {"error":"智能助手调用失败，请稍后重试"}\n\n']))
    const onError = vi.fn()
    const onDelta = vi.fn()
    const onDone = vi.fn()

    await ssePost('/ai/sessions/1/messages', { content: '你好' }, { onDelta, onDone, onError })

    expect(onError).toHaveBeenCalledWith('智能助手调用失败，请稍后重试')
    expect(onDelta).not.toHaveBeenCalled()
    expect(onDone).not.toHaveBeenCalled()
  })

  it('FT-SSE-05 网络异常时回调「网络连接失败」而不抛出', async () => {
    fetchMock.mockRejectedValue(new Error('offline'))
    const onError = vi.fn()

    await expect(
      ssePost('/ai/sessions/1/messages', { content: '你好' }, { onError })
    ).resolves.toBeUndefined()
    expect(onError).toHaveBeenCalledWith('网络连接失败')
  })

  it('FT-SSE-06 请求携带 JWT 与 JSON 头，并使用带版本的接口地址', async () => {
    fetchMock.mockResolvedValue(sseResponse(['data: {"messageId":1}\n\n']))

    await ssePost('/ai/sessions/9/messages', { content: '找房' }, {})

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/ai/sessions/9/messages')
    expect(init.method).toBe('POST')
    expect(init.headers.Authorization).toBe('Bearer jwt-token')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body)).toEqual({ content: '找房' })
  })

  it('FT-SSE-07 末尾事件不带换行时也要处理（收尾补刷缓冲）', async () => {
    // 服务端最后一帧没有以 \n 结束，事件会滞留在缓冲区里；不补刷就会把 done 丢掉
    fetchMock.mockResolvedValue(
      sseResponse(['data: {"delta":"你好"}\n\ndata: {"messageId":7,"latencyMs":120}'])
    )
    const deltas: string[] = []
    let done: Record<string, any> | undefined

    await ssePost(
      '/ai/sessions/1/messages',
      { content: '你好' },
      { onDelta: d => deltas.push(d), onDone: d => (done = d) }
    )

    expect(deltas).toEqual(['你好'])
    expect(done?.messageId).toBe(7)
  })

  it('FT-SSE-08 服务端未发终止事件时兜底收尾，界面不会永远停在「回复中…」', async () => {
    fetchMock.mockResolvedValue(sseResponse(['data: {"delta":"在"}\n\n']))
    const onDone = vi.fn()
    const onError = vi.fn()

    await ssePost('/ai/sessions/1/messages', { content: '在吗' }, { onDone, onError })

    expect(onDone).toHaveBeenCalledTimes(1)
    expect(onDone).toHaveBeenCalledWith({})
    expect(onError).not.toHaveBeenCalled()
  })

  it('FT-SSE-09 流中途断开时回调可读原因，且不抛未捕获异常', async () => {
    const encoder = new TextEncoder()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('data: {"delta":"半"}\n\n'))
        controller.error(new Error('connection reset'))
      }
    })
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      body: stream,
      headers: new Headers({ 'content-type': 'text/event-stream' }),
      json: async () => ({})
    })
    const onError = vi.fn()
    const onDone = vi.fn()

    await expect(
      ssePost('/ai/sessions/1/messages', { content: '你好' }, { onError, onDone })
    ).resolves.toBeUndefined()
    expect(onError).toHaveBeenCalledWith('连接中断，请重试')
    expect(onDone).not.toHaveBeenCalled()
  })

  it('FT-SSE-10 主动中断（切换会话）不触发任何回调，不误报网络错误', async () => {
    const encoder = new TextEncoder()
    fetchMock.mockImplementation((_url: string, init: RequestInit) => {
      const stream = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(encoder.encode('data: {"delta":"半"}\n\n'))
          // 之后不再产出，模拟模型仍在生成；中断时让在途的 read 以错误结束
          init.signal?.addEventListener('abort', () =>
            controller.error(new DOMException('aborted', 'AbortError'))
          )
        }
      })
      return Promise.resolve({
        ok: true,
        status: 200,
        body: stream,
        headers: new Headers({ 'content-type': 'text/event-stream' }),
        json: async () => ({})
      })
    })
    const ac = new AbortController()
    const deltas: string[] = []
    const onError = vi.fn()
    const onDone = vi.fn()
    let seenFirst!: () => void
    const firstDelta = new Promise<void>(r => (seenFirst = r))

    const pending = ssePost(
      '/ai/sessions/1/messages',
      { content: '你好' },
      {
        onDelta: d => {
          deltas.push(d)
          seenFirst()
        },
        onError,
        onDone
      },
      ac.signal
    )

    // 等首片真正被消费，这样中断才落在「流中途」而非请求发出前
    await firstDelta
    ac.abort()

    await expect(pending).resolves.toBeUndefined()
    expect(deltas).toEqual(['半'])
    expect(onError).not.toHaveBeenCalled()
    expect(onDone).not.toHaveBeenCalled()
  })
})
