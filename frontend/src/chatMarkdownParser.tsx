import type { ReactNode } from 'react'

/**
 * 格式化并校验链接地址：
 * 1. 兼容部分模型输出 %23 转义的 hash 路由（如 %23/app/houses/16）；
 * 2. 兼容漏写 # 的站内房源地址（如 /app/houses/16 自动补为 #/app/houses/16）；
 * 3. 仅放行以 #/ 或 http(s):// 开头的合法协议，其余（如 javascript:）拦截为纯文本。
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

export function renderInline(text: string, keyPrefix = 'in'): ReactNode[] {
  const inlineRe =
    /(`([^`\n]+)`)|(\[([^\]\n]+)\]\(([^)\s]+)\))|(\*\*([^\n]+?)\*\*)|(__([^\n]+?)__)|(\*([^\n*]+?)\*)|(_([^\n_]+?)_)|(~~([^\n~]+?)~~)/g
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
    } else if (match[3]) {
      // 链接 [text](url)
      const label = match[4]
      const rawHref = match[5]
      const validHref = sanitizeHref(rawHref)
      if (validHref) {
        nodes.push(
          <a
            key={key}
            href={validHref}
            className="md-link"
            {...(validHref.startsWith('http') ? { target: '_blank', rel: 'noreferrer' } : {})}
          >
            {renderInline(label, `${key}-lbl`)}
          </a>
        )
      } else {
        nodes.push(match[0])
      }
    } else if (match[6] || match[8]) {
      // 粗体 **bold** 或 __bold__
      const content = match[7] ?? match[9]
      nodes.push(
        <strong key={key} className="md-bold">
          {renderInline(content, `${key}-b`)}
        </strong>
      )
    } else if (match[10] || match[12]) {
      // 斜体 *italic* 或 _italic_
      const content = match[11] ?? match[13]
      nodes.push(
        <em key={key} className="md-italic">
          {renderInline(content, `${key}-i`)}
        </em>
      )
    } else if (match[14]) {
      // 删除线 ~~del~~
      nodes.push(
        <del key={key} className="md-del">
          {renderInline(match[15], `${key}-d`)}
        </del>
      )
    }

    lastIndex = match.index + match[0].length
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex))
  }

  return nodes
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

    // 8. 普通段落行
    flushList()
    if (!currP) currP = []
    currP.push(line)
  }

  // 循环结束时冲刷遗留块
  if (inCode) {
    // 流式输出未收完右围栏时，容错呈现为代码块
    blocks.push({ type: 'code', lang: codeLang, lines: codeLines })
  }
  flushAllNonCode()

  return blocks
}
