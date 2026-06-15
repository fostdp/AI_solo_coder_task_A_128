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
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(nullable = false)
    private String severity;

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "caravan_id")
    private Long caravanId;

    @Column(columnDefinition = "text")
    private String message;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
