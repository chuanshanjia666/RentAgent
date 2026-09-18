import { useState } from 'react'
import { Alert, Button, Descriptions, Empty, message, Modal, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import { useAuth } from '../auth'
import { CONTRACT_STATUS, CONTRACT_STATUS_TYPE, fmtMoney, parseJsonList } from '../constants'
import type { ContractRow } from '../types'
import { usePagedList } from '../usePagedList'

export default function ContractsView() {
  const auth = useAuth()
  const { rows, total, page, size, loading, reload } = usePagedList<ContractRow>('/contracts')
  const [current, setCurrent] = useState<ContractRow | null>(null)
  const [interp, setInterp] = useState<any>(null)

  /**
   * 打开合同时按 id 取一次详情（接口只返回合同本身，房源标题沿用列表里的）：
   * 列表是进入页面时拉的，而「AI 解读」会把风险条款下标写回合同，
   * 只读列表里的旧快照会出现"解读标了风险、合同里却没有红标"的不一致。
   */
  async function open(row: ContractRow) {
    const detail = await http.get<ContractRow['contract']>(`/contracts/${row.contract.id}`)
    setCurrent({ contract: detail, houseTitle: row.houseTitle })
  }

  async function act(row: ContractRow, action: string, tip: string) {
    await http.patch(`/contracts/${row.contract.id}`, { action })
    message.success(tip)
    reload()
  }

  function terminate(row: ContractRow) {
    Modal.confirm({
      title: '申请退租',
      content: '退租后合同终止、房源重新上架（押金按合同条款处理）。确认退租？',
      okText: '确认退租',
      type: 'warning',
      onOk: () => act(row, 'terminate', '已退租')
    })
  }

  async function interpret(row: ContractRow) {
    const vo = await http.post(`/contracts/${row.contract.id}/interpret`)
    setInterp(vo)
  }

  function signable(c: ContractRow['contract']): boolean {
    return (auth.role === 1 && c.status === 0) || (auth.role === 2 && c.status === 1)
  }

  const columns: ColumnsType<ContractRow> = [
    { title: '房源', dataIndex: 'houseTitle', render: v => v },
    { title: '租期', width: 200, render: (_, r) => `${r.contract.startDate} ~ ${r.contract.endDate}` },
    { title: '月租', width: 110, render: (_, r) => fmtMoney(r.contract.monthlyRent) },
    {
      title: '状态',
      width: 120,
      render: (_, r) => (
        <Tag color={CONTRACT_STATUS_TYPE[r.contract.status]}>
          {CONTRACT_STATUS[r.contract.status]}
        </Tag>
      )
    },
    {
      title: '操作',
      width: 340,
      render: (_, r) => (
        <>
          <Button size="small" onClick={() => open(r)}>
            查看合同
          </Button>
          {signable(r.contract) && (
            <Button
              size="small"
              type="primary"
              style={{ marginLeft: 6 }}
              onClick={() => act(r, 'sign', '签署成功')}
            >
              签署
            </Button>
          )}
          <Button size="small" style={{ marginLeft: 6 }} onClick={() => interpret(r)}>
            🤖 AI 解读
          </Button>
          {r.contract.status === 2 && (
            <Button size="small" style={{ marginLeft: 6 }} onClick={() => terminate(r)}>
              退租
            </Button>
          )}
          {[0, 1].includes(r.contract.status) && (
            <Button
              size="small"
              danger
              style={{ marginLeft: 6 }}
              onClick={() => act(r, 'reject', '已拒签')}
            >
              拒签
            </Button>
          )}
        </>
      )
    }
  ]

  const clauses = current ? parseJsonList<{ title: string; text: string }>(current.contract.clauses) : []
  const riskIdx = current ? parseJsonList<number>(current.contract.riskFlags) : []

  return (
    <div className="page">
      <h2 className="page-title">我的合同</h2>
      <Table
        rowKey={r => r.contract.id}
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{ current: page, pageSize: size, total, onChange: p => reload(p) }}
        scroll={{ x: 900 }}
        locale={{ emptyText: <Empty description="暂无合同，可在房源详情页发起签约" /> }}
      />

      <Modal
        title="电子合同"
        open={!!current}
        footer={null}
        width={720}
        onCancel={() => setCurrent(null)}
      >
        {current && (
          <>
            <Descriptions bordered size="small" column={2} style={{ marginBottom: 12 }}>
              <Descriptions.Item label="房源">{current.houseTitle}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {CONTRACT_STATUS[current.contract.status]}
              </Descriptions.Item>
              <Descriptions.Item label="租期">
                {current.contract.startDate} ~ {current.contract.endDate}
              </Descriptions.Item>
              <Descriptions.Item label="月租/押金">
                {fmtMoney(current.contract.monthlyRent)} / {fmtMoney(current.contract.deposit)}
              </Descriptions.Item>
            </Descriptions>
            {clauses.map((cl, i) => (
              <div key={i} className="clause">
                <b>
                  {i + 1}. {cl.title}
                </b>
                {riskIdx.includes(i) ? (
                  <Tag color="red" style={{ marginLeft: 6 }}>
                    ⚠ 风险条款
                  </Tag>
                ) : null}
                <p
                  style={{ margin: '4px 0 0', color: riskIdx.includes(i) ? '#e6392f' : '#303133' }}
                >
                  {cl.text}
                </p>
              </div>
            ))}
            <Alert
              style={{ marginTop: 10 }}
              type="warning"
              showIcon
              message="签约前建议使用「AI 解读」了解条款风险；AI 生成内容仅供参考"
            />
          </>
        )}
      </Modal>

      <Modal
        title="🤖 合同智能解读（FR-14）"
        open={!!interp}
        footer={null}
        width={760}
        onCancel={() => setInterp(null)}
      >
        {interp &&
          interp.items.map((it: any) => (
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
          <Alert
            style={{ marginTop: 10 }}
            type="info"
            showIcon
            message={interp.disclaimer || 'AI 生成，仅供参考'}
          />
        )}
      </Modal>
    </div>
  )
}
