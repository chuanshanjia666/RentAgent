import React, { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { LoginResult } from './types'

/** 登录态（token + 角色信息持久化到 localStorage） */
interface AuthState {
  token: string
  userId: number
  role: number // 1 租客 2 房东 3 管理员
  nickname: string
}

interface AuthCtxType extends AuthState {
  isLogin: boolean
  home: string
  setLogin: (d: LoginResult) => void
  refreshNickname: (n: string) => void
  logout: () => void
}

const AuthContext = createContext<AuthCtxType | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [auth, setAuth] = useState<AuthState>(() => ({
    token: localStorage.getItem('ra_token') || '',
    userId: Number(localStorage.getItem('ra_uid') || 0),
    role: Number(localStorage.getItem('ra_role') || 0),
    nickname: localStorage.getItem('ra_nickname') || ''
  }))

  const setLogin = useCallback((d: LoginResult) => {
    const next: AuthState = { token: d.token, userId: d.userId, role: d.role, nickname: d.nickname || '' }
    setAuth(next)
    localStorage.setItem('ra_token', next.token)
    localStorage.setItem('ra_uid', String(next.userId))
    localStorage.setItem('ra_role', String(next.role))
    localStorage.setItem('ra_nickname', next.nickname)
  }, [])

  const refreshNickname = useCallback((n: string) => {
    setAuth(s => ({ ...s, nickname: n }))
    localStorage.setItem('ra_nickname', n)
  }, [])

  const logout = useCallback(() => {
    setAuth({ token: '', userId: 0, role: 0, nickname: '' })
    ;['ra_token', 'ra_uid', 'ra_role', 'ra_nickname'].forEach(k => localStorage.removeItem(k))
  }, [])

  const value = useMemo<AuthCtxType>(() => ({
    ...auth,
    isLogin: !!auth.token,
    home: auth.role === 3 ? '/admin/audit' : auth.role === 2 ? '/landlord/houses' : '/app',
    setLogin,
    refreshNickname,
    logout
  }), [auth, setLogin, refreshNickname, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthCtxType {
  return useContext(AuthContext) as AuthCtxType
}
