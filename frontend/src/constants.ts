/** 字典与常量（与后端 TINYINT 枚举一一对应） */

export const DISTRICTS = ['甘井子区', '沙河口区', '高新园区', '中山区', '西岗区']

/** 地图找房用行政区中心坐标（演示数据集中在大连） */
export const DISTRICT_CENTER: Record<string, [number, number]> = {
  甘井子区: [121.52, 38.88],
  沙河口区: [121.575, 38.9],
  高新园区: [121.537, 38.847],
  中山区: [121.65, 38.92],
  西岗区: [121.61, 38.914]
}

export const LAYOUTS = ['1室1厅', '2室1厅', '2室2厅', '3室1厅', '3室2厅']
export const ORIENTATIONS = ['南', '南北', '东', '东南']
export const FLOORS = ['低层', '中层', '高层']
export const DEPOSIT_TYPES = ['押一付三', '押一付一', '押二付一', '半年付']
export const FACILITIES = ['近地铁', '精装修', '家电齐全', '拎包入住', '电梯', '采光好']

/**
 * `XXX_STATUS` 是状态文案，`XXX_STATUS_TYPE` 是同一状态在 antd `<Tag>` 上的配色，两者按下标一一对应。
 * 配色取值只能是 antd 的状态色（success / processing / error / default / warning）
 * 或预设调色板色名；写成 'danger'、'info'、'primary' 之类的非 antd 词会被当成 CSS 颜色透传，
 * 结果标签渲染成无样式的默认样式——不报错，只是悄悄不对。
 */
export const HOUSE_STATUS: Record<number, string> = {
  0: '待审核',
  1: '已通过',
  2: '已驳回',
  3: '已上架',
  4: '已下架',
  5: '已出租'
}
export const HOUSE_STATUS_TYPE: Record<number, string> = {
  0: 'warning',
  1: 'success',
  2: 'error',
  3: 'success',
  4: 'default',
  5: 'processing'
}

export const APPT_STATUS: Record<number, string> = {
  0: '待确认',
  1: '已确认',
  2: '已拒绝',
  3: '已完成',
  4: '已取消'
}
export const APPT_STATUS_TYPE: Record<number, string> = {
  0: 'warning',
  1: 'success',
  2: 'error',
  3: 'processing',
  4: 'default'
}

export const CONTRACT_STATUS: Record<number, string> = {
  0: '待租客确认',
  1: '待房东确认',
  2: '已生效',
  3: '已退租',
  4: '已作废'
}
export const CONTRACT_STATUS_TYPE: Record<number, string> = {
  0: 'warning',
  1: 'warning',
  2: 'success',
  3: 'default',
  4: 'error'
}

export const ORDER_STATUS: Record<number, string> = { 0: '在租', 1: '已退租', 2: '已到期' }
export const ORDER_STATUS_TYPE: Record<number, string> = {
  0: 'success',
  1: 'default',
  2: 'default'
}

export const BILL_STATUS: Record<number, string> = { 0: '待支付', 1: '已支付', 2: '已逾期' }
export const BILL_STATUS_TYPE: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'error' }

/**
 * 站内消息类型文案。注意后缀不是 `_TYPE`：本文件里 `XXX_STATUS` 是状态文案、`XXX_STATUS_TYPE` 是配色，
 * 而消息字典描述的是 `notification.type` 的**名字**而非颜色，沿用 `_TYPE` 会让人误以为是配色字典。
 */
export const NOTIFY_TYPE_NAME: Record<number, string> = {
  1: '预约',
  2: '审核',
  3: '签约',
  4: '账单',
  5: '举报',
  9: '系统'
}

/** 用户角色：1 租客 2 房东 3 管理员 */
export const USER_ROLE: Record<number, string> = { 1: '租客', 2: '房东', 3: '管理员' }

/** AI 会话场景配色：1 找房助手 2 智能客服 3 合同解读 */
export const SCENE_COLOR: Record<number, string> = { 1: 'blue', 2: 'green', 3: 'purple' }

export function fmtMoney(v: number | string | null | undefined): string {
  return v == null ? '-' : '¥' + Number(v).toLocaleString('zh-CN')
}

export function fmtTime(t: string | null | undefined): string {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

/**
 * 后端 JSON 列（设施 facilities / 合同条款 clauses / 风险条款下标 riskFlags）的解析兜底。
 * 同一字段在列表接口里可能已是数组、在详情接口里是 JSON 字符串，解析失败一律退化为空数组，
 * 避免一条脏数据把整个页面打挂。
 */
export function parseJsonList<T = string>(v: string | T[] | null | undefined): T[] {
  if (Array.isArray(v)) return v
  if (!v) return []
  try {
    const arr = JSON.parse(v)
    return Array.isArray(arr) ? (arr as T[]) : []
  } catch {
    return []
  }
}
