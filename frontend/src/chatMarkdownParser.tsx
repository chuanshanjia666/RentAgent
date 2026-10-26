import type { ReactNode } from 'react'

/**
 * 提取并规范化链接地址：
 * 1. 兼容部分模型将 # 编码为 %23（如 %23/app/houses/16）；
 * 2. 兼容部分模型漏写 hash 符号的站内地址（如 /app/houses/16）；
 * 3. 严格校验协议，仅放行 #/ 与 http(s):// 地址，其余一律拒绝以防 XSS。
 */
export function sanitizeHref(rawHref: string): string | null {
  let href = rawHref.trim()
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
    return href
  }
  return null
}

export interface ExtractedLink {
  raw: string
  title: string
  href: string | null
}

/**
 * 链接优先抽取容器（LinkStore）：
 * 在解析任何 Markdown 标记之前，优先保护所有形如 [标题](url) 的跳转链接，
 * 将其置换为安全的私有占位符 \x00LINK_i\x00，从根本上杜绝下划线、星号或代码块将链接切碎。
 */
export class LinkStore {
  private links: ExtractedLink[] = []

  tokenize(text: string): string {
    const linkRe = /\[([^\]\n]+)\]\(([^)\s]+)\)/g
    return text.replace(linkRe, (raw, title, rawHref) => {
      const id = this.links.length
      this.links.push({
        raw,
        title,
        href: sanitizeHref(rawHref)
      })
      return `@@LINK_${id}@@`
    })
  }

  get(id: number): ExtractedLink | undefined {
    return this.links[id]
  }
}

/**
 * 行内渲染核心：在已隔离链接占位符的文本中，递归解析行内代码、粗体、斜体、删除线与链接还原。
 */
