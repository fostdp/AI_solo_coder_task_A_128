package com.silkroad.controller;

import com.silkroad.dto.PathRequest;
import com.silkroad.dto.PathResult;
import com.silkroad.service.PathfindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pathfinding")
@RequiredArgsConstructor
public class PathfindingController {

    private final PathfindingService pathfindingService;

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
