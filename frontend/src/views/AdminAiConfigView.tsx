import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import http from '../api'
import type {
  AiConfigOverviewVO,
  AiConfigSaveReq,
  AiConfigTestReq,
  AiConfigTestVO,
  AiConfigVO
} from '../types'

const { Text } = Typography

/** 常用大模型 API 预设模板 */
const PRESETS = [
  {
    label: 'DeepSeek (官方 API)',
    name: 'DeepSeek 官方',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://api.deepseek.com/v1',
    model: 'deepseek-chat',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutSeconds: 60
  },
  {
    label: 'OpenAI (官方 API)',
    name: 'OpenAI 官方',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o-mini',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutSeconds: 60
  },
  {
    label: '通义千问 (阿里云百炼兼容模式)',
    name: '通义千问',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'qwen-plus',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutSeconds: 60
  },
  {
    label: 'Claude (Anthropic Messages API)',
    name: 'Claude 官方',
    protocol: 'anthropic-messages',
    baseUrl: 'https://api.anthropic.com/v1',
    model: 'claude-3-5-sonnet-20241022',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutSeconds: 60
  },
  {
    label: 'OpenCode GO (OpenAI兼容)',
    name: 'OpenCode GO',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://opencode.ai/zen/go/v1',
    model: 'deepseek-v4.1-flash',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutSeconds: 60,
    userAgent: 'RentAgent-Platform/1.0',
    customHeaders: JSON.stringify({ 'x-opencode-session': 'rentagent-session-default' }, null, 2)
  },
  {
    label: 'Command Code GOAT (OpenAI兼容)',
    name: 'Command Code GOAT',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://api.commandcode.ai/provider/v1',
    model: 'inclusionai/ling-3.1-flash:free',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutSeconds: 60,
    userAgent: 'RentAgent-Platform/1.0',
    customHeaders: ''
  },
  {
    label: 'CommandCode 聚合网关 (OpenAI兼容)',
    name: 'CommandCode 聚合通道',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://api.commandcode.ai/provider/v1',
    model: 'deepseek/deepseek-v4.1-flash',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutSeconds: 60,
    userAgent: 'RentAgent-Platform/1.0',
    customHeaders: ''
  }
]

