import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Checkbox,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Table,
  Tag
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import {
  DEPOSIT_TYPES,
  DISTRICTS,
  DISTRICT_CENTER,
  FACILITIES,
  FLOORS,
  fmtMoney,
  HOUSE_STATUS,
  HOUSE_STATUS_TYPE,
  LAYOUTS,
  ORIENTATIONS,
  parseJsonList
} from '../constants'

/** 发布/编辑房源表单 */
interface HouseForm {
  id: number | null
  title: string
  community: string
  city: string
  district?: string
  address: string
  layout: string
  area: number
  orientation: string
  floorDesc: string
  rent: number
  depositType: string
  facilities: string[]
  description: string
  lng: number
  lat: number
}

const emptyForm = (): HouseForm => ({
  id: null,
  title: '',
  community: '',
  city: '大连市',
  district: undefined,
  address: '',
  layout: '1室1厅',
  area: 45,
  orientation: '南',
  floorDesc: '中层',
  rent: 2000,
  depositType: '押一付三',
  facilities: [],
  description: '',
  lng: 121.52,
  lat: 38.88
})

export default function LandlordHousesView() {
  const [list, setList] = useState<any[]>([])
  const [publishOpen, setPublishOpen] = useState(false)
  const [form, setForm] = useState<HouseForm>(emptyForm())
  const [priceOpen, setPriceOpen] = useState(false)
  const [price, setPrice] = useState<any>(null)
  const [realnamePassed, setRealnamePassed] = useState(false)

  const set = (k: keyof HouseForm, v: any) => setForm(f => ({ ...f, [k]: v }) as HouseForm)

  async function load() {
    const p = await http.get('/landlord/houses', { params: { size: 50 } })
    setList(p.list.map((h: any) => ({ ...h, key: h.id })))
    const rn = await http.get('/users/me/realname')
    setRealnamePassed(!!rn && rn.status === 1)
  }

  function openPublish(row?: any) {
    const base = emptyForm()
    if (row) {
      Object.assign(base, row, { id: row.id })
      base.facilities = parseJsonList(row.facilities)
    }
    setForm(base)
    setPublishOpen(true)
  }

  function fillCenter(d: string) {
    set('district', d)
    const c = DISTRICT_CENTER[d]
    if (c) {
      set('lng', c[0])
      set('lat', c[1])
    }
  }

  async function aiFill() {
    const d = await http.post('/ai/assist-fill', {
      title: form.title,
      community: form.community,
      layout: form.layout,
      imageFileName: (form.facilities || []).join('')
    })
    setForm(f => ({
      ...f,
      description: d.description,
      orientation: d.orientation,
      floorDesc: d.floorDesc,
      facilities: [...new Set([...(f.facilities || []), ...d.facilities])]
    }))
    message.success('AI 已填充，可自行修改')
  }

  async function save() {
    if (!form.title || !form.community || !form.district || !form.address) {
      message.warning('请填写完整：标题/小区/行政区/地址')
      return
    }
    if (form.id) {
      await http.put(`/houses/${form.id}`, form)
      message.success('已保存，房源重新进入待审核')
    } else {
      await http.post('/houses', form)
      message.success('发布成功，等待管理员审核')
    }
    setPublishOpen(false)
    load()
  }

  async function status(row: any, action: string) {
    await http.patch(`/houses/${row.id}/status`, { action })
    message.success(action === 'online' ? '已上架' : '已下架')
    load()
  }

  async function pricing(row: any) {
    const vo = await http.post(`/ai/houses/${row.id}/pricing-suggestion`)
    setPrice(vo)
    setPriceOpen(true)
  }

  useEffect(() => {
    load()
  }, [])

  const columns: ColumnsType<any> = [
    {
      title: '房源',
      render: (_, row) => (
        <>
          <b>{row.title}</b>
          <div style={{ color: '#909399', fontSize: 12 }}>
            {row.district} · {row.community} · {row.layout}
          </div>
        </>
      )
    },
    { title: '租金', width: 110, render: (_, row) => fmtMoney(row.rent) },
    {
      title: '状态',
      width: 100,
      render: (_, row) => (
        <Tag color={HOUSE_STATUS_TYPE[row.status]}>{HOUSE_STATUS[row.status]}</Tag>
      )
    },
    {
      title: '浏览/评分',
      width: 120,
      render: (_, row) => `${row.viewCount} 次${row.avgScore ? ` / ⭐${row.avgScore}` : ''}`
    },
    {
      title: '操作',
      width: 340,
      render: (_, row) => (
        <>
          <Button size="small" onClick={() => openPublish(row)}>
            编辑
          </Button>
          {[1, 4].includes(row.status) && (
            <Button
              size="small"
              type="primary"
              ghost
              style={{ marginLeft: 6 }}
              onClick={() => status(row, 'online')}
            >
              上架
            </Button>
          )}
          {row.status === 3 && (
            <Button size="small" style={{ marginLeft: 6 }} onClick={() => status(row, 'offline')}>
              下架
            </Button>
          )}
          <Button size="small" style={{ marginLeft: 6 }} onClick={() => pricing(row)}>
            🤖 定价建议
          </Button>
        </>
      )
    }
  ]

  return (
    <div className="page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 className="page-title">房源管理</h2>
        <Button type="primary" onClick={() => openPublish()}>
          ＋ 发布房源
        </Button>
      </div>

      {!realnamePassed && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 14 }}
          message="您尚未通过实名认证，发布房源前请先到「个人中心」完成实名认证（FR-03）"
        />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={list}
        pagination={{ pageSize: 10 }}
        locale={{ emptyText: <Empty description="还没有房源，点右上角发布" /> }}
      />

      <Modal
        title={form.id ? '编辑房源（保存后重新审核）' : '发布房源'}
        open={publishOpen}
        onOk={save}
        okText={form.id ? '保存' : '保存（提交审核）'}
        width={640}
        onCancel={() => setPublishOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="标题" required>
            <Input
              value={form.title}
              maxLength={100}
              placeholder="如：近地铁精装一居室 拎包入住"
              onChange={e => set('title', e.target.value)}
            />
          </Form.Item>
          <div style={{ display: 'flex', gap: 10 }}>
            <Form.Item label="小区" required style={{ flex: 1 }}>
              <Input value={form.community} onChange={e => set('community', e.target.value)} />
            </Form.Item>
            <Form.Item label="行政区" required style={{ width: 160 }}>
              <Select
                value={form.district}
                style={{ width: '100%' }}
                onChange={fillCenter}
                options={DISTRICTS.map(d => ({ value: d, label: d }))}
              />
            </Form.Item>
          </div>
          <Form.Item label="详细地址" required>
            <Input value={form.address} onChange={e => set('address', e.target.value)} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 10 }}>
            <Form.Item label="户型" style={{ flex: 1 }}>
              <Select
                value={form.layout}
                onChange={v => set('layout', v)}
                options={LAYOUTS.map(l => ({ value: l, label: l }))}
              />
            </Form.Item>
            <Form.Item label="面积㎡" style={{ flex: 1 }}>
              <InputNumber
                min={5}
                max={500}
                value={form.area}
                style={{ width: '100%' }}
                onChange={v => set('area', v)}
              />
            </Form.Item>
            <Form.Item label="朝向" style={{ flex: 1 }}>
              <Select
                value={form.orientation}
                onChange={v => set('orientation', v)}
                options={ORIENTATIONS.map(o => ({ value: o, label: o }))}
              />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <Form.Item label="月租金¥" style={{ flex: 1 }}>
              <InputNumber
                min={100}
                max={100000}
                value={form.rent}
                style={{ width: '100%' }}
                onChange={v => set('rent', v)}
              />
            </Form.Item>
            <Form.Item label="押付" style={{ flex: 1 }}>
              <Select
                value={form.depositType}
                onChange={v => set('depositType', v)}
                options={DEPOSIT_TYPES.map(t => ({ value: t, label: t }))}
              />
            </Form.Item>
            <Form.Item label="楼层" style={{ flex: 1 }}>
              <Select
                value={form.floorDesc}
                onChange={v => set('floorDesc', v)}
                options={FLOORS.map(f => ({ value: f, label: f }))}
              />
            </Form.Item>
          </div>
          <Form.Item label="设施">
            <Checkbox.Group
              options={FACILITIES}
              value={form.facilities}
              onChange={v => set('facilities', v as string[])}
            />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea
              rows={3}
              maxLength={2000}
              value={form.description}
              onChange={e => set('description', e.target.value)}
            />
            <Button size="small" type="primary" ghost onClick={aiFill}>
              🤖 AI 智能填充描述与设施（FR-08）
            </Button>
          </Form.Item>
          <div style={{ display: 'flex', gap: 10 }}>
            <Form.Item label="经度" style={{ flex: 1 }}>
              <InputNumber
                precision={4}
                step={0.001}
                value={form.lng}
                style={{ width: '100%' }}
                onChange={v => set('lng', v)}
              />
            </Form.Item>
            <Form.Item label="纬度" style={{ flex: 1 }}>
              <InputNumber
                precision={4}
                step={0.001}
                value={form.lat}
                style={{ width: '100%' }}
                onChange={v => set('lat', v)}
              />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Modal
        title="🤖 智能定价建议（FR-15）"
        open={priceOpen}
        footer={null}
        width={460}
        onCancel={() => setPriceOpen(false)}
      >
        {price && (
          <>
            <div style={{ textAlign: 'center', margin: '10px 0 16px' }}>
              <span style={{ fontSize: 28, color: '#e6392f', fontWeight: 700 }}>
                ¥{price.low} ~ ¥{price.high}
              </span>
              <div style={{ color: '#909399', marginTop: 4 }}>样本均价 ¥{price.avg}</div>
            </div>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="依据">{price.basis}</Descriptions.Item>
              <Descriptions.Item label="样本量">{price.sampleCount} 套</Descriptions.Item>
              <Descriptions.Item label="建议">{price.note}</Descriptions.Item>
            </Descriptions>
            <Alert
              type="info"
              showIcon
              style={{ marginTop: 10 }}
              message="AI 生成，仅供参考；无同类样本时请参考周边挂牌价"
            />
          </>
        )}
      </Modal>
    </div>
  )
}
