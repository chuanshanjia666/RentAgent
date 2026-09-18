package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.PageVO;
import com.rentagent.dto.TradeDto;
import com.rentagent.entity.House;
import com.rentagent.entity.LeaseOrder;
import com.rentagent.entity.Review;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.LeaseOrderMapper;
import com.rentagent.mapper.ReviewMapper;
import com.rentagent.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 评价体系（FR-20）：仅完成合同（订单已退租/到期）可评，一单一评 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final LeaseOrderMapper orderMapper;
    private final HouseMapper houseMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notification;

    @Transactional
    public Review create(long leaseOrderId, int houseScore, int landlordScore, String content, long tenantId) {
        LeaseOrder order = orderMapper.selectById(leaseOrderId);
        if (order == null || order.getTenantId() != tenantId) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (order.getStatus() == 0) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED.getCode(), "合同履行完成后方可评价");
        }
        Review r = new Review();
        r.setLeaseOrderId(leaseOrderId);
        r.setHouseId(order.getHouseId());
        r.setLandlordId(order.getLandlordId());
        r.setTenantId(tenantId);
        r.setHouseScore(houseScore);
        r.setLandlordScore(landlordScore);
        r.setContent(content);
        r.setStatus(0);
        try {
            reviewMapper.insert(r);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED.getCode(), "该订单已评价过");
        }
        recalcAvg(order.getHouseId());
        notification.send(order.getLandlordId(), 9, "收到新评价",
                "您的房源收到新的评价：" + houseScore + " 星", "review", r.getId());
        return r;
    }

    public PageVO<TradeDto.ReviewVO> listByHouse(long houseId, long page, long size) {
        Page<Review> p = reviewMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<Review>()
                .eq(Review::getHouseId, houseId).eq(Review::getStatus, 0).orderByDesc(Review::getId));
        return PageVO.map(p, this::toVO);
    }

    private TradeDto.ReviewVO toVO(Review r) {
        SysUser tenant = userMapper.selectById(r.getTenantId());
        return new TradeDto.ReviewVO(r.getId(), r.getHouseScore(), r.getLandlordScore(), r.getContent(),
                r.getCreatedAt(), tenant == null ? "租客" : "租客" + tenant.getNickname());
    }

    /** 房源均评分冗余刷新（house.avg_score，展示与推荐用） */
    private void recalcAvg(Long houseId) {
        double avg = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getHouseId, houseId).eq(Review::getStatus, 0)).stream()
                .mapToInt(Review::getHouseScore).average().orElse(0);
        houseMapper.update(null, new LambdaUpdateWrapper<House>()
                .eq(House::getId, houseId)
                .set(House::getAvgScore, avg == 0 ? null : BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP)));
    }
}