export default function AdminAiConfigView() {
  const [overview, setOverview] = useState<AiConfigOverviewVO | null>(null)
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingItem, setEditingItem] = useState<AiConfigVO | null>(null)
  const [testingId, setTestingId] = useState<number | null>(null)
  const [testingModal, setTestingModal] = useState(false)
  const [testResult, setTestResult] = useState<AiConfigTestVO | null>(null)

  const [form] = Form.useForm<AiConfigSaveReq>()

  async function loadData() {
    setLoading(true)
    try {
      const data = await http.get<AiConfigOverviewVO>('/admin/ai-configs')
      setOverview(data)
    } catch {
      /* 拦截器已处理错误提示 */
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  function openCreate() {
    setEditingItem(null)
    setTestResult(null)
    form.resetFields()
    form.setFieldsValue({
      protocol: 'openai-chat-completions',
      temperature: 0.7,
      maxTokens: 4096,
      timeoutSeconds: 60,
      customHeaders: '',
      isActive: (overview?.configs.length ?? 0) === 0
    })
    setModalVisible(true)
  }

  function openEdit(item: AiConfigVO) {
    setEditingItem(item)
    setTestResult(null)
    form.resetFields()
    form.setFieldsValue({
      name: item.name,
      protocol: item.protocol,
      baseUrl: item.baseUrl,
      apiKey: '',
      model: item.model,
      temperature: item.temperature,
      maxTokens: item.maxTokens,
      timeoutSeconds: item.timeoutSeconds,
      userAgent: item.userAgent ?? '',
      customHeaders: item.customHeaders ?? '',
      isActive: item.isActive === 1
    })
    setModalVisible(true)
  }

  function applyPreset(presetIndex: number) {
    const p = PRESETS[presetIndex]
    if (!p) return
    form.setFieldsValue({
      name: p.name,
      protocol: p.protocol,
      baseUrl: p.baseUrl,
      model: p.model,
      temperature: p.temperature,
      maxTokens: p.maxTokens,
      timeoutSeconds: p.timeoutSeconds,
      userAgent: p.userAgent ?? '',
      customHeaders: p.customHeaders ?? ''
    })
    message.info(`已填充「${p.label}」参数模板，请输入 API Key`)
  }

  async function handleSave() {
    try {
      const values = await form.validateFields()
      if (editingItem) {
        await http.put(`/admin/ai-configs/${editingItem.id}`, values)
        message.success('供应商配置已更新')
      } else {
        await http.post('/admin/ai-configs', values)
        message.success('新建供应商配置成功并已存入数据库')
      }
      setModalVisible(false)
      loadData()
    } catch {
      /* 表单校验失败不动作 */
    }
  }

  async function handleActivate(id: number) {
    try {
      await http.patch(`/admin/ai-configs/${id}/activate`)
      message.success('已切换为当前生效供应商，运行时已热更新')
      loadData()
    } catch {
      /* 忽略 */
    }
  }

  async function handleDeactivate() {
    try {
      await http.patch('/admin/ai-configs/deactivate')
      message.success('已停用数据库配置，将回退为配置文件定义或未配置状态')
      loadData()
    } catch {
      /* 忽略 */
    }
  }

  async function handleDelete(id: number) {
    try {
      await http.delete(`/admin/ai-configs/${id}`)
      message.success('配置已删除')
      loadData()
    } catch {
      /* 忽略 */
    }
  }

  async function handleImportFromFile() {
    try {
      await http.post('/admin/ai-configs/import-from-file')
      message.success('已成功从后端配置文件导入并启用该配置')
      loadData()
    } catch {
      /* 忽略 */
    }
  }

  async function runTestConnection(req: AiConfigTestReq) {
    const res = await http.post<AiConfigTestVO>('/admin/ai-configs/test', req)
    return res
  }

  async function handleTestRow(item: AiConfigVO) {
    setTestingId(item.id)
    try {
      const res = await runTestConnection({
        id: item.id,
        protocol: item.protocol,
        baseUrl: item.baseUrl,
        model: item.model,
        timeoutSeconds: item.timeoutSeconds,
        userAgent: item.userAgent ?? undefined,
        customHeaders: item.customHeaders ?? undefined
      })
      if (res.success) {
        message.success(`「${item.name}」${res.message}`)
      } else {
        message.error(`「${item.name}」${res.message}`)
      }
    } catch {
      /* 忽略 */
    } finally {
      setTestingId(null)
    }
  }

  async function handleTestInModal() {
    try {
      const values = await form.validateFields(['protocol', 'baseUrl', 'model'])
      const currentApiKey = form.getFieldValue('apiKey')
      setTestingModal(true)
      const res = await runTestConnection({
        id: editingItem?.id,
        protocol: values.protocol,
        baseUrl: values.baseUrl,
        apiKey: currentApiKey,
        model: values.model,
        temperature: form.getFieldValue('temperature'),
        timeoutSeconds: form.getFieldValue('timeoutSeconds'),
        userAgent: form.getFieldValue('userAgent'),
        customHeaders: form.getFieldValue('customHeaders')
      })
      setTestResult(res)
      if (res.success) {
        message.success(res.message)
      } else {
        message.warning(res.message)
      }
    } catch {
      message.warning('请先填写协议、基址与模型名称后再进行测试')
    } finally {
      setTestingModal(false)
    }
  }

  const columns: ColumnsType<AiConfigVO> = [
    {
      title: '供应商名称',
      dataIndex: 'name',
      render: (val: string, r) => (
        <Space size="small">
          <Text strong>{val}</Text>
          {r.isActive === 1 && <Tag color="green">当前生效</Tag>}
        </Space>
      )
    },
    {
      title: '通信协议',
      dataIndex: 'protocol',
      width: 170,
      render: (p: string) => {
        if (p === 'anthropic-messages') return <Tag color="purple">Claude 原生</Tag>
        if (p === 'openai-responses') return <Tag color="orange">Responses API</Tag>
        return <Tag color="blue">OpenAI 兼容</Tag>
      }
    },
    {
      title: '接口基址',
      dataIndex: 'baseUrl',
      ellipsis: true,
      render: (url: string) => <Text code>{url}</Text>
    },
    {
      title: '模型标识',
      dataIndex: 'model',
      render: (m: string) => <Text code>{m}</Text>
    },
    {
      title: 'API Key',
      dataIndex: 'maskedApiKey',
      width: 140,
      render: (key: string, r) =>
        r.hasApiKey ? <Text code>{key}</Text> : <Tag color="red">未设置</Tag>
    },
    {
      title: '运行参数',
      width: 160,
      render: (_, r) => (
        <span style={{ fontSize: 12, color: '#657771' }}>
          T:{r.temperature} | {r.maxTokens} tokens | {r.timeoutSeconds}s
        </span>
      )
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 160,
      render: (t: string) => (
        <span style={{ fontSize: 12, color: '#888' }}>{t?.replace('T', ' ')}</span>
      )
    },
    {
      title: '操作',
      width: 220,
      render: (_, r) => (
        <Space size="small">
          {r.isActive === 1 ? (
            <Popconfirm
              title="确定停用此数据库供应商配置？"
              description="停用后系统将回退为使用配置文件默认设置"
              onConfirm={handleDeactivate}
            >
              <Button size="small">停用</Button>
            </Popconfirm>
          ) : (
            <Button size="small" type="primary" ghost onClick={() => handleActivate(r.id)}>
              设为生效
            </Button>
          )}

          <Button size="small" loading={testingId === r.id} onClick={() => handleTestRow(r)}>
            测试
          </Button>

          <Button size="small" onClick={() => openEdit(r)}>
            编辑
          </Button>

          <Popconfirm
            title="确定删除此供应商配置？"
            disabled={r.isActive === 1}
            onConfirm={() => handleDelete(r.id)}
          >
            <Button size="small" danger disabled={r.isActive === 1}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  const activeSource = overview?.activeSource ?? 'NONE'
  const activeDescribe = overview?.activeDescribe ?? 'none'

  return (
    <div className="page">
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: 16
        }}
      >
        <div>
          <h2 className="page-title" style={{ marginBottom: 4 }}>
            AI API 供应商配置
          </h2>
          <p style={{ margin: 0, color: '#657771', fontSize: 13 }}>
            在管理员控制台配置大模型供应商参数，保存后即时存入数据库并热生效，无需手动改动后端配置文件。
          </p>
        </div>
        <Space>
          <Button onClick={handleImportFromFile}>从配置文件导入</Button>
          <Button type="primary" onClick={openCreate}>
            新建供应商配置
          </Button>
        </Space>
      </div>

      <Card style={{ marginBottom: 16 }} bodyStyle={{ padding: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <Space size="middle">
              <span style={{ fontWeight: 600 }}>当前运行时生效状态：</span>
              {activeSource === 'DATABASE' && (
                <Tag color="success" style={{ fontSize: 13, padding: '2px 8px' }}>
                  数据库配置生效中
                </Tag>
              )}
              {activeSource === 'CONFIG_FILE' && (
                <Tag color="warning" style={{ fontSize: 13, padding: '2px 8px' }}>
                  配置文件/环境变量默认中（数据库未激活）
                </Tag>
              )}
              {activeSource === 'NONE' && (
                <Tag color="error" style={{ fontSize: 13, padding: '2px 8px' }}>
                  未接入大模型服务 (4001)
                </Tag>
              )}
              <Text code strong>
                {activeDescribe}
              </Text>
            </Space>
          </div>
          {activeSource === 'CONFIG_FILE' && (
            <span style={{ color: '#888', fontSize: 12 }}>
              提示：点击右上角「从配置文件导入」可将当前配置固化进数据库管理
            </span>
          )}
        </div>
      </Card>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={overview?.configs ?? []}
        loading={loading}
        pagination={false}
      />

      <Modal
        title={editingItem ? `编辑供应商配置 - ${editingItem.name}` : '新建 AI 供应商配置'}
        open={modalVisible}
        width={620}
        onCancel={() => setModalVisible(false)}
        footer={[
          <Button
            key="test"
            loading={testingModal}
            onClick={handleTestInModal}
            style={{ float: 'left' }}
          >
            测试连通性
          </Button>,
          <Button key="cancel" onClick={() => setModalVisible(false)}>
            取消
          </Button>,
          <Button key="submit" type="primary" onClick={handleSave}>
            保存并应用
          </Button>
        ]}
      >
        <div style={{ marginBottom: 16 }}>
          <Space size="middle">
            <span style={{ fontSize: 12, color: '#666' }}>快速填充模板：</span>
            <Select
              style={{ width: 340 }}
              placeholder="选择常用大模型厂商预设..."
              onChange={applyPreset}
              options={PRESETS.map((p, idx) => ({ value: idx, label: p.label }))}
            />
          </Space>
        </div>

        {testResult && (
          <Alert
            style={{ marginBottom: 16 }}
            type={testResult.success ? 'success' : 'error'}
            message={testResult.message}
            showIcon
          />
        )}

        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="供应商名称"
            rules={[{ required: true, message: '请输入供应商名称' }]}
          >
            <Input placeholder="例如：DeepSeek 生产通道 / 通义百炼 / 聚合网关" />
          </Form.Item>

          <Form.Item
            name="protocol"
            label="通信协议"
            rules={[{ required: true, message: '请选择协议' }]}
          >
            <Radio.Group>
              <Radio value="openai-chat-completions">OpenAI Chat Completions (通用经典协议)</Radio>
              <Radio value="anthropic-messages">Anthropic Messages (Claude 原生)</Radio>
              <Radio value="openai-responses">OpenAI Responses API</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="baseUrl"
            label="接口基址 (Base URL)"
            rules={[{ required: true, message: '请输入接口基址' }]}
            tooltip="例如：https://api.openai.com/v1 或 https://api.deepseek.com/v1"
          >
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>

          <Form.Item
            name="apiKey"
            label="API Key 凭据"
            rules={editingItem ? [] : [{ required: true, message: '新建配置必须输入 API Key' }]}
            tooltip={
              editingItem ? '留空表示沿用数据库中已保存的密匙' : '请输入服务提供方签发的 API Key'
            }
          >
            <Input.Password
              placeholder={editingItem?.hasApiKey ? '已设置（留空保留原 Key）' : 'sk-...'}
            />
          </Form.Item>

          <Form.Item
            name="model"
            label="模型标识 (Model)"
            rules={[{ required: true, message: '请输入模型标识' }]}
            tooltip="例如 deepseek-chat、gpt-4o-mini、qwen-plus 等"
          >
            <Input placeholder="例如 deepseek-chat" />
          </Form.Item>

          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="temperature"
              label="采样温度 (0.0~2.0)"
              tooltip="生成随机性，找房助手推荐设 0.7"
            >
              <InputNumber min={0} max={2} step={0.1} style={{ width: 130 }} />
            </Form.Item>

            <Form.Item
              name="maxTokens"
              label="最大 Token 预算"
              tooltip="单轮生成上限，推理模型包含思维链长度，建议 ≥ 4096"
            >
              <InputNumber min={256} max={32768} step={512} style={{ width: 140 }} />
            </Form.Item>

            <Form.Item
              name="timeoutSeconds"
              label="上游超时 (秒)"
              tooltip="单次请求大模型上游的最长等待时间，默认 60s"
            >
              <InputNumber min={10} max={600} step={10} style={{ width: 130 }} />
            </Form.Item>
          </Space>

          <Form.Item
            name="userAgent"
            label="自定义 User-Agent 请求头 (可选)"
            tooltip="部分聚合通道或云厂商 WAF 对默认 Java/OkHttp 客户端有访问限制，可填入常见浏览器或客户端标识"
          >
            <Input placeholder="留空使用网关默认 User-Agent" />
          </Form.Item>

          <Form.Item
            name="customHeaders"
            label="自定义扩展 HTTP 请求头 (JSON 格式，可选)"
            tooltip='适用于特定厂商/代理网关要求的额外 Header（如 OpenCode 要求 {"x-opencode-session": "..."}），留空或填 {} 表示无额外头'
            rules={[
              {
                validator: async (_, value) => {
                  if (!value || !value.trim()) return
                  try {
                    const parsed = JSON.parse(value)
                    if (typeof parsed !== 'object' || Array.isArray(parsed) || parsed === null) {
                      throw new Error('必须为有效的 JSON 对象')
                    }
                  } catch {
                    throw new Error(
                      '请输入合法的 JSON 对象字符串，例如 {"x-opencode-session": "xxx"}'
                    )
                  }
                }
              }
            ]}
          >
            <Input.TextArea
              rows={3}
              placeholder={'例如：{\n  "x-opencode-session": "my-session-uuid-123"\n}'}
            />
          </Form.Item>

          <Form.Item name="isActive" label="保存后立即设为当前生效配置" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
