package com.silkroad.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PathRequest {

    private Double startLng;

    private Double startLat;

    private Double endLng;

    private Double endLat;

    private String season;

    private Double caravanSpeed;

    private Double weightPenalty;

    private Boolean preferOasis;
}
