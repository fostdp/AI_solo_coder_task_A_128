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
@Table(name = "waypoints")
public class Waypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "waypoint_order")
    private Integer waypointOrder;

    @Column(name = "is_oasis")
    private Boolean isOasis;

    @Column(name = "water_available")
    private Boolean waterAvailable;

    @Column(name = "supply_station")
    private Boolean supplyStation;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", insertable = false, updatable = false)
    private Route route;
}
