package com.silkroad.service;

import com.silkroad.dto.AlertDTO;
import com.silkroad.entity.Alert;
import com.silkroad.entity.Caravan;
import com.silkroad.entity.WeatherReport;
import com.silkroad.entity.WeatherStation;
import com.silkroad.repository.AlertRepository;
import com.silkroad.repository.CaravanRepository;
import com.silkroad.repository.WeatherReportRepository;
import com.silkroad.repository.WeatherStationRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final WeatherReportRepository weatherReportRepository;
    private final WeatherStationRepository weatherStationRepository;
    private final CaravanRepository caravanRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${weather.alert.sandstorm-threshold:0.6}")
    private double sandstormThreshold;

    @Value("${weather.alert.wind-speed-threshold:60.0}")
    private double windSpeedThreshold;

    @Value("${weather.alert.temperature-high-threshold:45.0}")
    private double temperatureHighThreshold;

    @Value("${weather.alert.temperature-low-threshold:-20.0}")
    private double temperatureLowThreshold;

    @Value("${weather.alert.water-supply-min-liters:500}")
    private double waterSupplyMinLiters;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public List<AlertDTO> getActiveAlerts() {
        return alertRepository.findByIsActiveTrueOrderByTriggeredAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<AlertDTO> getAlertsByRoute(Long routeId) {
        return alertRepository.findByRouteIdAndIsActiveTrue(routeId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<AlertDTO> getAlertsByCaravan(Long caravanId) {
        return alertRepository.findByCaravanIdOrderByTriggeredAtDesc(caravanId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Alert resolveAlert(Long alertId) {
        Optional<Alert> alertOpt = alertRepository.findById(alertId);
        if (alertOpt.isPresent()) {
            Alert alert = alertOpt.get();
            alert.setIsActive(false);
            alert.setResolvedAt(LocalDateTime.now());
            Alert saved = alertRepository.save(alert);
            broadcastAlertUpdate(saved, "RESOLVED");
            return saved;
        }
        return null;
    }

    @Scheduled(fixedRate = 60000)
    public void checkWeatherAlerts() {
        List<WeatherStation> stations = weatherStationRepository.findByIsActiveTrue();
        LocalDateTime windowStart = LocalDateTime.now().minusHours(2);

        for (WeatherStation station : stations) {
            WeatherReport latestReport = weatherReportRepository
                    .findFirstByStationIdOrderByReportTimeDesc(station.getId());

            if (latestReport == null || latestReport.getReportTime().isBefore(windowStart)) {
                continue;
            }

            if (latestReport.getSandstormProbability() != null &&
                    latestReport.getSandstormProbability() >= sandstormThreshold) {
                createAndBroadcastAlert(
                        "SANDSTORM_WARNING",
                        "HIGH",
                        station.getId(),
                        station.getRouteId(),
                        null,
                        "沙尘暴预警：" + station.getName() + "沙尘暴概率达 " +
                                Math.round(latestReport.getSandstormProbability() * 100) + "%，能见度降低",
                        station.getGeom()
                );
            }

            if (latestReport.getWindSpeedKmh() != null &&
                    latestReport.getWindSpeedKmh() >= windSpeedThreshold) {
                createAndBroadcastAlert(
                        "HIGH_WIND_WARNING",
                        "MODERATE",
                        station.getId(),
                        station.getRouteId(),
                        null,
                        "大风预警：" + station.getName() + "风速达 " + latestReport.getWindSpeedKmh() + "km/h",
                        station.getGeom()
                );
            }

            if (latestReport.getTemperatureC() != null &&
                    latestReport.getTemperatureC() >= temperatureHighThreshold) {
                createAndBroadcastAlert(
                        "EXTREME_HEAT_WARNING",
                        "HIGH",
                        station.getId(),
                        station.getRouteId(),
                        null,
                        "高温预警：" + station.getName() + "温度达 " + latestReport.getTemperatureC() + "°C",
                        station.getGeom()
                );
            }

            if (latestReport.getTemperatureC() != null &&
                    latestReport.getTemperatureC() <= temperatureLowThreshold) {
                createAndBroadcastAlert(
                        "EXTREME_COLD_WARNING",
                        "HIGH",
                        station.getId(),
                        station.getRouteId(),
                        null,
                        "低温预警：" + station.getName() + "温度降至 " + latestReport.getTemperatureC() + "°C",
                        station.getGeom()
                );
            }
        }
    }

    @Scheduled(fixedRate = 120000)
    public void checkCaravanWaterSupply() {
        List<Caravan> caravans = caravanRepository.findByWaterSupplyLitersLessThan(waterSupplyMinLiters);
        for (Caravan caravan : caravans) {
            if (!"IDLE".equals(caravan.getStatus())) {
                createAndBroadcastAlert(
                        "WATER_SHORTAGE",
                        "CRITICAL",
                        null,
                        caravan.getRouteId(),
                        caravan.getId(),
                        "水源不足：驼队【" + caravan.getName() + "】剩余水量 " +
                                Math.round(caravan.getWaterSupplyLiters()) + "升，低于安全阈值",
                        caravan.getCurrentPosition()
                );
            }
        }
    }

    public Alert createAndBroadcastAlert(String alertType, String severity,
                                          Long stationId, Long routeId, Long caravanId,
                                          String message, Point geom) {
        Alert alert = new Alert();
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setStationId(stationId);
        alert.setRouteId(routeId);
        alert.setCaravanId(caravanId);
        alert.setMessage(message);
        alert.setGeom(geom);
        alert.setIsActive(true);
        alert.setTriggeredAt(LocalDateTime.now());

        Alert saved = alertRepository.save(alert);
        broadcastAlertUpdate(saved, "NEW");

        return saved;
    }

    private void broadcastAlertUpdate(Alert alert, String action) {
        AlertDTO dto = toDTO(alert);
        Map<String, Object> message = new HashMap<>();
        message.put("action", action);
        message.put("alert", dto);
        messagingTemplate.convertAndSend("/topic/alerts", message);

        if (alert.getCaravanId() != null) {
            messagingTemplate.convertAndSend("/topic/caravans/" + alert.getCaravanId() + "/alerts", message);
        }
        if (alert.getRouteId() != null) {
            messagingTemplate.convertAndSend("/topic/routes/" + alert.getRouteId() + "/alerts", message);
        }
    }

    private AlertDTO toDTO(Alert alert) {
        return AlertDTO.builder()
                .id(alert.getId())
                .alertType(alert.getAlertType())
                .severity(alert.getSeverity())
                .message(alert.getMessage())
                .lng(alert.getGeom() != null ? alert.getGeom().getX() : null)
                .lat(alert.getGeom() != null ? alert.getGeom().getY() : null)
                .routeId(alert.getRouteId())
                .stationId(alert.getStationId())
                .caravanId(alert.getCaravanId())
                .triggeredAt(alert.getTriggeredAt())
                .isActive(alert.getIsActive())
                .build();
    }

    public void simulateSandstormAlert(Long routeId) {
        List<WeatherStation> stations = weatherStationRepository.findByRouteId(routeId);
        if (stations.isEmpty()) return;

        WeatherStation station = stations.get(0);
        createAndBroadcastAlert(
                "SANDSTORM_WARNING",
                "HIGH",
                station.getId(),
                routeId,
                null,
                "模拟沙尘暴预警：" + station.getName() + "附近沙尘暴概率85%",
                station.getGeom()
        );
    }
}
