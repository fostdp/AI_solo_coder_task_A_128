package com.silkroad.dtu_receiver.service;

import com.silkroad.common.event.WeatherReportReceivedEvent;
import com.silkroad.dtu_receiver.validator.WeatherReportValidator;
import com.silkroad.dto.WeatherReportDTO;
import com.silkroad.entity.WeatherReport;
import com.silkroad.entity.WeatherStation;
import com.silkroad.model.Season;
import com.silkroad.repository.WeatherReportRepository;
import com.silkroad.repository.WeatherStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DtuWeatherService {

    private final WeatherStationRepository weatherStationRepository;
    private final WeatherReportRepository weatherReportRepository;
    private final WeatherReportValidator reportValidator;
    private final ApplicationEventPublisher eventPublisher;

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

        WeatherReportValidator.ValidationResult validation = reportValidator.validate(report);
        if (!validation.isValid()) {
            log.warn("气象报告校验失败, stationId={}: {}", stationId, validation.getErrors());
            throw new IllegalArgumentException("数据校验失败: " + String.join("; ", validation.getErrors()));
        }
        if (!validation.getWarnings().isEmpty()) {
            log.info("气象报告警告, stationId={}: {}", stationId, validation.getWarnings());
        }

        report = reportValidator.applyDefaults(report);

        WeatherReport saved = weatherReportRepository.save(report);

        eventPublisher.publishEvent(new WeatherReportReceivedEvent(
                this,
                stationId,
                saved.getWindSpeedKmh(),
                saved.getHumidityPct(),
                saved.getTemperatureC(),
                saved.getSandstormProbability(),
                saved.getVisibilityKm()
        ));

        log.debug("气象报告已保存并发布事件, stationId={}, sandstormProb={}",
                stationId, saved.getSandstormProbability());
        return saved;
    }

    public List<WeatherReportDTO> getLatestReports() {
        List<WeatherStation> stations = weatherStationRepository.findByIsActiveTrue();
        List<WeatherReportDTO> reports = new ArrayList<>();
        for (WeatherStation station : stations) {
            WeatherReport latest = weatherReportRepository
                    .findFirstByStationIdOrderByReportTimeDesc(station.getId());
            if (latest != null) {
                reports.add(toDTO(latest, station));
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
