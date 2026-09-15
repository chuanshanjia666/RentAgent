/** 与后端接口对齐的公共类型（宽松索引签名兜底后端动态字段） */

/** 分页/列表通用包装 */
export interface PageResult<T = any> {
  list: T[]
  total: number
  [key: string]: any
}

/** 房源实体（house 表） */
export interface House {
  id: number
  title: string
  city?: string
  district?: string
  community?: string
  address?: string
  layout?: string
  area?: number
  orientation?: string
  floorDesc?: string
  rent: number
  depositType?: string
  facilities?: string | string[]
  description?: string
  status?: number
  coverUrl?: string
  avgScore?: number | null
  viewCount?: number
  lng?: number | string
  lat?: number | string
  landlordId?: number
  [key: string]: any
}

/** 房源详情 VO：GET /houses/{id} */
export interface HouseDetail {
  house: House
  images: { id: number; url: string; [key: string]: any }[]
  landlordName: string
  favorited: boolean
  reviewCount: number
  [key: string]: any
}

/** 房源评价 */
export interface Review {
  id: number
  tenantName?: string
  houseScore?: number
  landlordScore?: number
  content?: string
  createdAt?: string
  [key: string]: any
}

/** AI 会话消息（前端态） */
export interface ChatMsg {
  role: number // 1 用户 2 AI
  content: string
  citations?: { title: string; snippet?: string }[]
  typing?: boolean
  [key: string]: any
}

/** 登录/注册响应 */
export interface LoginResult {
  token: string
  userId: number
  role: number
  nickname?: string
  [key: string]: any
}
