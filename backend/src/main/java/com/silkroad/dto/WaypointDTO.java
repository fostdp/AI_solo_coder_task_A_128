package com.silkroad.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaypointDTO {

    private Long id;

    private Long routeId;

    private String name;

    private String nameEn;

    private Double lng;

    private Double lat;

    private Double elevationM;

    private Integer waypointOrder;

    private Boolean isOasis;

    private Boolean waterAvailable;

    private Boolean supplyStation;

    private String description;
}
