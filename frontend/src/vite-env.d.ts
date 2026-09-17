/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端基址，缺省为空串（同源相对路径，交给 Nginx / Vite 代理） */
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
