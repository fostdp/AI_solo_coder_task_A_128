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
import java.util.stream.Collectors;

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

        int startX = clamp(startX = (int) Math.round((request.getStartLng() - minLng) / gridResolution), 0, gridWidth - 1);
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
                algorithmUsed = "A* + Genetic Algorithm (Oasis Optimization)";
            } catch (Exception e) {
                geneticResult = null;
            }
        }

        PathResult finalResult;
        if (geneticResult != null && geneticResult.getTotalRiskScore() != null &&
                aStarResult.getTotalRiskScore() != null &&
                geneticResult.getTotalRiskScore() < aStarResult.getTotalRiskScore() * 1.15 &&
                (geneticResult.getTotalDistanceKm() != null &&
                 aStarResult.getTotalDistanceKm() != null &&
                 geneticResult.getTotalDistanceKm() < aStarResult.getTotalDistanceKm() * 1.25)) {
            finalResult = PathResult.builder()
                    .pathPoints(geneticResult.getPathPoints())
                    .totalDistanceKm(geneticResult.getTotalDistanceKm())
                    .estimatedHours(geneticResult.getEstimatedHours())
                    .totalRiskScore(geneticResult.getTotalRiskScore())
                    .riskLevel(geneticResult.getRiskLevel())
                    .waypointNames(geneticResult.getWaypointNames())
                    .elevationGainM(geneticResult.getElevationGainM())
                    .waterRequiredLiters(geneticResult.getWaterRequiredLiters())
                    .algorithm(algorithmUsed)
                    .computationTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } else {
            finalResult = PathResult.builder()
                    .pathPoints(aStarResult.getPathPoints())
                    .totalDistanceKm(aStarResult.getTotalDistanceKm())
                    .estimatedHours(aStarResult.getEstimatedHours())
                    .totalRiskScore(aStarResult.getTotalRiskScore())
                    .riskLevel(aStarResult.getRiskLevel())
                    .waypointNames(aStarResult.getWaypointNames())
                    .elevationGainM(aStarResult.getElevationGainM())
                    .waterRequiredLiters(aStarResult.getWaterRequiredLiters())
                    .algorithm(algorithmUsed)
                    .computationTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        return finalResult;
    }

    private PathResult runAStar(GridNode startNode, GridNode endNode, GridNode[][] grid,
                                int gridWidth, int gridHeight, PathRequest request, Season season) {
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

        return buildPathResult(startNode, endNode, request, season, 0, iterations);
    }

    private PathResult runGeneticAlgorithm(double[] start, double[] end, List<double[]> oasisPoints,
                                            PathRequest request, Season season) {
        if (oasisPoints.size() > 15) {
            oasisPoints = filterRelevantOases(start, end, oasisPoints, 12);
        }

        List<List<double[]>> population = initializeGeneticPopulation(start, end, oasisPoints);

        double bestFitness = Double.NEGATIVE_INFINITY;
        List<double[]> bestPath = null;
        int generationsWithoutImprovement = 0;

        for (int gen = 0; gen < GENETIC_MAX_GENERATIONS; gen++) {
            List<Double> fitnessScores = new ArrayList<>();
            double maxFitness = Double.NEGATIVE_INFINITY;
            List<double[]> genBestPath = null;

            for (List<double[]> path : population) {
                double fitness = calculateFitness(path, request, season);
                fitnessScores.add(fitness);
                if (fitness > maxFitness) {
                    maxFitness = fitness;
                    genBestPath = path;
                }
            }

            if (maxFitness > bestFitness) {
                bestFitness = maxFitness;
                bestPath = new ArrayList<>(genBestPath);
                generationsWithoutImprovement = 0;
            } else {
                generationsWithoutImprovement++;
                if (generationsWithoutImprovement >= 15) {
                    break;
                }
            }

            List<List<double[]>> newPopulation = new ArrayList<>();
            newPopulation.add(bestPath);
            newPopulation.add(genBestPath);

            while (newPopulation.size() < GENETIC_POPULATION_SIZE) {
                List<double[]> parent1 = tournamentSelection(population, fitnessScores);
                List<double[]> parent2 = tournamentSelection(population, fitnessScores);

                if (Math.random() < GENETIC_CROSSOVER_RATE) {
                    List<List<double[]>> children = orderCrossover(parent1, parent2, start, end);
                    newPopulation.add(children.get(0));
                    if (newPopulation.size() < GENETIC_POPULATION_SIZE) {
                        newPopulation.add(children.get(1));
                    }
                } else {
                    newPopulation.add(new ArrayList<>(parent1));
                    if (newPopulation.size() < GENETIC_POPULATION_SIZE) {
                        newPopulation.add(new ArrayList<>(parent2));
                    }
                }

                if (Math.random() < GENETIC_MUTATION_RATE) {
                    mutate(newPopulation.get(newPopulation.size() - 1), oasisPoints, start, end);
                }
            }

            population = newPopulation;
        }

        if (bestPath == null) {
            return null;
        }

        bestPath = smoothPath(bestPath);
        return buildGeneticPathResult(bestPath, request, season);
    }

    private List<double[]> filterRelevantOases(double[] start, double[] end, List<double[]> oases, int maxCount) {
        List<double[]> filtered = new ArrayList<>(oases);
        filtered.sort((a, b) -> {
            double distA = haversineDistance(start[0], start[1], a[0], a[1]) +
                    haversineDistance(a[0], a[1], end[0], end[1]);
            double distB = haversineDistance(start[0], start[1], b[0], b[1]) +
                    haversineDistance(b[0], b[1], end[0], end[1]);
            return Double.compare(distA, distB);
        });
        return filtered.subList(0, Math.min(maxCount, filtered.size()));
    }

    private List<List<double[]>> initializeGeneticPopulation(double[] start, double[] end, List<double[]> oasisPoints) {
        List<List<double[]>> population = new ArrayList<>();

        List<double[]> directPath = new ArrayList<>();
        directPath.add(start);
        directPath.add(end);
        population.add(directPath);

        for (int i = 0; i < GENETIC_POPULATION_SIZE - 1; i++) {
            List<double[]> path = new ArrayList<>();
            path.add(start);

            int numOases = new Random().nextInt(Math.min(oasisPoints.size(), 6)) + 1;
            List<double[]> shuffled = new ArrayList<>(oasisPoints);
            Collections.shuffle(shuffled);

            for (int j = 0; j < numOases; j++) {
                path.add(shuffled.get(j));
            }

            sortPathByBearing(path, start, end);
            path.add(end);
            population.add(path);
        }

        return population;
    }

    private void sortPathByBearing(List<double[]> path, double[] start, double[] end) {
        if (path.size() <= 2) return;
        List<double[]> intermediate = path.subList(1, path.size());
        intermediate.sort((a, b) -> {
            double bearingA = calculateBearing(start[0], start[1], a[0], a[1]);
            double bearingB = calculateBearing(start[0], start[1], b[0], b[1]);
            return Double.compare(bearingA, bearingB);
        });
    }

    private double calculateBearing(double lng1, double lat1, double lng2, double lat2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double y = Math.sin(dLng) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);
        return (Math.atan2(y, x) + 2 * Math.PI) % (2 * Math.PI);
    }

    private double calculateFitness(List<double[]> path, PathRequest request, Season season) {
        double totalDistance = 0;
        double totalRisk = 0;
        int oasisCount = 0;
        double totalElevation = 0;

        for (int i = 1; i < path.size(); i++) {
            double[] prev = path.get(i - 1);
            double[] curr = path.get(i);

            double dist = haversineDistance(prev[0], prev[1], curr[0], curr[1]);
            totalDistance += dist;

            double terrainFactor = estimateTerrainFactor(curr[0], curr[1]);
            double weatherFactor = getSeasonalWeatherFactor(season, curr[0], curr[1], 1000);
            double risk = (terrainFactor * 0.6 + (weatherFactor - 1) * 0.4);
            totalRisk += risk * dist;

            if (isNearOasis(curr[0], curr[1])) {
                oasisCount++;
            }
        }

        double avgRisk = totalRisk / Math.max(1, totalDistance);
        double oasisBonus = oasisCount * 15;
        double distancePenalty = totalDistance * 0.1;
        double riskPenalty = avgRisk * 100;

        return oasisBonus - distancePenalty - riskPenalty;
    }

    private double estimateTerrainFactor(double lng, double lat) {
        if (isNearOasis(lng, lat)) return 0.1;

        double[] mountains = {{75.23, 39.72}, {106.16, 34.73}};
        for (double[] m : mountains) {
            if (haversineDistance(lng, lat, m[0], m[1]) < 50) {
                return 0.7;
            }
        }

        if (lng > 90 && lng < 100 && lat > 38 && lat < 42) {
            return 0.8;
        }

        return 0.4;
    }

    private boolean isNearOasis(double lng, double lat) {
        double[] oasisLngs = {94.66, 89.55, 85.54, 80.05, 76.87, 74.87, 66.96, 93.51, 88.23, 80.03};
        double[] oasisLats = {40.14, 40.51, 39.48, 36.95, 39.42, 40.12, 39.65, 42.83, 44.01, 43.27};

        for (int i = 0; i < oasisLngs.length; i++) {
            if (haversineDistance(lng, lat, oasisLngs[i], oasisLats[i]) < OASIS_BONUS_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private List<double[]> tournamentSelection(List<List<double[]>> population, List<Double> fitness) {
        Random rand = new Random();
        int i1 = rand.nextInt(population.size());
        int i2 = rand.nextInt(population.size());
        return fitness.get(i1) > fitness.get(i2) ? population.get(i1) : population.get(i2);
    }

    private List<List<double[]>> orderCrossover(List<double[]> parent1, List<double[]> parent2,
                                                 double[] start, double[] end) {
        List<double[]> mid1 = new ArrayList<>(parent1.subList(1, parent1.size() - 1));
        List<double[]> mid2 = new ArrayList<>(parent2.subList(1, parent2.size() - 1));

        Set<double[]> set1 = new LinkedHashSet<>(mid1);
        Set<double[]> set2 = new LinkedHashSet<>(mid2);

        Random rand = new Random();
        int cutPoint1 = rand.nextInt(Math.max(1, mid1.size()));
        int cutPoint2 = rand.nextInt(Math.max(1, mid2.size()));

        List<double[]> child1Mid = new ArrayList<>();
        List<double[]> child2Mid = new ArrayList<>();

        for (int i = 0; i < Math.min(cutPoint1, mid2.size()); i++) {
            child1Mid.add(mid2.get(i));
        }
        for (double[] point : mid1) {
            if (!containsPoint(child1Mid, point)) {
                child1Mid.add(point);
            }
        }

        for (int i = 0; i < Math.min(cutPoint2, mid1.size()); i++) {
            child2Mid.add(mid1.get(i));
        }
        for (double[] point : mid2) {
            if (!containsPoint(child2Mid, point)) {
                child2Mid.add(point);
            }
        }

        List<double[]> child1 = new ArrayList<>();
        child1.add(start);
        child1.addAll(child1Mid);
        child1.add(end);

        List<double[]> child2 = new ArrayList<>();
        child2.add(start);
        child2.addAll(child2Mid);
        child2.add(end);

        sortPathByBearing(child1, start, end);
        sortPathByBearing(child2, start, end);

        return Arrays.asList(child1, child2);
    }

    private boolean containsPoint(List<double[]> list, double[] point) {
        for (double[] p : list) {
            if (Math.abs(p[0] - point[0]) < 0.01 && Math.abs(p[1] - point[1]) < 0.01) {
                return true;
            }
        }
        return false;
    }

    private void mutate(List<double[]> path, List<double[]> oasisPoints, double[] start, double[] end) {
        if (path.size() <= 2) return;

        Random rand = new Random();
        int mutationType = rand.nextInt(3);

        switch (mutationType) {
            case 0:
                if (path.size() > 3 && oasisPoints.size() > 0) {
                    int insertPos = rand.nextInt(path.size() - 2) + 1;
                    double[] newOasis = oasisPoints.get(rand.nextInt(oasisPoints.size()));
                    if (!containsPoint(path, newOasis)) {
                        path.add(insertPos, newOasis);
                    }
                }
                break;
            case 1:
                if (path.size() > 3) {
                    int removePos = rand.nextInt(path.size() - 2) + 1;
                    path.remove(removePos);
                }
                break;
            case 2:
                if (path.size() > 4) {
                    int pos1 = rand.nextInt(path.size() - 2) + 1;
                    int pos2 = rand.nextInt(path.size() - 2) + 1;
                    if (pos1 != pos2) {
                        double[] temp = path.get(pos1);
                        path.set(pos1, path.get(pos2));
                        path.set(pos2, temp);
                    }
                }
                break;
        }

        sortPathByBearing(path, start, end);
    }

    private List<double[]> smoothPath(List<double[]> path) {
        if (path.size() <= 2) return path;

        List<double[]> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            double[] prev = path.get(i - 1);
            double[] curr = path.get(i);
            double[] next = path.get(i + 1);

            double distPrev = haversineDistance(prev[0], prev[1], curr[0], curr[1]);
            double distNext = haversineDistance(curr[0], curr[1], next[0], next[1]);
            double directDist = haversineDistance(prev[0], prev[1], next[0], next[1]);

            if (distPrev + distNext < directDist * 1.3 || isNearOasis(curr[0], curr[1])) {
                smoothed.add(curr);
            }
        }

        smoothed.add(path.get(path.size() - 1));
        return smoothed;
    }

    private PathResult buildGeneticPathResult(List<double[]> path, PathRequest request, Season season) {
        List<double[]> densePath = generateDensePath(path);
        List<String> waypointNames = new ArrayList<>();
        double totalDistance = 0;
        double elevationGain = 0;
        double totalRisk = 0;

        for (int i = 1; i < path.size(); i++) {
            double[] prev = path.get(i - 1);
            double[] curr = path.get(i);
            totalDistance += haversineDistance(prev[0], prev[1], curr[0], curr[1]);
            if (isNearOasis(curr[0], curr[1])) {
                waypointNames.add("绿洲补给点 " + waypointNames.size());
            }
        }

        for (int i = 1; i < densePath.size(); i++) {
            double[] prev = densePath.get(i - 1);
            double[] curr = densePath.get(i);
            double risk = 0.3 + estimateTerrainFactor(curr[0], curr[1]) * 0.5;
            totalRisk += risk;
        }

        double avgRisk = totalRisk / densePath.size();
        double speed = request.getCaravanSpeed() != null ? request.getCaravanSpeed() : 5.0;
        double estimatedHours = totalDistance / speed;
        double waterPerDay = 200;
        double waterRequired = estimatedHours / 24 * waterPerDay;

        return PathResult.builder()
                .pathPoints(densePath)
                .totalDistanceKm(round2(totalDistance))
                .estimatedHours(round2(estimatedHours))
                .totalRiskScore(round2(avgRisk))
                .riskLevel(getRiskLevel(avgRisk))
                .waypointNames(waypointNames)
                .elevationGainM(round2(elevationGain))
                .waterRequiredLiters(round2(waterRequired))
                .build();
    }

    private List<double[]> generateDensePath(List<double[]> sparsePath) {
        List<double[]> dense = new ArrayList<>();
        for (int i = 0; i < sparsePath.size() - 1; i++) {
            double[] from = sparsePath.get(i);
            double[] to = sparsePath.get(i + 1);
            double dist = haversineDistance(from[0], from[1], to[0], to[1]);
            int steps = Math.max(2, (int) (dist / 10));

            dense.add(from);
            for (int s = 1; s < steps; s++) {
                double ratio = (double) s / steps;
                dense.add(new double[]{
                        from[0] + (to[0] - from[0]) * ratio,
                        from[1] + (to[1] - from[1]) * ratio
                });
            }
        }
        dense.add(sparsePath.get(sparsePath.size() - 1));
        return dense;
    }

    private List<double[]> identifyOasisPoints(GridNode[][] grid, double minLng, double minLat,
                                               int width, int height) {
        List<double[]> oases = new ArrayList<>();
        double[] oasisLngs = {94.66, 89.55, 85.54, 80.05, 76.87, 74.87, 66.96, 93.51, 88.23, 80.03};
        double[] oasisLats = {40.14, 40.51, 39.48, 36.95, 39.42, 40.12, 39.65, 42.83, 44.01, 43.27};

        for (int i = 0; i < oasisLngs.length; i++) {
            oases.add(new double[]{oasisLngs[i], oasisLats[i]});
        }
        return oases;
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
        return getSeasonalWeatherFactor(season, node.getLng(), node.getLat(), node.getElevationM());
    }

    private double getSeasonalWeatherFactor(Season season, double lng, double lat, double elevation) {
        double baseFactor = 1.0;
        String terrain = estimateTerrainType(lng, lat, elevation);

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
                baseFactor += (elevation / 2000.0) * 0.2;
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

    private String estimateTerrainType(double lng, double lat, double elevation) {
        if (isNearOasis(lng, lat)) return "OASIS";
        if (elevation > 3000) return "HIGH_MOUNTAINS";
        if (elevation > 1800) return "MOUNTAINS";
        if (lng > 90 && lng < 100 && lat > 38 && lat < 42) return "DESERT";
        return "DESERT_STEPPE";
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
