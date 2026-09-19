import type { CSSProperties, ReactNode } from 'react'

/**
 * 自绘线性图标：统一 24 网格、1.7 描边、currentColor 取色。
 * 线稿风格与「户型图」装饰母题同源，替代原先散落各处的 emoji，
 * 不引入 @ant-design/icons 依赖（它只是 antd 的传递依赖，不宜直接 import）。
 */
interface IconProps {
  size?: number
  style?: CSSProperties
  /** 实心填充（用于收藏红心等少数场景），默认线稿 */
  filled?: boolean
}

function Svg({
  children,
  size = 16,
  style,
  filled
}: IconProps & {
  children: ReactNode
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill={filled ? 'currentColor' : 'none'}
      stroke={filled ? 'none' : 'currentColor'}
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      style={style}
      aria-hidden="true"
    >
      {children}
    </svg>
  )
}

/** 房子（找房 / 品牌） */
export function IconHome(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M3.5 9.6 12 3l8.5 6.6V20a1 1 0 0 1-1 1h-4.8v-6.4H9.3V21H4.5a1 1 0 0 1-1-1z" />
    </Svg>
  )
}

/** 机器人（AI 助手 / AI 能力入口） */
export function IconBot(p: IconProps) {
  return (
    <Svg {...p}>
      <rect x="4" y="8.2" width="16" height="11" rx="2.6" />
      <path d="M12 8V4.6" />
      <circle cx="12" cy="3.6" r="1.1" />
      <path d="M9 12.8h.01M15 12.8h.01M9.5 16.2h5" />
    </Svg>
  )
}

/** 日历（预约） */
export function IconCalendar(p: IconProps) {
  return (
    <Svg {...p}>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2" />
      <path d="M8 3v4M16 3v4M3.5 10.2h17" />
    </Svg>
  )
}

/** 文书（合同） */
export function IconFile(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M13.6 3H6.5A1.5 1.5 0 0 0 5 4.5v15A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V8.4z" />
      <path d="M13.6 3v5.4H19M9 13h6M9 16.6h4" />
    </Svg>
  )
}

/** 账单 / 订单 */
export function IconReceipt(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M6 3h12v17.6l-2-1.4-2 1.4-2-1.4-2 1.4-2-1.4-2 1.4z" />
      <path d="M9.2 8h5.6M9.2 12h5.6" />
    </Svg>
  )
}

/** 收藏红心 */
export function IconHeart(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M12 20.3S4.2 15.6 2.9 11.2A5 5 0 0 1 12 6.9a5 5 0 0 1 9.1 4.3C19.8 15.6 12 20.3 12 20.3z" />
    </Svg>
  )
}

/** 铃铛（消息通知） */
export function IconBell(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M17.8 9.4a5.8 5.8 0 1 0-11.6 0c0 5.8-2.4 7-2.4 7h16.4s-2.4-1.2-2.4-7" />
      <path d="M10 20a2.2 2.2 0 0 0 4 0" />
    </Svg>
  )
}

/** 搜索 */
export function IconSearch(p: IconProps) {
  return (
    <Svg {...p}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m20.2 20.2-4.5-4.5" />
    </Svg>
  )
}

/** 地图定位点 */
export function IconMapPin(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M12 21.2s-6.6-5.4-6.6-10.6a6.6 6.6 0 0 1 13.2 0c0 5.2-6.6 10.6-6.6 10.6z" />
      <circle cx="12" cy="10.4" r="2.3" />
    </Svg>
  )
}

/** 发送 */
export function IconSend(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M21 3.5 10.8 13.7M21 3.5 14.6 21l-3.8-7.3L3.5 9.9z" />
    </Svg>
  )
}

/** 新增 */
export function IconPlus(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M12 5.5v13M5.5 12h13" />
    </Svg>
  )
}

/** 星级评分 */
export function IconStar(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="m12 3.4 2.7 5.5 6 .9-4.3 4.2 1 6L12 17.2l-5.4 2.8 1-6-4.3-4.2 6-.9z" />
    </Svg>
  )
}

/** 盾牌对勾（审核工作台） */
export function IconShield(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M12 2.8 4.6 5.6v5.1c0 4.6 3.1 8.2 7.4 9.9 4.3-1.7 7.4-5.3 7.4-9.9V5.6z" />
      <path d="m8.8 11.6 2.2 2.2 4.2-4.6" />
    </Svg>
  )
}

/** 用户组（用户管理） */
export function IconUsers(p: IconProps) {
  return (
    <Svg {...p}>
      <circle cx="9" cy="8.3" r="3.3" />
      <path d="M3.4 20.2c.6-3.5 2.9-5.5 5.6-5.5s5 2 5.6 5.5M15.4 5.4a3.3 3.3 0 0 1 0 5.9M17.5 14.9c1.9.7 3.2 2.5 3.7 5.3" />
    </Svg>
  )
}

