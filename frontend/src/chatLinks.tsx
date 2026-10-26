import type { ReactNode } from 'react'

/**
 * 把助手回答里的 Markdown 链接（[标题](url)）渲染成可点击的 <a>，其余文字原样输出。
 * 只认站内 hash 路由（#/…）与 http(s) 两类地址，其余"链接"一律按纯文本显示，
 * 防止模型输出的奇怪地址被当成可执行链接；文字走 React 文本节点自动转义，不碰 innerHTML。
 * 兼容部分大模型输出 Markdown 链接时将 # URL 编码为 %23（如 %23/app/houses/16）或漏写 hash 符号。
 * 流式输出过程中链接可能先以半截文本出现，收完 ')' 后自然变成链接，无需特殊处理。
 * 独立成模块而非放在 AiChatView 内：组件文件只导出组件（react-refresh 约束），纯函数便于单测。
 */
export function renderWithLinks(content: string): ReactNode[] {
  const nodes: ReactNode[] = []
  const re = /\[([^\]\n]+)\]\(([^)\s]+)\)/g
  let last = 0
  let m: RegExpExecArray | null
  let k = 0
  while ((m = re.exec(content))) {
    if (m.index > last) nodes.push(content.slice(last, m.index))
    let href = m[2]
    if (href.startsWith('%23')) {
      try {
        href = decodeURIComponent(href)
      } catch {
        href = '#' + href.slice(3)
      }
    } else if (href.startsWith('/app/houses/')) {
      href = '#' + href
    }
    if (/^(#\/|https?:)/.test(href)) {
      nodes.push(
        <a
          key={k++}
          href={href}
          {...(href.startsWith('http') ? { target: '_blank', rel: 'noreferrer' } : {})}
        >
          {m[1]}
        </a>
      )
    } else {
      nodes.push(m[0])
    }
    last = m.index + m[0].length
  }
  if (last < content.length) nodes.push(content.slice(last))
  return nodes
}
