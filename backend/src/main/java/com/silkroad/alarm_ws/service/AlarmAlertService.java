package com.silkroad.alarm_ws.service;

import com.silkroad.common.event.CaravanStatusEvent;
import com.silkroad.common.event.RiskAssessmentEvent;
import com.silkroad.common.event.WeatherReportReceivedEvent;
import com.silkroad.dto.AlertDTO;
import com.silkroad.entity.Alert;
import com.silkroad.entity.WeatherStation;
import com.silkroad.repository.AlertRepository;
import com.silkroad.repository.WeatherStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmAlertService {

    private final AlertRepository alertRepository;
    private final WeatherStationRepository weatherStationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeometryFactory geometryFactory = new GeometryFactory();

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

    @EventListener
    public void onRiskAssessment(RiskAssessmentEvent event) {
        Long stationId = event.getStationId();
        String riskLevel = event.getRiskLevel();
        Double sandstormProb = event.getSandstormProbability();
        Double windSpeed = event.getWindSpeedKmh();
        Double temperature = event.getTemperatureC();

        WeatherStation station = weatherStationRepository.findById(stationId).orElse(null);
        if (station == null) return;

        if (sandstormProb != null && sandstormProb >= sandstormThreshold) {
            createAndBroadcastAlert("SANDSTORM_WARNING", "HIGH",
                    stationId, station.getRouteId(), null,
                    "沙尘暴预警：" + station.getName() + " 沙尘暴概率达 " + Math.round(sandstormProb * 100) + "%",
                    station.getGeom());
        }

        if (windSpeed != null && windSpeed >= windSpeedThreshold) {
            createAndBroadcastAlert("HIGH_WIND_WARNING", "MODERATE",
                    stationId, station.getRouteId(), null,
                    "大风预警：" + station.getName() + " 风速达 " + windSpeed + "km/h",
                    station.getGeom());
        }

        if (temperature != null && temperature >= temperatureHighThreshold) {
            createAndBroadcastAlert("EXTREME_HEAT_WARNING", "HIGH",
                    stationId, station.getRouteId(), null,
                    "高温预警：" + station.getName() + " 温度达 " + temperature + "°C",
                    station.getGeom());
        }

        if (temperature != null && temperature <= temperatureLowThreshold) {
            createAndBroadcastAlert("EXTREME_COLD_WARNING", "HIGH",
                    stationId, station.getRouteId(), null,
                    "低温预警：" + station.getName() + " 温度降至 " + temperature + "°C",
                    station.getGeom());
        }

        log.debug("告警模块处理风险评估事件, stationId={}, riskLevel={}", stationId, riskLevel);
    }

    @EventListener
    public void onCaravanStatus(CaravanStatusEvent event) {
        Long caravanId = event.getCaravanId();
        Double waterSupply = event.getWaterSupplyLiters();

        if (waterSupply != null && waterSupply < waterSupplyMinLiters && !"IDLE".equals(event.getStatus())) {
            Point geom = null;
            if (event.getLng() != null && event.getLat() != null) {
                geom = geometryFactory.createPoint(new Coordinate(event.getLng(), event.getLat()));
            }
            createAndBroadcastAlert("WATER_SHORTAGE", "CRITICAL",
                    null, null, caravanId,
                    "水源不足：驼队剩余水量 " + Math.round(waterSupply) + "升，低于安全阈值",
                    geom);
        }
    }

    @EventListener
    public void onWeatherReport(WeatherReportReceivedEvent event) {
        Double visibility = event.getVisibilityKm();
        if (visibility != null && visibility < 1.0) {
            WeatherStation station = weatherStationRepository.findById(event.getStationId()).orElse(null);
            if (station != null) {
                createAndBroadcastAlert("LOW_VISIBILITY", "MODERATE",
                        station.getId(), station.getRouteId(), null,
                        "低能见度预警：" + station.getName() + " 能见度仅 " + visibility + "km",
                        station.getGeom());
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void periodicWeatherCheck() {
        List<WeatherStation> stations = weatherStationRepository.findByIsActiveTrue();
        log.debug("定期气象检查，活跃站点数: {}", stations.size());
    }

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

    public void simulateSandstormAlert(Long routeId) {
        List<WeatherStation> stations = weatherStationRepository.findByRouteId(routeId);
        if (stations.isEmpty()) return;
        WeatherStation station = stations.get(0);
        createAndBroadcastAlert("SANDSTORM_WARNING", "HIGH",
                station.getId(), routeId, null,
                "模拟沙尘暴预警：" + station.getName() + "附近沙尘暴概率85%",
                station.getGeom());
    }

    private Alert createAndBroadcastAlert(String alertType, String severity,
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

        log.info("告警创建: type={}, severity={}, stationId={}, message={}",
                alertType, severity, stationId, message);
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
}
