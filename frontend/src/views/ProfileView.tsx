import { useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Input, message, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import http from '../api'
import { useAuth } from '../auth'
import { USER_ROLE } from '../constants'
import type { MeResult } from '../types'

interface RealnameInfo {
  status: number
  realName?: string
  maskedIdCard?: string
  rejectReason?: string
  [key: string]: any
}

export default function ProfileView() {
  const auth = useAuth()
  const nav = useNavigate()
  const [me, setMe] = useState({ username: '', phone: '', nickname: '', email: '' })
  const [realname, setRealname] = useState<RealnameInfo | null>(null)
  const [rn, setRn] = useState({ realName: '', idCardNo: '' })
  const [pwd, setPwd] = useState({ oldPassword: '', newPassword: '' })

  async function load() {
    const d = await http.get<MeResult>('/users/me')
    setMe(prev => ({ ...prev, ...d.user }))
    setRealname(d.realname)
  }

  async function saveProfile() {
    await http.patch('/users/me', { nickname: me.nickname, email: me.email })
    auth.refreshNickname(me.nickname)
    message.success('资料已更新')
  }

  async function submitRealname() {
    if (!rn.realName || !/^\d{17}[\dXx]$/.test(rn.idCardNo)) {
      message.warning('请填写姓名与 18 位身份证号')
      return
    }
    await http.post('/users/me/realname', rn)
    message.success('实名信息已加密提交，等待审核')
    load()
  }

  async function changePwd() {
    if (!pwd.oldPassword || (pwd.newPassword || '').length < 6) {
      message.warning('请填写原密码与至少 6 位新密码')
      return
    }
    await http.patch('/users/me/password', pwd)
    message.success('密码已修改，请重新登录')
    auth.logout()
    nav('/login')
  }

  useEffect(() => {
    load()
  }, [])

  return (
    <div className="page" style={{ maxWidth: 720 }}>
      <h2 className="page-title">个人中心</h2>

      <Card title="基本信息" style={{ marginBottom: 16 }}>
        <Form layout="vertical" style={{ maxWidth: 420 }}>
          <Form.Item label="昵称">
            <Input
              value={me.nickname}
              onChange={e => setMe(m => ({ ...m, nickname: e.target.value }))}
            />
          </Form.Item>
          <Form.Item label="邮箱">
            <Input value={me.email} onChange={e => setMe(m => ({ ...m, email: e.target.value }))} />
          </Form.Item>
          <Form.Item>
            <Tag>{USER_ROLE[auth.role] || '用户'}</Tag>
            <span style={{ color: '#909399', fontSize: 12, marginLeft: 8 }}>
              账号：{me.username} / {me.phone}
            </span>
          </Form.Item>
          <Button type="primary" onClick={saveProfile}>
            保存资料
          </Button>
        </Form>
      </Card>

      <Card title="实名认证（房东发布房源前置条件）" style={{ marginBottom: 16 }}>
        {auth.role === 2 ? (
          <>
            {realname && realname.status === 1 && (
              <Alert
                type="success"
                showIcon
                style={{ marginBottom: 10 }}
                message={`已通过实名认证：${realname.realName}（${realname.maskedIdCard}）`}
              />
            )}
            {realname && realname.status === 0 && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 10 }}
                message="实名认证审核中，请耐心等待"
              />
            )}
            {realname && realname.status === 2 && (
              <Alert
                type="error"
                showIcon
                style={{ marginBottom: 10 }}
                message={`认证被驳回：${realname.rejectReason || '信息有误'}，请重新提交`}
              />
            )}
            {(!realname || realname.status !== 0) && (
              <Form layout="vertical" style={{ maxWidth: 420 }}>
                <Form.Item label="真实姓名">
                  <Input
                    value={rn.realName}
                    onChange={e => setRn(r => ({ ...r, realName: e.target.value }))}
                  />
                </Form.Item>
                <Form.Item label="身份证号">
                  <Input
                    maxLength={18}
                    value={rn.idCardNo}
                    onChange={e => setRn(r => ({ ...r, idCardNo: e.target.value }))}
                  />
                </Form.Item>
                <Button type="primary" onClick={submitRealname}>
                  提交认证（加密存储）
                </Button>
              </Form>
            )}
          </>
        ) : (
          <Alert
            type="info"
            showIcon
            message="租客无需实名认证即可浏览与预约；签约时需登记姓名与联系方式"
          />
        )}
      </Card>

      <Card title="修改密码">
        <Form layout="vertical" style={{ maxWidth: 420 }}>
          <Form.Item label="原密码">
            <Input.Password
              value={pwd.oldPassword}
              onChange={e => setPwd(p => ({ ...p, oldPassword: e.target.value }))}
            />
          </Form.Item>
          <Form.Item label="新密码">
            <Input.Password
              placeholder="6~32 位"
              value={pwd.newPassword}
              onChange={e => setPwd(p => ({ ...p, newPassword: e.target.value }))}
            />
          </Form.Item>
          <Button danger onClick={changePwd}>
            修改密码（改后需重新登录）
          </Button>
        </Form>
      </Card>
    </div>
  )
}
