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
@Table(name = "caravans")
public class Caravan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "route_id")
    private Long routeId;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point currentPosition;

    @Column(name = "current_waypoint_id")
    private Long currentWaypointId;

    @Column(name = "speed_kmh")
    private Double speedKmh = 5.0;

    @Column(nullable = false)
    private String status = "IDLE";

    @Column(name = "cargo_type")
    private String cargoType;

    @Column(name = "cargo_weight_kg")
    private Double cargoWeightKg;

    @Column(name = "crew_count")
    private Integer crewCount;

    @Column(name = "camel_count")
    private Integer camelCount;

    @Column(name = "water_supply_liters")
    private Double waterSupplyLiters;

    @Column(name = "food_supply_days")
    private Double foodSupplyDays;

    @Column(name = "departure_time")
    private LocalDateTime departureTime;

    @Column(name = "estimated_arrival")
    private LocalDateTime estimatedArrival;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
