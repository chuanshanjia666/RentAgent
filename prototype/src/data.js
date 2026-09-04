/* ============================================================
 * RentAgent 高保真原型 —— Mock 数据
 * 说明：所有数据仅存于内存，刷新后重置；仅供原型演示。
 * 演示账号密码统一为 123456。
 * ============================================================ */

export const DB = {
  users: [
    { id: 'u1', role: 'tenant',  name: '小陈',     phone: '13800000001', password: '123456', verified: true,  disabled: false },
    { id: 'u2', role: 'landlord', name: '王房东',  phone: '13800000002', password: '123456', verified: false, disabled: false },
    { id: 'u3', role: 'landlord', name: '李房东',  phone: '13800000003', password: '123456', verified: true,  disabled: false },
    { id: 'u4', role: 'admin',   name: '平台管理员', phone: '13800000004', password: '123456', verified: true,  disabled: false },
    { id: 'u5', role: 'tenant',  name: '历史用户',  phone: '13800000005', password: '123456', verified: true,  disabled: true  },
    { id: 'u6', role: 'tenant',  name: '小林',     phone: '13800000006', password: '123456', verified: true,  disabled: false }
  ],

  listings: [
    { id: 'l1',  title: '云顶小区·精装两居·地铁口 300 米', community: '云顶小区', region: '城东区', layout: '两居', area: 78,  price: 2600, deposit: '押一付三', orientation: '朝南', floor: '中楼层/18层', facilities: ['近地铁', '独立卫浴', '朝南', '精装修', '家电齐全'], subway: true,  status: 'approved', landlordId: 'u3', risk: 12, mapX: 72, mapY: 30, desc: '距离 2 号线云顶站步行 5 分钟，精装修拎包入住，采光充足，小区绿化率高，周边配套成熟。' },
    { id: 'l2',  title: '大学城·青春公寓精装单间',       community: '青春公寓', region: '大学城', layout: '一居', area: 35,  price: 1500, deposit: '押一付一', orientation: '朝南', floor: '高楼层/24层', facilities: ['独立卫浴', '可短租', '家电齐全'], subway: false, status: 'approved', landlordId: 'u3', risk: 20, mapX: 30, mapY: 70, desc: '适合应届生与考研党，楼下即商业街，民水民电，可月付短租。' },
    { id: 'l3',  title: '高新区·珑悦台南北通透三居',     community: '珑悦台',   region: '高新区', layout: '三居', area: 120, price: 4200, deposit: '押一付三', orientation: '南北', floor: '中楼层/26层', facilities: ['近地铁', '独立卫浴', '精装修', '家电齐全'], subway: true,  status: 'approved', landlordId: 'u3', risk: 8,  mapX: 55, mapY: 18, desc: '双卫三居，南北通透，适合合租或家庭居住，小区 2021 年建成。' },
    { id: 'l4',  title: '城西·梧桐苑温馨一居',           community: '梧桐苑',   region: '城西区', layout: '一居', area: 45,  price: 1800, deposit: '押一付二', orientation: '朝南', floor: '低楼层/11层', facilities: ['近地铁', '朝南', '家电齐全'], subway: true,  status: 'approved', landlordId: 'u3', risk: 15, mapX: 18, mapY: 42, desc: '老小区一楼带院，地铁 1 号线步行 8 分钟，周边菜场超市齐全。' },
    { id: 'l5',  title: '高新区·翡翠湾两居拎包入住',     community: '翡翠湾',   region: '高新区', layout: '两居', area: 88,  price: 2800, deposit: '押一付三', orientation: '朝南', floor: '高楼层/33层', facilities: ['近地铁', '独立卫浴', '精装修', '家电齐全', '朝南'], subway: true,  status: 'approved', landlordId: 'u3', risk: 10, mapX: 48, mapY: 55, desc: '视野开阔可看江景，全屋品牌家电，物业负责，适合情侣或两人合租。' },
    { id: 'l6',  title: '大学城·书香雅苑安静两居',       community: '书香雅苑', region: '大学城', layout: '两居', area: 75,  price: 2200, deposit: '押一付二', orientation: '朝东', floor: '中楼层/16层', facilities: ['独立卫浴', '家电齐全'], subway: false, status: 'approved', landlordId: 'u3', risk: 18, mapX: 22, mapY: 82, desc: '紧邻大学城商圈，安静不临街，房东人好可谈长租优惠。' },
    { id: 'l7',  title: '城东·江畔明珠一线江景一居',     community: '江畔明珠', region: '城东区', layout: '一居', area: 52,  price: 2000, deposit: '押一付二', orientation: '朝南', floor: '高楼层/30层', facilities: ['近地铁', '独立卫浴', '朝南', '精装修'], subway: true,  status: 'approved', landlordId: 'u3', risk: 14, mapX: 82, mapY: 52, desc: '落地窗看江，傍晚景色一流，通勤 CBD 20 分钟。' },
    { id: 'l8',  title: '城西·锦绣家园实惠三居',         community: '锦绣家园', region: '城西区', layout: '三居', area: 110, price: 3600, deposit: '押一付三', orientation: '南北', floor: '低楼层/6层',  facilities: ['独立卫浴', '家电齐全'], subway: false, status: 'approved', landlordId: 'u3', risk: 22, mapX: 12, mapY: 62, desc: '多层三楼，适合三室友分摊，人均 1200，空间宽敞。' },
    { id: 'l9',  title: '高新区·云立方loft单间公寓',     community: '云立方',   region: '高新区', layout: '一居', area: 30,  price: 1300, deposit: '押一付一', orientation: '朝西', floor: '中楼层/20层', facilities: ['近地铁', '独立卫浴', '可短租', '家电齐全'], subway: true,  status: 'approved', landlordId: 'u3', risk: 25, mapX: 62, mapY: 38, desc: 'loft 复式小户型，青年公寓社区，含健身房与公共厨房。' },
    { id: 'l10', title: '城东·枫林雅舍大两居',           community: '枫林雅舍', region: '城东区', layout: '两居', area: 82,  price: 2500, deposit: '押一付三', orientation: '朝南', floor: '中楼层/18层', facilities: ['独立卫浴', '朝南', '家电齐全'], subway: false, status: 'approved', landlordId: 'u3', risk: 16, mapX: 78, mapY: 70, desc: '双阳台，主卧带飘窗，楼下即枫林公园，适合养宠人群（需报备）。' },
    { id: 'l11', title: '高新区·超值两居急租低于市价',   community: '某小区',   region: '高新区', layout: '两居', area: 90,  price: 1600, deposit: '押一付一', orientation: '朝南', floor: '-', facilities: ['精装修'], subway: false, status: 'pending', landlordId: 'u3', risk: 82, mapX: 50, mapY: 30, riskPoints: ['房源价格低于同小区均价 38%，异常偏低', '图片 EXIF 信息与描述位置不符', '描述中关键词大量重复堆砌'], desc: '豪华装修急租，价格远低于周边，先到先得……（描述含大量重复堆砌关键词）' },
    { id: 'l12', title: '大学城·南山别院整栋三居',       community: '南山别院', region: '大学城', layout: '三居', area: 130, price: 3900, deposit: '押一付三', orientation: '南北', floor: '-', facilities: ['独立卫浴', '精装修'], subway: false, status: 'pending', landlordId: 'u3', risk: 35, mapX: 35, mapY: 60, riskPoints: ['图片数量偏少，建议补充实拍图'], desc: '新挂牌三居，图片待补充完整。' },
    { id: 'l13', title: '城西·湖畔公寓湖景一居',         community: '湖畔公寓', region: '城西区', layout: '一居', area: 48,  price: 1900, deposit: '押一付二', orientation: '朝南', floor: '高楼层/22层', facilities: ['独立卫浴', '朝南'], subway: false, status: 'rejected', landlordId: 'u3', risk: 45, mapX: 20, mapY: 30, rejectReason: '图片与描述户型不符，请补充实拍图后重新提交。', desc: '高层湖景一居，带独立阳台。' },
    { id: 'l14', title: '云顶小区精装一居（已下架）',     community: '云顶小区', region: '城东区', layout: '一居', area: 50,  price: 2100, deposit: '押一付二', orientation: '朝南', floor: '中楼层/18层', facilities: ['近地铁', '独立卫浴', '精装修'], subway: true,  status: 'offlined', landlordId: 'u3', risk: 10, mapX: 70, mapY: 22, desc: '房东自住一段时间后下架，保留信息用于状态演示。' }
  ],

  reviews: [
    { id: 'r1', listingId: 'l1', byUserId: 'u6', tenantName: '小林', stars: 5, text: '房东很负责，房子和图片一致，地铁口真的很近，推荐！', date: '2026-08-20' },
    { id: 'r2', listingId: 'l1', byUserId: '',  tenantName: '张同学', stars: 4, text: '采光好，隔音一般，整体满意。', date: '2026-07-11' },
    { id: 'r3', listingId: 'l2', byUserId: 'u1', tenantName: '小陈', stars: 4, text: '性价比高，适合过渡居住。', date: '2026-06-30' }
  ],

  favorites: [
    { userId: 'u1', listingId: 'l7' }
  ],

  // 行为历史：浏览/搜索记录，驱动 FR-11 个性化推荐（预置小陈的演示数据，随操作实时变化）
  history: {
    u1: { viewed: ['l1', 'l7', 'l2'], searches: ['城东区 两居', '预算 2500 近地铁'] }
  },

  appointments: [
    { id: 'a1', listingId: 'l1', tenantId: 'u1', landlordId: 'u3', date: '2026-09-05', slot: '09:00-11:00', status: 'confirmed' },
    { id: 'a2', listingId: 'l3', tenantId: 'u1', landlordId: 'u3', date: '2026-09-02', slot: '14:00-16:00', status: 'rejected', reason: '该时段房东有事，请改约其他时间' },
    { id: 'a3', listingId: 'l2', tenantId: 'u1', landlordId: 'u3', date: '2026-08-25', slot: '10:00-12:00', status: 'completed' },
    { id: 'a4', listingId: 'l5', tenantId: 'u6', landlordId: 'u3', date: '2026-09-06', slot: '16:00-18:00', status: 'pending' }
  ],

  contracts: [
    {
      id: 'c1', listingId: 'l1', tenantId: 'u1', landlordId: 'u3',
      rent: 2600, deposit: 2600, months: 12, start: '2026-09-10', status: 'draft',
      tenantSigned: false, landlordSigned: false,
      clauses: [
        { id: 'k1', text: '租期与租金：租赁期自 2026-09-10 起共 12 个月，月租金人民币 2600 元，支付方式为押一付三。', risk: false, explain: '常规条款。注意核对起租日、付款周期与金额是否与你确认的一致。' },
        { id: 'k2', text: '押金：签订本合同当日支付押金 2600 元；租期届满且无违约情形的，房东应在交房之日起 7 日内无息退还。', risk: false, explain: '常规条款，明确了押金退还时限，对租客有保护作用。建议留存支付凭证。' },
        { id: 'k3', text: '违约金：任何一方提前解约的，应向对方支付相当于月租金 200% 的违约金。', risk: true, explain: '风险条款：违约金显著偏高（通常以不超过 1 个月租金为限）。200% 意味着提前退租要赔 5200 元，明显加重违约成本，建议协商改为不超过月租金 100%。' },
        { id: 'k4', text: '租金调整：租赁期内，房东有权根据市场情况单方上调租金，租客需在接到通知后 7 日内确认是否接受。', risk: true, explain: '风险条款：租期内单方涨租对租客不利。正常情况下租期内租金应以合同约定为准，建议删除该条，或改为"经双方协商一致后方可调整"。' },
        { id: 'k5', text: '退租与装修折旧：租客退租时，房东可按装修折旧扣除费用，且押金不予退还。', risk: true, explain: '风险条款："押金不予退还"属于典型不公平条款；装修折旧扣除亦无标准与凭证约定。建议删除本条，改为按实际损耗凭据合理结算。' },
        { id: 'k6', text: '维修责任：房屋自然损耗由房东负责维修；因租客使用不当造成的损坏由租客承担维修费用。', risk: false, explain: '常规条款，责任划分合理。建议入住时拍照留证，记录房屋现状。' }
      ]
    }
  ],

  leases: [
    { id: 'le0', listingId: 'l9', tenantId: 'u1', landlordId: 'u3', months: 3, start: '2026-05-01', status: 'completed', applyType: null, plan: [
      { period: '第 1 期', amount: 1300, status: 'paid' }, { period: '第 2 期', amount: 1300, status: 'paid' }, { period: '第 3 期', amount: 1300, status: 'paid' } ] },
    { id: 'le1', listingId: 'l2', tenantId: 'u1', landlordId: 'u3', months: 6, start: '2026-09-01', status: 'active', applyType: null, plan: [
      { period: '第 1 期', amount: 1500, status: 'paid' }, { period: '第 2 期', amount: 1500, status: 'paid' }, { period: '第 3 期', amount: 1500, status: 'unpaid' }, { period: '第 4 期', amount: 1500, status: 'unpaid' }, { period: '第 5 期', amount: 1500, status: 'unpaid' }, { period: '第 6 期', amount: 1500, status: 'unpaid' } ] }
  ],

  faqs: [
    { id: 'f01', keywords: ['押金', '退'], q: '押金什么时候退还？', a: '租期届满且无违约、无欠费的，房东应在交房之日起 7 个工作日内原路退还押金。', source: '《RentAgent 平台规则 FAQ》第 3 条' },
    { id: 'f02', keywords: ['提前', '退租'], q: '提前退租怎么处理？', a: '提前退租需提前 30 天在平台提交申请，与房东协商违约责任；协商不一致的可申请平台介入。', source: '《RentAgent 平台规则 FAQ》第 5 条' },
    { id: 'f03', keywords: ['维修', '坏了', '修'], q: '房屋设施坏了谁负责维修？', a: '自然损耗（水管老化、电路故障等）由房东负责维修；因使用不当造成的损坏由租客承担。可在订单页提交报修。', source: '《RentAgent 平台规则 FAQ》第 8 条' },
    { id: 'f04', keywords: ['水电', '物业', '费用'], q: '水电物业费由谁承担？', a: '以合同约定为准，平台默认模板为：水电燃气由租客按表计费承担，物业费由房东承担。', source: '《RentAgent 平台规则 FAQ》第 9 条' },
    { id: 'f05', keywords: ['违约'], q: '房东违约怎么办？', a: '可保留证据后在订单详情页发起投诉，平台将在 3 个工作日内介入核实，视情况对房东采取警告、下架房源、封禁等措施。', source: '《RentAgent 平台规则 FAQ》第 12 条' },
    { id: 'f06', keywords: ['实名', '认证'], q: '房东为什么要实名认证？', a: '依据平台规则与实名制要求，房东发布房源前须完成实名认证，信息加密存储，仅用于核验身份。', source: '《RentAgent 平台规则 FAQ》第 2 条' },
    { id: 'f07', keywords: ['审核', '多久'], q: '房源审核需要多久？', a: '管理员通常在 24 小时内完成人工审核，AI 辅助识别可提前标记疑似虚假房源，提升审核效率。', source: '《RentAgent 平台规则 FAQ》第 4 条' },
    { id: 'f08', keywords: ['预约', '看房'], q: '怎么预约看房？', a: '在房源详情页点击"预约看房"，选择日期与时段提交即可；房东确认后你会收到站内通知。', source: '《RentAgent 平台规则 FAQ》第 6 条' },
    { id: 'f09', keywords: ['合同', '签约'], q: '电子合同怎么签？', a: '双方确认租赁意向后，系统基于模板自动生成电子合同，双方在线逐条确认后完成签约，合同副本可在个人中心查看。', source: '《RentAgent 平台规则 FAQ》第 7 条' },
    { id: 'f10', keywords: ['租金', '支付', '付'], q: '租金怎么支付？', a: '本期版本租金支付仅做账单记录，不对接真实支付渠道。账单按期生成，双方线下完成支付后由房东确认到账。', source: '《RentAgent 平台规则 FAQ》第 10 条' },
    { id: 'f11', keywords: ['举报', '投诉', '虚假'], q: '发现虚假房源怎么举报？', a: '在房源详情页点击"举报"提交理由，管理员将结合 AI 风险识别在 24 小时内核实处理，结果通过站内消息通知你。', source: '《RentAgent 平台规则 FAQ》第 11 条' },
    { id: 'f12', keywords: ['换租', '转租'], q: '可以换租或转租吗？', a: '支持在租订单发起换租申请，需房东确认；未经平台与房东同意不得私自转租。', source: '《RentAgent 平台规则 FAQ》第 13 条' }
  ],

  notifications: [
    { id: 'n1', userId: 'u1', text: '你的预约已确认：云顶小区·精装两居（09-05 09:00-11:00）', time: '09-03 10:20', read: false },
    { id: 'n2', userId: 'u1', text: '你的预约已被拒绝：珑悦台三居（房东建议改约）', time: '09-02 18:40', read: true },
    { id: 'n3', userId: 'u3', text: '新预约待确认：翡翠湾两居（租客小林，09-06 16:00-18:00）', time: '09-03 09:05', read: false },
    { id: 'n4', userId: 'u3', text: '房源"湖畔公寓湖景一居"审核未通过，请查看驳回理由', time: '09-01 15:30', read: true }
  ],

  reports: [
    { id: 'rp1', listingId: 'l10', reporter: '小林', reason: '描述写"双阳台"，实拍图只有一个阳台，疑似虚假宣传', time: '09-03', status: 'pending' }
  ],

  stats: {
    daily:   { labels: ['08-29','08-30','08-31','09-01','09-02','09-03','09-04'], users: [3,5,4,6,8,7,9], listings: [1,2,0,3,2,4,2], deals: [0,1,0,0,2,1,3], ai: [18,25,22,31,40,38,45] },
    weekly:  { labels: ['第1周','第2周','第3周','第4周','第5周','第6周'], users: [21,30,28,41,52,48], listings: [6,9,7,12,10,14], deals: [2,4,3,7,9,12], ai: [120,156,171,210,244,262] },
    monthly: { labels: ['4月','5月','6月','7月','8月'], users: [88,102,131,150,178], listings: [31,42,55,61,70], deals: [12,18,26,31,38], ai: [520,680,750,910,1024] },
    cards: { users: 178, listings: 70, deals: 38, ai: 1024 }
  },

  demoCaptcha: '246810'
}

export const REGIONS = ['城东区', '城西区', '高新区', '大学城']
export const LAYOUTS = ['一居', '两居', '三居']
export const ORIENTS = ['朝南', '朝北', '朝东', '朝西', '南北']
export const DEPOSITS = ['押一付一', '押一付二', '押一付三']
export const FACILITY_OPTIONS = ['近地铁', '独立卫浴', '朝南', '精装修', '家电齐全', '可短租']
export const SLOTS = ['09:00-11:00', '11:00-13:00', '14:00-16:00', '16:00-18:00', '18:00-20:00']
