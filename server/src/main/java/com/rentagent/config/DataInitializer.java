package com.rentagent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.common.CryptoUtil;
import com.rentagent.entity.Contract;
import com.rentagent.entity.Favorite;
import com.rentagent.entity.House;
import com.rentagent.entity.HouseImage;
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
import com.rentagent.mapper.HouseImageMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 演示种子数据（幂等：仅空库时写入）。演示账号密码统一 123456：
 * 小陈（租客）/ 王房东（未实名，演示拦截）/ 李房东（已实名）/ admin（管理员）/ 历史用户（已禁用）。
 * 房源带真实感图片：图片文件打包在 classpath 的 {@code seed-images/}，空库播种时拷贝到上传目录，
 * URL 仍走 {@code /uploads/seed-*.jpg}，与前端 {@code assetUrl()} 的解析规则保持一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final RealnameAuthMapper realnameMapper;
    private final HouseMapper houseMapper;
    private final HouseImageMapper imageMapper;
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

    private final BCryptPasswordEncoder encoder;

    /** 上传目录：与 FileController / 静态资源映射共用同一配置，种子图片直接拷进来 */
    @Value("${rentagent.upload-dir}")
    private String uploadDir;

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

    /**
     * 演示房源 17 套：李房东 14 套（12 在架 + 在租 1 + 已驳回 1 + 已下架 1）、王房东 3 套全部待审核
     * （未实名房东的房源无法通过审核，与 FR-04 的拦截演示呼应）。
     * 楼层词表（低层/中层/高层）、户型、朝向、押付方式与设施标签均取自前端 constants.ts，
     * 总层数等细节写进描述。图片为 classpath seed-images/ 下的通用素材，按"封面 + 厨卫 + 卧室 + 楼栋"组合。
     */
    private void seedHouses(long li) {
        long wang = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "wanglandlord")).getId();

        // ── 李房东：在架房源 ──
        house(li, "近地铁精装一居室 拎包入住", "凌水小镇", "甘井子区", "1室1厅", 42, 2300,
                List.of("近地铁", "精装修", "家电齐全", "拎包入住"), 121.5268, 38.8718, ST_ONLINE,
                "距地铁 1 号线学苑广场站步行约 6 分钟，门口多路公交直达软件园与理工大学。2023 年全新装修，"
                        + "冰箱、洗衣机、空调、热水器等家电齐备，独立厨卫，采光充足。租金含物业与供暖费，电费按表结算。"
                        + "房东直租无中介费，可长租两年，看房请提前一天预约。",
                List.of("living-01.jpg", "kitchen-03.jpg", "bed-01.jpg", "bath-02.jpg", "bldg-01.jpg"));
        house(li, "电梯两居室 朝南采光好", "书香园", "沙河口区", "2室1厅", 68, 3200,
                List.of("电梯", "精装修", "采光好", "家电齐全"), 121.5688, 38.9089, ST_ONLINE,
                "书香园小区紧邻辽师大附中，两卧均朝南，客厅带落地窗，全天采光。电梯高层，视野开阔，"
                        + "小区绿化率高，出小区即是地铁站与商圈，生活便利。适合家庭或两人合租，家具家电可按需增配。",
                List.of("living-04.jpg", "bed-05.jpg", "kitchen-05.jpg", "bath-03.jpg", "bldg-02.jpg"));
        house(li, "海景两居 精装修带家电", "星海人家", "沙河口区", "2室1厅", 75, 3800,
                List.of("电梯", "精装修", "家电齐全", "采光好"), 121.5876, 38.8787, ST_ONLINE,
                "星海人家高层南向，客厅与主卧可看海，步行 10 分钟到星海广场与海滨浴场。全屋精装修，"
                        + "中央空调加地暖，家电全品牌配置。小区人车分流，自带花园与健身步道。看房随时可约，租金可小议。",
                List.of("living-02.jpg", "bed-06.jpg", "kitchen-02.jpg", "bath-04.jpg", "bldg-05.jpg"));
        houseFull(li, "高新园区一居 家电齐全", "创意大厦公寓", "高新园区", "1室1厅", 38, 1900,
                "东", "中层", "押一付一", List.of("电梯", "家电齐全", "近地铁"),
                121.5373, 38.8469, ST_ONLINE,
                "创意大厦公寓楼，出门即地铁 1 号线，软件园上班族步行可达。开间格局，空间利用率高，"
                        + "家电全新配齐，宽带到户。楼下有便利店、餐馆与健身房，通勤生活两相宜。支持押一付一，适合过渡居住。",
                List.of("living-09.jpg", "kitchen-03.jpg", "bed-02.jpg", "bath-01.jpg", "bldg-03.jpg"));
        house(li, "学苑广场三居 南北通透 近理工", "学苑广场", "甘井子区", "3室1厅", 89, 2600,
                List.of("近地铁", "拎包入住", "采光好"), 121.5150, 38.8898, ST_ONLINE,
                "学苑广场小区 11 层小高层，南北通透三居，主卧带阳台。周边理工附小、农贸市场、超市齐全，"
                        + "生活气息浓厚。房屋保持整洁，家具齐全可直接入住，适合三个同学或同事合租分摊，地铁口步行 8 分钟。",
                List.of("bed-08.jpg", "living-01.jpg", "bed-03.jpg", "kitchen-06.jpg", "bath-03.jpg", "bldg-01.jpg"));
        house(li, "中山广场旁一居室 带独卫", "人民路小区", "中山区", "1室1厅", 45, 2900,
                List.of("电梯", "精装修"), 121.6544, 38.9217, ST_ONLINE,
                "位于人民路 CBD 核心，中山广场、青泥洼桥商圈步行可达，地铁 2 号线中山广场站近在咫尺。"
                        + "精装修一居带独立卫生间，26 层视野开阔，夜晚可赏东港灯光。适合商务人士与白领常住，拎包入住。",
                List.of("living-03.jpg", "bed-04.jpg", "kitchen-04.jpg", "bath-02.jpg", "bldg-04.jpg"));
        houseFull(li, "软件园通勤两居 电梯高层", "学清园", "高新园区", "2室1厅", 62, 2600,
                "南北", "高层", "押一付一", List.of("近地铁", "电梯", "精装修", "家电齐全"),
                121.5489, 38.8452, ST_ONLINE,
                "距软件园步行约 15 分钟，地铁 1 号线两站直达河口。两卧南北向，客厅采光好，精装修拎包即住。"
                        + "园区安静，物业负责，楼下即公交站。支持押一付一，邻居多为 IT 从业者，作息相近。",
                List.of("living-08.jpg", "bed-01.jpg", "kitchen-09.jpg", "bath-02.jpg", "bldg-03.jpg"));
        houseFull(li, "七贤岭海景开间 拎包入住", "山海家园", "高新园区", "1室1厅", 35, 1650,
                "东", "中层", "押一付一", List.of("电梯", "拎包入住", "家电齐全", "采光好"),
                121.5237, 38.8543, ST_ONLINE,
                "七贤岭地铁站旁小户型开间，东向早晨满屋阳光，天气好时能望见海。楼龄新，电梯直达，"
                        + "独立厨卫与阳台。周边餐饮便利，距腾飞园区步行 10 分钟。租金 1650 元，适合刚毕业的年轻人。",
                List.of("living-10.jpg", "bed-02.jpg", "kitchen-03.jpg", "bath-01.jpg", "bldg-05.jpg"));
        houseFull(li, "星海公园旁两居 主卧看海", "星海名苑", "沙河口区", "2室1厅", 70, 3600,
                "南", "高层", "押一付三", List.of("电梯", "精装修", "家电齐全", "采光好"),
                121.5852, 38.8721, ST_ONLINE,
                "星海公园东门对面，主卧南向看海，次卧朝北安静。精装修三年保养良好，中央空调与地暖俱全。"
                        + "小区封闭管理带门禁，车位充足。步行至星海公园地铁站 5 分钟，通勤中山广场约 30 分钟。",
                List.of("bed-06.jpg", "living-02.jpg", "kitchen-02.jpg", "bath-04.jpg", "bldg-04.jpg"));
        houseFull(li, "青泥洼桥商圈一居 近地铁", "友好小区", "中山区", "1室1厅", 48, 2700,
                "南", "高层", "押一付三", List.of("近地铁", "电梯", "拎包入住", "家电齐全"),
                121.6442, 38.9183, ST_ONLINE,
                "友好广场旁 30 层高层一居，楼下就是商场与地铁 2 号线青泥洼桥站，购物娱乐步行即达。"
                        + "户型方正，明厨明卫，家具家电全齐。适合在中山广场附近上班的白领，晚上下楼就是大连最热闹的商圈。",
                List.of("bed-01.jpg", "living-03.jpg", "kitchen-07.jpg", "bath-03.jpg", "bldg-04.jpg"));
        houseFull(li, "桃源桥山景三居 南北通透", "桃源小区", "中山区", "3室1厅", 95, 4200,
                "南北", "中层", "半年付", List.of("电梯", "精装修", "家电齐全", "采光好"),
                121.6399, 38.8958, ST_ONLINE,
                "桃源桥 11 层小高层，三卧南北通透，客厅正对山景，空气清新。全屋新中式装修，实木家具，"
                        + "中央空调。一梯两户，住户素质高，适合一家人常住。半年付可享 95 折优惠。",
                List.of("living-07.jpg", "bed-03.jpg", "bed-08.jpg", "kitchen-05.jpg", "bath-02.jpg", "bldg-03.jpg"));

        // ── 王房东：未实名，房源全部停在待审核（演示审核队列与 FR-04 拦截） ──
        house(wang, "待审核两居 家电齐全近地铁", "锦绣小区", "西岗区", "2室1厅", 60, 2700,
                List.of("近地铁", "家电齐全"), 121.6137, 38.9148, ST_PENDING,
                "锦绣小区两室一厅，南北通透，家电齐全，距地铁口步行 10 分钟内，周边学校、医院、超市配套成熟。房东直租，长租优先。",
                List.of("living-05.jpg", "bed-07.jpg", "kitchen-01.jpg", "bath-04.jpg", "bldg-02.jpg"));
        houseFull(wang, "华南广场精装一居 急租", "华南名苑", "甘井子区", "1室1厅", 41, 1750,
                "南", "中层", "押二付一", List.of("电梯", "精装修", "家电齐全"),
                121.5952, 38.9602, ST_PENDING,
                "华南广场商圈核心，地铁 1 号线华南广场站步行 5 分钟。精装修一居，家电家具全新，业主急租价格可谈。"
                        + "周边万达广场、华南国际商城，生活极为便利。",
                List.of("bed-05.jpg", "living-09.jpg", "kitchen-01.jpg", "bath-01.jpg", "bldg-02.jpg"));
        houseFull(wang, "香炉礁两居 步梯三楼 南北通透", "香秀家园", "西岗区", "2室1厅", 66, 2400,
                "南北", "低层", "押一付三", List.of("精装修", "家电齐全", "采光好"),
                121.6235, 38.9351, ST_PENDING,
                "香炉礁商圈旁 6 层步梯三楼，南北通透采光极佳。2024 年重新粉刷，更换了热水器与洗衣机。"
                        + "近宜家与山姆会员店，购物方便，性价比高，适合精打细算的家庭。",
                List.of("bed-07.jpg", "living-05.jpg", "kitchen-06.jpg", "bath-04.jpg", "bldg-01.jpg"));

        // ── 状态机演示位：已出租（成交示例）/ 已驳回（AI 审核风险演示）/ 已下架 ──
        House rented = house(li, "在租两居（示例成交房源）", "凌水小镇", "甘井子区", "2室1厅", 65, 2800,
                List.of("精装修", "家电齐全"), 121.5275, 38.8722, ST_RENTED,
                "凌水小镇精装两居，已通过平台成功出租，当前在租中。此房源仅作成交示例展示，欢迎浏览同类在租房源。",
                List.of("living-06.jpg", "bed-03.jpg", "kitchen-08.jpg", "bath-01.jpg", "bldg-01.jpg"));
        rented.setAvgScore(BigDecimal.valueOf(4.5));
        houseMapper.updateById(rented);

        house(li, "低价整租两居 随时看房", "千山小区", "甘井子区", "2室1厅", 58, 800,
                List.of("拎包入住"), 121.5652, 38.9552, ST_REJECTED,
                "千山路附近两居，价格超低，随时看房，拎包即住。",
                List.of("bed-02.jpg", "living-10.jpg", "kitchen-08.jpg", "bldg-04.jpg"));
        // 已驳回的房源要给管理员一个可读的驳回理由（对应 house.reject_reason）
        rejectedReason("低价整租两居 随时看房",
                "疑似虚假房源：租金显著低于同区域同类房源均价，描述信息过于简单，请补充真实照片与完整信息后重新提交");

        houseFull(li, "华乐广场两居 业主自住装修", "华乐小区", "中山区", "2室2厅", 82, 3400,
                "南", "高层", "押一付三", List.of("电梯", "精装修", "家电齐全", "采光好"),
                121.6647, 38.9135, ST_OFFLINE,
                "华乐广场旁两室两厅，业主自住装修保养好，全屋品牌家电。近东港音乐喷泉与海之韵公园，环境宜居。"
                        + "当前已下架，预计下月重新上架。",
                List.of("bed-03.jpg", "living-04.jpg", "kitchen-09.jpg", "bath-01.jpg", "bldg-05.jpg"));
    }

    /** 朝向/楼层/押付用默认值（南 / 中层 / 押一付三）的简化版 */
    private House house(long landlordId, String title, String community, String district, String layout,
                        double area, double rent, List<String> facilities, double lng, double lat,
                        int status, String description, List<String> images) {
        return houseFull(landlordId, title, community, district, layout, area, rent,
                "南", "中层", "押一付三", facilities, lng, lat, status, description, images);
    }

    private House houseFull(long landlordId, String title, String community, String district, String layout,
                            double area, double rent, String orientation, String floorDesc, String depositType,
                            List<String> facilities, double lng, double lat, int status, String description,
                            List<String> images) {
        House h = new House();
        h.setLandlordId(landlordId);
        h.setTitle(title);
        h.setCommunity(community);
        h.setCity("大连市");
        h.setDistrict(district);
        h.setAddress(district + community);
        h.setLayout(layout);
        h.setArea(BigDecimal.valueOf(area));
        h.setOrientation(orientation);
        h.setFloorDesc(floorDesc);
        h.setRent(BigDecimal.valueOf(rent));
        h.setDepositType(depositType);
        h.setFacilities(facilities);
        h.setDescription(description == null
                ? title + "，" + String.join("、", facilities) + "，周边配套成熟，交通便捷，适合上班族居住。"
                : description);
        h.setLng(BigDecimal.valueOf(lng));
        h.setLat(BigDecimal.valueOf(lat));
        h.setViewCount(50 + (int) (Math.random() * 200));
        h.setStatus(status);
        houseMapper.insert(h);
        seedImages(h, images);
        return h;
    }

    /**
     * 把 classpath {@code seed-images/} 里的种子图片拷到上传目录并落 {@code house_image}，
     * 首图同步为封面（与 {@code HouseService.saveImages} 同一套约定）。拷贝失败只告警不回滚，
     * 种子数据不能因为图片目录不可写而播种失败；目标文件名加 {@code seed-} 前缀，与用户上传的 UUID 文件名隔离。
     */
    private void seedImages(House house, List<String> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                String name = "seed-" + files.get(i);
                Path target = dir.resolve(name);
                try (InputStream in = getClass().getResourceAsStream("/seed-images/" + files.get(i))) {
                    if (in == null) {
                        log.warn("种子图片缺失，跳过：{}", files.get(i));
                        continue;
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                String url = "/uploads/" + name;
                urls.add(url);
                HouseImage img = new HouseImage();
                img.setHouseId(house.getId());
                img.setUrl(url);
                img.setSort(i);
                img.setIsCover(i == 0 ? 1 : 0);
                imageMapper.insert(img);
            }
            if (!urls.isEmpty()) {
                houseMapper.update(null, new LambdaUpdateWrapper<House>()
                        .eq(House::getId, house.getId())
                        .set(House::getCoverUrl, urls.get(0)));
            }
        } catch (Exception e) {
            log.warn("种子图片写入失败（不影响房源数据）：{}", e.getMessage());
        }
    }

    /** 给指定标题的房源补驳回理由（仅用于已驳回演示房源） */
    private void rejectedReason(String title, String reason) {
        House h = houseMapper.selectOne(new LambdaQueryWrapper<House>().eq(House::getTitle, title).last("LIMIT 1"));
        if (h != null) {
            h.setRejectReason(reason);
            houseMapper.updateById(h);
        }
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

    private static final int ST_PENDING = 0;
    private static final int ST_REJECTED = 2;
    private static final int ST_ONLINE = 3;
    private static final int ST_OFFLINE = 4;
    private static final int ST_RENTED = 5;
}
