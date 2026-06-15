package com.silkroad.controller;

import com.silkroad.dto.HeatmapPoint;
import com.silkroad.dto.WeatherReportDTO;
import com.silkroad.dto.WeatherRiskAnalysis;
import com.silkroad.entity.WeatherReport;
import com.silkroad.entity.WeatherStation;
import com.silkroad.service.WeatherRiskService;
import com.silkroad.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final WeatherRiskService weatherRiskService;

    @GetMapping("/stations")
    public List<WeatherStation> getAllStations() {
        return weatherService.getAllStations();
    }

    @GetMapping("/stations/{id}")
    public WeatherStation getStation(@PathVariable Long id) {
        return weatherService.getStationById(id);
    }

    @GetMapping("/reports/latest")
    public List<WeatherReportDTO> getLatestReports() {
        return weatherService.getLatestReports();
    }

    @GetMapping("/reports/station/{stationId}")
    public List<WeatherReportDTO> getStationReports(
            @PathVariable Long stationId,
            @RequestParam(defaultValue = "10") int limit) {
        return weatherService.getStationReports(stationId, limit);
    }

    @GetMapping("/reports/range")
    public List<WeatherReportDTO> getReportsByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return weatherService.getReportsByTimeRange(start, end);
    }

    @PostMapping("/reports/{stationId}")
    public WeatherReport submitReport(
            @PathVariable Long stationId,
            @RequestBody WeatherReport report) {
        return weatherService.submitReport(stationId, report);
    }

    @GetMapping("/risk/route/{routeId}")
    public WeatherRiskAnalysis getRouteRisk(
            @PathVariable Long routeId,
            @RequestParam(defaultValue = "SPRING") String season) {
        return weatherRiskService.analyzeRouteRisk(routeId, season);
    }

    @GetMapping("/risk/all")
    public Map<String, List<WeatherRiskAnalysis>> getAllRoutesRisk() {
        return weatherRiskService.analyzeAllRoutesBySeason();
    }

    @GetMapping("/heatmap/sandstorm")
    public List<HeatmapPoint> getSandstormHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time) {
        return weatherRiskService.generateSandstormHeatmap(time);
    }

    @GetMapping("/heatmap/temperature")
    public List<HeatmapPoint> getTemperatureHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time) {
        return weatherRiskService.generateTemperatureHeatmap(time);
    }
}
