import { afterEach, describe, expect, it, vi } from 'vitest'

/**
 * 运行时适配单测（FT-RUNTIME-xx）：一套代码两种分发（Web / Electron 桌面端）的地址解析。
 * 关键约束：浏览器版走同源相对路径，桌面端由 preload 注入绝对地址（file:// 下相对路径会指向本地文件）。
 */
async function loadRuntime(bridge?: Record<string, unknown>, viteBase?: string) {
  vi.resetModules()
  const w = window as unknown as { RentAgent?: Record<string, unknown> }
  if (bridge === undefined) {
    delete w.RentAgent
  } else {
    w.RentAgent = bridge
  }
  if (viteBase === undefined) {
    vi.stubEnv('VITE_API_BASE', '')
  } else {
    vi.stubEnv('VITE_API_BASE', viteBase)
  }
  return await import('./runtime')
}

afterEach(() => {
  vi.unstubAllEnvs()
  delete (window as unknown as { RentAgent?: unknown }).RentAgent
})

describe('FT-RUNTIME 运行时地址解析', () => {
  it('FT-RUNTIME-01 浏览器版无注入时使用同源相对路径', async () => {
    const rt = await loadRuntime()

    expect(rt.API_BASE).toBe('')
    expect(rt.apiUrl('/houses')).toBe('/api/v1/houses')
    expect(rt.assetUrl('/uploads/a.png')).toBe('/uploads/a.png')
  })

  it('FT-RUNTIME-02 桌面端注入绝对地址后接口与资源均补全基址', async () => {
    const rt = await loadRuntime({ apiBase: 'http://127.0.0.1:8080' })

    expect(rt.API_BASE).toBe('http://127.0.0.1:8080')
    expect(rt.apiUrl('/houses')).toBe('http://127.0.0.1:8080/api/v1/houses')
    expect(rt.assetUrl('/uploads/a.png')).toBe('http://127.0.0.1:8080/uploads/a.png')
  })

  it('FT-RUNTIME-03 基址尾部斜杠被规范化，避免出现双斜杠', async () => {
    const rt = await loadRuntime({ apiBase: 'http://127.0.0.1:8080///' })

    expect(rt.API_BASE).toBe('http://127.0.0.1:8080')
    expect(rt.apiUrl('/houses')).toBe('http://127.0.0.1:8080/api/v1/houses')
  })

  it('FT-RUNTIME-04 注入值优先于构建期环境变量，未注入时回落到环境变量', async () => {
    const injected = await loadRuntime({ apiBase: 'http://injected:8080' }, 'http://from-env:8080')
    expect(injected.API_BASE).toBe('http://injected:8080')

    const fallback = await loadRuntime(undefined, 'http://from-env:8080')
    expect(fallback.API_BASE).toBe('http://from-env:8080')
  })

  it('FT-RUNTIME-05 资源地址：空值返回空串，绝对地址与 data/blob 原样直通', async () => {
    const rt = await loadRuntime({ apiBase: 'http://host:8080' })

    expect(rt.assetUrl(null)).toBe('')
    expect(rt.assetUrl(undefined)).toBe('')
    expect(rt.assetUrl('')).toBe('')
    expect(rt.assetUrl('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png')
    expect(rt.assetUrl('data:image/png;base64,AAAA')).toBe('data:image/png;base64,AAAA')
    expect(rt.assetUrl('blob:http://localhost/1')).toBe('blob:http://localhost/1')
  })
})