/** 旗帜（举报处理） */
export function IconFlag(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M5 21V4.2M5 4.6c3.6-2 6.6 1.8 10.4.1V13c-3.8 1.7-6.8-2.1-10.4-.1" />
    </Svg>
  )
}

/** 清单（操作留痕） */
export function IconList(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M9 6.2h11.5M9 12h11.5M9 17.8h11.5" />
      <path d="M4 6.2h.01M4 12h.01M4 17.8h.01" />
    </Svg>
  )
}

/** 对话气泡（AI 对话审计 / AI 解读） */
export function IconChat(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M21 14.6a1.9 1.9 0 0 1-1.9 1.9H8.2l-4.7 4V5.4a1.9 1.9 0 0 1 1.9-1.9h13.7A1.9 1.9 0 0 1 21 5.4z" />
      <path d="M8.5 9.5h7M8.5 12.8h4.5" />
    </Svg>
  )
}

/** 柱状图（数据看板） */
export function IconChart(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M5.5 20.5V13M12 20.5V4.5M18.5 20.5v-5.4" />
    </Svg>
  )
}

/** 楼栋（房源管理） */
export function IconBuilding(p: IconProps) {
  return (
    <Svg {...p}>
      <rect x="5" y="3.5" width="14" height="17" rx="1.6" />
      <path d="M9.3 20.5v-3.6h5.4v3.6" />
      <path d="M9 7.5h.01M12 7.5h.01M15 7.5h.01M9 11h.01M12 11h.01M15 11h.01M9 14.5h.01M15 14.5h.01" />
    </Svg>
  )
}

/** 单个用户（个人中心） */
export function IconUser(p: IconProps) {
  return (
    <Svg {...p}>
      <circle cx="12" cy="8.2" r="3.8" />
      <path d="M4.8 20.4c1-3.9 3.9-5.9 7.2-5.9s6.2 2 7.2 5.9" />
    </Svg>
  )
}

/** 返回箭头 */
export function IconArrowLeft(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M19.5 12H4.5M10.8 5.7 4.5 12l6.3 6.3" />
    </Svg>
  )
}

/** 警示三角（风险条款） */
export function IconAlert(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M12 3.6 2.8 19.6h18.4zM12 9.8v4M12 16.6h.01" />
    </Svg>
  )
}

/** 趋势上扬（智能定价） */
export function IconTrend(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M3 19.5h18" />
      <path d="m4.5 14.8 4.7-4.7 3.4 3L19 6.5" />
      <path d="M15.5 6.5H19V10" />
    </Svg>
  )
}

/** 魔棒（AI 智能填充） */
export function IconWand(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="m4 20 9.5-9.5M13.5 10.5l2-2" />
      <path d="M15 3.4v2.2M15 9.4v2.2M11.4 7h2.2M18.4 7h2.2M18.9 3.6l1.5 1.5M9.6 3.6 8.1 5.1" />
    </Svg>
  )
}

/** 眼睛（浏览量） */
export function IconEye(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M2.6 12S6.1 5.7 12 5.7 21.4 12 21.4 12 17.9 18.3 12 18.3 2.6 12 2.6 12z" />
      <circle cx="12" cy="12" r="2.7" />
    </Svg>
  )
}

/** 扳手（工具调用留痕） */
export function IconWrench(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="M14.6 6.4a1 1 0 0 0 0 1.4l1.5 1.5a1 1 0 0 0 1.4 0l3.3-3.3a5.7 5.7 0 0 1-7.5 7.5l-6.6 6.6a2 2 0 0 1-2.8-2.8l6.6-6.6a5.7 5.7 0 0 1 7.5-7.5z" />
    </Svg>
  )
}

/** 下拉箭头（用户菜单） */
export function IconChevronDown(p: IconProps) {
  return (
    <Svg {...p}>
      <path d="m6 9.5 6 6 6-6" />
    </Svg>
  )
}

/**
 * 户型线稿：外框 + 内墙 + 门弧 + 窗线，全站唯一的装饰母题。
 * 用于房源封面占位与登录页品牌面，颜色跟随 currentColor。
 */
export function FloorPlanArt({ width = 96, style }: { width?: number; style?: CSSProperties }) {
  return (
    <svg
      viewBox="0 0 96 72"
      width={width}
      style={style}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      aria-hidden="true"
    >
      <rect x="5" y="5" width="86" height="62" rx="2" />
      <path d="M5 43h29M34 5v22M34 27h26M60 67V40M60 40h31" strokeWidth={1.2} />
      <rect x="64" y="12" width="18" height="14" strokeWidth={1.2} />
      <path d="M13 14h14M13 50h14M68 53h14" strokeWidth={1.2} />
      <path d="M34 43a9 9 0 0 1 9 9" strokeWidth={1} strokeDasharray="2.5 3.5" />
    </svg>
  )
}
