package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.entity.House;
import com.rentagent.entity.Report;
import com.rentagent.entity.Review;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.ReportMapper;
import com.rentagent.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 举报处理（FR-23）：处理留痕 + 通知双方 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper mapper;
    private final HouseMapper houseMapper;
    private final ReviewMapper reviewMapper;
    private final NotificationService notification;

    public Report create(long reporterId, int targetType, long targetId, String reason) {
        Report r = new Report();
        r.setReporterId(reporterId);
        r.setTargetType(targetType);
        r.setTargetId(targetId);
        r.setReason(reason);
        r.setStatus(0);
        mapper.insert(r);
        return r;
    }

    /** 管理员处理：评价类举报直接隐藏评价；房源类举报通知房东整改 */
    @Transactional
    public void handle(long id, String remark, long adminId) {
        Report r = mapper.selectById(id);
        if (r == null) {
            throw new BizException(1006, "举报不存在");
        }
        if (r.getStatus() != 0) {
            throw new BizException(1000, "该举报已处理");
        }
        r.setStatus(1);
        r.setHandleBy(adminId);
        r.setHandleRemark(remark);
        r.setHandledAt(LocalDateTime.now());
        mapper.updateById(r);

        if (r.getTargetType() == 2) {
            Review review = reviewMapper.selectById(r.getTargetId());
            if (review != null) {
                review.setStatus(1);
                reviewMapper.updateById(review);
            }
        } else if (r.getTargetType() == 1) {
            House house = houseMapper.selectById(r.getTargetId());
            if (house != null) {
                house.setStatus(HouseService.ST_OFFLINE);
                houseMapper.updateById(house);
                notification.send(house.getLandlordId(), 5, "房源被举报下架",
                        "「" + house.getTitle() + "」因被举报已下架，处理意见：" + remark, "house", house.getId());
            }
        }
        notification.send(r.getReporterId(), 5, "举报已处理", "您提交的举报已处理：" + remark, "report", r.getId());
    }

    public Page<Report> page(int status, long page, long size) {
        return mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<Report>()
                .eq(status >= 0, Report::getStatus, status).orderByAsc(Report::getStatus)
                .orderByDesc(Report::getId));
    }

    public Map<String, Object> toVO(Report r) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("report", r);
        if (r.getTargetType() == 1) {
            House house = houseMapper.selectById(r.getTargetId());
            vo.put("targetTitle", house == null ? "(已删除)" : house.getTitle());
        } else if (r.getTargetType() == 2) {
            Review review = reviewMapper.selectById(r.getTargetId());
            vo.put("targetTitle", review == null ? "(已删除)" : "评价：" + review.getContent());
        } else {
            vo.put("targetTitle", "用户 #" + r.getTargetId());
        }
        return vo;
    }
}
