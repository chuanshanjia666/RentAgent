import { useState } from 'react'
import { Button, Form, Input, message, Radio, Space, Tabs } from 'antd'
import { useNavigate } from 'react-router-dom'
import http from '../api'
import { useAuth } from '../auth'
import { FloorPlanArt, IconBot, IconChat, IconFile } from '../components/icons'
import type { LoginResult } from '../types'

const QUICK_NAMES: Record<string, string> = {
  xiaochen: '小陈·租客',
  wanglandlord: '王房东·未实名',
  lilandlord: '李房东·已实名',
  admin: '管理员'
}

export default function LoginView() {
  const nav = useNavigate()
  const auth = useAuth()
  const [loading, setLoading] = useState(false)
  const [captchaSent, setCaptchaSent] = useState(false)
  const [loginForm, setLoginForm] = useState({ username: '', password: '' })
  const [regForm, setRegForm] = useState({
    role: 1,
    phone: '',
    captcha: '',
    password: '',
    nickname: ''
  })

  async function doLogin(u?: string, p?: string) {
    const username = u ?? loginForm.username
    const password = p ?? loginForm.password
    if (!username || !password) {
      message.warning('请输入账号与密码')
      return
    }
    setLoading(true)
    try {
      const d = await http.post<LoginResult>('/auth/login', { username, password })
      auth.setLogin(d)
      nav(d.role === 3 ? '/admin/audit' : d.role === 2 ? '/landlord/houses' : '/app')
    } finally {
      setLoading(false)
    }
  }

  async function sendCaptcha() {
    if (!/^1\d{10}$/.test(regForm.phone)) {
      message.warning('请输入 11 位手机号')
      return
    }
    await http.post('/auth/captcha', { phone: regForm.phone })
    setCaptchaSent(true)
    setRegForm(f => ({ ...f, captcha: '246810' }))
    message.success('验证码已发送（演示环境已自动填入）')
  }

  async function doRegister() {
    setLoading(true)
    try {
      const d = await http.post<LoginResult>('/auth/register', regForm)
      auth.setLogin(d)
      nav(d.role === 2 ? '/landlord/houses' : '/app')
    } finally {
      setLoading(false)
    }
  }

  const quick = (name: string) => (
    <a key={name} onClick={() => doLogin(name, '123456')}>
      {QUICK_NAMES[name]}
    </a>
  )

  return (
    <div className="login-page">
      {/* 品牌面：深釉青底 + 户型线稿母题，移动端隐藏 */}
      <div className="login-side">
        <FloorPlanArt
          width={420}
          style={{ position: 'absolute', right: -60, bottom: -40, color: '#fff', opacity: 0.08 }}
        />
        <FloorPlanArt
          width={200}
          style={{ position: 'absolute', left: -40, top: 90, color: '#fff', opacity: 0.06 }}
        />
        <div className="login-brand-row">
          <span className="seal">租</span>
          <div className="login-word">
            RentAgent
            <small>AI 智能体租房平台</small>
          </div>
        </div>
        <div>
          <p className="login-slogan">对话即服务</p>
          <p className="login-slogan-sub">说说你想怎么住，剩下的交给 AI</p>
        </div>
        <div className="login-points">
          <div className="login-point">
            <span className="login-point-ico">
              <IconBot size={17} />
            </span>
            <div>
              <b>对话找房</b>
              <span>用一句话说清需求，AI 替你筛出合适的房源</span>
            </div>
          </div>
          <div className="login-point">
            <span className="login-point-ico">
              <IconChat size={17} />
            </span>
            <div>
              <b>智能客服</b>
              <span>押金、维修、违约，常见问题即时解答</span>
            </div>
          </div>
          <div className="login-point">
            <span className="login-point-ico">
              <IconFile size={17} />
            </span>
            <div>
              <b>在线签约</b>
              <span>合同在线生成与签署，条款由 AI 辅助解读</span>
            </div>
          </div>
        </div>
      </div>

      <div className="login-panel">
        <div className="login-card">
          <div className="login-brand">
            <span className="seal">租</span>
            <div className="login-brand-name">RentAgent</div>
            <div className="slogan">基于 AI 智能体的房屋租赁平台 · 对话即服务</div>
          </div>
          <Tabs
            centered
            items={[
              {
                key: 'login',
                label: '登录',
                children: (
                  <div>
                    <Input
                      size="large"
                      placeholder="账号 / 手机号"
                      style={{ marginBottom: 14 }}
                      value={loginForm.username}
                      onChange={e => setLoginForm(f => ({ ...f, username: e.target.value }))}
                    />
                    <Input.Password
                      size="large"
                      placeholder="密码"
                      style={{ marginBottom: 18 }}
                      value={loginForm.password}
                      onChange={e => setLoginForm(f => ({ ...f, password: e.target.value }))}
                      onPressEnter={() => doLogin()}
                    />
                    <Button
                      type="primary"
                      size="large"
                      block
                      loading={loading}
                      onClick={() => doLogin()}
                    >
                      登 录
                    </Button>
                    <div className="demo-tip">
                      演示账号（密码统一 123456）：
                      {['xiaochen', 'wanglandlord', 'lilandlord', 'admin'].map(quick)}
                    </div>
                  </div>
                )
              },
              {
                key: 'register',
                label: '注册',
                children: (
                  <Form layout="vertical">
                    <Form.Item label="身份">
                      <Radio.Group
                        value={regForm.role}
                        optionType="button"
                        buttonStyle="solid"
                        onChange={e => setRegForm(f => ({ ...f, role: e.target.value }))}
                        options={[
                          { value: 1, label: '我是租客' },
                          { value: 2, label: '我是房东' }
                        ]}
                      />
                    </Form.Item>
                    <Form.Item label="手机号">
                      <Input
                        maxLength={11}
                        placeholder="11 位手机号"
                        value={regForm.phone}
                        onChange={e => setRegForm(f => ({ ...f, phone: e.target.value }))}
                      />
                    </Form.Item>
                    <Form.Item label="验证码">
                      <Space.Compact style={{ width: '100%' }}>
                        <Input
                          placeholder="验证码"
                          value={regForm.captcha}
                          onChange={e => setRegForm(f => ({ ...f, captcha: e.target.value }))}
                        />
                        <Button onClick={sendCaptcha}>
                          {captchaSent ? '已发送' : '发送验证码'}
                        </Button>
                      </Space.Compact>
                    </Form.Item>
                    <Form.Item label="密码">
                      <Input.Password
                        placeholder="6~32 位"
                        value={regForm.password}
                        onChange={e => setRegForm(f => ({ ...f, password: e.target.value }))}
                      />
                    </Form.Item>
                    <Form.Item label="昵称">
                      <Input
                        placeholder="选填"
                        value={regForm.nickname}
                        onChange={e => setRegForm(f => ({ ...f, nickname: e.target.value }))}
                      />
                    </Form.Item>
                    <Button
                      type="primary"
                      size="large"
                      block
                      loading={loading}
                      onClick={doRegister}
                    >
                      注册并登录
                    </Button>
                    <div style={{ fontSize: 12, color: '#82948e', marginTop: 8 }}>
                      演示环境验证码固定为 246810
                    </div>
                  </Form>
                )
              }
            ]}
          />
        </div>
      </div>
    </div>
  )
}