export function renderInlineText(
  text: string,
  linkStore: LinkStore,
  keyPrefix = 'in'
): ReactNode[] {
  const inlineRe =
    /(`([^`\n]+)`)|(\*\*([^\n]+?)\*\*)|(__([^\n]+?)__)|((?<!\*)\*([^\s*](?:[^\n*]*?[^\s*])?)\*(?!\*))|(~~([^\n~]+?)~~)|(@@LINK_(\d+)@@)/g
  const nodes: ReactNode[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null
  let k = 0

  while ((match = inlineRe.exec(text))) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index))
    }

    const key = `${keyPrefix}-${k++}`
    if (match[1]) {
      // 行内代码 `code`
      nodes.push(
        <code key={key} className="md-inline-code">
          {match[2]}
        </code>
      )
    } else if (match[3] || match[5]) {
      // 粗体 **bold** 或 __bold__
      const content = match[4] ?? match[6]
      nodes.push(
        <strong key={key} className="md-bold">
          {renderInlineText(content, linkStore, `${key}-b`)}
        </strong>
      )
    } else if (match[7]) {
      // 斜体 *italic*（要求星号两侧非空白，避免误伤列表项与强调符号）
      nodes.push(
        <em key={key} className="md-italic">
          {renderInlineText(match[8], linkStore, `${key}-i`)}
        </em>
      )
    } else if (match[9]) {
      // 删除线 ~~del~~
      nodes.push(
        <del key={key} className="md-del">
          {renderInlineText(match[10], linkStore, `${key}-d`)}
        </del>
      )
    } else if (match[11]) {
      // 还原跳转链接 Token
      const linkId = Number(match[12])
      const link = linkStore.get(linkId)
      if (link && link.href) {
        const isHttp = link.href.startsWith('http')
        nodes.push(
          <a
            key={key}
            href={link.href}
            className="md-link chat-house-link"
            {...(isHttp ? { target: '_blank', rel: 'noreferrer' } : {})}
            onClick={() => {
              if (!isHttp && link.href?.startsWith('#/')) {
                // 确保 SPA 路由顺畅响应 hash 变更
                window.location.hash = link.href
              }
            }}
          >
            {renderInlineText(link.title, linkStore, `${key}-lbl`)}
          </a>
        )
      } else {
        nodes.push(link ? link.raw : match[0])
      }
    }

    lastIndex = match.index + match[0].length
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex))
  }

  return nodes
}

export function renderInline(text: string, keyPrefix = 'in'): ReactNode[] {
  const linkStore = new LinkStore()
  const tokenized = linkStore.tokenize(text)
  return renderInlineText(tokenized, linkStore, keyPrefix)
}

export interface BlockCode {
  type: 'code'
  lang: string
  lines: string[]
}

export interface BlockHeading {
  type: 'heading'
  level: number
  text: string
}

export interface BlockUl {
  type: 'ul'
  items: string[]
}

export interface BlockOl {
  type: 'ol'
  items: string[]
}

export interface BlockQuote {
  type: 'quote'
  lines: string[]
}

export interface BlockHr {
  type: 'hr'
}

export interface BlockParagraph {
  type: 'p'
  lines: string[]
}

export type Block =
  BlockCode | BlockHeading | BlockUl | BlockOl | BlockQuote | BlockHr | BlockParagraph

/**
 * 将整段 Markdown 文本切分为块级语法单元
 */
export function parseBlocks(markdown: string): Block[] {
  const lines = markdown.split(/\r?\n/)
  const blocks: Block[] = []

  let inCode = false
  let codeLang = ''
  let codeLines: string[] = []

  let currQuote: string[] | null = null
  let currUl: string[] | null = null
  let currOl: string[] | null = null
  let currP: string[] | null = null

  function flushQuote() {
    if (currQuote && currQuote.length > 0) {
      blocks.push({ type: 'quote', lines: currQuote })
      currQuote = null
    }
  }

  function flushList() {
    if (currUl && currUl.length > 0) {
      blocks.push({ type: 'ul', items: currUl })
      currUl = null
    }
    if (currOl && currOl.length > 0) {
      blocks.push({ type: 'ol', items: currOl })
      currOl = null
    }
  }

  function flushParagraph() {
    if (currP && currP.length > 0) {
      blocks.push({ type: 'p', lines: currP })
      currP = null
    }
  }

  function flushAllNonCode() {
    flushQuote()
    flushList()
    flushParagraph()
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]

    // 1. 代码块围栏判定
    const codeMatch = line.match(/^```(\w+)?\s*$/)
    if (codeMatch) {
      if (inCode) {
        // 闭合代码块
        blocks.push({ type: 'code', lang: codeLang, lines: codeLines })
        inCode = false
        codeLang = ''
        codeLines = []
      } else {
        // 开启代码块
        flushAllNonCode()
        inCode = true
        codeLang = codeMatch[1] || ''
        codeLines = []
      }
      continue
    }

    if (inCode) {
      codeLines.push(line)
      continue
    }

    // 2. 空行
    if (line.trim() === '') {
      flushAllNonCode()
      continue
    }

    // 3. 水平分割线 (--- / *** / ___)
    if (/^(?:---|\*\*\*|___)\s*$/.test(line)) {
      flushAllNonCode()
      blocks.push({ type: 'hr' })
      continue
    }

    // 4. 标题 (# ... ######)
    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/)
    if (headingMatch) {
      flushAllNonCode()
      blocks.push({
        type: 'heading',
        level: headingMatch[1].length,
        text: headingMatch[2].trim()
      })
      continue
    }

    // 5. 引用块 (> ...)
    const quoteMatch = line.match(/^>\s?(.*)$/)
    if (quoteMatch) {
      flushList()
      flushParagraph()
      if (!currQuote) currQuote = []
      currQuote.push(quoteMatch[1])
      continue
    }
    flushQuote()

    // 6. 无序列表 (- / * / +)
    const ulMatch = line.match(/^[-*+]\s+(.+)$/)
    if (ulMatch) {
      if (currOl) flushList()
      flushParagraph()
      if (!currUl) currUl = []
      currUl.push(ulMatch[1])
      continue
    }

    // 7. 有序列表 (1. ...)
    const olMatch = line.match(/^\d+\.\s+(.+)$/)
    if (olMatch) {
      if (currUl) flushList()
      flushParagraph()
      if (!currOl) currOl = []
      currOl.push(olMatch[1])
      continue
    }

    // 8. 普通行：如果当前正处于有序列表中且该行看起来是房源信息的缩进描述，追加到当前列表中
    if (currOl && /^\s{2,}/.test(line)) {
      currOl[currOl.length - 1] += '\n' + line.trim()
      continue
    }
    if (currUl && /^\s{2,}/.test(line)) {
      currUl[currUl.length - 1] += '\n' + line.trim()
      continue
    }

    // 9. 普通段落行
    flushList()
    if (!currP) currP = []
    currP.push(line)
  }

  // 循环结束时冲刷遗留块
  if (inCode) {
    blocks.push({ type: 'code', lang: codeLang, lines: codeLines })
  }
  flushAllNonCode()

  return blocks
}
