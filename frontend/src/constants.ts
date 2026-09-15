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

export const HOUSE_STATUS: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已驳回', 3: '已上架', 4: '已下架', 5: '已出租' }
export const HOUSE_STATUS_TYPE: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'success', 4: 'info', 5: 'primary' }

export const APPT_STATUS: Record<number, string> = { 0: '待确认', 1: '已确认', 2: '已拒绝', 3: '已完成', 4: '已取消' }
export const APPT_STATUS_TYPE: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'primary', 4: 'info' }

export const CONTRACT_STATUS: Record<number, string> = { 0: '待租客确认', 1: '待房东确认', 2: '已生效', 3: '已退租', 4: '已作废' }
export const CONTRACT_STATUS_TYPE: Record<number, string> = { 0: 'warning', 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' }

export const ORDER_STATUS: Record<number, string> = { 0: '在租', 1: '已退租', 2: '已到期' }
export const BILL_STATUS: Record<number, string> = { 0: '待支付', 1: '已支付', 2: '已逾期' }

export const NOTIFY_TYPE: Record<number, string> = { 1: '预约', 2: '审核', 3: '签约', 4: '账单', 5: '举报', 9: '系统' }

export function fmtMoney(v: number | string | null | undefined): string {
  return v == null ? '-' : '¥' + Number(v).toLocaleString('zh-CN')
}

export function fmtTime(t: string | null | undefined): string {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}
