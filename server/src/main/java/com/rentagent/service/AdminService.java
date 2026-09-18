package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 后台管理（FR-22/23/24）：用户管理、审核工作台数据、统计看板 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final SysUserMapper userMapper;
    private final HouseMapper houseMapper;
    private final LeaseOrderMapper orderMapper;
    private final AuditLogMapper auditLogMapper;
    private final AdminChatService adminChatService;

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

    /** FR-22：禁用/启用（禁用后 JwtAuthFilter 校验状态，立即无法访问） */
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
    }

    /** FR-07：待审核房源列表 */
    public Page<House> pendingHouses(long page, long size) {
        return houseMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<House>()
                .eq(House::getStatus, HouseService.ST_PENDING).orderByAsc(House::getId));
    }

    /** FR-24：统计看板（按日/周/月聚合） */
    public Map<String, Object> dashboard(String granularity) {
        String fmt = switch (granularity == null ? "day" : granularity) {
            case "week" -> "%Y-%u";
            case "month" -> "%Y-%m";
            default -> "%Y-%m-%d";
        };
        Map<String, Object> vo = new HashMap<>();
        vo.put("userCount", userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, 1)));
        vo.put("landlordCount", userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, 2)));
        vo.put("houseCount", houseMapper.selectCount(null));
        vo.put("onlineCount", houseMapper.selectCount(new LambdaQueryWrapper<House>()
                .eq(House::getStatus, HouseService.ST_ONLINE)));
        vo.put("pendingCount", houseMapper.selectCount(new LambdaQueryWrapper<House>()
                .eq(House::getStatus, HouseService.ST_PENDING)));
        vo.put("rentedCount", houseMapper.selectCount(new LambdaQueryWrapper<House>()
                .eq(House::getStatus, HouseService.ST_RENTED)));
        vo.put("orderCount", orderMapper.selectCount(null));
        vo.put("userTrend", trend(userMapper, fmt, "sys_user", "role != 3"));
        vo.put("houseTrend", trend(houseMapper, fmt, "house", null));
        vo.put("orderTrend", trend(orderMapper, fmt, "lease_order", null));
        // FR-24 口径中的"AI 对话量"：会话数/消息数/工具调用量与新增趋势（含转人工会话数）
        vo.putAll(adminChatService.dashboardMetrics(fmt));
        return vo;
    }

    private <T> List<Map<String, Object>> trend(BaseMapper<T> mapper,
                                                String fmt, String table, String where) {
        QueryWrapper<T> w = new QueryWrapper<T>()
                .select("DATE_FORMAT(created_at, '" + fmt + "') AS period", "COUNT(*) AS cnt")
                .groupBy("period").orderByAsc("period").last("LIMIT 30");
        if (where != null) {
            w.apply(where);
        }
        return mapper.selectMaps(w);
    }

    public Page<AuditLog> auditLogs(long page, long size) {
        return auditLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getId));
    }
}
