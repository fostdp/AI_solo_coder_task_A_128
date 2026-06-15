package com.silkroad.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteDTO {

    private Long id;

    private String name;

    private String nameEn;

    private String description;

    private Double totalDistanceKm;

    private String difficultyLevel;

    private Integer waypointCount;

    private List<double[]> coordinates;
}
