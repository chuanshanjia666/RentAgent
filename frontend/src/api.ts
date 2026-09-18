import axios, { type AxiosRequestConfig } from 'axios'
import { message } from 'antd'
import { API_BASE, apiUrl } from './runtime'

/**
 * axios 实例：统一携带 JWT、解包 {code,message,data}、401 踢回登录。
 * 响应拦截器已把 AxiosResponse 解包为业务 data，因此这里用
 * `http.get<T, T>` 的第二个泛型把返回值直接声明为业务数据类型。
 */
const http = axios.create({ baseURL: API_BASE + '/api/v1', timeout: 20000 })

http.interceptors.request.use(cfg => {
  const t = localStorage.getItem('ra_token')
  if (t) cfg.headers.Authorization = 'Bearer ' + t
  return cfg
})

/**
 * 401 时的登录态回调，由 AuthProvider 在挂载时注册。
 * 拦截器只能清 localStorage，而路由守卫读的是 React 里的 auth 状态——
 * 只清 localStorage 会让守卫仍按"已登录"渲染受保护页面，此后每个请求继续 401。
 */
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(fn: (() => void) | null) {
  unauthorizedHandler = fn
}

http.interceptors.response.use(
  resp => {
    const r = resp.data
    if (r.code !== 0) {
      message.error(r.message || '请求失败')
      return Promise.reject(r)
    }
    return r.data
  },
  err => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('ra_token')
      unauthorizedHandler?.()
      if (!location.hash.startsWith('#/login')) location.hash = '#/login'
    }
    message.error(
      (err.response && err.response.data && err.response.data.message) || '网络异常，请稍后再试'
    )
    return Promise.reject(err)
  }
)

/**
 * AI 对话 SSE：POST 流式读取，delta 事件逐字回调，done 事件收尾回调。
 * 两类失败都要如实透出原因：① 响应不是事件流（如未配置模型时后端返回 {code:4001,message}）；
 * ② 流中收到 {error} 载荷（模型调用失败）。
 *
 * 契约：onDone 与 onError **恰好触发一个**，调用方据此收尾即可，不必再猜流什么时候结束——
 * 少了这条保证，服务端不发终止事件时界面会永远停在「回复中…」。
 * `signal` 用于主动中断（如切换会话），中断不算失败，两个回调都不触发。
 */
export async function ssePost(
  path: string,
  body: unknown,
  cbs: {
    onDelta?: (delta: string) => void
    onDone?: (done: Record<string, any>) => void
    onError?: (msg?: string) => void
  },
  signal?: AbortSignal
) {
  // 已结算标记：保证终止回调只发一次
  let settled = false
  const fail = (msg?: string) => {
    if (settled) return
    settled = true
    cbs.onError?.(msg)
  }
  const finish = (d: Record<string, any>) => {
    if (settled) return
    settled = true
    cbs.onDone?.(d)
  }

  const token = localStorage.getItem('ra_token')
  let resp: Response
  try {
    resp = await fetch(apiUrl(path), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify(body),
      signal
    })
  } catch {
    // 主动中断不是失败，不该弹错
    if (!signal?.aborted) fail('网络连接失败')
    return
  }
  const contentType = resp.headers ? resp.headers.get('content-type') || '' : ''
  if (!resp.ok || !resp.body || !contentType.includes('text/event-stream')) {
    // 与 axios 拦截器同一套登录态处理：SSE 走原生 fetch，不共用拦截器，
    // 少了这段，对话页 token 过期时只会弹一句错误提示，人卡在页面上反复失败，而别的页面会被踢回登录
    if (resp.status === 401) {
      localStorage.removeItem('ra_token')
      unauthorizedHandler?.()
      if (!location.hash.startsWith('#/login')) location.hash = '#/login'
    }
    let reason = '连接智能助手失败（' + resp.status + '）'
    try {
      const payload = await resp.json()
      reason = payload.message || payload.error || reason
    } catch {
      /* 响应体不是 JSON，保留状态码提示 */
    }
    fail(reason)
    return
  }

  const handleLine = (line: string) => {
    if (!line.startsWith('data:')) return
    // 解析与分发分开：解析失败只是"这一行不完整"，不该把调用方回调里抛出的异常也一起吞掉
    let d: any
    try {
      d = JSON.parse(line.slice(5))
    } catch {
      return
    }
    if (d.error) fail(d.error)
    else if (d.delta !== undefined) cbs.onDelta?.(d.delta)
    else finish(d)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const lines = buf.split('\n')
      buf = lines.pop() as string
      for (const line of lines) handleLine(line)
    }
    // 最后一行的结尾未必带换行，还留在 buf 里；不补这一下就会丢事件
    if (buf) handleLine(buf)
  } catch {
    // 流中途断开（网络抖动、服务端中断、主动 abort）
    if (!signal?.aborted) fail('连接中断，请重试')
    return
  }

  // 流正常结束却没等到终止事件（如服务端漏发 done），也要让调用方收尾
  finish({})
}

/** 统一请求入口：返回值即后端 data 字段（拦截器已解包） */
export default {
  get: <T = any>(url: string, config?: AxiosRequestConfig) => http.get<T, T>(url, config),
  post: <T = any>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    http.post<T, T>(url, data, config),
  put: <T = any>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    http.put<T, T>(url, data, config),
  patch: <T = any>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    http.patch<T, T>(url, data, config),
  delete: <T = any>(url: string, config?: AxiosRequestConfig) => http.delete<T, T>(url, config)
}
