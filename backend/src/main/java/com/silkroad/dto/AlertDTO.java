package com.silkroad.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDTO {

    private Long id;

    private String alertType;

    private String severity;

    private String message;

    private Double lng;

    private Double lat;

    private Long routeId;

    private Long stationId;

    private Long caravanId;

    private LocalDateTime triggeredAt;

    private Boolean isActive;
}
