import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ssePost } from './api'

/**
 * AI 对话 SSE 客户端单测（FT-SSE-xx）：
 * 流式响应按行解析、半行缓冲、非 data 行与坏 JSON 容错、失败降级回调。
 * 后端 /ai/sessions/{id}/messages 为 text/event-stream，前端必须逐字渲染而不能等整包。
 */
function sseResponse(chunks: string[], init: { ok?: boolean; status?: number; body?: unknown } = {}) {
  const encoder = new TextEncoder()
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    }
  })
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    body: init.body === undefined ? stream : init.body
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

    await ssePost('/ai/sessions/1/messages', { content: '预算 2500 以内' }, {
      onDelta: d => deltas.push(d),
      onDone: d => (done = d)
    })

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

    await ssePost('/ai/sessions/1/messages', { content: '你好' }, {
      onDelta: d => deltas.push(d),
      onDone: d => (done = d)
    })

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

    await ssePost('/ai/sessions/1/messages', { content: '你好' }, {
      onDelta: d => deltas.push(d),
      onDone: d => (done = d)
    })

    expect(deltas).toEqual(['正常'])
    expect(done?.messageId).toBe(3)
  })

  it('FT-SSE-04 HTTP 失败时回调错误并带上状态码', async () => {
    fetchMock.mockResolvedValue(sseResponse([], { ok: false, status: 4002, body: null }))
    const onError = vi.fn()
    const onDelta = vi.fn()

    await ssePost('/ai/sessions/1/messages', { content: '你好' }, { onDelta, onError })

    expect(onError).toHaveBeenCalledWith('连接智能助手失败（4002）')
    expect(onDelta).not.toHaveBeenCalled()
  })

  it('FT-SSE-05 网络异常时回调「网络连接失败」而不抛出', async () => {
    fetchMock.mockRejectedValue(new Error('offline'))
    const onError = vi.fn()

    await expect(ssePost('/ai/sessions/1/messages', { content: '你好' }, { onError })).resolves.toBeUndefined()
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
})
