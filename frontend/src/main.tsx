import { createRoot } from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'
import { AuthProvider } from './auth'
import './styles.css'

/**
 * 全局主题：釉青 × 瓷白 × 柿朱。
 * 色板语义见 styles.css 令牌区——釉青=品牌与操作、柿朱=钱与风险；
 * 这里与 CSS 变量保持同值，改色请两处同步。
 */
const theme = {
  token: {
    colorPrimary: '#0d6b5b',
    colorInfo: '#0d6b5b',
    colorLink: '#0d6b5b',
    colorSuccess: '#3d8f63',
    colorWarning: '#d98a1f',
    colorError: '#bc4a1d',
    colorText: '#1d2c28',
    colorTextSecondary: '#4a5c56',
    colorTextTertiary: '#82948e',
    colorBgLayout: '#f3f6f5',
    colorBorder: '#ccd7d4',
    colorBorderSecondary: '#e1e8e6',
    borderRadius: 8,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif"
  },
  components: {
    Menu: {
      itemColor: '#4a5c56',
      itemHoverColor: '#0d6b5b',
      itemSelectedColor: '#0d6b5b',
      horizontalItemSelectedColor: '#0d6b5b',
      activeBarHeight: 3
    },
    Table: {
      headerBg: '#eef5f3',
      headerColor: '#1d2c28',
      rowHoverBg: '#f6faf8',
      borderColor: '#e1e8e6'
    },
    Button: {
      fontWeight: 500
    }
  }
}

createRoot(document.getElementById('root')!).render(
  <ConfigProvider locale={zhCN} theme={theme}>
    <AntApp>
      <AuthProvider>
        <HashRouter>
          <App />
        </HashRouter>
      </AuthProvider>
    </AntApp>
  </ConfigProvider>
)
