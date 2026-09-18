package com.rentagent.dto;

import com.rentagent.entity.House;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** 房源模块 DTO 集合（FR-05/06/07/09/10/11） */
public class HouseDto {

    public record SaveReq(
            @NotBlank String title,
            @NotBlank String community,
            @NotBlank String city,
            @NotBlank String district,
            @NotBlank String address,
            @NotBlank String layout,
            @NotNull @DecimalMin(value = "1", message = "面积过小") BigDecimal area,
            String orientation,
            String floorDesc,
            @NotNull @DecimalMin(value = "1", message = "租金过小") @DecimalMax(value = "1000000", message = "租金过大") BigDecimal rent,
            @NotBlank String depositType,
            List<String> facilities,
            String description,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            List<String> images) {
    }

    public record StatusReq(@NotBlank String action) { // 上架/下架
    }

    public record SearchReq(String keyword, String district, String layout, String orientation,
            BigDecimal rentMin, BigDecimal rentMax, List<String> facilities,
            BigDecimal lngMin, BigDecimal lngMax, BigDecimal latMin, BigDecimal latMax,
            String sort, // rent_asc / rent_desc / hot / new
            Long page, Long size) {
    }

    public record Item(House house, List<Image> images, String landlordName, Boolean favorited,
            Long reviewCount) {
        public record Image(Long id, String url, Integer sort, Boolean isCover) {
        }
    }

    public record AiFillReq(String title, String community, String layout, String imageFileName) {
    }

    public record AiFillVO(String description, String orientation, String floorDesc, List<String> facilities) {
    }
}
