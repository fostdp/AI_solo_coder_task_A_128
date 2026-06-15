package com.silkroad.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weather_reports")
public class WeatherReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "report_time", nullable = false)
    private LocalDateTime reportTime;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "precipitation_mm")
    private Double precipitationMm;

    @Column(name = "wind_speed_kmh")
    private Double windSpeedKmh;

    @Column(name = "wind_direction")
    private Integer windDirection;

    @Column(name = "humidity_pct")
    private Double humidityPct;

    @Column(name = "sandstorm_probability")
    private Double sandstormProbability;

    @Column(name = "visibility_km")
    private Double visibilityKm;

    @Column(name = "air_pressure_hpa")
    private Double airPressureHpa;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
