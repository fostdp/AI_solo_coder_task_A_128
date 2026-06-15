package com.silkroad.weather_risk_analyzer.controller;

import com.silkroad.dto.HeatmapPoint;
import com.silkroad.dto.WeatherRiskAnalysis;
import com.silkroad.weather_risk_analyzer.service.AnalyzerWeatherRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class RiskAnalyzerController {

    private final AnalyzerWeatherRiskService riskService;

    @GetMapping("/risk/route/{routeId}")
    public WeatherRiskAnalysis getRouteRisk(
            @PathVariable Long routeId,
            @RequestParam(defaultValue = "SPRING") String season) {
        return riskService.analyzeRouteRisk(routeId, season);
    }

    @GetMapping("/risk/all")
    public Map<String, List<WeatherRiskAnalysis>> getAllRoutesRisk() {
        return riskService.analyzeAllRoutesBySeason();
    }

    @GetMapping("/heatmap/sandstorm")
    public List<HeatmapPoint> getSandstormHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time) {
        return riskService.generateSandstormHeatmap(time);
    }

    @GetMapping("/heatmap/temperature")
    public List<HeatmapPoint> getTemperatureHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time) {
        return riskService.generateTemperatureHeatmap(time);
    }
}
