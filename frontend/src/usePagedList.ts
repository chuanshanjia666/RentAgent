import { useCallback, useEffect, useRef, useState } from 'react'
import http from './api'
import type { PageResult } from './types'

export interface PagedList<T> {
  rows: T[]
  total: number
  page: number
  size: number
  loading: boolean
  /** 重新拉取（默认当前页） */
  reload: (p?: number) => void
}

/**
 * 服务端分页列表：统一取数、翻页与 total 口径。
 *
 * 早先多数页面写死 `size: 50` 再用 antd 的前端分页显示每页 10 条：
 * 数据超过 50 条就会静默截断，分页器还在按 50 条的总数"假装"翻页。
 * 这里把 page/size 一路传给后端，分页元数据以后端返回为准。
 */
export function usePagedList<T = any>(
  url: string,
  params: Record<string, unknown> = {},
  size = 10
): PagedList<T> {
  const [rows, setRows] = useState<T[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  // 参数按内容比对：调用方通常直接传对象字面量，引用每次都变，不能进依赖数组
  const paramsKey = JSON.stringify(params)
  const current = useRef({ page: 1 })
  current.current.page = page
  // 请求序号：关键词类筛选每击键都会发请求，先发的可能后到。
  // 没有这道闸，晚到的旧响应会把新筛选结果覆盖回去（输入 abc，列表停在第 1 个字符的结果）
  const seq = useRef(0)

  const load = useCallback(
    async (p = current.current.page) => {
      const mine = ++seq.current
      setLoading(true)
      try {
        const data = await http.get<PageResult<T>>(url, {
          params: { ...JSON.parse(paramsKey), page: p, size }
        })
        if (mine !== seq.current) {
          return // 已有更新的请求在途：丢弃这次过期响应
        }
        setRows(data.list)
        setTotal(data.total)
        setPage(p)
      } finally {
        if (mine === seq.current) {
          setLoading(false)
        }
      }
    },
    [url, paramsKey, size]
  )

  // 查询条件变化时回到第 1 页，避免停留在越界页码上
  useEffect(() => {
    load(1)
  }, [load])

  return { rows, total, page, size, loading, reload: load }
}
