/** 与后端接口对齐的公共类型（宽松索引签名兜底后端动态字段） */

/** 分页/列表通用包装（后端 PageVO：list + 分页元数据） */
export interface PageResult<T = any> {
  list: T[]
  total: number
  page?: number
  size?: number
}

/** 房源实体（house 表）。facilities 由后端 JSON 列统一映射为数组，前端不必再兼容字符串 */
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
  facilities?: string[]
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

/** 后台会话审计列表行：GET /admin/chats */
export interface AdminChatSession {
  id: number
  scene: number
  sceneName: string
  title?: string
  userId: number
  nickname?: string
  username?: string
  phone?: string
  userRole?: number
  isTransferred: number
  messageCount: number
  roundCount: number
  toolCallCount: number
  totalTokens: number
  lastMessage?: string | null
  createdAt?: string
  updatedAt?: string
  [key: string]: any
}

/** 后台会话审计消息：role 1用户 2助手 3工具调用 */
export interface AdminChatMessage {
  id: number
  role: number
  roleName: string
  content?: string | null
  toolName?: string | null
  toolArgs?: unknown
  toolResult?: unknown
  citations?: { title: string; snippet?: string }[]
  tokenCount?: number | null
  latencyMs?: number | null
  createdAt?: string
  [key: string]: any
}

/** 后台会话审计详情：GET /admin/chats/{id} */
export interface AdminChatDetail {
  session: AdminChatSession
  currentEngine: string
  systemPrompt: string
  messages: AdminChatMessage[]
  stats: {
    roundCount: number
    assistantCount: number
    toolCallCount: number
    totalTokens: number
    avgLatencyMs: number
    tools: { name: string; count: number; avgLatencyMs: number }[]
  }
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

/** 当前用户信息：GET /users/me（后端 MeVO，只含展示字段） */
export interface MeResult {
  user: {
    id: number
    username?: string
    phone?: string
    email?: string
    nickname?: string
    avatarUrl?: string
    role?: number
    status?: number
    createdAt?: string
    [key: string]: any
  }
  realname: {
    realName?: string
    status: number
    rejectReason?: string
    maskedIdCard?: string
    [key: string]: any
  } | null
}

/** 交易类列表行（后端 TradeDto 的 record VO，字段名与 JSON 一致） */
export interface AppointmentRow {
  appointment: {
    id: number
    houseId: number
    status: number
    appointmentTime?: string
    remark?: string
    rejectReason?: string
    [key: string]: any
  }
  houseTitle?: string | null
  houseCover?: string | null
  tenantName?: string | null
  rent?: number | null
  [key: string]: any
}

export interface ReviewRow {
  id: number
  houseScore?: number
  landlordScore?: number
  content?: string
  createdAt?: string
  tenantName?: string
  [key: string]: any
}

export interface ContractRow {
  contract: {
    id: number
    houseId: number
    status: number
    startDate?: string
    endDate?: string
    monthlyRent?: number
    deposit?: number
    clauses?: string | { title: string; text: string }[]
    riskFlags?: string | number[]
    [key: string]: any
  }
  houseTitle: string
}

export interface OrderRow {
  order: {
    id: number
    contractId: number
    status: number
    startDate?: string
    endDate?: string
    monthlyRent?: number
    [key: string]: any
  }
  contractStatus?: number | null
  houseTitle?: string
  billCount: number
  unpaidCount: number
  [key: string]: any
}

export interface ReportRow {
  report: {
    id: number
    targetType: number
    status: number
    reason?: string
    handleRemark?: string
    [key: string]: any
  }
  targetTitle?: string
  [key: string]: any
}

/** FR-24 数据看板：GET /admin/dashboard（后端 DashboardVO） */
export interface DashboardData {
  userCount: number
  landlordCount: number
  houseCount: number
  onlineCount: number
  pendingCount: number
  rentedCount: number
  orderCount: number
  chatCount: number
  chatMessageCount: number
  toolCallCount: number
  transferredCount: number
  userTrend: TrendPoint[]
  houseTrend: TrendPoint[]
  orderTrend: TrendPoint[]
  chatTrend: TrendPoint[]
}

export interface TrendPoint {
  period: string
  cnt: number | string
}

/** 租金账单：GET /orders/{id}/bills */
export interface RentBill {
  id: number
  leaseOrderId?: number
  periodNo: number
  dueDate?: string
  amount?: number
  status: number
  paidAt?: string
  [key: string]: any
}

/** 管理员视角的用户行：GET /admin/users（后端 AdminDto.UserVO） */
export interface AdminUserRow {
  id: number
  username?: string
  phone?: string
  email?: string
  nickname?: string
  role?: number
  status?: number
  [key: string]: any
}

/** 操作留痕行：GET /admin/audits */
export interface AuditLogRow {
  id: number
  operatorId?: number
  action?: string
  targetType?: string | null
  targetId?: number | null
  detail?: string | null
  createdAt?: string
  [key: string]: any
}
