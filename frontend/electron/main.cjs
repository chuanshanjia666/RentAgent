/**
 * RentAgent 桌面端外壳。
 *
 * 只做一件事：用原生窗口打开 Web 版的构建产物（`frontend/dist`），
 * 页面、交互、业务逻辑与浏览器版完全一致，未新增任何界面或功能。
 *
 * 之所以需要这个外壳注入后端地址：桌面端页面以 file:// 加载，
 * 相对路径 `/api/v1` 会被解析为本地文件，必须换成后端绝对地址。
 *
 * 用法：
 *   npm run dev:desktop      # 加载 Vite 开发服务器（改代码热更新）
 *   npm run start:desktop    # 加载 dist 构建产物
 *   npm run dist:desktop     # 打包为 AppImage / deb 安装包
 * 后端地址按需覆盖：
 *   RENTAGENT_API_BASE=http://192.168.1.10:8080 ./RentAgent-1.0.0.AppImage
 *   ./RentAgent-1.0.0.AppImage --api-base=http://192.168.1.10:8080
 */
const path = require('node:path')
const { app, BrowserWindow, ipcMain, shell } = require('electron')

/** 后端默认地址（本机演示） */
const DEFAULT_API_BASE = 'http://localhost:8080'
/** 开发模式下的页面地址（Vite 开发服务器） */
const DEV_SERVER_URL = 'http://localhost:5173'
/** 以 --dev 启动时加载开发服务器，而非 dist 构建产物 */
const DEV_MODE = process.argv.includes('--dev')

/** 后端地址优先级：命令行 --api-base= > 环境变量 RENTAGENT_API_BASE > 默认值 */
function resolveApiBase() {
  const PREFIX = '--api-base='
  const fromArg = (process.argv.find(a => a.startsWith(PREFIX)) || '').slice(PREFIX.length)
  const raw = fromArg || process.env.RENTAGENT_API_BASE || DEFAULT_API_BASE
  return raw.replace(/\/+$/, '')
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 680,
    title: 'RentAgent · AI 智能租房平台',
    autoHideMenuBar: true,
    backgroundColor: '#f5f6f8',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  // 站外链接（如后端 Swagger 地址）交给系统浏览器，不在应用内开新窗口
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })

  if (DEV_MODE) {
    win.loadURL(DEV_SERVER_URL)
  } else {
    win.loadFile(path.join(__dirname, '..', 'dist', 'index.html'))
  }
}

// 页面加载前，预加载脚本同步取一次运行时配置
ipcMain.on('rentagent:config', event => {
  event.returnValue = { apiBase: resolveApiBase() }
})

app.whenReady().then(() => {
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
