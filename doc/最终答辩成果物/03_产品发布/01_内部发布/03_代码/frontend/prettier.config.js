/**
 * Prettier 配置：参数取现有代码的多数派风格，而非 Prettier 默认值，
 * 目的是让首次 `npm run format` 的改动集中在真正不统一的写法上，而不是把全部文件推倒重排。
 *
 * 逐项依据（统计于 src/ 下 2824 行源码）：
 * - semi=false        全仓无分号，无例外
 * - singleQuote=true  全仓单引号，无例外
 * - trailingComma=none 多行对象/数组末项一律不写逗号（默认 'all' 会逐处加逗号，纯噪音）
 * - arrowParens=avoid 单参数箭头不带括号（34 处 vs 带括号 0 处）
 * - printWidth=100    超 120 列的仅 13 行、超 100 的 91 行；100 能收掉真正过长的 JSX 属性行，
 *                     又不会重排大量刚好 90 列左右的 antd 组件行
 */
export default {
  semi: false,
  singleQuote: true,
  trailingComma: 'none',
  arrowParens: 'avoid',
  printWidth: 100,
  tabWidth: 2,
  useTabs: false,
  endOfLine: 'lf'
}
