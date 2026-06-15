package com.silkroad.service;

import com.silkroad.dto.PathRequest;
import com.silkroad.dto.PathResult;
import com.silkroad.model.GridNode;
import com.silkroad.model.Season;
import com.silkroad.model.TerrainType;
import com.silkroad.repository.SeasonalRiskProfileRepository;
import com.silkroad.repository.TerrainGridRepository;
import com.silkroad.repository.WaterSourceRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PathfindingService {

    private final TerrainGridRepository terrainGridRepository;
    private final SeasonalRiskProfileRepository seasonalRiskProfileRepository;
    private final WaterSourceRepository waterSourceRepository;

    @Value("${pathfinding.grid-resolution:0.5}")
    private double gridResolution;

    @Value("${pathfinding.elevation-weight:0.3}")
    private double elevationWeight;

    @Value("${pathfinding.terrain-weight:0.4}")
    private double terrainWeight;

    @Value("${pathfinding.weather-weight:0.3}")
    private double weatherWeight;

    @Value("${pathfinding.max-iterations:100000}")
    private int maxIterations;

    private static final double EARTH_RADIUS_KM = 6371.0;

    public PathResult findOptimalPath(PathRequest request) {
        long startTime = System.currentTimeMillis();
        Season season = Season.fromCode(request.getSeason());

        double minLng = Math.min(request.getStartLng(), request.getEndLng()) - 2;
        double maxLng = Math.max(request.getStartLng(), request.getEndLng()) + 2;
        double minLat = Math.min(request.getStartLat(), request.getEndLat()) - 2;
        double maxLat = Math.max(request.getStartLat(), request.getEndLat()) + 2;

        int gridWidth = (int) Math.ceil((maxLng - minLng) / gridResolution) + 1;
        int gridHeight = (int) Math.ceil((maxLat - minLat) / gridResolution) + 1;

        GridNode[][] grid = initializeGrid(minLng, minLat, gridWidth, gridHeight);

        int startX = (int) Math.round((request.getStartLng() - minLng) / gridResolution);
        int startY = (int) Math.round((request.getStartLat() - minLat) / gridResolution);
        int endX = (int) Math.round((request.getEndLng() - minLng) / gridResolution);
        int endY = (int) Math.round((request.getEndLat() - minLat) / gridResolution);

        startX = clamp(startX, 0, gridWidth - 1);
        startY = clamp(startY, 0, gridHeight - 1);
        endX = clamp(endX, 0, gridWidth - 1);
        endY = clamp(endY, 0, gridHeight - 1);

        GridNode startNode = grid[startY][startX];
        GridNode endNode = grid[endY][endX];

        startNode.setGCost(0);
        startNode.setHCost(haversineDistance(startNode.getLng(), startNode.getLat(),
                endNode.getLng(), endNode.getLat()));
        startNode.calculateFCost();

        PriorityQueue<GridNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(GridNode::getFCost));
        Set<GridNode> closedSet = new HashSet<>();
        openSet.add(startNode);

        int iterations = 0;
        while (!openSet.isEmpty() && iterations < maxIterations) {
            GridNode current = openSet.poll();
            closedSet.add(current);

            if (current.equals(endNode)) {
                break;
            }

            for (GridNode neighbor : getNeighbors(grid, current, gridWidth, gridHeight)) {
                if (closedSet.contains(neighbor)) {
                    continue;
                }

                double movementCost = calculateMovementCost(current, neighbor, request, season);
                double tentativeGCost = current.getGCost() + movementCost;

                if (tentativeGCost < neighbor.getGCost() || !openSet.contains(neighbor)) {
                    neighbor.setGCost(tentativeGCost);
                    neighbor.setHCost(haversineDistance(neighbor.getLng(), neighbor.getLat(),
                            endNode.getLng(), endNode.getLat()));
                    neighbor.calculateFCost();
                    neighbor.setParent(current);

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
            iterations++;
        }

        return buildPathResult(startNode, endNode, request, season,
                System.currentTimeMillis() - startTime, iterations);
    }

    private GridNode[][] initializeGrid(double minLng, double minLat, int width, int height) {
        GridNode[][] grid = new GridNode[height][width];
        Random random = new Random(42);

        String[] terrainTypes = {"PLAINS", "HILLS", "DESERT", "DESERT_STEPPE", "OASIS", "MOUNTAINS", "SAND_DUNES"};

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double lng = minLng + x * gridResolution;
                double lat = minLat + y * gridResolution;

                GridNode node = new GridNode(lng, lat, x, y);
                node.setGCost(Double.MAX_VALUE);

                double noise = simplexNoise2D(x * 0.1, y * 0.1);
                double elevation = 500 + noise * 2000 + random.nextDouble() * 200;
                node.setElevationM(elevation);

                int terrainIndex = Math.abs((int) (noise * 7)) % terrainTypes.length;
                TerrainType terrain = TerrainType.fromCode(terrainTypes[terrainIndex]);

                if (isNearRiver(lng, lat)) {
                    terrain = TerrainType.OASIS;
                    node.setWaterAccessibility(0.8 + random.nextDouble() * 0.2);
                }

                if (elevation > 2500) {
                    terrain = TerrainType.HIGH_MOUNTAINS;
                } else if (elevation > 1800) {
                    terrain = TerrainType.MOUNTAINS;
                }

                node.setTerrainType(terrain.getCode());
                node.setPassability(terrain.getPassability());
                grid[y][x] = node;
            }
        }
        return grid;
    }

    private boolean isNearRiver(double lng, double lat) {
        double[] riverLngs = {103.83, 97.5, 87.5, 80.0, 77.0, 80.0, 67.0};
        double[] riverLats = {36.06, 40.5, 40.5, 37.0, 38.5, 43.3, 39.5};

        for (int i = 0; i < riverLngs.length; i++) {
            double dist = haversineDistance(lng, lat, riverLngs[i], riverLats[i]);
            if (dist < 15) return true;
        }
        return false;
    }

    private List<GridNode> getNeighbors(GridNode[][] grid, GridNode node, int width, int height) {
        List<GridNode> neighbors = new ArrayList<>();
        int[][] dirs = {{1,0}, {-1,0}, {0,1}, {0,-1}, {1,1}, {1,-1}, {-1,1}, {-1,-1}};

        for (int[] dir : dirs) {
            int nx = node.getGridX() + dir[0];
            int ny = node.getGridY() + dir[1];

            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                neighbors.add(grid[ny][nx]);
            }
        }
        return neighbors;
    }

    private double calculateMovementCost(GridNode from, GridNode to, PathRequest request, Season season) {
        double distance = haversineDistance(from.getLng(), from.getLat(), to.getLng(), to.getLat());
        if (distance <= 0) return 0;

        TerrainType fromTerrain = TerrainType.fromCode(from.getTerrainType());
        TerrainType toTerrain = TerrainType.fromCode(to.getTerrainType());
        double avgResistance = (fromTerrain.getResistanceFactor() + toTerrain.getResistanceFactor()) / 2.0;
        double terrainCost = distance * avgResistance * terrainWeight;

        double elevationDiff = Math.abs(to.getElevationM() - from.getElevationM());
        double elevationFactor = 1.0 + (elevationDiff / 500.0) * 0.5;
        double elevationCost = distance * elevationFactor * elevationWeight;

        double weatherFactor = getSeasonalWeatherFactor(season, to);
        double weatherCost = distance * weatherFactor * weatherWeight;

        double totalCost = terrainCost + elevationCost + weatherCost;

        double weightPenalty = request.getWeightPenalty() != null ? request.getWeightPenalty() : 1.0;
        totalCost *= weightPenalty;

        if (Boolean.TRUE.equals(request.getPreferOasis()) &&
                "OASIS".equalsIgnoreCase(to.getTerrainType())) {
            totalCost *= 0.7;
        }

        return Math.max(totalCost, distance * 0.1);
    }

    private double getSeasonalWeatherFactor(Season season, GridNode node) {
        double baseFactor = 1.0;
        String terrain = node.getTerrainType();

        switch (season) {
            case SPRING:
                if ("DESERT".equals(terrain) || "SAND_DUNES".equals(terrain)) {
                    baseFactor = 1.2;
                }
                break;
            case SUMMER:
                if ("DESERT".equals(terrain) || "SAND_DUNES".equals(terrain)) {
                    baseFactor = 1.5;
                }
                baseFactor += (node.getElevationM() / 2000.0) * 0.2;
                break;
            case AUTUMN:
                baseFactor = 1.1;
                break;
            case WINTER:
                if ("HIGH_MOUNTAINS".equals(terrain) || "MOUNTAINS".equals(terrain)) {
                    baseFactor = 2.0;
                }
                if ("DESERT".equals(terrain)) {
                    baseFactor = 1.3;
                }
                break;
        }
        return baseFactor;
    }

    private PathResult buildPathResult(GridNode start, GridNode end, PathRequest request,
                                        Season season, long computationTime, int iterations) {
        List<double[]> pathPoints = new ArrayList<>();
        List<String> waypointNames = new ArrayList<>();
        double totalDistance = 0;
        double elevationGain = 0;
        double totalRisk = 0;

        GridNode current = end;
        List<GridNode> reversePath = new ArrayList<>();

        if (current.getParent() == null && !current.equals(start)) {
            pathPoints.add(new double[]{end.getLng(), end.getLat()});
            pathPoints.add(new double[]{start.getLng(), start.getLat()});
            return PathResult.builder()
                    .pathPoints(pathPoints)
                    .totalDistanceKm(haversineDistance(start.getLng(), start.getLat(), end.getLng(), end.getLat()))
                    .estimatedHours(0.0)
                    .totalRiskScore(0.5)
                    .riskLevel("MODERATE")
                    .waypointNames(Collections.emptyList())
                    .elevationGainM(0.0)
                    .waterRequiredLiters(1000.0)
                    .algorithm("A* (no path found)")
                    .computationTimeMs(computationTime)
                    .build();
        }

        while (current != null) {
            reversePath.add(current);
            current = current.getParent();
        }
        Collections.reverse(reversePath);

        GridNode prev = null;
        for (GridNode node : reversePath) {
            pathPoints.add(new double[]{node.getLng(), node.getLat()});
            if ("OASIS".equalsIgnoreCase(node.getTerrainType())) {
                waypointNames.add("绿洲 " + waypointNames.size());
            }
            if (prev != null) {
                totalDistance += haversineDistance(prev.getLng(), prev.getLat(), node.getLng(), node.getLat());
                if (node.getElevationM() > prev.getElevationM()) {
                    elevationGain += node.getElevationM() - prev.getElevationM();
                }
                totalRisk += calculateRiskAtNode(node, season);
            }
            prev = node;
        }

        double avgRisk = pathPoints.size() > 1 ? totalRisk / (pathPoints.size() - 1) : 0;
        double speed = request.getCaravanSpeed() != null ? request.getCaravanSpeed() : 5.0;
        double estimatedHours = totalDistance / speed;
        double waterPerDay = 200;
        double waterRequired = estimatedHours / 24 * waterPerDay;

        String riskLevel = getRiskLevel(avgRisk);

        return PathResult.builder()
                .pathPoints(pathPoints)
                .totalDistanceKm(round2(totalDistance))
                .estimatedHours(round2(estimatedHours))
                .totalRiskScore(round2(avgRisk))
                .riskLevel(riskLevel)
                .waypointNames(waypointNames)
                .elevationGainM(round2(elevationGain))
                .waterRequiredLiters(round2(waterRequired))
                .algorithm("A* with terrain and weather heuristic")
                .computationTimeMs(computationTime)
                .build();
    }

    private double calculateRiskAtNode(GridNode node, Season season) {
        double terrainRisk = 1.0 - node.getPassability();
        double weatherRisk = getSeasonalWeatherFactor(season, node) - 1.0;
        return Math.min(1.0, terrainRisk * 0.6 + weatherRisk * 0.4);
    }

    private String getRiskLevel(double riskScore) {
        if (riskScore < 0.2) return "LOW";
        if (riskScore < 0.4) return "MODERATE";
        if (riskScore < 0.6) return "HIGH";
        return "EXTREME";
    }

    private double haversineDistance(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private double simplexNoise2D(double x, double y) {
        double n = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453;
        return (n - Math.floor(n)) * 2 - 1;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
