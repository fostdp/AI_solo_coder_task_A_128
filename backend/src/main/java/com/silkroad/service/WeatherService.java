package com.silkroad.service;

import com.silkroad.dto.WeatherReportDTO;
import com.silkroad.entity.WeatherReport;
import com.silkroad.entity.WeatherStation;
import com.silkroad.model.Season;
import com.silkroad.repository.WeatherReportRepository;
import com.silkroad.repository.WeatherStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherStationRepository weatherStationRepository;
    private final WeatherReportRepository weatherReportRepository;
    private final WeatherRiskService weatherRiskService;

    public List<WeatherStation> getAllStations() {
        return weatherStationRepository.findByIsActiveTrue();
    }

    public WeatherStation getStationById(Long id) {
        return weatherStationRepository.findById(id).orElse(null);
    }

    public WeatherStation getStationByCode(String code) {
        return weatherStationRepository.findByStationCode(code).orElse(null);
    }

    public WeatherReport submitReport(Long stationId, WeatherReport report) {
        report.setStationId(stationId);
        if (report.getReportTime() == null) {
            report.setReportTime(LocalDateTime.now());
        }
        report.setCreatedAt(LocalDateTime.now());

        if (report.getSandstormProbability() == null) {
            WeatherStation station = weatherStationRepository.findById(stationId).orElse(null);
            Season season = getSeasonFromMonth(report.getReportTime().getMonthValue());
            String terrain = station != null ? inferTerrain(station) : "DESERT";
            double prob = weatherRiskService.calculateSandstormProbability(
                    report.getWindSpeedKmh() != null ? report.getWindSpeedKmh() : 0,
                    report.getHumidityPct() != null ? report.getHumidityPct() : 20,
                    report.getTemperatureC() != null ? report.getTemperatureC() : 25,
                    terrain,
                    season
            );
            report.setSandstormProbability(prob);
        }

        return weatherReportRepository.save(report);
    }

    public List<WeatherReportDTO> getLatestReports() {
        List<WeatherStation> stations = weatherStationRepository.findByIsActiveTrue();
        List<WeatherReportDTO> reports = new ArrayList<>();

        for (WeatherStation station : stations) {
            WeatherReport latest = weatherReportRepository.findFirstByStationIdOrderByReportTimeDesc(station.getId());
            if (latest != null) {
                reports.add(toDTO(latest, station));
            } else {
                WeatherReport simulated = simulateReport(station, LocalDateTime.now());
                reports.add(toDTO(simulated, station));
            }
        }
        return reports;
    }

    public List<WeatherReportDTO> getStationReports(Long stationId, int limit) {
        return weatherReportRepository.findTop10ByStationIdOrderByReportTimeDesc(stationId).stream()
                .map(r -> {
                    WeatherStation station = weatherStationRepository.findById(stationId).orElse(null);
                    return toDTO(r, station);
                })
                .collect(Collectors.toList());
    }

    public List<WeatherReportDTO> getReportsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return weatherReportRepository.findByReportTimeBetweenOrderByReportTimeAsc(start, end).stream()
                .map(r -> {
                    WeatherStation station = weatherStationRepository.findById(r.getStationId()).orElse(null);
                    return toDTO(r, station);
                })
                .collect(Collectors.toList());
    }

    public WeatherReport simulateReport(WeatherStation station, LocalDateTime time) {
        Random random = new Random(station.getId() + time.getHour() + time.getDayOfYear() * 100);

        Season season = getSeasonFromMonth(time.getMonthValue());
        double elevation = station.getElevationM() != null ? station.getElevationM() : 1000;
        double elevationCorrection = (elevation - 1000) / 1000 * 6;

        double baseTemp;
        switch (season) {
            case SPRING: baseTemp = 15; break;
            case SUMMER: baseTemp = 32; break;
            case AUTUMN: baseTemp = 12; break;
            case WINTER: baseTemp = -5; break;
            default: baseTemp = 20;
        }

        int hour = time.getHour();
        double dailyVariation = -7 * Math.cos((hour - 14) * Math.PI / 12);
        double temperature = baseTemp + dailyVariation - elevationCorrection + random.nextGaussian() * 2;

        double windBase = 15;
        double windSeason = season == Season.SPRING ? 25 : season == Season.SUMMER ? 20 : 15;
        double windHourFactor = hour >= 10 && hour <= 18 ? 1.3 : 0.7;
        double windSpeed = Math.max(0, (windBase + windSeason) / 2 * windHourFactor + random.nextGaussian() * 5);

        double humidityBase = season == Season.SUMMER ? 20 : season == Season.WINTER ? 50 : 35;
        double humidity = Math.max(5, Math.min(95, humidityBase - windSpeed * 0.5 + random.nextGaussian() * 5));

        double precipitation = 0;
        if (random.nextDouble() < 0.1) {
            precipitation = random.nextDouble() * 5;
        }

        String terrain = inferTerrain(station);
        double sandstormProb = weatherRiskService.calculateSandstormProbability(
                windSpeed, humidity, temperature, terrain, season);

        double visibility = 10.0;
        if (sandstormProb > 0.5) {
            visibility = 10 * (1 - sandstormProb * 0.8);
        }

        WeatherReport report = new WeatherReport();
        report.setStationId(station.getId());
        report.setReportTime(time);
        report.setTemperatureC(Math.round(temperature * 10.0) / 10.0);
        report.setPrecipitationMm(Math.round(precipitation * 10.0) / 10.0);
        report.setWindSpeedKmh(Math.round(windSpeed * 10.0) / 10.0);
        report.setWindDirection(random.nextInt(360));
        report.setHumidityPct(Math.round(humidity * 10.0) / 10.0);
        report.setSandstormProbability(Math.round(sandstormProb * 100.0) / 100.0);
        report.setVisibilityKm(Math.round(visibility * 10.0) / 10.0);
        report.setAirPressureHpa(Math.round((1013 - elevation * 0.012 + random.nextGaussian() * 3) * 10.0) / 10.0);

        return report;
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

    private Season getSeasonFromMonth(int month) {
        if (month >= 3 && month <= 5) return Season.SPRING;
        if (month >= 6 && month <= 8) return Season.SUMMER;
        if (month >= 9 && month <= 11) return Season.AUTUMN;
        return Season.WINTER;
    }

    private WeatherReportDTO toDTO(WeatherReport report, WeatherStation station) {
        return WeatherReportDTO.builder()
                .stationId(report.getStationId())
                .stationCode(station != null ? station.getStationCode() : null)
                .reportTime(report.getReportTime())
                .temperatureC(report.getTemperatureC())
                .precipitationMm(report.getPrecipitationMm())
                .windSpeedKmh(report.getWindSpeedKmh())
                .windDirection(report.getWindDirection())
                .humidityPct(report.getHumidityPct())
                .sandstormProbability(report.getSandstormProbability())
                .visibilityKm(report.getVisibilityKm())
                .lng(station != null && station.getGeom() != null ? station.getGeom().getX() : null)
                .lat(station != null && station.getGeom() != null ? station.getGeom().getY() : null)
                .build();
    }
}
