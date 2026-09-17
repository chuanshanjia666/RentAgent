/**
 * 桌面端预加载脚本：把后端地址交给页面。
 * 页面侧在 `src/runtime.ts` 读取，浏览器版没有这个全局变量，因此自动走同源相对路径。
 */
const { contextBridge, ipcRenderer } = require('electron')

const { apiBase } = ipcRenderer.sendSync('rentagent:config')

contextBridge.exposeInMainWorld('RentAgent', { apiBase })
