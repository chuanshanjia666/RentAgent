package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.AdminChatDto;
import com.rentagent.dto.AdminDto;
import com.rentagent.entity.AuditLog;
import com.rentagent.entity.House;
import com.rentagent.entity.LeaseOrder;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.AuditLogMapper;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.LeaseOrderMapper;
import com.rentagent.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 后台管理（FR-22/23/24）：用户管理、审核工作台数据、统计看板 */
@Service
@RequiredArgsConstructor
public class AdminService {

    /** 趋势图最多展示的周期数（超出则只保留最近这些周期） */
    private static final int MAX_PERIODS = 30;

    private final SysUserMapper userMapper;
    private final HouseMapper houseMapper;
    private final LeaseOrderMapper orderMapper;
    private final AuditLogMapper auditLogMapper;
    private final AdminChatService adminChatService;
    private final AuditLogService auditLogService;

    /** FR-22：用户查询 */
    public Page<SysUser> users(String keyword, long page, long size) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysUser::getNickname, keyword).or().like(SysUser::getPhone, keyword)
                    .or().like(SysUser::getUsername, keyword));
        }
        w.orderByDesc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), w);
        p.getRecords().forEach(u -> u.setPassword(null));
        return p;
    }

    /** FR-22：禁用/启用（禁用后 JwtAuthFilter 校验状态，立即无法访问），并留痕 */
    public void setUserStatus(long id, boolean enabled, long adminId) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getRole() == 3) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "不能禁用管理员账号");
        }
        user.setStatus(enabled ? 1 : 0);
        userMapper.updateById(user);
        auditLogService.log(adminId, enabled ? "USER_ENABLE" : "USER_BAN", "user", id,
                Map.of("enabled", enabled), null);
    }

    /** FR-07：待审核房源列表 */
    public Page<House> pendingHouses(long page, long size) {
        return houseMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<House>()
                .eq(House::getStatus, HouseService.ST_PENDING).orderByAsc(House::getId));
    }

    /** FR-24：统计看板（按日/周/月聚合） */
    public AdminDto.DashboardVO dashboard(String granularity) {
        String fmt = switch (granularity == null ? "day" : granularity) {
            case "week" -> "%Y-%u";
            case "month" -> "%Y-%m";
            default -> "%Y-%m-%d";
        };
        // FR-24 口径中的"AI 对话量"：会话数/消息数/工具调用量与新增趋势（含转人工会话数）
        AdminChatDto.ChatMetricsVO ai = adminChatService.dashboardMetrics(fmt);
        return new AdminDto.DashboardVO(
                userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, 1)),
                userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, 2)),
                houseMapper.selectCount(null),
                houseMapper.selectCount(new LambdaQueryWrapper<House>().eq(House::getStatus, HouseService.ST_ONLINE)),
                houseMapper.selectCount(new LambdaQueryWrapper<House>().eq(House::getStatus, HouseService.ST_PENDING)),
                houseMapper.selectCount(new LambdaQueryWrapper<House>().eq(House::getStatus, HouseService.ST_RENTED)),
                orderMapper.selectCount(null),
                trend(userMapper, fmt, "sys_user", "role != 3"),
                trend(houseMapper, fmt, "house", null),
                trend(orderMapper, fmt, "lease_order", null),
                ai.chatCount(), ai.chatMessageCount(), ai.toolCallCount(), ai.transferredCount(), ai.chatTrend());
    }

    /**
     * 按周期聚合的增量趋势。
     * <p>
     * 取的是**最近** {@code MAX_PERIODS} 个周期：早先写成 {@code orderByAsc + LIMIT}，
     * 拿到的是全量数据里最早的那些周期，库龄超过 30 天后看板会永远停在陈年区间、看不到近期增长。
     */
    private <T> List<Map<String, Object>> trend(BaseMapper<T> mapper,
                                                String fmt, String table, String where) {
        QueryWrapper<T> w = new QueryWrapper<T>()
                .select("DATE_FORMAT(created_at, '" + fmt + "') AS period", "COUNT(*) AS cnt")
                .groupBy("period").orderByDesc("period").last("LIMIT " + MAX_PERIODS);
        if (where != null) {
            w.apply(where);
        }
        List<Map<String, Object>> rows = new ArrayList<>(mapper.selectMaps(w));
        // 图表按时间正序绘制，倒序取回后再翻正
        Collections.reverse(rows);
        return rows;
    }

    public Page<AuditLog> auditLogs(long page, long size) {
        return auditLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getId));
    }
}
