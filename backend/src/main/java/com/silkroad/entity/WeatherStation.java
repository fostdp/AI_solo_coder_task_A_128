package com.silkroad.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weather_stations")
public class WeatherStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_code", unique = true, nullable = false)
    private String stationCode;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "coverage_radius_km")
    private Double coverageRadiusKm = 50.0;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "installed_at")
    private LocalDateTime installedAt;
}
