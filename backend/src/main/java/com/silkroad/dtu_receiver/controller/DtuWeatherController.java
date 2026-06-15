package com.silkroad.dtu_receiver.controller;

import com.silkroad.dto.WeatherReportDTO;
import com.silkroad.entity.WeatherReport;
import com.silkroad.entity.WeatherStation;
import com.silkroad.dtu_receiver.service.DtuWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class DtuWeatherController {

    private final DtuWeatherService dtuWeatherService;

    @GetMapping("/stations")
    public List<WeatherStation> getAllStations() {
        return dtuWeatherService.getAllStations();
    }

    @GetMapping("/stations/{id}")
    public WeatherStation getStation(@PathVariable Long id) {
        return dtuWeatherService.getStationById(id);
    }

    @GetMapping("/reports/latest")
    public List<WeatherReportDTO> getLatestReports() {
        return dtuWeatherService.getLatestReports();
    }

    @GetMapping("/reports/station/{stationId}")
    public List<WeatherReportDTO> getStationReports(
            @PathVariable Long stationId,
            @RequestParam(defaultValue = "10") int limit) {
        return dtuWeatherService.getStationReports(stationId, limit);
    }

    @GetMapping("/reports/range")
    public List<WeatherReportDTO> getReportsByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return dtuWeatherService.getReportsByTimeRange(start, end);
    }

    @PostMapping("/reports/{stationId}")
    public WeatherReport submitReport(
            @PathVariable Long stationId,
            @RequestBody WeatherReport report) {
        return dtuWeatherService.submitReport(stationId, report);
    }
}
