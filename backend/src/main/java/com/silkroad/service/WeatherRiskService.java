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

    public double calculateSandstormProbability(double windSpeedKmh, double humidityPct,
                                                 double temperatureC, String terrainType, Season season) {
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

        double probability = windFactor * 0.35 + humidityFactor * 0.25 +
                tempFactor * 0.15 + terrainFactor * 0.15 + seasonFactor * 0.1;

        return Math.min(1.0, Math.max(0.0, probability));
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

        if (profile.getSandstormFrequency() != null && profile.getSandstormFrequency() > 0.3) {
            riskFactors.add("沙尘暴高发期，频率达" + Math.round(profile.getSandstormFrequency() * 100) + "%");
            recommendations.add("携带护目镜、口罩等防沙装备");
            recommendations.add("选择清晨或傍晚赶路，避开午后强风时段");
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
                .sandstormProbability(profile.getSandstormFrequency())
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
            heatmap.add(new HeatmapPoint(lng, lat, sandstormProb));

            double radius = station.getCoverageRadiusKm() != null ? station.getCoverageRadiusKm() : 50;
            for (int i = 0; i < 5; i++) {
                double angle = (i / 5.0) * 2 * Math.PI;
                double distance = radius * 0.6 * (0.7 + Math.random() * 0.3);
                double offsetLng = distance / 111.0 * Math.cos(angle) / Math.cos(Math.toRadians(lat));
                double offsetLat = distance / 111.0 * Math.sin(angle);
                heatmap.add(new HeatmapPoint(lng + offsetLng, lat + offsetLat,
                        sandstormProb * (0.5 + Math.random() * 0.3)));
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

        return Math.min(1.0, baseProb * seasonMultiplier * hourMultiplier);
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
