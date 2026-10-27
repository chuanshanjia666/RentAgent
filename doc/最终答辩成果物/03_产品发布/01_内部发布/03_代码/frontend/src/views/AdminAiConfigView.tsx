import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
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

/** 常用大语言模型 API 预设模板 */
const LLM_PRESETS = [
  {
    label: '大工 AI 网关 (DeepSeek-V4-Flash)',
    name: '大工 AI 网关',
    protocol: 'openai-chat-completions',
    baseUrl: 'http://aigw.dlut.edu.cn/v1',
    model: 'DeepSeek-V4-Flash-0731-W8A8',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutSeconds: 60,
    userAgent: 'RentAgent-Platform/1.0',
    customHeaders: ''
  },
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
  }
]

/** 常用词嵌入（Embedding）模型 API 预设模板 */
const EMBEDDING_PRESETS = [
  {
    label: '大工 AI 网关 (BGE-M3 稠密向量嵌入)',
    name: '大工 AI 网关 (BGE-M3)',
    protocol: 'openai-chat-completions',
    baseUrl: 'http://aigw.dlut.edu.cn/v1',
    model: 'bge-m3',
    timeoutSeconds: 30,
    userAgent: 'RentAgent-Platform/1.0',
    customHeaders: ''
  },
  {
    label: 'OpenAI (text-embedding-3-small 官方)',
    name: 'OpenAI Embedding',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://api.openai.com/v1',
    model: 'text-embedding-3-small',
    timeoutSeconds: 30
  },
  {
    label: '通义千问 (text-embedding-v3 百炼兼容)',
    name: '通义千问 Embedding',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'text-embedding-v3',
    timeoutSeconds: 30
  },
  {
    label: '智谱 AI (Embedding-3 兼容模式)',
    name: '智谱 Embedding',
    protocol: 'openai-chat-completions',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    model: 'embedding-3',
    timeoutSeconds: 30
  }
]

