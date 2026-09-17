import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'

/**
 * ESLint 扁平配置（ESLint 10）。
 *
 * 职责划分：ESLint 管「写得对不对」（未使用变量、hook 调用规则、依赖数组），
 * Prettier 管「长得齐不齐」（缩进、引号、换行）。二者用 eslint-config-prettier 隔开，
 * 避免格式类规则在两边各判一次、互相打架。
 */
export default tseslint.config(
  // 构建产物、桌面端打包产物与依赖不参与检查
  { ignores: ['dist/', 'release/', 'build/', 'node_modules/'] },

  js.configs.recommended,
  tseslint.configs.recommended,

  // ── React 源码 ────────────────────────────────────────────────
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.browser },
      parserOptions: {
        /**
         * 项目用自动 JSX 运行时（tsconfig 的 "jsx": "react-jsx"），运行时由构建工具注入，
         * 无需 `import React`。但 typescript-eslint 默认按经典运行时假定 `React` 被 JSX 隐式引用，
         * 于是从不报「导入了 React 却没用」——正是本次要清掉的那类残留。
         * 置 null 表示「本项目的 JSX 不依赖名为 React 的变量」，让死导入如实暴露。
         */
        jsxPragma: null
      }
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh
    },
    rules: {
      /**
       * 只开 react-hooks 的两条经典规则，不整体铺开 v7 的 `configs.recommended`：
       * 后者含 static-components / purity / immutability / set-state-in-effect 等
       * React Compiler 专用规则（要求 React 19 + 编译器），本项目是 React 18 且未接编译器，
       * 全量打开只会得到一片无法通过改代码消除的报错。
       */
      'react-hooks/rules-of-hooks': 'error',
      // 依赖数组缺失只警告：多处刻意用 [] 只跑一次首屏加载
      'react-hooks/exhaustive-deps': 'warn',
      // 允许文件同时导出组件与常量（本项目视图为一文件一组件，常量另有 constants.ts）
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      /**
       * 后端 VO 带动态字段（见 types.ts 的索引签名兜底），前端表格行大量按 `any` 取用。
       * 收敛到强类型是一次独立的类型化改造，不在格式化范围内，故此处关掉而非降级为 warn
       * ——保留成 warn 只会让 npm run lint 永远有几十条噪声，反而盖住真正的未使用变量告警。
       */
      '@typescript-eslint/no-explicit-any': 'off'
    }
  },

  // ── 测试：显式从 vitest 导入 describe/it/expect，故无需注入测试全局 ──
  {
    files: ['src/**/*.test.ts'],
    languageOptions: { globals: { ...globals.node } }
  },

  // ── Electron 外壳与构建/工具配置：Node 环境，CommonJS 或 ESM ──
  {
    files: ['electron/**/*.cjs'],
    languageOptions: {
      sourceType: 'commonjs',
      globals: { ...globals.node }
    },
    // Electron 主进程/proload 必须以 CommonJS 加载（package.json 是 "type": "module"，故用 .cjs 后缀）
    rules: { '@typescript-eslint/no-require-imports': 'off' }
  },
  {
    files: ['*.config.{js,ts}', 'vite.config.ts', 'vitest.config.ts'],
    languageOptions: { globals: { ...globals.node } }
  },

  // 必须放最后：关掉所有与 Prettier 重叠的格式规则
  prettier
)
