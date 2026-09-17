import { describe, expect, it } from 'vitest'
import {
  APPT_STATUS,
  BILL_STATUS,
  CONTRACT_STATUS,
  DEPOSIT_TYPES,
  DISTRICT_CENTER,
  DISTRICTS,
  FACILITIES,
  FLOORS,
  HOUSE_STATUS,
  LAYOUTS,
  NOTIFY_TYPE,
  ORIENTATIONS,
  ORDER_STATUS,
  fmtMoney,
  fmtTime
} from './constants'

/**
 * 常量与格式化单测（FT-DICT-xx / FT-FMT-xx）：
 * 字典键必须与后端 TINYINT 枚举一一对应，否则页面会把状态渲染成空白。
 */
describe('FT-DICT 状态字典与后端枚举对齐', () => {
  it('FT-DICT-01 房源状态覆盖后端 0~5 且文案与库表注释一致', () => {
    expect(HOUSE_STATUS).toEqual({
      0: '待审核',
      1: '已通过',
      2: '已驳回',
      3: '已上架',
      4: '已下架',
      5: '已出租'
    })
  })

  it('FT-DICT-02 预约/合同/订单/账单状态字典完整覆盖后端状态机取值', () => {
    expect(Object.keys(APPT_STATUS).map(Number).sort()).toEqual([0, 1, 2, 3, 4])
    expect(Object.keys(CONTRACT_STATUS).map(Number).sort()).toEqual([0, 1, 2, 3, 4])
    expect(Object.keys(ORDER_STATUS).map(Number).sort()).toEqual([0, 1, 2])
    expect(Object.keys(BILL_STATUS).map(Number).sort()).toEqual([0, 1, 2])
  })

  it('FT-DICT-03 通知类型字典覆盖后端使用的 type 值', () => {
    // 后端使用 1 预约 / 2 审核 / 3 签约 / 4 账单 / 9 评价（映射为"系统"）
    for (const t of [1, 2, 3, 4, 9]) {
      expect(NOTIFY_TYPE[t]).toBeTruthy()
    }
  })

  it('FT-DICT-04 行政区均有地图中心坐标：地图找房不会出现无坐标的区', () => {
    DISTRICTS.forEach(d => {
      expect(DISTRICT_CENTER[d], `${d} 缺少中心坐标`).toBeDefined()
      const [lng, lat] = DISTRICT_CENTER[d]
      expect(lng).toBeGreaterThan(120)
      expect(lng).toBeLessThan(122)
      expect(lat).toBeGreaterThan(38)
      expect(lat).toBeLessThan(40)
    })
  })

  it('FT-DICT-05 表单选项集合非空且无重复项', () => {
    for (const list of [DISTRICTS, LAYOUTS, ORIENTATIONS, FLOORS, DEPOSIT_TYPES, FACILITIES]) {
      expect(list.length).toBeGreaterThan(0)
      expect(new Set(list).size).toBe(list.length)
    }
    // 设施选项与后端虚假检测/智能识别的标签体系一致
    expect(FACILITIES).toContain('近地铁')
    expect(FACILITIES).toContain('精装修')
  })
})

describe('FT-FMT 金额与时间格式化', () => {
  it('FT-FMT-01 金额为空时占位为短横线，不显示 0', () => {
    expect(fmtMoney(null)).toBe('-')
    expect(fmtMoney(undefined)).toBe('-')
  })

  it('FT-FMT-02 金额带人民币符号且按千分位展示，兼容字符串数字', () => {
    expect(fmtMoney(2100)).toBe('¥2,100')
    expect(fmtMoney('2100')).toBe('¥2,100')
    expect(fmtMoney(1234567)).toBe('¥1,234,567')
    expect(fmtMoney(0)).toBe('¥0')
  })

  it('FT-FMT-03 时间取到分钟并替换 T 分隔符', () => {
    expect(fmtTime('2026-09-17T14:30:25')).toBe('2026-09-17 14:30')
    expect(fmtTime('2026-09-17T14:30')).toBe('2026-09-17 14:30')
  })

  it('FT-FMT-04 时间为空时占位为短横线', () => {
    expect(fmtTime(null)).toBe('-')
    expect(fmtTime(undefined)).toBe('-')
    expect(fmtTime('')).toBe('-')
  })
})