export default function AdminAiConfigView() {
  const [overview, setOverview] = useState<AiConfigOverviewVO | null>(null)
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'LLM' | 'EMBEDDING'>('LLM')
  const [modalVisible, setModalVisible] = useState(false)
  const [modalCategory, setModalCategory] = useState<'LLM' | 'EMBEDDING'>('LLM')
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

  function openCreate(category: 'LLM' | 'EMBEDDING') {
    setEditingItem(null)
    setModalCategory(category)
    setTestResult(null)
    form.resetFields()
    form.setFieldsValue({
      category,
      protocol: 'openai-chat-completions',
      model: category === 'EMBEDDING' ? 'bge-m3' : 'DeepSeek-V4-Flash-0731-W8A8',
      temperature: 0.7,
      maxTokens: 4096,
      timeoutSeconds: category === 'EMBEDDING' ? 30 : 60,
      customHeaders: '',
      isActive:
        overview?.configs.filter(c => (c.category || 'LLM') === category && c.isActive === 1)
          .length === 0
    })
    setModalVisible(true)
  }

  function openEdit(item: AiConfigVO) {
    const cat = item.category || 'LLM'
    setEditingItem(item)
    setModalCategory(cat)
    setTestResult(null)
    form.resetFields()
    form.setFieldsValue({
      name: item.name,
      category: cat,
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
    const list = modalCategory === 'EMBEDDING' ? EMBEDDING_PRESETS : LLM_PRESETS
    const p = list[presetIndex]
    if (!p) return
    form.setFieldsValue({
      name: p.name,
      protocol: p.protocol,
      baseUrl: p.baseUrl,
      model: p.model,
      timeoutSeconds: p.timeoutSeconds,
      userAgent: p.userAgent ?? '',
      customHeaders: p.customHeaders ?? ''
    })
    if ('temperature' in p) {
      form.setFieldValue('temperature', p.temperature)
    }
    if ('maxTokens' in p) {
      form.setFieldValue('maxTokens', p.maxTokens)
    }
    message.info(`已填充「${p.label}」参数模板，请输入 API Key`)
  }

  async function handleSave() {
    try {
      const values = await form.validateFields()
      values.category = modalCategory
      if (editingItem) {
        await http.put(`/admin/ai-configs/${editingItem.id}`, values)
        message.success('供应商配置已更新')
      } else {
        await http.post('/admin/ai-configs', values)
        message.success('新供应商配置已保存并入库')
      }
      setModalVisible(false)
      loadData()
    } catch {
      /* 验证或请求异常 */
    }
  }

  async function handleActivate(id: number) {
    try {
      await http.patch(`/admin/ai-configs/${id}/activate`)
      message.success('已切换当前生效供应商')
      loadData()
    } catch {
      /* 忽略 */
    }
  }

  async function handleDeactivate() {
    try {
      await http.patch('/admin/ai-configs/deactivate')
      message.info('已停用当前生效供应商，回退为配置文件默认')
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
      message.success('已成功从后端配置文件导入大模型配置并启用')
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
        category: item.category,
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
        category: modalCategory,
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
      /* 校验错误 */
    } finally {
      setTestingModal(false)
    }
  }

  const columns: ColumnsType<AiConfigVO> = [
    {
      title: '状态',
      dataIndex: 'isActive',
      width: 100,
      render: (val: number) =>
        val === 1 ? <Tag color="success">当前生效</Tag> : <Tag color="default">未激活</Tag>
    },
    {
      title: '供应商名称',
      dataIndex: 'name',
      width: 160,
      render: (text: string, r) => (
        <div>
          <span style={{ fontWeight: 600 }}>{text}</span>
          {r.isActive === 1 && (
            <Tag color="cyan" style={{ marginLeft: 6, fontSize: 11 }}>
              运行中
            </Tag>
          )}
        </div>
      )
    },
    {
      title: '通信协议',
      dataIndex: 'protocol',
      width: 130,
      render: (p: string) => {
        if (p === 'anthropic-messages') return <Tag color="purple">Claude 原生</Tag>
        if (p === 'openai-responses') return <Tag color="orange">Responses API</Tag>
        return <Tag color="blue">OpenAI 兼容</Tag>
      }
    },
    {
      title: '接口基址 (Base URL)',
      dataIndex: 'baseUrl',
      ellipsis: true,
      render: (url: string) => (
        <Tooltip title={url} placement="topLeft">
          <Text code style={{ maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {url}
          </Text>
        </Tooltip>
      )
    },
    {
      title: activeTab === 'EMBEDDING' ? '嵌入模型 (Embedding)' : '语言模型 (LLM)',
      dataIndex: 'model',
      width: 300,
      render: (m: string) => (
        <Tooltip title={m} placement="topLeft">
          <Tag
            color={activeTab === 'EMBEDDING' ? 'geekblue' : 'cyan'}
            style={{
              maxWidth: '100%',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              verticalAlign: 'middle'
            }}
          >
            <Text code style={{ color: 'inherit' }}>
              {m}
            </Text>
          </Tag>
        </Tooltip>
      )
    },
    {
      title: 'API Key 凭据',
      dataIndex: 'maskedApiKey',
      width: 140,
      render: (key: string, r) =>
        r.hasApiKey ? <Text code>{key}</Text> : <Tag color="red">未设置</Tag>
    },
    {
      title: '参数',
      width: 110,
      render: (_, r) => (
        <span style={{ fontSize: 12, color: '#657771' }}>
          {r.category === 'EMBEDDING'
            ? `${r.timeoutSeconds}s 超时`
            : `T:${r.temperature} | ${r.timeoutSeconds}s`}
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
      width: 260,
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
  const activeEmbSource = overview?.activeEmbeddingSource ?? 'NONE'
  const activeEmbDescribe = overview?.activeEmbeddingDescribe ?? 'none'

  const currentConfigs = overview?.configs.filter(c => (c.category || 'LLM') === activeTab) ?? []

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
            AI 供应商配置
          </h2>
          <p style={{ margin: 0, color: '#657771', fontSize: 13 }}>
            支持分别配置大语言模型（LLM）与词嵌入模型（Embedding）的接口地址、密钥及生效状态。
          </p>
        </div>
        <Space>
          <Button onClick={handleImportFromFile}>从配置文件导入大模型</Button>
          <Button type="primary" onClick={() => openCreate(activeTab)}>
            {activeTab === 'EMBEDDING' ? '+ 新建词嵌入供应商' : '+ 新建大语言模型供应商'}
          </Button>
        </Space>
      </div>

      <Card style={{ marginBottom: 16 }} bodyStyle={{ padding: 14 }}>
        <Row gutter={[16, 12]}>
          <Col xs={24} md={12}>
            <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 6 }}>
              <span style={{ fontWeight: 600 }}>🤖 大语言模型 (LLM)：</span>
              {activeSource === 'DATABASE' && <Tag color="success">数据库生效</Tag>}
              {activeSource === 'CONFIG_FILE' && <Tag color="warning">配置/环境变量</Tag>}
              {activeSource === 'NONE' && <Tag color="error">未接入 (4001)</Tag>}
              <Text code strong style={{ wordBreak: 'break-all' }}>
                {activeDescribe}
              </Text>
            </div>
          </Col>
          <Col xs={24} md={12}>
            <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 6 }}>
              <span style={{ fontWeight: 600 }}>🧠 词嵌入模型 (RAG)：</span>
              {activeEmbSource === 'DATABASE' && <Tag color="success">数据库生效</Tag>}
              {activeEmbSource === 'CONFIG_FILE' && <Tag color="warning">配置/环境变量</Tag>}
              {activeEmbSource === 'NONE' && <Tag color="error">未配置 (降级关键词)</Tag>}
              <Text code strong style={{ wordBreak: 'break-all' }}>
                {activeEmbDescribe}
              </Text>
            </div>
          </Col>
        </Row>
      </Card>

      <Tabs
        activeKey={activeTab}
        onChange={k => setActiveTab(k as 'LLM' | 'EMBEDDING')}
        type="card"
        items={[
          {
            key: 'LLM',
            label: `🤖 大语言模型供应商 (Chat / Completions) (${overview?.configs.filter(c => (c.category || 'LLM') === 'LLM').length ?? 0})`
          },
          {
            key: 'EMBEDDING',
            label: `🧠 词嵌入模型供应商 (Embedding / RAG) (${overview?.configs.filter(c => c.category === 'EMBEDDING').length ?? 0})`
          }
        ]}
      />

      <Table
        rowKey="id"
        columns={columns}
        dataSource={currentConfigs}
        loading={loading}
        pagination={false}
      />

      <Modal
        title={
          editingItem
            ? `编辑${modalCategory === 'EMBEDDING' ? '词嵌入' : '大语言模型'}供应商 - ${editingItem.name}`
            : `新建${modalCategory === 'EMBEDDING' ? '词嵌入' : '大语言模型'}供应商配置`
        }
        open={modalVisible}
        width={620}
        onCancel={() => setModalVisible(false)}
        footer={[
          <Button key="cancel" onClick={() => setModalVisible(false)}>
            取消
          </Button>,
          <Button key="test" loading={testingModal} onClick={handleTestInModal}>
            连通性测试
          </Button>,
          <Button key="submit" type="primary" onClick={handleSave}>
            保存并入库
          </Button>
        ]}
      >
        {testResult && (
          <Alert
            style={{ marginBottom: 16 }}
            type={testResult.success ? 'success' : 'warning'}
            showIcon
            message={testResult.message}
          />
        )}

        <Form form={form} layout="vertical">
          <Form.Item label="常用服务商预设模板">
            <Select
              placeholder="选择预设自动填充端点与模型参数"
              onChange={applyPreset}
              options={(modalCategory === 'EMBEDDING' ? EMBEDDING_PRESETS : LLM_PRESETS).map(
                (p, idx) => ({
                  value: idx,
                  label: p.label
                })
              )}
            />
          </Form.Item>

          <Form.Item
            name="name"
            label="供应商配置名称"
            rules={[{ required: true, message: '请输入配置名称' }]}
          >
            <Input
              placeholder={
                modalCategory === 'EMBEDDING'
                  ? '例如：大工 AI 网关 (BGE-M3 嵌入)'
                  : '例如：DeepSeek 官方 API'
              }
            />
          </Form.Item>

          <Form.Item
            name="protocol"
            label="通信协议"
            rules={[{ required: true, message: '请选择协议' }]}
            tooltip={
              modalCategory === 'EMBEDDING'
                ? '词嵌入通常使用标准 OpenAI 兼容的 /v1/embeddings 协议'
                : '绝大多数国产聚合网关与 OpenAI、DeepSeek 均兼容 openai-chat-completions'
            }
          >
            <Radio.Group>
              <Radio.Button value="openai-chat-completions">OpenAI 兼容</Radio.Button>
              {modalCategory === 'LLM' && (
                <>
                  <Radio.Button value="anthropic-messages">Claude 原生</Radio.Button>
                  <Radio.Button value="openai-responses">Responses API</Radio.Button>
                </>
              )}
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="baseUrl"
            label="接口基址 (Base URL)"
            rules={[{ required: true, message: '请输入接口基址' }]}
            tooltip="例如：http://aigw.dlut.edu.cn/v1 或 https://api.openai.com/v1"
          >
            <Input placeholder="http://aigw.dlut.edu.cn/v1" />
          </Form.Item>

          <Form.Item
            name="apiKey"
            label="API Key 凭据"
            rules={editingItem ? [] : [{ required: true, message: '新建配置必须输入 API Key' }]}
            tooltip={
              editingItem ? '留空表示沿用数据库中已保存的密钥' : '请输入服务提供方签发的 API Key'
            }
          >
            <Input.Password
              placeholder={editingItem?.hasApiKey ? '已设置（留空保留原 Key）' : 'sk-...'}
            />
          </Form.Item>

          <Form.Item
            name="model"
            label={
              modalCategory === 'EMBEDDING'
                ? '词嵌入模型标识 (Embedding Model)'
                : '大语言模型标识 (LLM Model)'
            }
            rules={[{ required: true, message: '请输入模型标识' }]}
            tooltip={
              modalCategory === 'EMBEDDING'
                ? '例如 bge-m3、text-embedding-3-small、text-embedding-v3 等'
                : '例如 deepseek-chat、DeepSeek-V4-Flash-0731-W8A8、gpt-4o-mini 等'
            }
          >
            <Input placeholder={modalCategory === 'EMBEDDING' ? 'bge-m3' : 'deepseek-chat'} />
          </Form.Item>

          {modalCategory === 'LLM' && (
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
                tooltip="单轮生成上限，推理模型建议 ≥ 4096"
              >
                <InputNumber min={256} max={32768} step={512} style={{ width: 140 }} />
              </Form.Item>

              <Form.Item
                name="timeoutSeconds"
                label="超时 (秒)"
                tooltip="单次请求最长等待时间，默认 60s"
              >
                <InputNumber min={10} max={600} step={10} style={{ width: 130 }} />
              </Form.Item>
            </Space>
          )}

          {modalCategory === 'EMBEDDING' && (
            <Form.Item
              name="timeoutSeconds"
              label="词嵌入请求超时 (秒)"
              tooltip="单次向量化请求超时时间，默认 30s"
            >
              <InputNumber min={5} max={180} step={5} style={{ width: 150 }} />
            </Form.Item>
          )}

          <Form.Item
            name="userAgent"
            label="自定义 User-Agent 请求头 (可选)"
            tooltip="部分聚合通道对默认客户端有限制，可填入常见浏览器标识"
          >
            <Input placeholder="留空使用网关默认 User-Agent" />
          </Form.Item>

          <Form.Item
            name="customHeaders"
            label="自定义扩展 HTTP 请求头 (JSON 格式，可选)"
            tooltip="适用于特定代理网关要求的额外 Header，留空或填 {}"
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
              rows={2}
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
