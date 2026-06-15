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
public class WeatherRiskAnalysis {

    private Long routeId;

    private String routeName;

    private String season;

    private Double overallRiskScore;

    private String riskLevel;

    private Double avgTemperature;

    private Double maxTemperature;

    private Double minTemperature;

    private Double sandstormProbability;

    private Double waterAvailabilityScore;

    private Integer recommendedTravelDays;

    private List<String> riskFactors;

    private List<String> recommendations;
}
