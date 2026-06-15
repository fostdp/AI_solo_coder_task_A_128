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
public class WeatherReportDTO {

    private Long stationId;

    private String stationCode;

    private LocalDateTime reportTime;

    private Double temperatureC;

    private Double precipitationMm;

    private Double windSpeedKmh;

    private Integer windDirection;

    private Double humidityPct;

    private Double sandstormProbability;

    private Double visibilityKm;

    private Double lng;

    private Double lat;
}
