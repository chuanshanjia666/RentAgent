import React, { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Empty, message, Modal, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { useAuth } from '../auth'
import { CONTRACT_STATUS, CONTRACT_STATUS_TYPE, fmtMoney } from '../constants'

/** 表格行：合同实体 + 房源标题 */
interface ContractRow {
  key: number
  c: any
  houseTitle: string
}

export default function ContractsView() {
  const auth = useAuth()
  const [rows, setRows] = useState<ContractRow[]>([])
  const [current, setCurrent] = useState<ContractRow | null>(null)
  const [interp, setInterp] = useState<any>(null)

  function parse(json: string | null): any[] {
    try { return JSON.parse(json || '[]') } catch (e) { return [] }
  }

  async function load() {
    const p = await http.get('/contracts', { params: { size: 50 } })
    const enriched: ContractRow[] = []
    for (const c of p.list) {
      let houseTitle = '房源 #' + c.houseId
      try {
        const item = await http.get('/houses/' + c.houseId)
        houseTitle = item.house.title
      } catch (e) { /* 忽略 */ }
      enriched.push({ key: c.id, c, houseTitle })
    }
    setRows(enriched)
  }

  async function act(row: ContractRow, action: string, tip: string) {
    await http.patch('/contracts/' + row.c.id, { action })
    message.success(tip)
    load()
  }

  async function terminate(row: ContractRow) {
    Modal.confirm({
      title: '申请退租',
      content: '退租后合同终止、房源重新上架（押金按合同条款处理）。确认退租？',
      okText: '确认退租',
      type: 'warning',
      onOk: () => act(row, 'terminate', '已退租')
    })
  }

  async function interpret(row: ContractRow) {
    const vo = await http.post(`/contracts/${row.c.id}/interpret`)
    setInterp(vo)
  }

  function signable(c: any): boolean {
    return (auth.role === 1 && c.status === 0) || (auth.role === 2 && c.status === 1)
  }

  useEffect(() => { load() }, [])

  const columns: ColumnsType<ContractRow> = [
    { title: '房源', dataIndex: 'houseTitle', render: v => v },
    { title: '租期', width: 200, render: (_, r) => `${r.c.startDate} ~ ${r.c.endDate}` },
    { title: '月租', width: 110, render: (_, r) => fmtMoney(r.c.monthlyRent) },
    {
      title: '状态', width: 120,
      render: (_, r) => <Tag color="blue">{CONTRACT_STATUS[r.c.status]}</Tag>
    },
    {
      title: '操作', width: 340,
      render: (_, r) => (
        <>
          <Button size="small" onClick={() => setCurrent(r)}>查看合同</Button>
          {signable(r.c) && (
            <Button size="small" type="primary" style={{ marginLeft: 6 }}
              onClick={() => act(r, 'sign', '签署成功')}>签署</Button>
          )}
          <Button size="small" style={{ marginLeft: 6 }} onClick={() => interpret(r)}>🤖 AI 解读</Button>
          {r.c.status === 2 && (
            <Button size="small" style={{ marginLeft: 6 }} onClick={() => terminate(r)}>退租</Button>
          )}
          {[0, 1].includes(r.c.status) && (
            <Button size="small" danger style={{ marginLeft: 6 }}
              onClick={() => act(r, 'reject', '已拒签')}>拒签</Button>
          )}
        </>
      )
    }
  ]

  const clauses = current ? parse(current.c.clauses) : []
  const riskIdx = current ? parse(current.c.riskFlags) : []

  return (
    <div className="page">
      <h2 className="page-title">我的合同</h2>
      <Table rowKey="key" columns={columns} dataSource={rows} pagination={{ pageSize: 10 }}
        scroll={{ x: 900 }}
        locale={{ emptyText: <Empty description="暂无合同，可在房源详情页发起签约" /> }} />

      <Modal title="电子合同" open={!!current} footer={null} width={720} onCancel={() => setCurrent(null)}>
        {current && (
          <>
            <Descriptions bordered size="small" column={2} style={{ marginBottom: 12 }}>
              <Descriptions.Item label="房源">{current.houseTitle}</Descriptions.Item>
              <Descriptions.Item label="状态">{CONTRACT_STATUS[current.c.status]}</Descriptions.Item>
              <Descriptions.Item label="租期">{current.c.startDate} ~ {current.c.endDate}</Descriptions.Item>
              <Descriptions.Item label="月租/押金">
                {fmtMoney(current.c.monthlyRent)} / {fmtMoney(current.c.deposit)}
              </Descriptions.Item>
            </Descriptions>
            {clauses.map((cl, i) => (
              <div key={i} className="clause">
                <b>{i + 1}. {cl.title}</b>
                {riskIdx.includes(i) ? <Tag color="red" style={{ marginLeft: 6 }}>⚠ 风险条款</Tag> : null}
                <p style={{ margin: '4px 0 0', color: riskIdx.includes(i) ? '#e6392f' : '#303133' }}>{cl.text}</p>
              </div>
            ))}
            <Alert style={{ marginTop: 10 }} type="warning" showIcon
              message="签约前建议使用「AI 解读」了解条款风险；AI 生成内容仅供参考" />
          </>
        )}
      </Modal>

      <Modal title="🤖 合同智能解读（FR-14）" open={!!interp} footer={null} width={760}
        onCancel={() => setInterp(null)}>
        {interp && interp.items.map((it: any) => (
          <div key={it.index} className="clause">
            <b>条款 {it.index + 1}</b>
            <Tag color={it.risk ? 'red' : 'green'} style={{ marginLeft: 6 }}>
              {it.risk ? '⚠ 风险条款' : '常规条款'}
            </Tag>
            <p style={{ margin: '4px 0', color: '#606266' }}>{it.clause}</p>
            <p style={{ margin: 0, color: '#303133' }}>💬 {it.explanation}</p>
          </div>
        ))}
        {interp && (
          <Alert style={{ marginTop: 10 }} type="info" showIcon
            message={interp.disclaimer || 'AI 生成，仅供参考'} />
        )}
      </Modal>
    </div>
  )
}
