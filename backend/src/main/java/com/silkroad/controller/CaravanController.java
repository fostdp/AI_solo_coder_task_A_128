package com.silkroad.controller;

import com.silkroad.dto.CaravanStatusDTO;
import com.silkroad.entity.Caravan;
import com.silkroad.service.CaravanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/caravans")
@RequiredArgsConstructor
public class CaravanController {

    private final CaravanService caravanService;

    @GetMapping
    public List<CaravanStatusDTO> getAllCaravans() {
        return caravanService.getAllCaravans();
    }

    @GetMapping("/{id}")
    public CaravanStatusDTO getCaravan(@PathVariable Long id) {
        return caravanService.getCaravanById(id);
    }

    @PostMapping
    public Caravan createCaravan(@RequestBody Caravan caravan) {
        return caravanService.createCaravan(caravan);
    }

    @PutMapping("/{id}/status")
    public Caravan updateStatus(@PathVariable Long id, @RequestParam String status) {
        return caravanService.updateCaravanStatus(id, status);
    }

    @PostMapping("/{id}/start")
    public String startCaravan(@PathVariable Long id) {
        boolean started = caravanService.startCaravan(id);
        return started ? "驼队已出发" : "启动失败";
    }

    @PostMapping("/{id}/stop")
    public String stopCaravan(@PathVariable Long id) {
        boolean stopped = caravanService.stopCaravan(id);
        return stopped ? "驼队已停靠" : "停止失败";
    }

    @PutMapping("/{id}/position")
    public Caravan moveCaravan(
            @PathVariable Long id,
            @RequestParam double lng,
            @RequestParam double lat) {
        return caravanService.moveCaravan(id, lng, lat);
    }
}
