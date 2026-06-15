package com.silkroad.controller;

import com.silkroad.dto.RouteDTO;
import com.silkroad.dto.WaypointDTO;
import com.silkroad.entity.Route;
import com.silkroad.entity.Waypoint;
import com.silkroad.repository.RouteRepository;
import com.silkroad.repository.WaypointRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteRepository routeRepository;
    private final WaypointRepository waypointRepository;

    @GetMapping
    public List<RouteDTO> getAllRoutes() {
        return routeRepository.findAllByOrderByIdAsc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public RouteDTO getRouteById(@PathVariable Long id) {
        return routeRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    @GetMapping("/{id}/waypoints")
    public List<WaypointDTO> getRouteWaypoints(@PathVariable Long id) {
        return waypointRepository.findByRouteIdOrderByWaypointOrderAsc(id).stream()
                .map(this::toWaypointDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public Route createRoute(@RequestBody RouteDTO dto) {
        Route route = new Route();
        route.setName(dto.getName());
        route.setNameEn(dto.getNameEn());
        route.setDescription(dto.getDescription());
        route.setTotalDistanceKm(dto.getTotalDistanceKm());
        route.setDifficultyLevel(dto.getDifficultyLevel());
        return routeRepository.save(route);
    }

    private RouteDTO toDTO(Route route) {
        List<double[]> coords = new ArrayList<>();
        if (route.getGeom() != null) {
            LineString line = route.getGeom();
            for (Coordinate coord : line.getCoordinates()) {
                coords.add(new double[]{coord.x, coord.y});
            }
        }

        Integer waypointCount = waypointRepository.findByRouteIdOrderByWaypointOrderAsc(route.getId()).size();

        return RouteDTO.builder()
                .id(route.getId())
                .name(route.getName())
                .nameEn(route.getNameEn())
                .description(route.getDescription())
                .totalDistanceKm(route.getTotalDistanceKm())
                .difficultyLevel(route.getDifficultyLevel())
                .waypointCount(waypointCount)
                .coordinates(coords)
                .build();
    }

    private WaypointDTO toWaypointDTO(Waypoint wp) {
        Point p = wp.getGeom();
        return WaypointDTO.builder()
                .id(wp.getId())
                .routeId(wp.getRouteId())
                .name(wp.getName())
                .nameEn(wp.getNameEn())
                .lng(p != null ? p.getX() : null)
                .lat(p != null ? p.getY() : null)
                .elevationM(wp.getElevationM())
                .waypointOrder(wp.getWaypointOrder())
                .isOasis(wp.getIsOasis())
                .waterAvailable(wp.getWaterAvailable())
                .supplyStation(wp.getSupplyStation())
                .description(wp.getDescription())
                .build();
    }
}
