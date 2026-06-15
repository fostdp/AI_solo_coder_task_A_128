package com.silkroad.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "seasonal_risk_profiles")
public class SeasonalRiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(nullable = false)
    private String season;

    @Column(name = "avg_temperature_c")
    private Double avgTemperatureC;

    @Column(name = "max_temperature_c")
    private Double maxTemperatureC;

    @Column(name = "min_temperature_c")
    private Double minTemperatureC;

    @Column(name = "avg_precipitation_mm")
    private Double avgPrecipitationMm;

    @Column(name = "avg_wind_speed_kmh")
    private Double avgWindSpeedKmh;

    @Column(name = "sandstorm_frequency")
    private Double sandstormFrequency;

    @Column(name = "water_availability_pct")
    private Double waterAvailabilityPct;

    @Column(name = "overall_risk_score")
    private Double overallRiskScore;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(columnDefinition = "text")
    private String notes;
}
