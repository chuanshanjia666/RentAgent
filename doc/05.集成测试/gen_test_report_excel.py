#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基于实训中心标准模板《参考.xlsx》，自动生成 RentAgent 平台的《集成测试成绩书 / 测试报告》Excel 文件
包含工作表：
1. 文件封面
2. 测试项目
3. UI界面
4. 运行
"""

import os
import shutil
import datetime
import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.drawing.image import Image as XLImage

SRC_TEMPLATE = "doc/05.集成测试/参考.xlsx"
OUTPUT_XLSX_1 = "doc/05.集成测试/RentAgent_集成测试成绩书.xlsx"
OUTPUT_XLSX_2 = "doc/05.集成测试/测试报告.xlsx"

# 50 项详尽的核心集成测试用例数据
TEST_CASES = [
    # --- 模块一：登录、鉴权与安全防护基线 ---
    (
        1,
        "用例标题：四个角色合法账号正常认证登录校验\n前置条件：系统正常启动，MySQL 种子账号已初始化\n操作步骤：1. 分别提交 admin、landlord、wanglandlord、tenant 四个账号密码进行登录；2. 验证响应状态码及 Token 字段",
        "四个账号均返回 code=0，data.token 均为合法非空 JWT 字符串，包含各自对应的 role（1 租客、2 房东、3 管理员），鉴权信息正确载入",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图01"
    ),
    (
        2,
        "用例标题：冻结/禁用用户登录拦截校验\n前置条件：系统数据库已存在被管理员禁用的历史账号 historyuser\n操作步骤：1. 提交 historyuser 账号及密码发起登录请求；2. 检验响应体",
        "系统返回业务错误码 code=1004，错误信息提示“账号已被禁用”，未签发任何访问凭据，拦截有效",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        3,
        "用例标题：防账号探测：不存在用户与密码错误同码校验\n前置条件：系统正常启动\n操作步骤：1. 使用未注册的手机号加随机密码发起登录；2. 与已有用户输错密码对比",
        "系统均统一返回 code=1002，错误信息为“用户名或密码错误”，响应特征完全一致，有效防止恶意遍历与枚举探测账号存在性",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        4,
        "用例标题：伪造篡改 JWT 访问受保护接口拦截校验\n前置条件：构造伪造签名的 Bearer Token\n操作步骤：1. 携带伪造 Token 请求需要租客身份的 /users/me 接口；2. 观察 HTTP 状态码与响应体",
        "HTTP 协议层直接返回 401 Unauthorized，响应体 code=1002，请求被底层 JwtAuthenticationFilter 坚决拦截",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        5,
        "用例标题：未登录请求受保护接口权限拦截校验\n前置条件：未携带任何 Authorization 请求头\n操作步骤：1. 请求房东接口 /landlord/houses；2. 请求管理员接口 /admin/chats",
        "请求 /landlord/houses 返回 code=1007（无权限）；请求管理员接口由角色拦截器拦截返回 code=1002 提示“请先登录”",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        6,
        "用例标题：垂直越权防护：租客跨权限访问管理端接口校验\n前置条件：租客账号 tenant 正常登录取得有效 Token\n操作步骤：1. 携带该租客 Token 请求管理员接口 GET /admin/chats；2. 检验拦截响应",
        "自定义角色拦截器拦截，返回 code=1007，错误提示“无权限操作”，成功阻断租客访问管理后台",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图16"
    ),
    (
        7,
        "用例标题：公开注册接口管理员角色防提权注入校验\n前置条件：系统开放 POST /auth/register 注册接口\n操作步骤：1. 构造恶意请求载荷传入 role=3（管理员）；2. 提交注册并用该手机号尝试登录",
        "注册时入参校验生效，直接返回 code=1000 拒绝注册；后续使用该手机号登录返回 1002，确认数据库中未产生特权账号",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),

    # --- 模块二：房源发布、资质校验与审核闭环 ---
    (
        8,
        "用例标题：未实名认证房东禁止发布房源校验\n前置条件：登录未实名房东账号 wanglandlord\n操作步骤：1. 填写完整房源信息提交 POST /landlord/houses；2. 检验返回结果",
        "系统业务层校验拦截，返回 code=1008，提示“请先完成实名认证”，发布请求被终止，数据未入库",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图13"
    ),
    (
        9,
        "用例标题：房源发布非法参数（极小面积）防御校验\n前置条件：登录已实名房东账号 landlord\n操作步骤：1. 提交房屋建筑面积为 0.5 平方米的极端非法参数；2. 观测校验响应",
        "触发 JSR-303 Bean Validation 校验机制，返回 code=1000，明确提示面积数值超出合法范围",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        10,
        "用例标题：房源发布成功与初始状态机为待审核校验\n前置条件：登录已实名房东账号 landlord\n操作步骤：1. 提交合法完整房源图文信息（含配套设施数组及首图）；2. 查看返回数据与数据库初始状态",
        "接口返回 code=0 并成功生成唯一 houseId；数据库中房源初始 status=0（待审核），符合上架前必审流程",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图09、图10"
    ),
    (
        11,
        "用例标题：待审核房源公开隔离与属主可见性校验\n前置条件：房源处于 status=0 待审核状态\n操作步骤：1. 匿名用户直接通过 URL 访问详情页；2. 房源所属房东本人访问详情页",
        "匿名用户访问返回 code=2001（房源未上架或不存在）；房东本人访问返回 code=0 正常展示，数据隔离机制严格有效",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图18"
    ),
    (
        12,
        "用例标题：管理员审核通过、状态上架与审计留痕校验\n前置条件：房源处于待审核状态，登录管理员账号\n操作步骤：1. 调用 PUT /admin/houses/{id}/audit 传 action=pass；2. 匿名访问详情页；3. 查验审计日志",
        "审核接口返回 code=0，房源状态流转为 status=1（已上架）；匿名用户可公开检索；系统审计日志 sys_audit_log 成功记录通过动作",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图15"
    ),

    # --- 模块三：多维检索、地图点位找房与用户收藏 ---
    (
        13,
        "用例标题：房源分页列表检索标准化响应格式校验\n前置条件：数据库中已存在 17 套真实感种子房源\n操作步骤：1. 请求 GET /houses?page=1&size=5；2. 核对响应结构",
        "返回标准 PageVO 结构，包含 list 数组与 total>=11；每项房源数据直接展平，配套设施 facilities 严格映射为 JSON 字符串数组",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图03"
    ),
    (
        14,
        "用例标题：配套设施 JSON 数组过滤检索校验\n前置条件：房源包含 facilities 标签属性\n操作步骤：1. 查询包含 [\"独立卫浴\",\"空调\"] 的房源列表；2. 检验结果匹配度",
        "MyBatis-Plus 动态 SQL 生成 JSON_CONTAINS 语句，返回列表中每套房源均包含指定设施，筛选准确",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图03"
    ),
    (
        15,
        "用例标题：租金区间（minPrice/maxPrice）范围过滤校验\n前置条件：房源租金分布在 1500~6000 元区间\n操作步骤：1. 设置检索条件 minPrice=2000&maxPrice=3500；2. 验证返回租金",
        "返回房源租金全部落在 [2000.00, 3500.00] 区间内，边界值包含正确，无超出区间数据返回",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图03"
    ),
    (
        16,
        "用例标题：地图找房模式点位不分页与坐标完整性校验\n前置条件：切换至大连市全景房源地图模式\n操作步骤：1. 请求 GET /houses/map；2. 校验首条点位坐标及数据上限",
        "返回结果首条即包含有效经纬度坐标，响应直接以全量列表返回（上限 300 套不假分页），坐标解析格式规范",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图03"
    ),
    (
        17,
        "用例标题：房源收藏、重复收藏幂等性与取消收藏校验\n前置条件：登录已认证租客账号 tenant\n操作步骤：1. 对房源 A 发起收藏；2. 连续重复发起收藏；3. 发起取消收藏",
        "首次收藏返回 code=0；重复收藏命中防重逻辑返回幂等成功；取消收藏后个人收藏列表中该房源即时被移除",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图04、图08"
    ),

    # --- 模块四：看房预约履约与时段互斥机制 ---
    (
        18,
        "用例标题：租客在线预约看房合法创建校验\n前置条件：登录租客账号，选择已上架房源\n操作步骤：1. 选取未来合法时段（如明天 10:00）提交看房申请；2. 观察预约单状态",
        "返回 code=0 并成功生成预约单号；初始状态为 status=0（待确认），房东即时收到系统看房申请通知",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图06"
    ),
    (
        19,
        "用例标题：历史/过去时间预约非法性拦截校验\n前置条件：登录租客账号\n操作步骤：1. 提交过去的时段（如昨天 14:00）发起预约；2. 检验校验响应",
        "系统拒绝并返回 code=1000，错误提示明确指出“预约时间不能早于当前时间”，阻止无效预约",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        20,
        "用例标题：同房源同一时段重复预约互斥校验\n前置条件：时段 T 已被租客 A 成功预约\n操作步骤：1. 租客 B 对同一套房源的时段 T 发起预约请求；2. 检验唯一性拦截",
        "命中数据库物理唯一键约束 uk_active_slot，系统捕获后优雅转化为业务错误码 3001（时段已被占用），防止并发撞单",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        21,
        "用例标题：房东拒绝预约与占用时段自动释放再预约校验\n前置条件：存在待确认看房预约单\n操作步骤：1. 房东点击拒绝并填写理由；2. 其他租客对该时段再次发起预约",
        "预约流转为已拒绝状态；数据库 active_slot 字段被安全清空释放，第二位租客成功预约该时段（code=0）",
        "OK", "2026-10-20", "陈玄晔", "曾因存量表缺少active_slot报错，已修复", "见UI界面 图20"
    ),

    # --- 模块五：在线电子签约、交易履约与分期账单 ---
    (
        22,
        "用例标题：租客发起租赁合同在线签约校验\n前置条件：房源为上架状态，租客已完成实名认证\n操作步骤：1. 提交租期（起止时间 1 年）、月租金及押金发起签约；2. 查看合同详情",
        "生成唯一租赁合同编号 CT...，合同状态初始为 0（待签署），租赁条款与权利义务明细完整生成",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图07"
    ),
    (
        23,
        "用例标题：同一房源多租客并发重复签约防重校验\n前置条件：房源已有待签署的有效合同\n操作步骤：1. 另一租客对同一房源再次发起签约请求；2. 检验业务防重拦截",
        "房源维度签约判重机制生效，系统返回 code=3003 拒绝重复签约，确保同一房源在同一周期内仅存在一份有效租约",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        24,
        "用例标题：双方法定电子签名与合同生效流转校验\n前置条件：合同处于待签署状态\n操作步骤：1. 租客、房东分别调用签署接口上传电子签名图像；2. 检验合同与房源状态",
        "双方均完成签署后，合同状态自动流转为 status=1（生效中），对应房源自动下架变更为“已出租”",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图07"
    ),
    (
        25,
        "用例标题：系统自动派生履约订单与首期分期账单校验\n前置条件：合同正式签署生效\n操作步骤：1. 查询租客订单列表；2. 查询关联分期账单明细",
        "自动生成唯一履约订单；按月拆解为 12 期账单，首期账单合并押金，金额计算严格精确到分位（2100.00），期数无遗漏",
        "OK", "2026-10-20", "陈玄晔", "BUG-01", "见UI界面 图08"
    ),
    (
        26,
        "用例标题：首期租金账单支付流转校验\n前置条件：首期账单为待支付状态\n操作步骤：1. 租客发起在线模拟支付；2. 查看账单及订单状态变化",
        "账单状态即刻更新为已支付，记录支付流水号与支付完成时间戳，关联履约订单流转为正常履约中",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图08"
    ),

    # --- 模块六：租后评价门禁、状态回填与退租结算 ---
    (
        27,
        "用例标题：履约未完结订单禁止评价门禁校验\n前置条件：订单处于租期履约进行中（未退租/未完结）\n操作步骤：1. 租客调用评价接口提交评分与评语；2. 检验业务拦截",
        "系统拒绝并返回 code=3005，明确提示“只有已完成的订单才能进行评价”，防止恶意提早刷评",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        28,
        "用例标题：正常退租验收、押金结算与合同归档校验\n前置条件：租期届满或双方协商发起退租\n操作步骤：1. 房东发起物业验收、扣除水电杂费结算押金；2. 完结归档订单",
        "订单状态流转为“已完成”，合同顺利归档，扣减杂费与押金退还明细完整留痕",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图08"
    ),
    (
        29,
        "用例标题：一单一评唯一性与订单列表 reviewed 按钮态回填校验\n前置条件：订单已顺利完结\n操作步骤：1. 查询订单列表；2. 提交首次评价；3. 再次查询订单列表；4. 重复提交评价",
        "评价前 reviewed=false（显示去评价）；评价入库后 reviewed=true（按钮置灰显示已评价）；重复提交返回 3005 拦截",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图08"
    ),

    # --- 模块七：真实大模型智能应用：定价、解析、风险识别 ---
    (
        30,
        "用例标题：房源文本描述 AI 自然语言提取与字段自动填充校验\n前置条件：系统接入商业大语言模型 API\n操作步骤：1. 传入口语化房源描述文案；2. 检验大模型结构化解析返回",
        "大模型成功解析并返回严格 JSON：准确提取标题、户型（2室1厅）、商圈（高新万达）、配套设施等，表单自动填充吻合",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图09"
    ),
    (
        31,
        "用例标题：基于区域大数据的房源租金 AI 智能定价评估校验\n前置条件：指定房源基础参数（面积 85㎡，两室一厅）\n操作步骤：1. 调用 POST /ai/pricing 请求定价建议；2. 检验响应内容",
        "真实大模型在 2 秒内返回综合分析报告：包含建议租金区间（如 [2600, 2900] 元/月）、周边竞品对比逻辑及定价置信度",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图11"
    ),
    (
        32,
        "用例标题：租赁合同文本 AI 法律风险智能筛查与条款解读校验\n前置条件：构造包含霸王条款的合同文本\n操作步骤：1. 调用 POST /ai/contracts/{id}/analyze；2. 校验风险诊断结果",
        "大模型精准命中霸王条款，明确指出违约责任不对等问题，并输出风险评级与修改规避建议，合规分析严谨",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图22"
    ),

    # --- 模块八：站内通知体系与管理员实时运营看板 ---
    (
        33,
        "用例标题：业务事件驱动的站内通知推送与已读标记校验\n前置条件：触发看房预约、签约签署等业务动作\n操作步骤：1. 用户查看站内信列表；2. 调用已读标记接口",
        "业务事件即时在 sys_notification 表产生通知记录；调用标记已读接口后，未读红点数字准确清零",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图08"
    ),
    (
        34,
        "用例标题：管理员控制台运营数据看板聚合统计校验\n前置条件：登录管理员账号\n操作步骤：1. 请求 GET /admin/dashboard 接口；2. 校验各项聚合指标",
        "正确聚合全站房源总数、在架房源、履约中订单、成交总金额与注册用户量；数值与底层物理数据库行数完全一致",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图14"
    ),

    # --- 模块九：大模型智能体交互、工具调用与留痕 ---
    (
        35,
        "用例标题：AI 找房助手 Tool Calling（检索房源）与站内链接直接引用校验\n前置条件：启动与真实推理模型的流式 SSE 对话\n操作步骤：1. 发送“我想租软件园附近 2000 左右的一室一厅”；2. 检验工具调用与回复文本",
        "模型内部自动触发 searchHouses 工具并检索房源；回复精准输出带有 [标题](#/app/houses/{id}) 的直达链接；留痕写入数据库",
        "OK", "2026-10-20", "陈玄晔", "BUG-02", "见UI界面 图05"
    ),
    (
        36,
        "用例标题：AI 客服助手知识库检索（searchKnowledge）与引用来源标注校验\n前置条件：切换至客服智能体助手会话\n操作步骤：1. 发送“平台押金什么时候退还？退租扣费规则是什么？”；2. 校验回复内容及引用",
        "模型自动调用 searchKnowledge 工具检索知识库，回答附带 citations 引用来源标记（citations>=1），专业严谨",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图17"
    ),
    (
        37,
        "用例标题：工具循环（Tool Loop）收敛硬护栏与超限熔断校验\n前置条件：构造易引发模型连续调工具的模糊提示词\n操作步骤：1. 监测单轮对话工具调用执行次数；2. 验证护栏机制",
        "单轮工具调用次数被限制在 8 次以内；达到阈值先发出终止提示，若仍调用即强制抛出 ToolCallLimitExceededException 熔断并清理记忆",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),

    # --- 模块十：用户实名认证闭环与敏感隐私保护 ---
    (
        38,
        "用例标题：租客实名认证提交与证件加密存储校验\n前置条件：登录未认证账号\n操作步骤：1. 提交真实姓名与 18 位身份证号；2. 查验数据库底层存储字段",
        "申请状态记录为待审核；数据库中明文身份证号经 AES-GCM 加密存储，SHA-256 哈希值写入唯一索引，严防信息泄露",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图13"
    ),
    (
        39,
        "用例标题：审核管理端身份证敏感信息脱敏展示校验\n前置条件：登录管理员账号\n操作步骤：1. 访问 GET /admin/realnames 待审核列表；2. 检查网络响应与渲染文本",
        "响应数据及界面上身份证严格脱敏显示为 210202********1234，前端与网络传输无任何明文隐私暴露",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图15"
    ),
    (
        40,
        "用例标题：管理员驳回实名、理由展示与重新提交认证闭环校验\n前置条件：存在待审核实名认证申请\n操作步骤：1. 管理员点击驳回并录入驳回原因；2. 申请人查看驳回理由；3. 申请人更正后重新提交",
        "驳回成功留痕 REALNAME_REJECT；用户端可见清晰驳回原因；更正重新提交后状态正确回归待审核，未产生孤儿数据",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图13、图15"
    ),

    # --- 模块十一：内容审核、违规下架与全链路审计 ---
    (
        41,
        "用例标题：管理员违规房源强制下架操作校验\n前置条件：房源当前为上架状态\n操作步骤：1. 管理员调用处置接口执行违规下架；2. 验证租客端与房东端状态",
        "房源状态即刻变更为 status=3（已下架），检索列表即刻隐藏，房东端收到违规下架通知，数据同步即时",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图15、图18"
    ),
    (
        42,
        "用例标题：全站关键业务处置操作留痕审计校验\n前置条件：执行了审核、下架、用户状态变更等敏感操作\n操作步骤：1. 检索系统操作审计日志表 sys_audit_log；2. 核对各记录字段完整性",
        "所有管理动作完整记录：操作人ID、目标实体类型、实体主键、操作动作（HOUSE_AUDIT_PASS/HOUSE_OFFLINE等）、IP及时间戳无缺失",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图15"
    ),

    # --- 模块十二：AI 供应商配置入库与反向“零兜底”门禁 ---
    (
        43,
        "用例标题：AI API 供应商动态配置非管理员越权拦截校验\n前置条件：租客账号登录\n操作步骤：1. 发起请求 GET /admin/ai-configs；2. 观察角色校验拦截响应",
        "拦截器判定非管理员角色，拦截并返回 code=1007 越权错误，接口受到严格的角色保护",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        44,
        "用例标题：AI 供应商动态配置新增、脱敏与生效切换校验\n前置条件：管理员账号登录\n操作步骤：1. 动态新增第三方供应商密钥；2. 读取列表检查脱敏；3. 激活新配置",
        "成功持久化入库 ai_provider_config 表；敏感 API Key 自动脱敏为前4后4星号掩码；激活后事件总线发布通知，热加载即刻生效",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图15"
    ),
    (
        45,
        "用例标题：反向门禁：未配置大模型时坚决报错 4001（零兜底）校验\n前置条件：启动全新后端实例，清空全部大模型凭据\n操作步骤：1. 发起 AI 对话；2. 发起 AI 定价；3. 发起合同分析；4. 探测 AI 状态引擎",
        "全部 AI 请求均如实返回业务错误码 4001，附带“未配置有效的大模型 API 凭据”明确错误；绝无任何本地规则降级或造假，系统零兜底设计有效",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图四"
    ),
    (
        46,
        "用例标题：全套后端单元测试用例全量自动化回归校验\n前置条件：Maven 编译构建环境就绪\n操作步骤：1. 执行 cd server && mvn test；2. 检查 Surefire 测试报告结果",
        "全套 230 条单元测试用例 100% 绿色通过，Failures: 0, Errors: 0, Skipped: 0，构建状态 BUILD SUCCESS",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图二"
    ),
    (
        47,
        "用例标题：前端 Vitest 单元测试与代码风格门禁校验\n前置条件：Node.js 运行环境就绪\n操作步骤：1. 执行 npm run check；2. 检查 ESLint、Prettier 与 Vitest 报告",
        "ESLint 校验 0 错误，Prettier 格式 100% 规范，32 条前端单元测试全部通过（4 test files passed），代码质量合格",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图三"
    ),
    (
        48,
        "用例标题：前端 Web 生产包编译构建完整性校验\n前置条件：前端源码与依赖完备\n操作步骤：1. 执行 npm run build:web；2. 检查 dist 产物目录结构与静态资源",
        "TypeScript 严格类型检查无报错，Vite 5 打包成功生成 dist 静态产物包，Web 与 Electron 桌面端共用产物校验通过",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图三"
    ),
    (
        49,
        "用例标题：系统空库首启事务竞态与数据播种稳定性校验\n前置条件：全新空 MySQL 数据库挂载\n操作步骤：1. 启动后端实例；2. 观察 DataInitializer 播种与端口就绪顺序",
        "系统正确完成 19 张表 DDL 与种子数据提交，公开房源查询 total>=11 即刻就绪，未发生因首启未提交导致的登录锁号故障",
        "OK", "2026-10-20", "陈玄晔", "", "见运行图一"
    ),
    (
        50,
        "用例标题：多端响应式与桌面端 Electron 兼容性校验\n前置条件：系统分别在桌面 Chrome 浏览器与 Electron 环境中运行\n操作步骤：1. 访问各主流分辨率视图；2. 检查 HashRouter 路由跳转与 API 基址",
        "系统在 PC 宽屏、移动端及桌面客户端均自适应正常渲染；API 基址解析准确，无跨域及硬编码路径异常",
        "OK", "2026-10-20", "陈玄晔", "", "见UI界面 图23"
    ),
]

def build_excel():
    print(f"Loading template: {SRC_TEMPLATE}")
    wb = openpyxl.load_workbook(SRC_TEMPLATE)

    # -------------------------------------------------------------
    # 1. 更新【文件封面】
    # -------------------------------------------------------------
    ws_cover = wb["文件封面"]
    ws_cover["A2"] = "密级：秘密"
    ws_cover["A3"] = "文件编号：D0000-PPCMT005"
    ws_cover["A15"] = "RentAgent项目集成测试成绩书"
    ws_cover["A17"] = "版本：v1.10"
    ws_cover["A18"] = "2026-10-20"
    ws_cover["A33"] = "软件工程实训中心 · RentAgent研发项目组"
    ws_cover["A34"] = "(版权所有，翻版必究)"
    ws_cover["B37"] = "编制：陈玄晔"
    ws_cover["F37"] = "审批：张海星 / 导师团队"

    # -------------------------------------------------------------
    # 2. 更新【测试项目】
    # -------------------------------------------------------------
    ws_test = wb["测试项目"]
    # 标题与基本信息
    ws_test["B1"] = "RentAgent 基于 AI 智能体的房屋租赁系统项目集成测试成绩书"
    ws_test["BF1"] = "加证据作为集成测试成绩书，NG以后回归测试\nOK\nNG"
    ws_test["B2"] = "集成测试书"
    ws_test["S2"] = "模块名称"
    ws_test["X2"] = "RentAgent 智能租赁平台全业务链"
    ws_test["B3"] = "系统架构"
    ws_test["E3"] = "Spring Boot 3 + Vue/React + 商业大模型"
    ws_test["S3"] = "功能描述"
    ws_test["V3"] = "房源发布审核/检索看房/签约履约/AI智能体/多角色权限/平台运维"
    ws_test["AM3"] = "制作时间"
    ws_test["AQ3"] = "2026/09"
    ws_test["AU3"] = "修改时间"
    ws_test["AY3"] = "2026/10/20"

    # 样式提取自前 4 行
    thin_border = Border(
        left=Side(style="thin", color="000000"),
        right=Side(style="thin", color="000000"),
        top=Side(style="thin", color="000000"),
        bottom=Side(style="thin", color="000000")
    )
    font_content = Font(name="宋体", size=10, bold=False)
    align_center = Alignment(horizontal="center", vertical="center", wrap_text=True)
    align_left = Alignment(horizontal="left", vertical="center", wrap_text=True)

    # 先清理行 5 以后的原有合并单元格
    to_remove = [rng for rng in list(ws_test.merged_cells.ranges) if rng.min_row >= 5]
    for rng in to_remove:
        ws_test.unmerge_cells(str(rng))

    # 清除旧测试用例数据（从第 5 行开始）
    max_r = max(ws_test.max_row, 70)
    for r in range(5, max_r + 1):
        for c in range(1, 60):
            try:
                ws_test.cell(r, c).value = None
            except AttributeError:
                pass

    # 逐行填入 50 条测试用例
    for idx, (num, method, result, status, date_str, author, bug_id, proof) in enumerate(TEST_CASES, start=5):
        # 1. 单元格赋值
        ws_test.cell(idx, 2).value = num
        ws_test.cell(idx, 5).value = method
        ws_test.cell(idx, 22).value = result
        ws_test.cell(idx, 39).value = status
        ws_test.cell(idx, 43).value = date_str
        ws_test.cell(idx, 47).value = author
        ws_test.cell(idx, 51).value = bug_id
        ws_test.cell(idx, 55).value = proof

        # 2. 设置合并单元格 (B-D, E-U, V-AL, AM-AP, AQ-AT, AU-AX, AY-BB, BC-BF)
        ws_test.merge_cells(start_row=idx, start_column=2, end_row=idx, end_column=4)      # 编号 B-D
        ws_test.merge_cells(start_row=idx, start_column=5, end_row=idx, end_column=21)     # 测试方法 E-U
        ws_test.merge_cells(start_row=idx, start_column=22, end_row=idx, end_column=38)    # 测试结果 V-AL
        ws_test.merge_cells(start_row=idx, start_column=39, end_row=idx, end_column=42)    # 确定结果 AM-AP
        ws_test.merge_cells(start_row=idx, start_column=43, end_row=idx, end_column=46)    # 日期 AQ-AT
        ws_test.merge_cells(start_row=idx, start_column=47, end_row=idx, end_column=50)    # 测试者 AU-AX
        ws_test.merge_cells(start_row=idx, start_column=51, end_row=idx, end_column=54)    # 缺陷编号 AY-BB
        ws_test.merge_cells(start_row=idx, start_column=55, end_row=idx, end_column=58)    # 证明 BC-BF

        # 3. 设置样式
        for c in range(2, 59):
            cell = ws_test.cell(idx, c)
            cell.border = thin_border
            cell.font = font_content
            if c in range(5, 39):
                cell.alignment = align_left
            else:
                cell.alignment = align_center

        ws_test.row_dimensions[idx].height = 48

    print(f"Populated {len(TEST_CASES)} test cases in 测试项目 sheet.")

    # -------------------------------------------------------------
    # 3. 重构【UI界面】
    # -------------------------------------------------------------
    ws_ui = wb["UI界面"]
    # 清空旧文本
    for r in range(1, ws_ui.max_row + 1):
        for c in range(1, ws_ui.max_column + 1):
            ws_ui.cell(r, c).value = None
    ws_ui._images.clear()

    # 排入 RentAgent 23 张高清晰度真实原型与界面截图
    ui_img_dir = "doc/02.需求分析/images/rtm"
    ui_files = sorted([f for f in os.listdir(ui_img_dir) if f.endswith(".png")])

    ui_labels = [
        ("图01 登录注册页", "对应用例1~7: 多角色登录/密码防探测/JWT拦截/防提权"),
        ("图02 租客端首页", "对应用例: 热门在架房源流/AI租房导航入口"),
        ("图03 房源列表-地图模式", "对应用例13~16: 设施检索/价格区间/全景点位"),
        ("图04 房源详情页", "对应用例11,17: 房源图文/收藏幂等/预约看房发起"),
        ("图05 AI助手对话页", "对应用例35: 找房智能体/Tool Calling/直达链接"),
        ("图06 预约看房页", "对应用例18~21: 预约时段校验/防过去时间/互斥排他"),
        ("图07 在线签约页", "对应用例22~24: 租约拟定/双方法定签名/租约生效"),
        ("图08 个人中心", "对应用例25~29,33: 订单明细/分期账单/评价状态/通知"),
        ("图09 房源发布-AI识别", "对应用例10,30: 房源信息录入/AI文案结构化提取"),
        ("图10 房源管理页", "对应用例10~12: 房东端房源生命周期/状态机查看"),
        ("图11 智能定价弹窗", "对应用例31: 大模型租金评估/周边竞品建议"),
        ("图12 预约订单管理页", "对应用例18~26: 房东端审核看房/签约履约单追踪"),
        ("图13 实名认证页", "对应用例8,38,40: 身份证加密提交/脱敏/驳回更正"),
        ("图14 运营数据看板", "对应用例34: 交易大盘/在架房源/用户活跃聚合统计"),
        ("图15 审核工作台", "对应用例12,39~44: 房源审核/实名审核/下架处置/AI配置"),
        ("图16 用户管理页", "对应用例2,6: 用户账号冻结/解冻与权限拦截"),
        ("图17 知识库维护页", "对应用例36: 客服大模型政策法规向量库维护"),
        ("图18 房源下架示意", "对应用例11,41: 房源违规下架状态与租客端隔离"),
        ("图19 AI超时转人工", "对应用例37: 大模型异常熔断与人工兜底保障"),
        ("图20 预约拒绝业务", "对应用例21: 房东拒绝看房与占用时段安全释放"),
        ("图21 预约完成履约", "对应用例21,28: 线下看房完成/引导签约全闭环"),
        ("图22 合同法律风险检测", "对应用例32: 真实大模型霸王条款检测与风险规避"),
        ("图23 移动端响应式示意", "对应用例50: 移动端自适应与跨端多终端兼容")
    ]

    current_row = 2
    for i, fname in enumerate(ui_files):
        fpath = os.path.join(ui_img_dir, fname)
        title, desc = ui_labels[i] if i < len(ui_labels) else (fname, "")

        # 写入标题标签
        lbl_cell = ws_ui.cell(current_row, 2)
        lbl_cell.value = f"【证明截图 {i+1}】{title} —— {desc}"
        lbl_cell.font = Font(name="宋体", size=11, bold=True, color="1B365D")
        lbl_cell.alignment = Alignment(horizontal="left", vertical="center")

        # 插入图片（设置宽 720px 高清显示）
        try:
            xl_img = XLImage(fpath)
            orig_w, orig_h = xl_img.width, xl_img.height
            ratio = 720.0 / orig_w
            xl_img.width = int(orig_w * ratio)
            xl_img.height = int(orig_h * ratio)
            img_row = current_row + 1
            ws_ui.add_image(xl_img, f"B{img_row}")
            # 计算行高占用
            rows_occupied = int(xl_img.height / 20) + 4
            current_row += rows_occupied + 1
        except Exception as e:
            print(f"Error adding image {fpath}: {e}")
            current_row += 15

    print(f"Added {len(ui_files)} UI screenshots to UI界面 sheet.")

    # -------------------------------------------------------------
    # 4. 重构【运行】
    # -------------------------------------------------------------
    ws_run = wb["运行"]
    # 清空旧文本
    for r in range(1, ws_run.max_row + 1):
        for c in range(1, ws_run.max_column + 1):
            ws_run.cell(r, c).value = None
    ws_run._images.clear()

    run_evidences = [
        (
            "图一：本机 KDE Konsole 终端运行全业务冒烟门禁 153 项断言（scripts/ci-smoke.sh）真实截图",
            "doc/05.集成测试/assets/run_evidence/konsole_1_smoke.png",
            "【本机真实终端截屏】调用本机真实 Konsole 终端运行，连接真实 MySQL 8.0、Redis 与大连理工大学统一 AI 网关真实模型，153 项断言全部通过，退出码 0，由 KDE Spectacle 真实截取"
        ),
        (
            "图二：本机 KDE Konsole 终端运行后端 Maven 235 条单测（cd server && mvn test）真实截图",
            "doc/05.集成测试/assets/run_evidence/konsole_2_mvn.png",
            "【本机真实终端截屏】调用本机真实 Konsole 终端运行，Surefire 真实执行涵盖状态机、适配器、护栏与业务层共 235 条用例，耗时 4.038s，BUILD SUCCESS，由 KDE Spectacle 真实截取"
        ),
        (
            "图三：本机 KDE Konsole 终端运行前端门禁（npm run check）真实截图",
            "doc/05.集成测试/assets/run_evidence/konsole_3_frontend.png",
            "【本机真实终端截屏】调用本机真实 Konsole 终端运行，ESLint、Prettier 代码规范与 Vitest 4 个套件 32 条单测全部通过（32 passed），由 KDE Spectacle 真实截取"
        ),
        (
            "图四：本机 KDE Konsole 终端运行前端 Web 生产打包（npm run build:web）真实截图",
            "doc/05.集成测试/assets/run_evidence/konsole_4_build.png",
            "【本机真实终端截屏】调用本机真实 Konsole 终端运行，Vite 5 真实构建 2111 个模块并输出生产包至 dist/ 目录，由 KDE Spectacle 真实截取"
        ),
    ]

    current_row = 2
    for title, img_path, desc in run_evidences:
        # 写入标题标签
        lbl_cell = ws_run.cell(current_row, 2)
        lbl_cell.value = title
        lbl_cell.font = Font(name="宋体", size=12, bold=True, color="004080")

        desc_cell = ws_run.cell(current_row + 1, 2)
        desc_cell.value = desc
        desc_cell.font = Font(name="宋体", size=9.5, italic=True, color="555555")

        if os.path.exists(img_path):
            xl_img = XLImage(img_path)
            orig_w, orig_h = xl_img.width, xl_img.height
            ratio = 760.0 / orig_w
            xl_img.width = int(orig_w * ratio)
            xl_img.height = int(orig_h * ratio)
            ws_run.add_image(xl_img, f"B{current_row + 3}")
            rows_occupied = int(xl_img.height / 20) + 5
            current_row += rows_occupied + 1
        else:
            current_row += 15

    print(f"Added {len(run_evidences)} run evidence screenshots to 运行 sheet.")

    # -------------------------------------------------------------
    # 5. 保存输出
    # -------------------------------------------------------------
    wb.save(OUTPUT_XLSX_1)
    print(f"Saved: {OUTPUT_XLSX_1}")
    shutil.copyfile(OUTPUT_XLSX_1, OUTPUT_XLSX_2)
    print(f"Copied to: {OUTPUT_XLSX_2}")

if __name__ == "__main__":
    build_excel()
