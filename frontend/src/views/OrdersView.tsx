import { useEffect, useState } from 'react'
import { Button, Empty, Input, message, Modal, Rate, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { useAuth } from '../auth'
import {
  BILL_STATUS,
  BILL_STATUS_TYPE,
  fmtMoney,
  ORDER_STATUS,
  ORDER_STATUS_TYPE
} from '../constants'

/** 订单行：订单 VO + 展开态账单 */
interface OrderRow {
  key: number
  order: any
  bills: any[]
  [key: string]: any
}

export default function OrdersView() {
  const auth = useAuth()
  const isLandlord = auth.role === 2
  const [rows, setRows] = useState<OrderRow[]>([])
  const [reviewRow, setReviewRow] = useState<OrderRow | null>(null)
  const [reviewForm, setReviewForm] = useState({ houseScore: 5, landlordScore: 5, content: '' })

  async function load() {
    const p = await http.get('/orders', { params: { size: 50 } })
    setRows(p.list.map((r: any) => ({ ...r, key: r.order.id, bills: [] })))
  }

  async function loadBills(row: OrderRow) {
    const bills = await http.get(`/orders/${row.order.id}/bills`)
    setRows(prev => prev.map(r => (r.key === row.key ? { ...r, bills } : r)))
  }

  async function pay(row: OrderRow, bill: any) {
    await http.patch(`/bills/${bill.id}/pay`)
    message.success('已记录支付（演示环境不对接真实支付）')
    loadBills(row)
  }

  function openReview(row: OrderRow) {
    setReviewRow(row)
    setReviewForm({ houseScore: 5, landlordScore: 5, content: '' })
  }

  async function submitReview() {
    if (!reviewRow) return
    await http.post('/reviews', {
      leaseOrderId: reviewRow.order.id,
      houseScore: reviewForm.houseScore,
      landlordScore: reviewForm.landlordScore,
      content: reviewForm.content
    })
    message.success('评价已提交')
    setReviewRow(null)
  }

  function terminate(row: OrderRow) {
    Modal.confirm({
      title: '申请退租',
      content: '退租后合同终止、房源重新上架。确认退租？',
      okText: '确认退租',
      type: 'warning',
      onOk: async () => {
        await http.patch(`/contracts/${row.order.contractId}`, { action: 'terminate' })
        message.success('已退租')
        load()
      }
    })
  }

  useEffect(() => {
    load()
  }, [])

  const columns: ColumnsType<OrderRow> = [
    {
      title: '房源',
      render: (_, row) => row.houseTitle
    },
    {
      title: '租期',
      width: 200,
      render: (_, row) => `${row.order.startDate} ~ ${row.order.endDate}`
    },
    { title: '月租', width: 110, render: (_, row) => fmtMoney(row.order.monthlyRent) },
    {
      title: '账单',
      width: 110,
      render: (_, row) => `${row.billCount - row.unpaidCount}/${row.billCount} 已付`
    },
    {
      title: '状态',
      width: 100,
      render: (_, row) => (
        <Tag color={ORDER_STATUS_TYPE[row.order.status]}>{ORDER_STATUS[row.order.status]}</Tag>
      )
    },
    {
      title: '操作',
      width: 170,
      render: (_, row) => (
        <>
          {!isLandlord && row.order.status !== 0 && (
            <Button size="small" type="primary" onClick={() => openReview(row)}>
              评价
            </Button>
          )}
          {row.order.status === 0 && row.contractStatus === 2 && (
            <Button size="small" style={{ marginLeft: 6 }} onClick={() => terminate(row)}>
              退租
            </Button>
          )}
        </>
      )
    }
  ]

  function billColumns(row: OrderRow): ColumnsType<any> {
    const cols: ColumnsType<any> = [
      { title: '期数', width: 80, render: (_, b) => `第 ${b.periodNo} 期` },
      { title: '应付日', width: 120, render: (_, b) => b.dueDate },
      { title: '金额', width: 120, render: (_, b) => fmtMoney(b.amount) },
      {
        title: '状态',
        width: 100,
        render: (_, b) => <Tag color={BILL_STATUS_TYPE[b.status]}>{BILL_STATUS[b.status]}</Tag>
      }
    ]
    if (!isLandlord) {
      cols.push({
        title: '操作',
        render: (_, b) =>
          b.status === 0 ? (
            <Button size="small" type="primary" onClick={() => pay(row, b)}>
              标记已支付
            </Button>
          ) : null
      })
    }
    return cols
  }

  return (
    <div className="page">
      <h2 className="page-title">{isLandlord ? '出租订单管理' : '我的租单'}</h2>
      <Table
        rowKey="key"
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10 }}
        scroll={{ x: 900 }}
        locale={{ emptyText: <Empty description="暂无订单" /> }}
        expandable={{
          onExpand: (expanded, row) => expanded && loadBills(row),
          expandedRowRender: row => (
            <div style={{ padding: '4px 12px' }}>
              <Button size="small" onClick={() => loadBills(row)}>
                刷新账单
              </Button>
              <Table
                style={{ marginTop: 8 }}
                size="small"
                rowKey="id"
                columns={billColumns(row)}
                dataSource={row.bills}
                pagination={false}
              />
            </div>
          )
        }}
      />

      <Modal
        title="评价本次租房（FR-20）"
        open={!!reviewRow}
        onOk={submitReview}
        okText="提交评价"
        onCancel={() => setReviewRow(null)}
      >
        <p>
          房源评分：
          <Rate
            value={reviewForm.houseScore}
            onChange={v => setReviewForm(f => ({ ...f, houseScore: v }))}
          />
        </p>
        <p>
          房东评分：
          <Rate
            value={reviewForm.landlordScore}
            onChange={v => setReviewForm(f => ({ ...f, landlordScore: v }))}
          />
        </p>
        <Input.TextArea
          rows={3}
          maxLength={500}
          placeholder="说说你的居住体验…"
          value={reviewForm.content}
          onChange={e => setReviewForm(f => ({ ...f, content: e.target.value }))}
        />
      </Modal>
    </div>
  )
}
