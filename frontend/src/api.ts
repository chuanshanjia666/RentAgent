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
      if (!location.hash.startsWith('#/login')) location.hash = '#/login'
    }
    message.error((err.response && err.response.data && err.response.data.message) || '网络异常，请稍后再试')
    return Promise.reject(err)
  }
)

/**
 * AI 对话 SSE：POST 流式读取，delta 事件逐字回调，done 事件收尾回调。
 * 两类失败都要如实透出原因：① 响应不是事件流（如未配置模型时后端返回 {code:4001,message}）；
 * ② 流中收到 {error} 载荷（模型调用失败）。
 */
export async function ssePost(
  path: string,
  body: unknown,
  cbs: {
    onDelta?: (delta: string) => void
    onDone?: (done: Record<string, any>) => void
    onError?: (msg?: string) => void
  }
) {
  const token = localStorage.getItem('ra_token')
  let resp: Response
  try {
    resp = await fetch(apiUrl(path), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify(body)
    })
  } catch (e) {
    cbs.onError && cbs.onError('网络连接失败')
    return
  }
  const contentType = resp.headers ? resp.headers.get('content-type') || '' : ''
  if (!resp.ok || !resp.body || !contentType.includes('text/event-stream')) {
    let reason = '连接智能助手失败（' + resp.status + '）'
    try {
      const payload = await resp.json()
      reason = payload.message || payload.error || reason
    } catch (e) { /* 响应体不是 JSON，保留状态码提示 */ }
    cbs.onError && cbs.onError(reason)
    return
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    const lines = buf.split('\n')
    buf = lines.pop() as string
    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      try {
        const d = JSON.parse(line.slice(5))
        if (d.error) cbs.onError && cbs.onError(d.error)
        else if (d.delta !== undefined) cbs.onDelta && cbs.onDelta(d.delta)
        else cbs.onDone && cbs.onDone(d)
      } catch (e) { /* 忽略不完整行 */ }
    }
  }
}

/** 统一请求入口：返回值即后端 data 字段（拦截器已解包） */
export default {
  get: <T = any>(url: string, config?: AxiosRequestConfig) => http.get<T, T>(url, config),
  post: <T = any>(url: string, data?: unknown, config?: AxiosRequestConfig) => http.post<T, T>(url, data, config),
  put: <T = any>(url: string, data?: unknown, config?: AxiosRequestConfig) => http.put<T, T>(url, data, config),
  patch: <T = any>(url: string, data?: unknown, config?: AxiosRequestConfig) => http.patch<T, T>(url, data, config),
  delete: <T = any>(url: string, config?: AxiosRequestConfig) => http.delete<T, T>(url, config)
}
