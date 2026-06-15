package com.silkroad.weather_risk_analyzer.service;

import com.silkroad.common.event.RiskAssessmentEvent;
import com.silkroad.common.event.WeatherReportReceivedEvent;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyzerWeatherRiskService {

    private final WeatherStationRepository weatherStationRepository;
    private final WeatherReportRepository weatherReportRepository;
    private final SeasonalRiskProfileRepository seasonalRiskProfileRepository;
    private final RouteRepository routeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${terrain.mountains:}")
    private List<Map<String, Object>> mountainsConfig;

    @Value("${terrain.sand-sources:}")
    private List<Map<String, Object>> sandSourcesConfig;

    @Value("${weather.sandstorm.wind-threshold:40.0}")
    private double sandstormWindThreshold;

    @Value("${weather.sandstorm.humidity-threshold:30.0}")
    private double sandstormHumidityThreshold;

    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final double[][] MAJOR_MOUNTAINS = {
        {35.0, 75.0, 39.0, 80.0, 5500},
        {39.0, 74.0, 45.0, 96.0, 4000},
        {35.0, 95.0, 40.0, 104.0, 3500},
        {32.0, 80.0, 36.0, 95.0, 6000},
        {40.0, 92.0, 42.0, 100.0, 2500}
    };

    private static final double[][] SAND_SOURCE_AREAS = {
        {38.0, 88.0, 42.0, 95.0, 0.9},
        {39.0, 95.0, 42.0, 102.0, 0.7},
        {40.0, 102.0, 42.0, 106.0, 0.6},
        {38.0, 80.0, 40.0, 88.0, 0.85}
    };

    @EventListener
    public void onWeatherReportReceived(WeatherReportReceivedEvent event) {
        Long stationId = event.getStationId();
        Double windSpeed = event.getWindSpeedKmh();
        Double humidity = event.getHumidityPct();
        Double temperature = event.getTemperatureC();

        if (windSpeed == null || humidity == null || temperature == null) return;

        WeatherStation station = weatherStationRepository.findById(stationId).orElse(null);
        if (station == null) return;

        double lng = station.getGeom().getX();
        double lat = station.getGeom().getY();
        double elevation = station.getElevationM() != null ? station.getElevationM() : 1000;
        String terrain = inferTerrain(station);
        Season season = getSeasonFromMonth(LocalDateTime.now().getMonthValue());

        double sandstormProb = calculateSandstormProbability(
                windSpeed, humidity, temperature, terrain, season, lng, lat, elevation);

        String riskLevel;
        if (sandstormProb >= 0.7) riskLevel = "EXTREME";
        else if (sandstormProb >= 0.5) riskLevel = "HIGH";
        else if (sandstormProb >= 0.3) riskLevel = "MODERATE";
        else riskLevel = "LOW";

        if ("HIGH".equals(riskLevel) || "EXTREME".equals(riskLevel)) {
            eventPublisher.publishEvent(new RiskAssessmentEvent(
                    this, stationId, sandstormProb, windSpeed, temperature, riskLevel));
            log.info("风险评估事件发布, stationId={}, riskLevel={}, sandstormProb={}",
                    stationId, riskLevel, String.format("%.2f", sandstormProb));
        }
    }

    public double calculateSandstormProbability(double windSpeedKmh, double humidityPct,
                                                 double temperatureC, String terrainType, Season season,
                                                 double stationLng, double stationLat, double stationElevation) {
        double windFactor = Math.min(1.0, windSpeedKmh / sandstormWindThreshold);
        double humidityFactor = Math.max(0.0, 1.0 - humidityPct / sandstormHumidityThreshold);
        double tempFactor = Math.min(1.0, Math.max(0.0, (temperatureC - 10) / 30.0));

        double terrainFactor = getTerrainFactor(terrainType);
        double seasonFactor = getSeasonFactor(season);
        double sandSourceFactor = calculateSandSourceFactor(stationLng, stationLat, windSpeedKmh, 180);
        double terrainBlockFactor = calculateTerrainBlockingFactor(stationLng, stationLat, stationElevation, windSpeedKmh, 180);
        double sandTransportFactor = calculateSandTransportFactor(windSpeedKmh, stationElevation);

        double probability = windFactor * 0.25 + humidityFactor * 0.15 +
                tempFactor * 0.1 + terrainFactor * 0.12 +
                seasonFactor * 0.08 + sandSourceFactor * 0.2 +
                sandTransportFactor * 0.1;

        probability *= terrainBlockFactor;
        return Math.min(1.0, Math.max(0.0, probability));
    }

    private double getTerrainFactor(String terrainType) {
        if (terrainType == null) return 0.3;
        switch (terrainType.toUpperCase()) {
            case "DESERT": case "SAND_DUNES": return 0.9;
            case "DESERT_STEPPE": return 0.7;
            case "OASIS": return 0.1;
            case "MOUNTAINS": case "HIGH_MOUNTAINS": return 0.2;
            default: return 0.3;
        }
    }

    private double getSeasonFactor(Season season) {
        if (season == null) return 0.5;
        switch (season) {
            case SPRING: return 0.7;
            case SUMMER: return 0.8;
            case AUTUMN: return 0.6;
            case WINTER: return 0.2;
            default: return 0.5;
        }
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
        double maxBlockingAngle = 0;

        for (int i = 1; i <= 10; i++) {
            double distance = 50 * i;
            double[] upwindPoint = calculateDestinationPoint(stationLng, stationLat, upwindDirection, distance);
            double sampledElevation = getTerrainElevation(upwindPoint[0], upwindPoint[1]);
            double elevationDiff = sampledElevation - stationElevation;
            if (elevationDiff > 0) {
                double blockingAngle = Math.toDegrees(Math.atan2(elevationDiff, distance * 1000));
                if (blockingAngle > maxBlockingAngle) maxBlockingAngle = blockingAngle;
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
                        Math.pow(lat - (mountain[0] + mountain[2]) / 2, 2));
                double heightFactor = Math.max(0, 1.0 - distToCenter * 2);
                return mountain[4] * heightFactor + baseElevation * (1 - heightFactor);
            }
        }
        if (lng > 75 && lng < 90 && lat > 37 && lat < 42) return 800 + Math.random() * 200;
        if (lng > 90 && lng < 100 && lat > 38 && lat < 42) return 1200 + Math.random() * 300;
        return baseElevation + Math.random() * 500;
    }

    private double calculateSandTransportFactor(double windSpeed, double elevation) {
        double velocityFactor = Math.pow(Math.min(1.0, windSpeed / 50.0), 1.5);
        double elevationFactor = Math.max(0.3, 1.0 - elevation / 4000.0);
        return velocityFactor * elevationFactor;
    }

    public WeatherRiskAnalysis analyzeRouteRisk(Long routeId, String seasonStr) {
        Season season = Season.fromCode(seasonStr);
        List<WeatherStation> stations = weatherStationRepository.findByRouteId(routeId);
        Optional<SeasonalRiskProfile> profileOpt = seasonalRiskProfileRepository.findByRouteIdAndSeason(routeId, season.getCode());

        if (profileOpt.isEmpty()) {
            return WeatherRiskAnalysis.builder()
                    .routeId(routeId).season(season.getCode())
                    .overallRiskScore(0.5).riskLevel("MODERATE")
                    .riskFactors(Collections.singletonList("缺乏历史气候数据"))
                    .recommendations(Collections.singletonList("建议获取更多气象数据后再规划"))
                    .build();
        }

        SeasonalRiskProfile profile = profileOpt.get();
        List<String> riskFactors = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        double adjustedSandstormFreq = profile.getSandstormFrequency() != null ? profile.getSandstormFrequency() : 0.3;
        for (WeatherStation station : stations) {
            double blockFactor = calculateTerrainBlockingFactor(
                    station.getGeom().getX(), station.getGeom().getY(),
                    station.getElevationM() != null ? station.getElevationM() : 1000, 30, 180);
            adjustedSandstormFreq *= blockFactor;
        }
        adjustedSandstormFreq = Math.min(1.0, adjustedSandstormFreq);

        if (adjustedSandstormFreq > 0.5) {
            riskFactors.add("沙尘暴高发期，频率达" + Math.round(adjustedSandstormFreq * 100) + "%");
            recommendations.add("携带护目镜、口罩等防沙装备");
            recommendations.add("根据地形遮挡规划避风营地");
        } else if (adjustedSandstormFreq > 0.3) {
            riskFactors.add("沙尘暴风险中等，频率达" + Math.round(adjustedSandstormFreq * 100) + "%");
            recommendations.add("准备防沙装备，关注天气预报");
        }

        if (profile.getMaxTemperatureC() != null && profile.getMaxTemperatureC() > 40) {
            riskFactors.add("极端高温，最高达" + profile.getMaxTemperatureC() + "°C");
            recommendations.add("储备充足饮用水");
        }
        if (profile.getMinTemperatureC() != null && profile.getMinTemperatureC() < -15) {
            riskFactors.add("极端低温，最低达" + profile.getMinTemperatureC() + "°C");
            recommendations.add("携带保暖装备");
        }
        if (profile.getWaterAvailabilityPct() != null && profile.getWaterAvailabilityPct() < 0.3) {
            riskFactors.add("水源极度稀缺");
            recommendations.add("出发前加满水囊，优先经过绿洲");
        }

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
                .riskFactors(riskFactors)
                .recommendations(recommendations)
                .build();
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
                sandstormProb = reports.stream().mapToDouble(WeatherReport::getSandstormProbability).average().orElse(0.2);
            } else {
                sandstormProb = 0.2 + Math.random() * 0.3;
            }
            double lng = station.getGeom().getX();
            double lat = station.getGeom().getY();
            double elevation = station.getElevationM() != null ? station.getElevationM() : 1000;
            double blockFactor = calculateTerrainBlockingFactor(lng, lat, elevation, 25, 180);
            sandstormProb *= (0.7 + blockFactor * 0.3);
            sandstormProb = Math.min(1.0, sandstormProb);
            heatmap.add(new HeatmapPoint(lng, lat, sandstormProb));

            double radius = station.getCoverageRadiusKm() != null ? station.getCoverageRadiusKm() : 50;
            for (int i = 0; i < 4; i++) {
                double angle = (i / 4.0) * 2 * Math.PI;
                double offsetLng = radius * 0.6 / 111.0 * Math.cos(angle) / Math.cos(Math.toRadians(lat));
                double offsetLat = radius * 0.6 / 111.0 * Math.sin(angle);
                heatmap.add(new HeatmapPoint(lng + offsetLng, lat + offsetLat, sandstormProb * (0.5 + Math.random() * 0.3)));
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
                temp = reports.stream().mapToDouble(WeatherReport::getTemperatureC).average().orElse(20.0);
            } else {
                temp = 15 + Math.random() * 15;
            }
            double normalizedTemp = Math.max(0, Math.min(1, (temp + 20) / 70.0));
            heatmap.add(new HeatmapPoint(station.getGeom().getX(), station.getGeom().getY(), normalizedTemp));
        }
        return heatmap;
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

    private String inferTerrain(WeatherStation station) {
        if (station.getElevationM() != null) {
            if (station.getElevationM() > 3000) return "HIGH_MOUNTAINS";
            if (station.getElevationM() > 1800) return "MOUNTAINS";
        }
        double lng = station.getGeom().getX();
        if (lng < 90 && lng > 75) return "OASIS";
        if (lng > 90 && lng < 100) return "DESERT";
        return "DESERT_STEPPE";
    }

    private double haversineDistance(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double calculateBearing(double lng1, double lat1, double lng2, double lat2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLng);
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
                Math.cos(distRatio) - Math.sin(latRad) * Math.sin(destLat));
        return new double[]{Math.toDegrees(destLng), Math.toDegrees(destLat)};
    }

    private Season getSeasonFromMonth(int month) {
        if (month >= 3 && month <= 5) return Season.SPRING;
        if (month >= 6 && month <= 8) return Season.SUMMER;
        if (month >= 9 && month <= 11) return Season.AUTUMN;
        return Season.WINTER;
    }
}
