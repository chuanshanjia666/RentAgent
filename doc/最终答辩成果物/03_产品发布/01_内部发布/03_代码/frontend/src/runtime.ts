/**
 * 运行环境适配：同一份构建产物同时供浏览器版与桌面端（Electron 外壳）使用。
 *
 * 浏览器版：页面由 Nginx / Vite 提供，接口走同源相对路径 `/api/v1`，由代理转发到后端；
 * 桌面端：页面以 `file://` 加载，相对路径会被解析成本地文件（`file:///api/v1`），
 *        因此由 Electron 预加载脚本注入后端绝对地址。
 */

/** 桌面端外壳注入的运行时配置（浏览器版不存在） */
interface RuntimeBridge {
  apiBase?: string
}

const bridge: RuntimeBridge = (window as unknown as { RentAgent?: RuntimeBridge }).RentAgent ?? {}

/** 后端服务基址（协议 + 域名 + 端口），浏览器版为空串表示同源相对路径 */
export const API_BASE: string = (bridge.apiBase ?? import.meta.env.VITE_API_BASE ?? '').replace(
  /\/+$/,
  ''
)

/** 后端接口绝对地址：`apiUrl('/houses')` → `/api/v1/houses`（浏览器版）或 `http://host:8080/api/v1/houses`（桌面版） */
export function apiUrl(path: string): string {
  return `${API_BASE}/api/v1${path}`
}

/** 后端返回的资源地址（如 `/uploads/xxx.png`）补全基址，绝对地址原样返回 */
export function assetUrl(url: string | null | undefined): string {
  if (!url) return ''
  return /^(https?:|data:|blob:)/.test(url) ? url : API_BASE + url
}
