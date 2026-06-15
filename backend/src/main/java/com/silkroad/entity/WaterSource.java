package com.silkroad.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "water_sources")
public class WaterSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    @Column(name = "source_type")
    private String sourceType;

    private String reliability;

    @Column(name = "average_flow_liters_per_day")
    private Double averageFlowLitersPerDay;

    @Column(name = "is_permanent")
    private Boolean isPermanent;

    @Column(name = "nearest_waypoint_id")
    private Long nearestWaypointId;
}
