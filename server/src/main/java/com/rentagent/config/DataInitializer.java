package com.rentagent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.common.CryptoUtil;
import com.rentagent.entity.Contract;
import com.rentagent.entity.Favorite;
import com.rentagent.entity.House;
import com.rentagent.entity.KbChunk;
import com.rentagent.entity.KbDocument;
import com.rentagent.entity.LeaseOrder;
import com.rentagent.entity.RealnameAuth;
import com.rentagent.entity.RentBill;
import com.rentagent.entity.Review;
import com.rentagent.entity.SysUser;
import com.rentagent.entity.ViewingAppointment;
import com.rentagent.mapper.ContractMapper;
import com.rentagent.mapper.FavoriteMapper;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.KbChunkMapper;
import com.rentagent.mapper.KbDocumentMapper;
import com.rentagent.mapper.LeaseOrderMapper;
import com.rentagent.mapper.RealnameAuthMapper;
import com.rentagent.mapper.RentBillMapper;
import com.rentagent.mapper.ReviewMapper;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.mapper.ViewingAppointmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 演示种子数据（幂等：仅空库时写入）。演示账号密码统一 123456：
 * 小陈（租客）/ 王房东（未实名，演示拦截）/ 李房东（已实名）/ admin（管理员）/ 历史用户（已禁用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final RealnameAuthMapper realnameMapper;
    private final HouseMapper houseMapper;
    private final FavoriteMapper favoriteMapper;
    private final ViewingAppointmentMapper appointmentMapper;
    private final ContractMapper contractMapper;
    private final LeaseOrderMapper orderMapper;
    private final RentBillMapper billMapper;
    private final ReviewMapper reviewMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkMapper kbChunkMapper;
    private final CryptoUtil crypto;
    private final ObjectMapper objectMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(String... args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        log.info("空库检测到，写入演示种子数据...");
        long li = insertUser("lilandlord", "13800000003", "李房东", 2, 1);
        seedUsers(li);
        seedHouses(li);
        seedHistory();
        seedKnowledge();
        log.info("演示种子数据写入完成：账号 小陈/王房东/李房东/admin，密码统一 123456");
    }

    private void seedUsers(long liId) {
        insertUser("xiaochen", "13800000001", "小陈", 1, 1);
        insertUser("wanglandlord", "13800000002", "王房东", 2, 1);
        insertUser("admin", "13800000009", "平台管理员", 3, 1);
        insertUser("historyuser", "13800000005", "历史用户", 1, 0);
        RealnameAuth auth = new RealnameAuth();
        auth.setUserId(liId);
        auth.setRealName("李建国");
        auth.setIdCardNoEnc(crypto.encrypt("210202199001011234"));
        auth.setIdCardHash(crypto.sha256Hex("210202199001011234"));
        auth.setStatus(1);
        auth.setAuditTime(LocalDateTime.now().minusDays(1));
        realnameMapper.insert(auth);
    }

    private long insertUser(String username, String phone, String nickname, int role, int status) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPhone(phone);
        u.setPassword(encoder.encode("123456"));
        u.setNickname(nickname);
        u.setRole(role);
        u.setStatus(status);
        userMapper.insert(u);
        return u.getId();
    }

    private void seedHouses(long li) {
        long wang = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "wanglandlord")).getId();

        house(li, "近地铁精装一居室 拎包入住", "凌水小镇", "甘井子区", "1室1厅", 42, 2300,
                List.of("近地铁", "精装修", "家电齐全", "拎包入住"), 121.5268, 38.8718, ST_ONLINE, null);
        house(li, "电梯两居室 朝南采光好", "书香园", "沙河口区", "2室1厅", 68, 3200,
                List.of("电梯", "精装修", "采光好", "家电齐全"), 121.5688, 38.9089, ST_ONLINE, null);
        house(li, "海景两居 精装修带家电", "星海人家", "沙河口区", "2室1厅", 75, 3800,
                List.of("精装修", "家电齐全", "近地铁"), 121.5876, 38.8787, ST_ONLINE, null);
        house(li, "高新园区一居 家电齐全", "创意大厦公寓", "高新园区", "1室1厅", 38, 1900,
                List.of("电梯", "家电齐全", "近地铁"), 121.5373, 38.8469, ST_ONLINE, null);
        house(li, "三居合租主卧 近理工", "学苑广场", "甘井子区", "3室1厅", 89, 2600,
                List.of("近地铁", "拎包入住", "采光好"), 121.5150, 38.8898, ST_ONLINE, null);
        house(li, "中山广场旁一居室 带独卫", "人民路小区", "中山区", "1室1厅", 45, 2900,
                List.of("电梯", "精装修"), 121.6544, 38.9217, ST_ONLINE, null);
        house(wang, "待审核两居 家电齐全近地铁", "锦绣小区", "西岗区", "2室1厅", 60, 2700,
                List.of("近地铁", "家电齐全"), 121.6137, 38.9148, ST_PENDING, null);
        house(li, "在租两居（示例成交房源）", "凌水小镇", "甘井子区", "2室1厅", 65, 2800,
                List.of("精装修", "家电齐全"), 121.5275, 38.8722, ST_RENTED, null);
    }

    private void house(long landlordId, String title, String community, String district, String layout,
                       double area, double rent, List<String> facilities, double lng, double lat,
                       int status, String description) {
        House h = new House();
        h.setLandlordId(landlordId);
        h.setTitle(title);
        h.setCommunity(community);
        h.setCity("大连市");
        h.setDistrict(district);
        h.setAddress(district + community);
        h.setLayout(layout);
        h.setArea(BigDecimal.valueOf(area));
        h.setOrientation("南");
        h.setFloorDesc("中层");
        h.setRent(BigDecimal.valueOf(rent));
        h.setDepositType("押一付三");
        h.setFacilities(toJson(facilities));
        h.setDescription(description == null
                ? title + "，" + String.join("、", facilities) + "，周边配套成熟，交通便捷，适合上班族居住。"
                : description);
        h.setLng(BigDecimal.valueOf(lng));
        h.setLat(BigDecimal.valueOf(lat));
        h.setViewCount(50 + (int) (Math.random() * 200));
        h.setStatus(status);
        houseMapper.insert(h);
    }

    /** 历史成交：一条已退租订单（带评价）+ 一条在租订单 + 收藏与已完成预约（推荐偏好数据源） */
    private void seedHistory() {
        SysUser tenant = byUsername("xiaochen");
        SysUser landlord = byUsername("lilandlord");
        House rented = houseMapper.selectOne(new LambdaQueryWrapper<House>()
                .eq(House::getStatus, ST_RENTED).last("LIMIT 1"));
        House online = houseMapper.selectOne(new LambdaQueryWrapper<House>()
                .like(House::getTitle, "电梯两居室").last("LIMIT 1"));
        if (tenant == null || landlord == null || rented == null || online == null) {
            return;
        }
        ViewingAppointment appt = new ViewingAppointment();
        appt.setHouseId(online.getId());
        appt.setTenantId(tenant.getId());
        appt.setLandlordId(landlord.getId());
        appt.setAppointmentTime(LocalDateTime.now().minusDays(3));
        appt.setStatus(3);
        appointmentMapper.insert(appt);

        for (Long hid : List.of(online.getId(), rented.getId())) {
            Favorite f = new Favorite();
            f.setUserId(tenant.getId());
            f.setHouseId(hid);
            favoriteMapper.insert(f);
        }

        Contract done = contract(rented, tenant, landlord,
                LocalDate.now().minusMonths(3), LocalDate.now().minusDays(10), 3);
        LeaseOrder doneOrder = order(done, 1);
        bills(doneOrder, true);
        Review review = new Review();
        review.setLeaseOrderId(doneOrder.getId());
        review.setHouseId(rented.getId());
        review.setLandlordId(landlord.getId());
        review.setTenantId(tenant.getId());
        review.setHouseScore(5);
        review.setLandlordScore(4);
        review.setContent("房子干净整洁，房东很负责，地理位置也好，推荐！");
        review.setStatus(0);
        reviewMapper.insert(review);

        Contract active = contract(rented, tenant, landlord,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(11), 2);
        LeaseOrder activeOrder = order(active, 0);
        bills(activeOrder, false);
    }

    private void seedKnowledge() {
        kb("押金怎么退？什么情况下会被扣押金？", "押金退还规则：合同期满或正常退租后，房屋验收无违约、无损坏、无欠费的，押金在 3 个工作日内原路全额退还。" +
                "以下情形可能被扣除部分押金：拖欠水电燃气费；人为损坏家具家电；提前退租且未提前 30 日书面通知房东。" +
                "押金不予退还的约定如与实际履行情况不符，可提交平台申诉。", 2);
        kb("提前退租需要承担什么违约责任？", "提前退租规则：租客需提前 30 日向房东书面（含站内消息）提出退租申请。" +
                "按合同约定，提前退租押金不予退还的，以合同条款为准；平台示范合同同时约定房东单方收房的，应向租客支付相当于两个月租金的违约金。" +
                "双方协商一致的退租不受上述限制。", 2);
        kb("房子里东西坏了维修费谁出？", "维修责任划分：房屋主体结构与附属设施的非人为损坏（如管道老化、电路故障、门窗变形）由房东承担维修费用，" +
                "房东应在接到报修通知后 7 日内维修；因租客人为原因造成的损坏，由租客承担维修费用。" +
                "入住时建议拍照留存房屋现状，作为退租时核对依据。", 2);
        kb("房东可以中途涨租金吗？", "租金调整规则：租赁期内房东不得单方涨租。合同期内租金以合同约定为准，房东单方涨租或单方收房视为违约。" +
                "续租时的租金调整由双方协商确定，平台上调幅度建议不超过同小区同类房源挂牌均价的 5%。", 2);
        kb("看房预约后房东不确认怎么办？", "看房预约说明：租客发起预约后，房东应在 24 小时内确认或拒绝。超时未处理的预约会自动提醒房东；" +
                "同一房源同一时段仅允许一个有效预约，如遇时段冲突请选择其他时间。预约被拒绝后可重新发起。", 1);
        kb("电子合同有法律效力吗？签约流程是什么？", "电子合同说明：平台电子合同为示范文本，签约流程为：租客发起并确认 → 房东确认签署 → 合同生效并生成订单与租金计划。" +
                "合同副本可在个人中心随时查看。当前演示版本不对接 CA 电子签章，正式签署请以线下纸质或具备法律效力的电子签平台为准。", 1);
        kb("租客实名认证是必须的吗？房东为什么必须实名？", "实名认证规则：平台对房东实施强制实名认证（姓名 + 身份证号），未通过实名的房东无法发布房源；" +
                "租客注册即可使用，但在签约时至少需登记真实姓名与联系方式。身份信息加密存储，仅用于安全核验。", 1);
        kb("如何判断房源是不是虚假房源？", "虚假房源识别：平台 AI 辅助审核会对租金显著低于区域均价、描述含引流话术、信息完整度低的房源输出风险分，" +
                "管理员保留最终裁决权。租客遇到疑似虚假房源可点击「举报」，平台将在 48 小时内处理并通知结果。", 1);
        kb("租金账单怎么生成和支付？", "租金计划说明：合同生效后系统自动按月生成租金账单（每期应付金额与应付日），租客在订单页标记支付，房东侧同步可见。" +
                "逾期账单会标记为逾期状态并提醒双方。当前演示版本仅做支付记录，不对接真实支付渠道。", 1);
        kb("平台如何保护我的个人信息？", "隐私保护承诺：身份证号等敏感信息采用加密存储；AI 对话不上传可识别个人身份的信息；" +
                "个人信息收集遵循最小必要原则。如需删除账号数据，可联系平台客服处理。", 1);
        kb("评价规则是什么？谁能评价？", "评价规则：仅完成过合同（已退租或到期）的租客可对房源与房东评分评价，一单一评；" +
                "评价内容展示于房源详情页。恶意评价经举报核实后将被隐藏。", 1);
        kb("平台收费标准是什么？", "平台收费说明：演示版本对租客与房东均免费。后续规划的增值服务（如置顶推广、AI 定价报告导出）以正式公告为准。", 1);
    }

    private void kb(String title, String content, int category) {
        KbDocument doc = new KbDocument();
        doc.setTitle(title);
        doc.setCategory(category);
        doc.setContent(content);
        doc.setStatus(1);
        kbDocumentMapper.insert(doc);
        KbChunk chunk = new KbChunk();
        chunk.setDocumentId(doc.getId());
        chunk.setSeq(0);
        chunk.setContent(content);
        chunk.setTokenCount(content.length());
        kbChunkMapper.insert(chunk);
        doc.setChunkCount(1);
        kbDocumentMapper.updateById(doc);
    }

    private SysUser byUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    private Contract contract(House house, SysUser tenant, SysUser landlord, LocalDate start, LocalDate end, int status) {
        Contract c = new Contract();
        c.setHouseId(house.getId());
        c.setTenantId(tenant.getId());
        c.setLandlordId(landlord.getId());
        c.setStartDate(start);
        c.setEndDate(end);
        c.setMonthlyRent(house.getRent());
        c.setDeposit(house.getRent());
        c.setStatus(status);
        c.setSignedTenantAt(LocalDateTime.now().minusMonths(3));
        c.setSignedLandlordAt(LocalDateTime.now().minusMonths(3));
        try {
            c.setClauses(objectMapper.writeValueAsString(List.of(
                    Map.of("title", "租赁标的", "text", "甲方（房东）将" + house.getCommunity() + "房屋出租给乙方（租客）居住使用。"),
                    Map.of("title", "租期", "text", "租赁期自 " + start + " 至 " + end + "。"),
                    Map.of("title", "租金与押金", "text", "月租金人民币 " + house.getRent() + " 元，押付方式：押一付三，押金 " + house.getRent() + " 元。"),
                    Map.of("title", "违约责任", "text", "乙方提前退租的押金不予退还；甲方单方涨租或单方收房视为甲方违约，应向乙方支付相当于两个月租金的违约金。"))));
        } catch (Exception ignored) {
        }
        contractMapper.insert(c);
        return c;
    }

    private LeaseOrder order(Contract c, int status) {
        LeaseOrder o = new LeaseOrder();
        o.setContractId(c.getId());
        o.setHouseId(c.getHouseId());
        o.setTenantId(c.getTenantId());
        o.setLandlordId(c.getLandlordId());
        o.setStartDate(c.getStartDate());
        o.setEndDate(c.getEndDate());
        o.setMonthlyRent(c.getMonthlyRent());
        o.setDeposit(c.getDeposit());
        o.setStatus(status);
        orderMapper.insert(o);
        return o;
    }

    private void bills(LeaseOrder order, boolean paid) {
        for (int i = 0; i < 3; i++) {
            RentBill b = new RentBill();
            b.setLeaseOrderId(order.getId());
            b.setPeriodNo(i + 1);
            b.setDueDate(order.getStartDate().plusMonths(i));
            b.setAmount(order.getMonthlyRent());
            b.setStatus(paid ? 1 : 0);
            if (paid) {
                b.setPaidAt(order.getStartDate().plusMonths(i).atTime(12, 0));
            }
            billMapper.insert(b);
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static final int ST_ONLINE = 3;
    private static final int ST_PENDING = 0;
    private static final int ST_RENTED = 5;
}
