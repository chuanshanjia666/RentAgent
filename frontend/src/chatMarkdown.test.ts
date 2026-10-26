import type { ReactElement } from 'react'
import { describe, expect, it } from 'vitest'
import { ChatMarkdown } from './chatMarkdown'
import { parseBlocks, renderInline, sanitizeHref } from './chatMarkdownParser'

describe('chatMarkdown 单元测试', () => {
  describe('sanitizeHref 安全链接校验', () => {
    it('正确解析站内 hash 路由与兼容 %23 转义及缺少 # 的房源链接', () => {
      expect(sanitizeHref('#/app/houses/16')).toBe('#/app/houses/16')
      expect(sanitizeHref('%23/app/houses/16')).toBe('#/app/houses/16')
      expect(sanitizeHref('/app/houses/16')).toBe('#/app/houses/16')
    })

    it('正确识别 http/https 外链', () => {
      expect(sanitizeHref('https://example.com/faq')).toBe('https://example.com/faq')
      expect(sanitizeHref('http://example.com/test')).toBe('http://example.com/test')
    })

    it('拦截恶意脚本协议与相对路径', () => {
      expect(sanitizeHref('javascript:alert(1)')).toBeNull()
      expect(sanitizeHref('data:text/html,evil')).toBeNull()
      expect(sanitizeHref('foo/bar')).toBeNull()
    })
  })

  describe('renderInline 行内解析', () => {
    it('支持加粗与嵌套标点', () => {
      const nodes = renderInline('关于**房屋维修责任**划分如下')
      expect(nodes).toHaveLength(3)
      expect(nodes[0]).toBe('关于')
      const strong = nodes[1] as ReactElement
      expect(strong.type).toBe('strong')
      expect(nodes[2]).toBe('划分如下')
    })

    it('支持包含空格的粗体（如 ** 7 日内**）', () => {
      const nodes = renderInline('应在接到报修通知后 ** 7 日内**完成维修。')
      expect(nodes.some(n => typeof n !== 'string' && (n as ReactElement).type === 'strong')).toBe(
        true
      )
    })

    it('支持行内代码与链接混排', () => {
      const nodes = renderInline('执行 `npm test` 并访问 [首页](#/app/home)')
      expect(nodes.some(n => typeof n !== 'string' && (n as ReactElement).type === 'code')).toBe(
        true
      )
      expect(nodes.some(n => typeof n !== 'string' && (n as ReactElement).type === 'a')).toBe(true)
    })
  })

  describe('parseBlocks 块级解析', () => {
    it('解析用户关于维修责任的真实问答结构', () => {
      const md = `我来帮您查询平台关于维修责任的相关规定。

根据平台知识库的相关规定，关于房屋维修责任划分如下：

**维修责任划分：**
- **房东承担**：房屋主体结构与附属设施的**非人为损坏**（如管道老化、电路故障、门窗变形等），房东应在接到报修通知后 **7 日内**完成维修。
- **租客承担**：因**租客人原因**造成的损坏，由租客自己承担维修费用。

**温馨提示：**
- 入住时建议**拍照留存房屋现状**，作为退租时核对依据，避免纠纷。

（来源：知识库《房子里东西坏了维修费谁出？》）

如果还有其他关于租赁的问题，欢迎随时咨询我哦～`

      const blocks = parseBlocks(md)
      expect(blocks.length).toBeGreaterThanOrEqual(6)

      const ulBlocks = blocks.filter(b => b.type === 'ul')
      expect(ulBlocks.length).toBe(2)
      if (ulBlocks[0].type === 'ul') {
        expect(ulBlocks[0].items).toHaveLength(2)
        expect(ulBlocks[0].items[0]).toContain('房东承担')
        expect(ulBlocks[0].items[1]).toContain('租客承担')
      }
      if (ulBlocks[1].type === 'ul') {
        expect(ulBlocks[1].items).toHaveLength(1)
        expect(ulBlocks[1].items[0]).toContain('拍照留存房屋现状')
      }
    })

    it('解析代码块与标题', () => {
      const md = `# 标题一\n\`\`\`ts\nconst x = 1\n\`\`\`\n普通段落`
      const blocks = parseBlocks(md)
      expect(blocks).toHaveLength(3)
      expect(blocks[0].type).toBe('heading')
      expect(blocks[1].type).toBe('code')
      if (blocks[1].type === 'code') {
        expect(blocks[1].lang).toBe('ts')
        expect(blocks[1].lines).toEqual(['const x = 1'])
      }
      expect(blocks[2].type).toBe('p')
    })
  })

  describe('ChatMarkdown 组件渲染', () => {
    it('正确渲染带 typing 光标的组件结构', () => {
      const element = ChatMarkdown({ content: '回复内容', typing: true })
      expect(element).not.toBeNull()
      expect(element?.props.className).toBe('chat-markdown')
      expect(element?.props.children).toBeDefined()
    })

    it('空内容但 typing 时渲染光标', () => {
      const element = ChatMarkdown({ content: '', typing: true })
      expect(element?.props.className).toBe('cursor')
    })
  })
})
