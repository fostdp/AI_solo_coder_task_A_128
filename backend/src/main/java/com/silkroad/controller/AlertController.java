package com.silkroad.controller;

import com.silkroad.dto.AlertDTO;
import com.silkroad.entity.Alert;
import com.silkroad.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public List<AlertDTO> getActiveAlerts() {
        return alertService.getActiveAlerts();
    }

    @GetMapping("/route/{routeId}")
    public List<AlertDTO> getAlertsByRoute(@PathVariable Long routeId) {
        return alertService.getAlertsByRoute(routeId);
    }

    @GetMapping("/caravan/{caravanId}")
    public List<AlertDTO> getAlertsByCaravan(@PathVariable Long caravanId) {
        return alertService.getAlertsByCaravan(caravanId);
    }

    @PutMapping("/{id}/resolve")
    public Alert resolveAlert(@PathVariable Long id) {
        return alertService.resolveAlert(id);
    }

    @PostMapping("/simulate/sandstorm/{routeId}")
    public String simulateSandstorm(@PathVariable Long routeId) {
        alertService.simulateSandstormAlert(routeId);
        return "沙尘暴模拟告警已触发";
    }
}
