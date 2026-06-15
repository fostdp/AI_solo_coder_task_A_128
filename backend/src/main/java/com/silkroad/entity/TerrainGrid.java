package com.silkroad.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Polygon;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "terrain_grids")
public class TerrainGrid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon geom;

    @Column(name = "grid_row")
    private Integer gridRow;

    @Column(name = "grid_col")
    private Integer gridCol;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "terrain_type")
    private String terrainType;

    @Column
    private Double passability;

    @Column(name = "water_accessibility")
    private Double waterAccessibility;

    @Column(name = "vegetation_index")
    private Double vegetationIndex;
}
