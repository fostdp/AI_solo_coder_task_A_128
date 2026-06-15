package com.silkroad.service;

import com.silkroad.dto.HeatmapPoint;
import com.silkroad.dto.WeatherRiskAnalysis;
import com.silkroad.entity.SeasonalRiskProfile;
import com.silkroad.entity.WeatherReport;
import com.silkroad.entity.WeatherStation;
import com.silkroad.model.Season;
import com.silkroad.repository.RouteRepository;
import com.silkroad.repository.SeasonalRiskProfileRepository;
import com.silkroad.repository.WeatherReportRepository;
import com.silkroad.repository.WeatherStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeatherRiskService {

    private final WeatherStationRepository weatherStationRepository;
    private final WeatherReportRepository weatherReportRepository;
    private final SeasonalRiskProfileRepository seasonalRiskProfileRepository;
    private final RouteRepository routeRepository;

    private static final double SANDSTORM_WIND_THRESHOLD = 40.0;
    private static final double SANDSTORM_HUMIDITY_THRESHOLD = 30.0;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final double[][] MAJOR_MOUNTAINS = {
        {35.0, 75.0, 39.0, 80.0, 5500, "昆仑山"},
        {39.0, 74.0, 45.0, 96.0, 4000, "天山山脉"},
        {35.0, 95.0, 40.0, 104.0, 3500, "祁连山"},
        {32.0, 80.0, 36.0, 95.0, 6000, "喀喇昆仑山"},
        {40.0, 92.0, 42.0, 100.0, 2500, "马鬃山"}
    };

    private static final double[][] SAND_SOURCE_AREAS = {
        {38.0, 88.0, 42.0, 95.0, 0.9, "塔克拉玛干沙漠东缘"},
        {39.0, 95.0, 42.0, 102.0, 0.7, "巴丹吉林沙漠"},
        {40.0, 102.0, 42.0, 106.0, 0.6, "腾格里沙漠"},
        {38.0, 80.0, 40.0, 88.0, 0.85, "塔克拉玛干沙漠腹地"}
    };

    public double calculateSandstormProbability(double windSpeedKmh, double humidityPct,
                                                 double temperatureC, String terrainType, Season season,
                                                 double stationLng, double stationLat, double stationElevation) {
        double windFactor = Math.min(1.0, windSpeedKmh / SANDSTORM_WIND_THRESHOLD);
        double humidityFactor = Math.max(0.0, 1.0 - humidityPct / SANDSTORM_HUMIDITY_THRESHOLD);
        double tempFactor = Math.min(1.0, Math.max(0.0, (temperatureC - 10) / 30.0));

        double terrainFactor = 0.3;
        if (terrainType != null) {
            switch (terrainType.toUpperCase()) {
                case "DESERT":
                case "SAND_DUNES":
                    terrainFactor = 0.9;
                    break;
                case "DESERT_STEPPE":
                    terrainFactor = 0.7;
                    break;
                case "OASIS":
                    terrainFactor = 0.1;
                    break;
                case "MOUNTAINS":
                case "HIGH_MOUNTAINS":
                    terrainFactor = 0.2;
                    break;
            }
        }

        double seasonFactor = 0.5;
        if (season != null) {
            switch (season) {
                case SPRING:
                    seasonFactor = 0.7;
                    break;
                case SUMMER:
                    seasonFactor = 0.8;
                    break;
                case AUTUMN:
                    seasonFactor = 0.6;
                    break;
                case WINTER:
                    seasonFactor = 0.2;
                    break;
            }
        }

        double sandSourceFactor = calculateSandSourceFactor(stationLng, stationLat, windSpeedKmh, 180);
        double terrainBlockFactor = calculateTerrainBlockingFactor(stationLng, stationLat, stationElevation, windSpeedKmh, 180);
        double sandTransportFactor = calculateSandTransportFactor(windSpeedKmh, stationElevation);

        double probability = windFactor * 0.25 +
                            humidityFactor * 0.15 +
                            tempFactor * 0.1 +
                            terrainFactor * 0.12 +
                            seasonFactor * 0.08 +
                            sandSourceFactor * 0.2 +
                            sandTransportFactor * 0.1;

        probability *= terrainBlockFactor;

        return Math.min(1.0, Math.max(0.0, probability));
    }

    private double calculateSandSourceFactor(double lng, double lat, double windSpeed, double windDirection) {
        double sourceFactor = 0;
        double effectiveRadius = 300 + windSpeed * 5;

        for (double[] source : SAND_SOURCE_AREAS) {
            double sourceCenterLat = (source[0] + source[2]) / 2;
            double sourceCenterLng = (source[1] + source[3]) / 2;
            double sourceIntensity = source[4];

            double distance = haversineDistance(lng, lat, sourceCenterLng, sourceCenterLat);

            if (distance < effectiveRadius) {
                double bearingToSource = calculateBearing(lng, lat, sourceCenterLng, sourceCenterLat);
                double windAngleDiff = Math.abs(normalizeAngle(bearingToSource - windDirection));

                double alignmentFactor = Math.max(0, Math.cos(Math.toRadians(windAngleDiff)));
                double distanceFactor = 1.0 - Math.min(1.0, distance / effectiveRadius);
                double contribution = sourceIntensity * alignmentFactor * distanceFactor * 0.8;
                sourceFactor = Math.max(sourceFactor, contribution);
            }
        }
        return Math.min(1.0, sourceFactor);
    }

    private double calculateTerrainBlockingFactor(double stationLng, double stationLat,
                                                  double stationElevation, double windSpeed, double windDirection) {
        double blockFactor = 1.0;
        double upwindDirection = normalizeAngle(windDirection + 180);
        double sampleDistance = 50;
        int sampleCount = 10;

        double maxElevationDiff = 0;
        double maxBlockingAngle = 0;

        for (int i = 1; i <= sampleCount; i++) {
            double distance = sampleDistance * i;
            double[] upwindPoint = calculateDestinationPoint(stationLng, stationLat, upwindDirection, distance);
            double sampledElevation = getTerrainElevation(upwindPoint[0], upwindPoint[1]);
            double elevationDiff = sampledElevation - stationElevation;

            if (elevationDiff > 0) {
                double blockingAngle = Math.toDegrees(Math.atan2(elevationDiff, distance * 1000));
                if (blockingAngle > maxBlockingAngle) {
                    maxBlockingAngle = blockingAngle;
                }
                if (elevationDiff > maxElevationDiff) {
                    maxElevationDiff = elevationDiff;
                }
            }
        }

        for (double[] mountain : MAJOR_MOUNTAINS) {
            double mCenterLat = (mountain[0] + mountain[2]) / 2;
            double mCenterLng = (mountain[1] + mountain[3]) / 2;
            double mHeight = mountain[4];
            double distance = haversineDistance(stationLng, stationLat, mCenterLng, mCenterLat);

            if (distance < 500) {
                double bearingToMountain = calculateBearing(stationLng, stationLat, mCenterLng, mCenterLat);
                double angleDiff = Math.abs(normalizeAngle(bearingToMountain - upwindDirection));

                if (angleDiff < 45) {
                    double elevationDiff = mHeight - stationElevation;
                    if (elevationDiff > 0) {
                        double blockingAngle = Math.toDegrees(Math.atan2(elevationDiff, distance * 1000));
                        double windFactor = Math.min(1.0, windSpeed / 50.0);
                        double mountainBlock = Math.max(0, 1.0 - blockingAngle / 30.0 * (1.0 - windFactor * 0.5));
                        blockFactor = Math.min(blockFactor, mountainBlock);
                    }
                }
            }
        }

        if (maxBlockingAngle > 0) {
            double localBlock = Math.max(0, 1.0 - maxBlockingAngle / 20.0);
            blockFactor = Math.min(blockFactor, localBlock);
        }

        return Math.max(0.1, blockFactor);
    }

    private double getTerrainElevation(double lng, double lat) {
        double baseElevation = 1000;

        for (double[] mountain : MAJOR_MOUNTAINS) {
            if (lat >= mountain[0] && lat <= mountain[2] && lng >= mountain[1] && lng <= mountain[3]) {
                double distToCenter = Math.sqrt(
                    Math.pow(lng - (mountain[1] + mountain[3]) / 2, 2) +
                    Math.pow(lat - (mountain[0] + mountain[2]) / 2, 2)
                );
                double heightFactor = Math.max(0, 1.0 - distToCenter * 2);
                return mountain[4] * heightFactor + baseElevation * (1 - heightFactor);
            }
        }

        if (lng > 75 && lng < 90 && lat > 37 && lat < 42) {
            return 800 + Math.random() * 200;
        }
        if (lng > 90 && lng < 100 && lat > 38 && lat < 42) {
            return 1200 + Math.random() * 300;
        }
        if (lng > 74 && lng < 76 && lat > 39 && lat < 40) {
            return 3000 + Math.random() * 500;
        }
        return baseElevation + Math.random() * 500;
    }

    private double calculateSandTransportFactor(double windSpeed, double elevation) {
        double velocityFactor = Math.pow(Math.min(1.0, windSpeed / 50.0), 1.5);
        double elevationFactor = Math.max(0.3, 1.0 - elevation / 4000.0);
        return velocityFactor * elevationFactor;
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

    private double calculateBearing(double lng1, double lat1, double lng2, double lat2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double y = Math.sin(dLng) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);
        return Math.toDegrees(Math.atan2(y, x));
    }

    private double normalizeAngle(double angle) {
        while (angle < 0) angle += 360;
        while (angle >= 360) angle -= 360;
        return angle;
    }

    private double[] calculateDestinationPoint(double lng, double lat, double bearing, double distanceKm) {
        double latRad = Math.toRadians(lat);
        double lngRad = Math.toRadians(lng);
        double bearingRad = Math.toRadians(bearing);
        double distRatio = distanceKm / EARTH_RADIUS_KM;

        double destLat = Math.asin(Math.sin(latRad) * Math.cos(distRatio) +
                Math.cos(latRad) * Math.sin(distRatio) * Math.cos(bearingRad));
        double destLng = lngRad + Math.atan2(
                Math.sin(bearingRad) * Math.sin(distRatio) * Math.cos(latRad),
                Math.cos(distRatio) - Math.sin(latRad) * Math.sin(destLat)
        );

        return new double[]{Math.toDegrees(destLng), Math.toDegrees(destLat)};
    }

    public double calculateSandstormProbability(double windSpeedKmh, double humidityPct,
                                                 double temperatureC, String terrainType, Season season) {
        return calculateSandstormProbability(windSpeedKmh, humidityPct, temperatureC,
                terrainType, season, 90.0, 40.0, 1000.0);
    }

    public WeatherRiskAnalysis analyzeRouteRisk(Long routeId, String seasonStr) {
        Season season = Season.fromCode(seasonStr);
        List<WeatherStation> stations = weatherStationRepository.findByRouteId(routeId);

        Optional<SeasonalRiskProfile> profileOpt =
                seasonalRiskProfileRepository.findByRouteIdAndSeason(routeId, season.getCode());

        if (profileOpt.isEmpty()) {
            return WeatherRiskAnalysis.builder()
                    .routeId(routeId)
                    .season(season.getCode())
                    .overallRiskScore(0.5)
                    .riskLevel("MODERATE")
                    .riskFactors(Collections.singletonList("缺乏历史气候数据"))
                    .recommendations(Collections.singletonList("建议获取更多气象数据后再规划"))
                    .build();
        }

        SeasonalRiskProfile profile = profileOpt.get();

        List<String> riskFactors = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        double adjustedSandstormFreq = profile.getSandstormFrequency() != null
                ? profile.getSandstormFrequency() : 0.3;
        for (WeatherStation station : stations) {
            double blockFactor = calculateTerrainBlockingFactor(
                    station.getGeom().getX(), station.getGeom().getY(),
                    station.getElevationM() != null ? station.getElevationM() : 1000,
                    30, 180);
            adjustedSandstormFreq *= blockFactor;
        }
        adjustedSandstormFreq = Math.min(1.0, adjustedSandstormFreq);

        if (adjustedSandstormFreq > 0.5) {
            riskFactors.add("沙尘暴高发期，频率达" + Math.round(adjustedSandstormFreq * 100) + "%");
            recommendations.add("携带护目镜、口罩等防沙装备");
            recommendations.add("选择清晨或傍晚赶路，避开午后强风时段");
            recommendations.add("根据地形遮挡规划避风营地");
        } else if (adjustedSandstormFreq > 0.3) {
            riskFactors.add("沙尘暴风险中等，频率达" + Math.round(adjustedSandstormFreq * 100) + "%");
            recommendations.add("准备防沙装备，关注天气预报");
        }

        if (profile.getMaxTemperatureC() != null && profile.getMaxTemperatureC() > 40) {
            riskFactors.add("极端高温，最高达" + profile.getMaxTemperatureC() + "°C");
            recommendations.add("储备充足饮用水，每人每天不少于3升");
            recommendations.add("避免正午时段在沙漠中行进");
        }

        if (profile.getMinTemperatureC() != null && profile.getMinTemperatureC() < -15) {
            riskFactors.add("极端低温，最低达" + profile.getMinTemperatureC() + "°C");
            recommendations.add("携带厚实保暖装备，防止冻伤");
            recommendations.add("准备燃料用于取暖和融雪取水");
        }

        if (profile.getWaterAvailabilityPct() != null && profile.getWaterAvailabilityPct() < 0.3) {
            riskFactors.add("水源极度稀缺");
            recommendations.add("出发前加满所有水囊");
            recommendations.add("规划路线时优先经过绿洲");
        }

        if (profile.getAvgWindSpeedKmh() != null && profile.getAvgWindSpeedKmh() > 30) {
            riskFactors.add("平均风速较高，达" + profile.getAvgWindSpeedKmh() + "km/h");
            recommendations.add("加固帐篷，注意防风");
            recommendations.add("沿山体背风面行进可减少风阻");
        }

        int recommendedDays = estimateTravelDays(routeId, season);

        return WeatherRiskAnalysis.builder()
                .routeId(routeId)
                .routeName(routeRepository.findById(routeId).map(r -> r.getName()).orElse("未知"))
                .season(season.getCode())
                .overallRiskScore(profile.getOverallRiskScore())
                .riskLevel(profile.getRiskLevel())
                .avgTemperature(profile.getAvgTemperatureC())
                .maxTemperature(profile.getMaxTemperatureC())
                .minTemperature(profile.getMinTemperatureC())
                .sandstormProbability(adjustedSandstormFreq)
                .waterAvailabilityScore(profile.getWaterAvailabilityPct())
                .recommendedTravelDays(recommendedDays)
                .riskFactors(riskFactors)
                .recommendations(recommendations)
                .build();
    }

    private int estimateTravelDays(Long routeId, Season season) {
        return routeRepository.findById(routeId)
                .map(route -> {
                    double distance = route.getTotalDistanceKm() != null ? route.getTotalDistanceKm() : 1000;
                    double dailyDistance = 30.0;
                    switch (season) {
                        case SPRING:
                        case AUTUMN:
                            dailyDistance = 35.0;
                            break;
                        case SUMMER:
                            dailyDistance = 25.0;
                            break;
                        case WINTER:
                            dailyDistance = 20.0;
                            break;
                    }
                    return (int) Math.ceil(distance / dailyDistance);
                })
                .orElse(30);
    }

    public List<HeatmapPoint> generateSandstormHeatmap(LocalDateTime time) {
        List<HeatmapPoint> heatmap = new ArrayList<>();
        List<WeatherStation> stations = weatherStationRepository.findByIsActiveTrue();

        LocalDateTime queryTime = time != null ? time : LocalDateTime.now();
        LocalDateTime windowStart = queryTime.minusHours(2);

        for (WeatherStation station : stations) {
            List<WeatherReport> reports = weatherReportRepository
                    .findByStationIdAndReportTimeBetween(station.getId(), windowStart, queryTime);

            double sandstormProb;
            if (!reports.isEmpty()) {
                sandstormProb = reports.stream()
                        .mapToDouble(WeatherReport::getSandstormProbability)
                        .average()
                        .orElse(0.2);
            } else {
                sandstormProb = simulateSandstormProbability(station, queryTime);
            }

            double lng = station.getGeom().getX();
            double lat = station.getGeom().getY();
            double elevation = station.getElevationM() != null ? station.getElevationM() : 1000;

            double blockFactor = calculateTerrainBlockingFactor(lng, lat, elevation, 25, 180);
            sandstormProb *= (0.7 + blockFactor * 0.3);
            sandstormProb = Math.min(1.0, sandstormProb);

            heatmap.add(new HeatmapPoint(lng, lat, sandstormProb));

            double radius = station.getCoverageRadiusKm() != null ? station.getCoverageRadiusKm() : 50;
            for (int i = 0; i < 5; i++) {
                double angle = (i / 5.0) * 2 * Math.PI;
                double distance = radius * 0.6 * (0.7 + Math.random() * 0.3);
                double offsetLng = distance / 111.0 * Math.cos(angle) / Math.cos(Math.toRadians(lat));
                double offsetLat = distance / 111.0 * Math.sin(angle);
                double localBlock = calculateTerrainBlockingFactor(
                        lng + offsetLng, lat + offsetLat, elevation, 20, angle);
                heatmap.add(new HeatmapPoint(lng + offsetLng, lat + offsetLat,
                        sandstormProb * (0.5 + Math.random() * 0.3) * (0.5 + localBlock * 0.5)));
            }
        }

        return heatmap;
    }

    public List<HeatmapPoint> generateTemperatureHeatmap(LocalDateTime time) {
        List<HeatmapPoint> heatmap = new ArrayList<>();
        List<WeatherStation> stations = weatherStationRepository.findByIsActiveTrue();
        LocalDateTime queryTime = time != null ? time : LocalDateTime.now();
        LocalDateTime windowStart = queryTime.minusHours(2);

        for (WeatherStation station : stations) {
            List<WeatherReport> reports = weatherReportRepository
                    .findByStationIdAndReportTimeBetween(station.getId(), windowStart, queryTime);

            double temp;
            if (!reports.isEmpty()) {
                temp = reports.stream()
                        .mapToDouble(WeatherReport::getTemperatureC)
                        .average()
                        .orElse(20.0);
            } else {
                temp = simulateTemperature(station, queryTime);
            }

            double normalizedTemp = normalizeTemperature(temp);
            heatmap.add(new HeatmapPoint(station.getGeom().getX(), station.getGeom().getY(), normalizedTemp));
        }

        return heatmap;
    }

    private double simulateSandstormProbability(WeatherStation station, LocalDateTime time) {
        Season season = getSeasonFromMonth(time.getMonthValue());
        Random random = new Random(station.getId() + time.getDayOfYear());
        double baseProb = 0.2 + random.nextDouble() * 0.3;
        double seasonMultiplier;

        switch (season) {
            case SPRING:
                seasonMultiplier = 1.3;
                break;
            case SUMMER:
                seasonMultiplier = 1.5;
                break;
            case AUTUMN:
                seasonMultiplier = 1.1;
                break;
            case WINTER:
                seasonMultiplier = 0.4;
                break;
            default:
                seasonMultiplier = 1.0;
        }

        int hour = time.getHour();
        double hourMultiplier = 1.0;
        if (hour >= 12 && hour <= 18) {
            hourMultiplier = 1.3;
        } else if (hour >= 0 && hour <= 6) {
            hourMultiplier = 0.6;
        }

        double lng = station.getGeom().getX();
        double lat = station.getGeom().getY();
        double elevation = station.getElevationM() != null ? station.getElevationM() : 1000;
        double blockFactor = calculateTerrainBlockingFactor(lng, lat, elevation, 25, 180);

        return Math.min(1.0, baseProb * seasonMultiplier * hourMultiplier * (0.6 + blockFactor * 0.4));
    }

    private double simulateTemperature(WeatherStation station, LocalDateTime time) {
        Season season = getSeasonFromMonth(time.getMonthValue());
        Random random = new Random(station.getId() * 7 + time.getDayOfYear());
        double elevation = station.getElevationM() != null ? station.getElevationM() : 1000;
        double elevationCorrection = (elevation - 1000) / 1000 * 6;

        double baseTemp;
        switch (season) {
            case SPRING:
                baseTemp = 15;
                break;
            case SUMMER:
                baseTemp = 30;
                break;
            case AUTUMN:
                baseTemp = 12;
                break;
            case WINTER:
                baseTemp = -5;
                break;
            default:
                baseTemp = 15;
        }

        int hour = time.getHour();
        double dailyVariation = -5 * Math.cos((hour - 14) * Math.PI / 12);
        double randomVariation = random.nextGaussian() * 2;

        return baseTemp + dailyVariation - elevationCorrection + randomVariation;
    }

    private double normalizeTemperature(double temp) {
        double minTemp = -20;
        double maxTemp = 50;
        return Math.max(0, Math.min(1, (temp - minTemp) / (maxTemp - minTemp)));
    }

    private Season getSeasonFromMonth(int month) {
        if (month >= 3 && month <= 5) return Season.SPRING;
        if (month >= 6 && month <= 8) return Season.SUMMER;
        if (month >= 9 && month <= 11) return Season.AUTUMN;
        return Season.WINTER;
    }

    public Map<String, List<WeatherRiskAnalysis>> analyzeAllRoutesBySeason() {
        Map<String, List<WeatherRiskAnalysis>> result = new HashMap<>();
        for (Season season : Season.values()) {
            List<WeatherRiskAnalysis> analyses = new ArrayList<>();
            routeRepository.findAllByOrderByIdAsc().forEach(route -> {
                analyses.add(analyzeRouteRisk(route.getId(), season.getCode()));
            });
            result.put(season.getCode(), analyses);
        }
        return result;
    }
}
