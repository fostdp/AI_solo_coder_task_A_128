package com.silkroad.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaravanStatusDTO {

    private Long caravanId;

    private String name;

    private String status;

    private Double lng;

    private Double lat;

    private Long routeId;

    private Double speedKmh;

    private Double waterSupplyLiters;

    private Double waterRemainingDays;

    private Double foodSupplyDays;

    private String cargoType;

    private LocalDateTime lastUpdate;

    private List<String> activeAlerts;
}
