import { parseBlocks, renderInline } from './chatMarkdownParser'

export interface ChatMarkdownProps {
  content: string
  typing?: boolean
}

/**
 * 适用于 AI 气泡对话的 Markdown 渲染组件：
 * 1. 深度支持标题、粗体、斜体、列表、代码块、引用、安全链接；
 * 2. 针对大模型输出习惯（如加粗包含中文标点、空格等）具备强容错性；
 * 3. 完美兼容流式输出与末尾光标闪烁，不依赖任何第三方重量级库，零 XSS 注入风险。
 */
export function ChatMarkdown({ content, typing }: ChatMarkdownProps) {
  if (!content) {
    return typing ? <span className="cursor" /> : null
  }

  const blocks = parseBlocks(content)

  const renderedBlocks = blocks.map((block, bi) => {
    switch (block.type) {
      case 'code':
        return (
          <pre key={bi} className="md-code-block">
            {block.lang && <div className="md-code-lang">{block.lang}</div>}
            <code>{block.lines.join('\n')}</code>
          </pre>
        )

      case 'heading': {
        const Comp = block.level <= 2 ? 'h4' : block.level === 3 ? 'h5' : 'h6'
        return (
          <Comp key={bi} className="md-h">
            {renderInline(block.text, `h-${bi}`)}
          </Comp>
        )
      }

      case 'ul':
        return (
          <ul key={bi} className="md-ul">
            {block.items.map((item, ii) => (
              <li key={ii} className="md-li">
                {renderInline(item, `ul-${bi}-${ii}`)}
              </li>
            ))}
          </ul>
        )

      case 'ol':
        return (
          <ol key={bi} className="md-ol">
            {block.items.map((item, ii) => (
              <li key={ii} className="md-li">
                {renderInline(item, `ol-${bi}-${ii}`)}
              </li>
            ))}
          </ol>
        )

      case 'quote':
        return (
          <blockquote key={bi} className="md-quote">
            {block.lines.map((line, li) => (
              <div key={li}>{renderInline(line, `q-${bi}-${li}`)}</div>
            ))}
          </blockquote>
        )

      case 'hr':
        return <hr key={bi} className="md-hr" />

      case 'p':
        return (
          <div key={bi} className="md-p">
            {block.lines.map((line, li) => (
              <span key={li}>
                {li > 0 && <br />}
                {renderInline(line, `p-${bi}-${li}`)}
              </span>
            ))}
          </div>
        )
    }
  })

  return (
    <div className="chat-markdown">
      {renderedBlocks}
      {typing && <span className="cursor" />}
    </div>
  )
}
