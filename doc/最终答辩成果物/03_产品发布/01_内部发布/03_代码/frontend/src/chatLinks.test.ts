import type { ReactElement } from 'react'
import { describe, expect, it } from 'vitest'
import { renderWithLinks } from './chatLinks'

/** 取节点数组中的锚点元素（调用方已保证该位置是 <a>） */
function linkAt(nodes: React.ReactNode[], i: number): ReactElement {
  return nodes[i] as ReactElement
}

/**
 * FT-CHAT AI 回复内链接渲染（FR-12）— 气泡里的 renderWithLinks：
 * 找房助手按系统提示词以 Markdown 输出房源详情页链接（[标题](#/app/houses/{id})），
 * 渲染时站内 hash 路由与 http(s) 链接转成可点击 <a>，其余"链接"按纯文本显示。
 * 回归点：站内链接前缀是 #/ 而非 #:——历史版本判定正则误写为 ^(#:|https?:)，
 * 导致模型生成的房源链接永远以纯文本显示、无法点击跳转。
 */
describe('FT-CHAT AI 回复内链接渲染', () => {
  it('FT-CHAT-01 站内 hash 路由链接渲染为可点击 <a>，本窗口跳转（不带 target）', () => {
    const nodes = renderWithLinks('推荐：[金州站前一居 交通枢纽旁](#/app/houses/16)')
    expect(nodes).toHaveLength(2)
    expect(typeof nodes[0]).toBe('string')
    const a = linkAt(nodes, 1)
    expect(a.type).toBe('a')
    expect(a.props.href).toBe('#/app/houses/16')
    expect(a.props.target).toBeUndefined()
    expect(a.props.children).toBe('金州站前一居 交通枢纽旁')

    // 兼容部分大模型将 # URL 编码为 %23 或漏写 hash 符号
    const encodedNodes = renderWithLinks('推荐：[沙河口台山新村两居](%23/app/houses/97)')
    const encodedA = linkAt(encodedNodes, 1)
    expect(encodedA.props.href).toBe('#/app/houses/97')

    const noHashNodes = renderWithLinks('推荐：[甘井子三居](/app/houses/101)')
    const noHashA = linkAt(noHashNodes, 1)
    expect(noHashA.props.href).toBe('#/app/houses/101')
  })

  it('FT-CHAT-02 http(s) 链接新窗口打开（target=_blank + noreferrer）', () => {
    const nodes = renderWithLinks('[平台协议](https://example.com/agreement)')
    expect(nodes).toHaveLength(1)
    const a = linkAt(nodes, 0)
    expect(a.type).toBe('a')
    expect(a.props.href).toBe('https://example.com/agreement')
    expect(a.props.target).toBe('_blank')
    expect(a.props.rel).toBe('noreferrer')
  })

  it('FT-CHAT-03 多套房源推荐：链接与文字交错时顺序与内容不乱', () => {
    const content = '1. [甲](#/app/houses/16) 月租1300\n2. [乙](#/app/houses/17) 月租2000'
    const nodes = renderWithLinks(content)
    expect(nodes).toHaveLength(5)
    expect(linkAt(nodes, 1).props.href).toBe('#/app/houses/16')
    expect(nodes[2]).toBe(' 月租1300\n2. ')
    expect(linkAt(nodes, 3).props.href).toBe('#/app/houses/17')
    expect(nodes[4]).toBe(' 月租2000')
  })

  it('FT-CHAT-04 非 hash/http 地址（javascript:、相对路径）不成链接，按原文显示', () => {
    const nodes = renderWithLinks('[别点](javascript:evil) 和 [相对路径](foo/bar)')
    expect(nodes.every(n => typeof n === 'string')).toBe(true)
    const text = nodes.join('')
    expect(text).toContain('[别点](javascript:evil)')
    expect(text).toContain('[相对路径](foo/bar)')
  })

  it('FT-CHAT-05 流式半截链接（未收到右括号）不误渲染，收完才成链接', () => {
    const half = renderWithLinks('推荐 [金州站前一居](#/app/houses/16')
    expect(half.every(n => typeof n === 'string')).toBe(true)
    const done = renderWithLinks('推荐 [金州站前一居](#/app/houses/16)')
    expect(linkAt(done, 1).props.href).toBe('#/app/houses/16')
  })

  it('FT-CHAT-06 无链接的纯文本回复原样输出', () => {
    expect(renderWithLinks('押金退还请先看合同第五条。')).toEqual(['押金退还请先看合同第五条。'])
  })
})
