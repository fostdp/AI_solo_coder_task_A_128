package com.silkroad.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PathResult {

    private List<double[]> pathPoints;

    private Double totalDistanceKm;

    private Double estimatedHours;

    private Double totalRiskScore;

    private String riskLevel;

    private List<String> waypointNames;

    private Double elevationGainM;

    private Double waterRequiredLiters;

    private String algorithm;

    private Long computationTimeMs;
}
