package com.silkroad.route_planner.service;

import com.silkroad.dto.PathRequest;
import com.silkroad.dto.PathResult;
import com.silkroad.model.GridNode;
import com.silkroad.model.Season;
import com.silkroad.model.TerrainType;
import com.silkroad.repository.SeasonalRiskProfileRepository;
import com.silkroad.repository.TerrainGridRepository;
import com.silkroad.repository.WaterSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PlannerPathfindingService {

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
    private static final int GENETIC_POPULATION_SIZE = 50;
    private static final int GENETIC_MAX_GENERATIONS = 100;
    private static final double GENETIC_MUTATION_RATE = 0.15;
    private static final double GENETIC_CROSSOVER_RATE = 0.7;
    private static final double OASIS_BONUS_THRESHOLD = 50.0;

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

        int startX = clamp((int) Math.round((request.getStartLng() - minLng) / gridResolution), 0, gridWidth - 1);
        int startY = clamp((int) Math.round((request.getStartLat() - minLat) / gridResolution), 0, gridHeight - 1);
        int endX = clamp((int) Math.round((request.getEndLng() - minLng) / gridResolution), 0, gridWidth - 1);
        int endY = clamp((int) Math.round((request.getEndLat() - minLat) / gridResolution), 0, gridHeight - 1);

        GridNode startNode = grid[startY][startX];
        GridNode endNode = grid[endY][endX];

        PathResult aStarResult = runAStar(startNode, endNode, grid, gridWidth, gridHeight, request, season);

        List<double[]> oasisPoints = identifyOasisPoints(grid, minLng, minLat, gridWidth, gridHeight);
        PathResult geneticResult = null;
        String algorithmUsed = "A* with terrain and weather heuristic";

        if (oasisPoints.size() >= 2 && Boolean.TRUE.equals(request.getPreferOasis())) {
            try {
                geneticResult = runGeneticAlgorithm(
                        new double[]{request.getStartLng(), request.getStartLat()},
                        new double[]{request.getEndLng(), request.getEndLat()},
                        oasisPoints, request, season);
                algorithmUsed = "A* + Genetic Algorithm (oasis optimization)";
            } catch (Exception e) {
                geneticResult = null;
            }
        }

        PathResult finalResult;
        if (geneticResult != null && shouldUseGeneticResult(aStarResult, geneticResult)) {
            finalResult = geneticResult;
            algorithmUsed = "A* + Genetic Algorithm (oasis optimization)";
        } else {
            finalResult = aStarResult;
        }

        long computationTime = System.currentTimeMillis() - startTime;

        return PathResult.builder()
                .pathPoints(finalResult.getPathPoints())
                .totalDistanceKm(finalResult.getTotalDistanceKm())
                .estimatedHours(finalResult.getEstimatedHours())
                .totalRiskScore(finalResult.getTotalRiskScore())
                .riskLevel(finalResult.getRiskLevel())
                .elevationGainM(finalResult.getElevationGainM())
                .waterRequiredLiters(finalResult.getWaterRequiredLiters())
                .algorithmUsed(algorithmUsed)
                .computationTimeMs(computationTime)
                .build();
    }

    private boolean shouldUseGeneticResult(PathResult aStarResult, PathResult geneticResult) {
        if (geneticResult.getTotalRiskScore() < aStarResult.getTotalRiskScore() * 0.85) {
            return geneticResult.getTotalDistanceKm() <= aStarResult.getTotalDistanceKm() * 1.25;
        }
        return false;
    }

    private GridNode[][] initializeGrid(double minLng, double minLat, int width, int height) {
        GridNode[][] grid = new GridNode[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double lng = minLng + x * gridResolution;
                double lat = minLat + y * gridResolution;
                GridNode node = new GridNode();
                node.setLng(lng);
                node.setLat(lat);
                node.setX(x);
                node.setY(y);
                node.setTerrainType(inferTerrainType(lng, lat));
                node.setElevation(inferElevation(lng, lat));
                node.setWalkable(true);
                grid[y][x] = node;
            }
        }
        return grid;
    }

    private String inferTerrainType(double lng, double lat) {
        if (lng > 75 && lng < 90 && lat > 35 && lat < 42) return "DESERT";
        if (lng > 88 && lng < 96 && lat > 38 && lat < 43) return "SAND_DUNES";
        if (lng > 92 && lng < 105 && lat > 36 && lat < 43) return "DESERT_STEPPE";
        if (lat > 42) return "STEPPE";
        if (lng > 70 && lng < 80 && lat > 35 && lat < 40) return "MOUNTAINS";
        return "DESERT_STEPPE";
    }

    private double inferElevation(double lng, double lat) {
        if (lat > 39 && lat < 45 && lng > 74 && lng < 96) return 3000 + Math.random() * 1500;
        if (lat > 35 && lat < 39 && lng > 75 && lng < 80) return 4000 + Math.random() * 1500;
        if (lat > 35 && lat < 40 && lng > 95 && lng < 104) return 3000 + Math.random() * 1000;
        return 800 + Math.random() * 600;
    }

    private PathResult runAStar(GridNode start, GridNode end, GridNode[][] grid,
                                 int width, int height, PathRequest request, Season season) {
        PriorityQueue<GridNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(GridNode::getF));
        Set<String> closedSet = new HashSet<>();
        Map<String, Double> gScore = new HashMap<>();
        Map<String, GridNode> cameFrom = new HashMap<>();

        String startKey = start.getY() + "," + start.getX();
        gScore.put(startKey, 0.0);
        start.setH(haversineDistance(start.getLng(), start.getLat(), end.getLng(), end.getLat()));
        start.setF(start.getH());
        openSet.add(start);

        int iterations = 0;
        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            GridNode current = openSet.poll();
            String currentKey = current.getY() + "," + current.getX();

            if (current.getX() == end.getX() && current.getY() == end.getY()) {
                return buildPathResult(cameFrom, current, start, request, season);
            }

            closedSet.add(currentKey);

            for (int[] dir : new int[][]{{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1}}) {
                int nx = current.getX() + dir[0];
                int ny = current.getY() + dir[1];

                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                String neighborKey = ny + "," + nx;
                if (closedSet.contains(neighborKey)) continue;

                GridNode neighbor = grid[ny][nx];
                if (!neighbor.isWalkable()) continue;

                double moveCost = calculateMoveCost(current, neighbor, dir, season);
                double tentativeG = gScore.getOrDefault(currentKey, Double.MAX_VALUE) + moveCost;

                if (tentativeG < gScore.getOrDefault(neighborKey, Double.MAX_VALUE)) {
                    cameFrom.put(neighborKey, current);
                    gScore.put(neighborKey, tentativeG);
                    neighbor.setG(tentativeG);
                    neighbor.setH(haversineDistance(neighbor.getLng(), neighbor.getLat(), end.getLng(), end.getLat()));
                    neighbor.setF(tentativeG + neighbor.getH());
                    openSet.remove(neighbor);
                    openSet.add(neighbor);
                }
            }
        }

        return buildDirectPathResult(start, end, request, season);
    }

    private double calculateMoveCost(GridNode from, GridNode to, int[] dir, Season season) {
        double baseCost = (dir[0] != 0 && dir[1] != 0) ? 1.414 : 1.0;
        double distKm = haversineDistance(from.getLng(), from.getLat(), to.getLng(), to.getLat());
        baseCost *= distKm;

        double terrainCost = getTerrainCost(to.getTerrainType());
        double elevationCost = Math.max(0, to.getElevation() - from.getElevation()) * 0.01;
        double weatherCost = getSeasonWeatherCost(season, to.getTerrainType());

        return baseCost * (1 + terrainWeight * terrainCost + elevationWeight * elevationCost + weatherWeight * weatherCost);
    }

    private double getTerrainCost(String terrainType) {
        if (terrainType == null) return 0.5;
        switch (terrainType) {
            case "DESERT": case "SAND_DUNES": return 0.8;
            case "DESERT_STEPPE": return 0.5;
            case "STEPPE": return 0.3;
            case "OASIS": return -0.2;
            case "MOUNTAINS": return 1.5;
            case "HIGH_MOUNTAINS": return 2.0;
            default: return 0.5;
        }
    }

    private double getSeasonWeatherCost(Season season, String terrainType) {
        double seasonRisk = season == Season.SUMMER ? 0.8 : season == Season.SPRING ? 0.5 : 0.3;
        boolean isDesert = "DESERT".equals(terrainType) || "SAND_DUNES".equals(terrainType);
        return isDesert ? seasonRisk : seasonRisk * 0.3;
    }

    private PathResult buildPathResult(Map<String, GridNode> cameFrom, GridNode end,
                                        GridNode start, PathRequest request, Season season) {
        List<double[]> path = new ArrayList<>();
        GridNode current = end;
        while (current != null) {
            path.add(0, new double[]{current.getLng(), current.getLat()});
            String key = current.getY() + "," + current.getX();
            current = cameFrom.get(key);
        }

        path = smoothPath(path);
        return calculatePathStatistics(path, request, season);
    }

    private PathResult buildDirectPathResult(GridNode start, GridNode end,
                                               PathRequest request, Season season) {
        List<double[]> path = new ArrayList<>();
        path.add(new double[]{start.getLng(), start.getLat()});
        path.add(new double[]{end.getLng(), end.getLat()});
        return calculatePathStatistics(path, request, season);
    }

    private List<double[]> smoothPath(List<double[]> path) {
        if (path.size() <= 2) return path;
        List<double[]> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));
        for (int i = 1; i < path.size() - 1; i++) {
            double[] prev = path.get(i - 1);
            double[] curr = path.get(i);
            double[] next = path.get(i + 1);
            double[] smoothedPoint = new double[]{
                    curr[0] * 0.6 + (prev[0] + next[0]) * 0.2,
                    curr[1] * 0.6 + (prev[1] + next[1]) * 0.2
            };
            smoothed.add(smoothedPoint);
        }
        smoothed.add(path.get(path.size() - 1));
        return smoothed;
    }

    private PathResult calculatePathStatistics(List<double[]> path, PathRequest request, Season season) {
        double totalDistance = 0;
        double elevationGain = 0;
        double totalRisk = 0;
        double prevElevation = inferElevation(path.get(0)[0], path.get(0)[1]);

        for (int i = 1; i < path.size(); i++) {
            totalDistance += haversineDistance(path.get(i-1)[0], path.get(i-1)[1], path.get(i)[0], path.get(i)[1]);
            double currElevation = inferElevation(path.get(i)[0], path.get(i)[1]);
            if (currElevation > prevElevation) elevationGain += currElevation - prevElevation;
            prevElevation = currElevation;
            totalRisk += getSeasonWeatherCost(season, inferTerrainType(path.get(i)[0], path.get(i)[1]));
        }

        double avgRisk = path.size() > 1 ? totalRisk / (path.size() - 1) : 0;
        double speed = request.getCaravanSpeed() != null ? request.getCaravanSpeed() : 5.0;
        double estimatedHours = totalDistance / speed;
        double waterRequired = estimatedHours * 20 * 0.5;

        String riskLevel = avgRisk < 0.3 ? "LOW" : avgRisk < 0.5 ? "MODERATE" : avgRisk < 0.7 ? "HIGH" : "EXTREME";

        return PathResult.builder()
                .pathPoints(path)
                .totalDistanceKm(Math.round(totalDistance * 10.0) / 10.0)
                .estimatedHours(Math.round(estimatedHours * 10.0) / 10.0)
                .totalRiskScore(Math.round(avgRisk * 100.0) / 100.0)
                .riskLevel(riskLevel)
                .elevationGainM(Math.round(elevationGain))
                .waterRequiredLiters(Math.round(waterRequired))
                .build();
    }

    private List<double[]> identifyOasisPoints(GridNode[][] grid, double minLng, double minLat,
                                                 int width, int height) {
        List<double[]> oasisPoints = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridNode node = grid[y][x];
                if ("OASIS".equals(node.getTerrainType())) {
                    oasisPoints.add(new double[]{node.getLng(), node.getLat()});
                }
            }
        }
        return oasisPoints;
    }

    private PathResult runGeneticAlgorithm(double[] start, double[] end,
                                            List<double[]> oasisPoints,
                                            PathRequest request, Season season) {
        Random random = new Random(42);
        List<List<double[]>> population = new ArrayList<>();

        for (int i = 0; i < GENETIC_POPULATION_SIZE; i++) {
            List<double[]> individual = new ArrayList<>();
            individual.add(start.clone());
            List<double[]> shuffled = new ArrayList<>(oasisPoints);
            Collections.shuffle(shuffled, random);
            int count = 1 + random.nextInt(Math.min(shuffled.size(), 5));
            for (int j = 0; j < count; j++) {
                individual.add(shuffled.get(j).clone());
            }
            individual.add(end.clone());
            population.add(individual);
        }

        for (int gen = 0; gen < GENETIC_MAX_GENERATIONS; gen++) {
            List<Double> fitnesses = population.stream()
                    .map(ind -> calculateFitness(ind, request, season))
                    .collect(Collectors.toList());

            List<List<double[]>> newPopulation = new ArrayList<>();
            int bestIdx = IntStream.range(0, fitnesses.size())
                    .reduce((a, b) -> fitnesses.get(a) > fitnesses.get(b) ? a : b)
                    .orElse(0);
            newPopulation.add(new ArrayList<>(population.get(bestIdx)));

            while (newPopulation.size() < GENETIC_POPULATION_SIZE) {
                List<double[]> parent1 = tournamentSelect(population, fitnesses, random);
                List<double[]> parent2 = tournamentSelect(population, fitnesses, random);

                List<double[]> child;
                if (random.nextDouble() < GENETIC_CROSSOVER_RATE) {
                    child = orderCrossover(parent1, parent2, random);
                } else {
                    child = new ArrayList<>(parent1);
                }

                if (random.nextDouble() < GENETIC_MUTATION_RATE) {
                    mutate(child, oasisPoints, random);
                }

                newPopulation.add(child);
            }
            population = newPopulation;
        }

        List<double[]> bestPath = population.stream()
                .max(Comparator.comparingDouble(ind -> calculateFitness(ind, request, season)))
                .orElse(population.get(0));

        List<double[]> fullPath = interpolatePath(bestPath);
        return calculatePathStatistics(fullPath, request, season);
    }

    private double calculateFitness(List<double[]> path, PathRequest request, Season season) {
        double totalDistance = 0;
        double totalRisk = 0;
        int oasisCount = 0;

        for (int i = 1; i < path.size(); i++) {
            totalDistance += haversineDistance(path.get(i-1)[0], path.get(i-1)[1], path.get(i)[0], path.get(i)[1]);
            String terrain = inferTerrainType(path.get(i)[0], path.get(i)[1]);
            totalRisk += getSeasonWeatherCost(season, terrain);
            if ("OASIS".equals(terrain)) oasisCount++;
        }

        double avgRisk = path.size() > 1 ? totalRisk / (path.size() - 1) : 0;
        double oasisBonus = oasisCount * 15;
        double distancePenalty = totalDistance * 0.1;
        double riskPenalty = avgRisk * 100;

        return oasisBonus - distancePenalty - riskPenalty;
    }

    private List<double[]> tournamentSelect(List<List<double[]>> population,
                                             List<Double> fitnesses, Random random) {
        int tournamentSize = 3;
        int best = random.nextInt(population.size());
        for (int i = 1; i < tournamentSize; i++) {
            int candidate = random.nextInt(population.size());
            if (fitnesses.get(candidate) > fitnesses.get(best)) best = candidate;
        }
        return population.get(best);
    }

    private List<double[]> orderCrossover(List<double[]> parent1, List<double[]> parent2, Random random) {
        int len = Math.min(parent1.size(), parent2.size());
        if (len <= 3) return new ArrayList<>(parent1);

        int cut1 = 1 + random.nextInt(len - 2);
        int cut2 = cut1 + 1 + random.nextInt(len - cut1 - 1);

        List<double[]> child = new ArrayList<>();
        child.add(parent1.get(0));
        for (int i = cut1; i <= cut2 && i < parent1.size(); i++) {
            child.add(parent1.get(i).clone());
        }
        for (int i = 1; i < parent2.size() - 1; i++) {
            double[] point = parent2.get(i);
            boolean exists = child.stream().anyMatch(p ->
                    Math.abs(p[0] - point[0]) < 0.01 && Math.abs(p[1] - point[1]) < 0.01);
            if (!exists) child.add(child.size() - 1, point.clone());
        }
        if (child.size() < 2 || !Arrays.equals(child.get(child.size()-1), parent1.get(parent1.size()-1))) {
            child.add(parent1.get(parent1.size()-1).clone());
        }
        return child;
    }

    private void mutate(List<double[]> path, List<double[]> oasisPoints, Random random) {
        if (path.size() <= 2) return;
        int mutationType = random.nextInt(3);
        switch (mutationType) {
            case 0:
                if (!oasisPoints.isEmpty()) {
                    int insertPos = 1 + random.nextInt(path.size() - 1);
                    path.add(insertPos, oasisPoints.get(random.nextInt(oasisPoints.size())).clone());
                }
                break;
            case 1:
                if (path.size() > 3) {
                    path.remove(1 + random.nextInt(path.size() - 2));
                }
                break;
            case 2:
                if (path.size() > 3) {
                    int i = 1 + random.nextInt(path.size() - 2);
                    int j = 1 + random.nextInt(path.size() - 2);
                    if (i != j) Collections.swap(path, i, j);
                }
                break;
        }
    }

    private List<double[]> interpolatePath(List<double[]> waypoints) {
        List<double[]> fullPath = new ArrayList<>();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            double[] from = waypoints.get(i);
            double[] to = waypoints.get(i + 1);
            double dist = haversineDistance(from[0], from[1], to[0], to[1]);
            int steps = Math.max(2, (int) (dist / 5));
            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                fullPath.add(new double[]{
                        from[0] + (to[0] - from[0]) * t,
                        from[1] + (to[1] - from[1]) * t
                });
            }
        }
        fullPath.add(waypoints.get(waypoints.size() - 1));
        return fullPath;
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
