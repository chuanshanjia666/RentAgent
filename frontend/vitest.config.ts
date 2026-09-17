import { defineConfig } from 'vitest/config'

/**
 * 前端单测配置：与 vite.config.ts 分离，避免测试配置进入构建产物配置。
 * environment=jsdom 供运行时适配与 SSE 解析用例使用（模块级读取 window / fetch）。
 */
export default defineConfig({
  test: {
    environment: 'jsdom',
    // jsdom 需要非 opaque 的 origin 才会提供 localStorage（JWT 存储依赖）
    environmentOptions: { jsdom: { url: 'http://localhost:5173/' } },
    include: ['src/**/*.test.ts'],
    reporters: 'default'
  }
})
