package com.silkroad.route_planner.controller;

import com.silkroad.dto.CaravanStatusDTO;
import com.silkroad.dto.PathRequest;
import com.silkroad.dto.PathResult;
import com.silkroad.entity.Caravan;
import com.silkroad.route_planner.service.PlannerCaravanService;
import com.silkroad.route_planner.service.PlannerPathfindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pathfinding")
@RequiredArgsConstructor
public class PlannerPathfindingController {

    private final PlannerPathfindingService pathfindingService;

    @PostMapping("/plan")
    public PathResult planPath(@RequestBody PathRequest request) {
        return pathfindingService.findOptimalPath(request);
    }

    @GetMapping("/quick")
    public PathResult quickPlan(
            @RequestParam double startLng,
            @RequestParam double startLat,
            @RequestParam double endLng,
            @RequestParam double endLat,
            @RequestParam(defaultValue = "SPRING") String season,
            @RequestParam(defaultValue = "5.0") Double speed) {
        PathRequest request = PathRequest.builder()
                .startLng(startLng)
                .startLat(startLat)
                .endLng(endLng)
                .endLat(endLat)
                .season(season)
                .caravanSpeed(speed)
                .preferOasis(true)
                .build();
        return pathfindingService.findOptimalPath(request);
    }
}

@RestController
@RequestMapping("/caravans")
@RequiredArgsConstructor
class CaravanRestController {

    private final PlannerCaravanService caravanService;

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

    @PostMapping("/{id}/start")
    public boolean startCaravan(@PathVariable Long id) {
        return caravanService.startCaravan(id);
    }

    @PostMapping("/{id}/stop")
    public boolean stopCaravan(@PathVariable Long id) {
        return caravanService.stopCaravan(id);
    }
}
