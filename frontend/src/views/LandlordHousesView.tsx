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
  Tag,
  Upload
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
  ORIENTATIONS
} from '../constants'
import { assetUrl } from '../runtime'
import type { House, HouseDetail } from '../types'
import { usePagedList } from '../usePagedList'

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
  /** 房源图片 URL 列表（首图由后端同步为封面） */
  images: string[]
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
  lat: 38.88,
  images: []
})

export default function LandlordHousesView() {
  const list = usePagedList<House>('/landlord/houses')
  const [publishOpen, setPublishOpen] = useState(false)
  const [form, setForm] = useState<HouseForm>(emptyForm())
  const [priceOpen, setPriceOpen] = useState(false)
  const [price, setPrice] = useState<any>(null)
  const [realnamePassed, setRealnamePassed] = useState(false)
  const [uploading, setUploading] = useState(false)

  const set = (k: keyof HouseForm, v: any) => setForm(f => ({ ...f, [k]: v }) as HouseForm)

  useEffect(() => {
    http
      .get('/users/me/realname')
      .then(rn => setRealnamePassed(!!rn && rn.status === 1))
      .catch(() => {
        /* 实名状态拉取失败不影响房源列表展示 */
      })
  }, [publishOpen])

  /**
   * 编辑时按 id 取详情：房源图片在 house_image 表里，只有详情接口会返回，
   * 直接拿列表行编辑会丢掉已有图片（保存时按空数组处理就等于把照片删了）。
   */
  async function openPublish(row?: House) {
    if (!row) {
      setForm(emptyForm())
      setPublishOpen(true)
      return
    }
    const item = await http.get<HouseDetail>(`/houses/${row.id}`)
    setForm({
      ...emptyForm(),
      ...item.house,
      id: item.house.id,
      facilities: item.house.facilities ?? [],
      images: (item.images ?? []).map(img => img.url)
    } as HouseForm)
    setPublishOpen(true)
  }

  /** 上传单张图片：后端返回 {url}，失败由 axios 拦截器统一提示 */
  async function uploadImage(file: File) {
    const body = new FormData()
    body.append('file', file)
    setUploading(true)
    try {
      const d = await http.post<{ url: string }>('/files/upload', body)
      set('images', [...form.images, d.url])
    } finally {
      setUploading(false)
    }
    return false
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
    // 不传 imageFileName：后端把它作为「图片文件名」拼进模型提示词（AnalysisService.fillPayload），
    // 而本表单并无上传入口，此前传的 facilities.join('') 等于把设施标签当成照片文件名喂给模型。
    // 留空后端会渲染成「（未填写）」；FR-08 的图片识别要等发布页接上上传功能才谈得上。
    const d = await http.post('/ai/assist-fill', {
      title: form.title,
      community: form.community,
      layout: form.layout
    })
    // AI 可能返回白名单外的标签：这类标签在 Checkbox.Group 里没有对应复选框，
    // 用户既看不见也去不掉，却会随表单一起提交，故只保留平台字典内的取值
    const picked = (d.facilities ?? []).filter((f: string) => FACILITIES.includes(f))
    const dropped = (d.facilities ?? []).length - picked.length
    setForm(f => ({
      ...f,
      description: d.description,
      orientation: d.orientation,
      floorDesc: d.floorDesc,
      facilities: [...new Set([...(f.facilities || []), ...picked])]
    }))
    message.success(dropped > 0 ? `AI 已填充（${dropped} 个未知标签已忽略）` : 'AI 已填充，可自行修改')
  }

  /** 只提交后端 SaveReq 声明的字段：早先直接 PUT 整个表单对象，会把 id/status/viewCount 等
   *  从列表行带过来的字段一并发出，靠 Jackson 忽略未知字段兜着 */
  function payload(): Record<string, unknown> {
    return {
      title: form.title,
      community: form.community,
      city: form.city,
      district: form.district,
      address: form.address,
      layout: form.layout,
      area: form.area,
      orientation: form.orientation,
      floorDesc: form.floorDesc,
      rent: form.rent,
      depositType: form.depositType,
      facilities: form.facilities,
      description: form.description,
      lng: form.lng,
      lat: form.lat,
      images: form.images
    }
  }

  async function save() {
    if (!form.title || !form.community || !form.district || !form.address) {
      message.warning('请填写完整：标题/小区/行政区/地址')
      return
    }
    if (form.id) {
      await http.put(`/houses/${form.id}`, payload())
      message.success('已保存，房源重新进入待审核')
    } else {
      await http.post('/houses', payload())
      message.success('发布成功，等待管理员审核')
    }
    setPublishOpen(false)
    list.reload()
  }

  async function status(row: House, action: string) {
    await http.patch(`/houses/${row.id}/status`, { action })
    message.success(action === 'online' ? '已上架' : '已下架')
    list.reload()
  }

  async function pricing(row: House) {
    const vo = await http.post(`/ai/houses/${row.id}/pricing-suggestion`)
    setPrice(vo)
    setPriceOpen(true)
  }

  const columns: ColumnsType<House> = [
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
        <Tag color={HOUSE_STATUS_TYPE[row.status ?? 0]}>{HOUSE_STATUS[row.status ?? 0]}</Tag>
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
          {[1, 4].includes(row.status ?? -1) && (
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
        dataSource={list.rows}
        loading={list.loading}
        pagination={{
          current: list.page,
          pageSize: list.size,
          total: list.total,
          onChange: p => list.reload(p)
        }}
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
          <Form.Item label="房源图片（首图自动作为封面，支持 jpg/png/webp）">
            <Upload
              listType="picture-card"
              accept="image/png,image/jpeg,image/webp"
              fileList={form.images.map((url, i) => ({
                uid: url,
                name: `图片${i + 1}`,
                status: 'done' as const,
                url: assetUrl(url)
              }))}
              beforeUpload={file => {
                void uploadImage(file as File)
                return false
              }}
              onRemove={file => {
                set(
                  'images',
                  form.images.filter(u => u !== file.uid)
                )
                return true
              }}
              showUploadList={{ showPreviewIcon: false }}
            >
              {uploading ? '上传中…' : '＋ 上传'}
            </Upload>
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
