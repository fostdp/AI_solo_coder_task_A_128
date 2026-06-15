package com.silkroad.alarm_ws.controller;

import com.silkroad.dto.AlertDTO;
import com.silkroad.alarm_ws.service.AlarmAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlarmAlertController {

    private final AlarmAlertService alertService;

    @GetMapping
    public List<AlertDTO> getActiveAlerts() {
        return alertService.getActiveAlerts();
    }

    @GetMapping("/route/{routeId}")
    public List<AlertDTO> getRouteAlerts(@PathVariable Long routeId) {
        return alertService.getAlertsByRoute(routeId);
    }

    @GetMapping("/caravan/{caravanId}")
    public List<AlertDTO> getCaravanAlerts(@PathVariable Long caravanId) {
        return alertService.getAlertsByCaravan(caravanId);
    }

    @PutMapping("/{id}/resolve")
    public void resolveAlert(@PathVariable Long id) {
        alertService.resolveAlert(id);
    }

    @PostMapping("/simulate/sandstorm/{routeId}")
    public void simulateSandstorm(@PathVariable Long routeId) {
        alertService.simulateSandstormAlert(routeId);
    }
}
