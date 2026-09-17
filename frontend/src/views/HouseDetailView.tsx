import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Descriptions, Empty, Input, message, Modal, Tag, Typography } from 'antd'
import { useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import http from '../api'
import { useAuth } from '../auth'
import { fmtMoney, fmtTime, HOUSE_STATUS, HOUSE_STATUS_TYPE, parseJsonList } from '../constants'
import { assetUrl } from '../runtime'
import type { HouseDetail, PageResult, Review } from '../types'

const { Title, Paragraph } = Typography

export default function HouseDetailView() {
  const { id } = useParams<{ id: string }>()
  const auth = useAuth()
  const [item, setItem] = useState<HouseDetail | null>(null)
  const [reviews, setReviews] = useState<Review[]>([])
  const [apptOpen, setApptOpen] = useState(false)
  const [apptTime, setApptTime] = useState<string | null>(null)
  const [apptRemark, setApptRemark] = useState('')
  const [reportOpen, setReportOpen] = useState(false)
  const [reportReason, setReportReason] = useState('')
  const [contractOpen, setContractOpen] = useState(false)
  const [contractStart, setContractStart] = useState(dayjs().add(1, 'day').format('YYYY-MM-DD'))
  const [contractEnd, setContractEnd] = useState(dayjs().add(1, 'year').format('YYYY-MM-DD'))
  const [contracting, setContracting] = useState(false)

  const isTenant = auth.role === 1

  const facilities = useMemo(() => parseJsonList(item?.house.facilities), [item])

  async function load() {
    const d = await http.get<HouseDetail>(`/houses/${id}`)
    setItem(d)
    const p = await http.get<PageResult<Review>>(`/houses/${id}/reviews`, { params: { size: 20 } })
    setReviews(p.list)
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 只在房源 id 变化时重新拉取
  }, [id])

  async function toggleFav() {
    if (!item) return
    if (item.favorited) {
      await http.delete(`/favorites/${id}`)
      message.success('已取消收藏')
    } else {
      await http.post(`/favorites/${id}`)
      message.success('已加入收藏')
    }
    setItem(s => (s ? { ...s, favorited: !s.favorited } : s))
  }

  async function submitAppt() {
    if (!apptTime) {
      message.warning('请选择看房时间')
      return
    }
    await http.post('/appointments', {
      houseId: Number(id),
      appointmentTime: apptTime.length === 16 ? apptTime + ':00' : apptTime,
      remark: apptRemark
    })
    message.success('预约已提交，等待房东确认')
    setApptOpen(false)
  }

  async function submitContract() {
    if (!contractStart || !contractEnd || contractEnd <= contractStart) {
      message.warning('请选择合法租期（止晚于起）')
      return
    }
    setContracting(true)
    try {
      await http.post('/contracts', {
        houseId: Number(id),
        startDate: contractStart,
        endDate: contractEnd
      })
      message.success('合同已生成，请在「我的合同」中确认签署')
      setContractOpen(false)
    } finally {
      setContracting(false)
    }
  }

  async function submitReport() {
    if (!reportReason.trim()) {
      message.warning('请填写举报理由')
      return
    }
    await http.post('/reports', { targetType: 1, targetId: Number(id), reason: reportReason })
    message.success('举报已提交，平台将在 48 小时内处理')
    setReportOpen(false)
  }

  if (!item)
    return (
      <div className="page">
        <p>加载中…</p>
      </div>
    )

  return (
    <div className="page">
      <Button type="text" onClick={() => history.back()} style={{ marginBottom: 10 }}>
        ← 返回
      </Button>
      <div className="detail-layout">
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="detail-cover">
            {item.images && item.images.length ? (
              <img src={assetUrl(item.images[0].url)} alt="" />
            ) : (
              <span style={{ fontSize: 90 }}>🏠</span>
            )}
          </div>
          <Title level={3} style={{ marginTop: 16 }}>
            {item.house.title}
            <Tag
              color={HOUSE_STATUS_TYPE[item.house.status ?? 0]}
              style={{ verticalAlign: 'middle', marginLeft: 8 }}
            >
              {HOUSE_STATUS[item.house.status ?? 0]}
            </Tag>
          </Title>
          <Descriptions bordered size="small" column={3}>
            <Descriptions.Item label="小区">{item.house.community}</Descriptions.Item>
            <Descriptions.Item label="区域">
              {item.house.city} {item.house.district}
            </Descriptions.Item>
            <Descriptions.Item label="地址">{item.house.address}</Descriptions.Item>
            <Descriptions.Item label="户型">{item.house.layout}</Descriptions.Item>
            <Descriptions.Item label="面积">{item.house.area} ㎡</Descriptions.Item>
            <Descriptions.Item label="朝向/楼层">
              {item.house.orientation || '-'} / {item.house.floorDesc || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="押付方式">{item.house.depositType}</Descriptions.Item>
            <Descriptions.Item label="房东">{item.landlordName}</Descriptions.Item>
            <Descriptions.Item label="评分">
              {item.house.avgScore ? `⭐ ${item.house.avgScore}` : '暂无'}（{item.reviewCount}{' '}
              条评价）
            </Descriptions.Item>
          </Descriptions>

          <h4 style={{ marginTop: 18 }}>设施</h4>
          {facilities.map(f => (
            <Tag key={f} style={{ marginBottom: 8 }}>
              {f}
            </Tag>
          ))}
          <h4>房源描述</h4>
          <Paragraph style={{ color: '#303133' }}>{item.house.description || '暂无描述'}</Paragraph>

          <h4>评价（{reviews.length}）</h4>
          {reviews.map(r => (
            <div key={r.id} className="review-item">
              <b>{r.tenantName}</b>
              <span style={{ color: '#faad14', marginLeft: 8 }}>
                {'★'.repeat(r.houseScore ?? 0)}
              </span>
              <span style={{ color: '#909399', fontSize: 12, marginLeft: 8 }}>
                {fmtTime(r.createdAt)}
              </span>
              <p style={{ margin: '4px 0 0' }}>{r.content}</p>
            </div>
          ))}
          {reviews.length === 0 && <Empty imageStyle={{ height: 60 }} description="暂无评价" />}
        </div>

        <div style={{ width: 300, flexShrink: 0 }}>
          <div className="price-card">
            <div className="house-rent" style={{ fontSize: 26 }}>
              {fmtMoney(item.house.rent)}
              <small> /月</small>
            </div>
            <div style={{ color: '#8492a6', fontSize: 13, margin: '4px 0 14px' }}>
              押付：{item.house.depositType}
            </div>
            {isTenant ? (
              <>
                <Button type="primary" size="large" block onClick={() => setApptOpen(true)}>
                  📅 预约看房
                </Button>
                <Button
                  size="large"
                  block
                  style={{ margin: '10px 0 0' }}
                  onClick={() => setContractOpen(true)}
                >
                  📝 发起在线签约
                </Button>
                <Button type="text" block style={{ marginTop: 10 }} onClick={toggleFav}>
                  {item.favorited ? '♥ 已收藏' : '♡ 收藏房源'}
                </Button>
                <Button type="text" size="small" block onClick={() => setReportOpen(true)}>
                  举报该房源
                </Button>
              </>
            ) : auth.role === 2 && item.house.landlordId === auth.userId ? (
              <Alert type="info" showIcon message="这是您发布的房源" />
            ) : null}
          </div>
        </div>
      </div>

      <Modal
        title="预约看房"
        open={apptOpen}
        onOk={submitAppt}
        onCancel={() => setApptOpen(false)}
        okText="提交预约"
      >
        <p>选择看房时间（同时段每套房源仅一个有效预约）：</p>
        <input
          type="datetime-local"
          style={{ width: '100%', padding: 6 }}
          value={apptTime || ''}
          onChange={e => setApptTime(e.target.value)}
        />
        <Input.TextArea
          style={{ marginTop: 12 }}
          rows={2}
          maxLength={200}
          placeholder="留言（选填）"
          value={apptRemark}
          onChange={e => setApptRemark(e.target.value)}
        />
      </Modal>

      <Modal
        title="发起在线签约（FR-18）"
        open={contractOpen}
        onOk={submitContract}
        confirmLoading={contracting}
        onCancel={() => setContractOpen(false)}
        okText="生成合同"
      >
        <p>系统将按模板生成电子合同并自动填充双方信息，租客确认后由房东确认签署。</p>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <span>租期起：</span>
          <input
            type="date"
            value={contractStart}
            onChange={e => setContractStart(e.target.value)}
            style={{ padding: 6 }}
          />
          <span>止：</span>
          <input
            type="date"
            value={contractEnd}
            onChange={e => setContractEnd(e.target.value)}
            style={{ padding: 6 }}
          />
        </div>
      </Modal>

      <Modal
        title="举报房源"
        open={reportOpen}
        onOk={submitReport}
        okText="提交举报"
        okButtonProps={{ danger: true }}
        onCancel={() => setReportOpen(false)}
      >
        <Input.TextArea
          rows={3}
          maxLength={200}
          placeholder="请描述举报理由（虚假房源、价格异常等）"
          value={reportReason}
          onChange={e => setReportReason(e.target.value)}
        />
      </Modal>
    </div>
  )
}
