package com.silkroad.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.LineString;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "geometry(LineString,4326)")
    private LineString geom;

    @Column(name = "total_distance_km")
    private Double totalDistanceKm;

    @Column(name = "difficulty_level")
    private String difficultyLevel;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Waypoint> waypoints;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
