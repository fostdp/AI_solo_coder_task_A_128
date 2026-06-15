package com.silkroad.service;

import com.silkroad.dto.CaravanStatusDTO;
import com.silkroad.entity.Caravan;
import com.silkroad.entity.Waypoint;
import com.silkroad.repository.CaravanRepository;
import com.silkroad.repository.WaypointRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaravanService {

    private final CaravanRepository caravanRepository;
    private final WaypointRepository waypointRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private static final double EARTH_RADIUS_KM = 6371.0;

    public List<CaravanStatusDTO> getAllCaravans() {
        return caravanRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CaravanStatusDTO getCaravanById(Long id) {
        return caravanRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    public Caravan createCaravan(Caravan caravan) {
        caravan.setCreatedAt(LocalDateTime.now());
        caravan.setUpdatedAt(LocalDateTime.now());
        return caravanRepository.save(caravan);
    }

    public Caravan updateCaravanStatus(Long id, String status) {
        Optional<Caravan> opt = caravanRepository.findById(id);
        if (opt.isPresent()) {
            Caravan caravan = opt.get();
            caravan.setStatus(status);
            caravan.setUpdatedAt(LocalDateTime.now());
            Caravan saved = caravanRepository.save(caravan);
            broadcastCaravanUpdate(saved);
            return saved;
        }
        return null;
    }

    public Caravan moveCaravan(Long id, double lng, double lat) {
        Optional<Caravan> opt = caravanRepository.findById(id);
        if (opt.isPresent()) {
            Caravan caravan = opt.get();
            Point newPos = geometryFactory.createPoint(new Coordinate(lng, lat));
            caravan.setCurrentPosition(newPos);
            caravan.setUpdatedAt(LocalDateTime.now());
            Caravan saved = caravanRepository.save(caravan);
            broadcastCaravanUpdate(saved);
            return saved;
        }
        return null;
    }

    @Scheduled(fixedRate = 5000)
    public void simulateCaravanMovement() {
        List<Caravan> caravans = caravanRepository.findByStatus("EN_ROUTE");
        for (Caravan caravan : caravans) {
            if (caravan.getRouteId() == null || caravan.getCurrentPosition() == null) continue;

            List<Waypoint> waypoints = waypointRepository.findByRouteIdOrderByWaypointOrderAsc(caravan.getRouteId());
            if (waypoints.size() < 2) continue;

            Waypoint current = findNextWaypoint(caravan, waypoints);
            if (current == null) continue;

            double currentLng = caravan.getCurrentPosition().getX();
            double currentLat = caravan.getCurrentPosition().getY();
            double targetLng = current.getGeom().getX();
            double targetLat = current.getGeom().getY();

            double distance = haversineDistance(currentLng, currentLat, targetLng, targetLat);
            double speed = caravan.getSpeedKmh() != null ? caravan.getSpeedKmh() : 5.0;
            double moveDistance = speed * (5.0 / 3600.0);

            if (distance <= moveDistance) {
                caravan.setCurrentPosition(current.getGeom());
                caravan.setCurrentWaypointId(current.getId());
                consumeSupplies(caravan, distance);
                checkWaypointArrival(caravan, current);
            } else {
                double ratio = moveDistance / distance;
                double newLng = currentLng + (targetLng - currentLng) * ratio;
                double newLat = currentLat + (targetLat - currentLat) * ratio;
                caravan.setCurrentPosition(geometryFactory.createPoint(new Coordinate(newLng, newLat)));
                consumeSupplies(caravan, moveDistance);
            }

            caravan.setUpdatedAt(LocalDateTime.now());
            caravanRepository.save(caravan);
            broadcastCaravanUpdate(caravan);
        }
    }

    private Waypoint findNextWaypoint(Caravan caravan, List<Waypoint> waypoints) {
        if (caravan.getCurrentWaypointId() == null) {
            return waypoints.get(0);
        }
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).getId().equals(caravan.getCurrentWaypointId())) {
                if (i < waypoints.size() - 1) {
                    return waypoints.get(i + 1);
                } else {
                    return waypoints.get(0);
                }
            }
        }
        return waypoints.get(0);
    }

    private void consumeSupplies(Caravan caravan, double distanceKm) {
        double hours = distanceKm / (caravan.getSpeedKmh() != null ? caravan.getSpeedKmh() : 5.0);
        double waterPerHour = (caravan.getCrewCount() != null ? caravan.getCrewCount() : 20) * 0.5;
        double foodPerHour = (caravan.getCrewCount() != null ? caravan.getCrewCount() : 20) * 0.02;

        double water = caravan.getWaterSupplyLiters() != null ? caravan.getWaterSupplyLiters() : 2000;
        double food = caravan.getFoodSupplyDays() != null ? caravan.getFoodSupplyDays() : 30;

        caravan.setWaterSupplyLiters(Math.max(0, water - waterPerHour * hours));
        caravan.setFoodSupplyDays(Math.max(0, food - foodPerHour * hours));
    }

    private void checkWaypointArrival(Caravan caravan, Waypoint waypoint) {
        if (Boolean.TRUE.equals(waypoint.getWaterAvailable())) {
            double maxWater = 3000;
            caravan.setWaterSupplyLiters(maxWater);
        }
        if (Boolean.TRUE.equals(waypoint.getSupplyStation())) {
            caravan.setFoodSupplyDays(30.0);
        }
    }

    private double haversineDistance(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private void broadcastCaravanUpdate(Caravan caravan) {
        CaravanStatusDTO dto = toDTO(caravan);
        messagingTemplate.convertAndSend("/topic/caravans", dto);
        messagingTemplate.convertAndSend("/topic/caravans/" + caravan.getId(), dto);
    }

    private CaravanStatusDTO toDTO(Caravan caravan) {
        double waterDays = 0;
        if (caravan.getWaterSupplyLiters() != null && caravan.getCrewCount() != null) {
            double dailyConsumption = caravan.getCrewCount() * 12;
            waterDays = dailyConsumption > 0 ? caravan.getWaterSupplyLiters() / dailyConsumption : 0;
        }

        return CaravanStatusDTO.builder()
                .caravanId(caravan.getId())
                .name(caravan.getName())
                .status(caravan.getStatus())
                .lng(caravan.getCurrentPosition() != null ? caravan.getCurrentPosition().getX() : null)
                .lat(caravan.getCurrentPosition() != null ? caravan.getCurrentPosition().getY() : null)
                .routeId(caravan.getRouteId())
                .speedKmh(caravan.getSpeedKmh())
                .waterSupplyLiters(caravan.getWaterSupplyLiters())
                .waterRemainingDays(round2(waterDays))
                .foodSupplyDays(caravan.getFoodSupplyDays())
                .cargoType(caravan.getCargoType())
                .lastUpdate(caravan.getUpdatedAt())
                .activeAlerts(Collections.emptyList())
                .build();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public boolean startCaravan(Long id) {
        Optional<Caravan> opt = caravanRepository.findById(id);
        if (opt.isPresent()) {
            Caravan caravan = opt.get();
            caravan.setStatus("EN_ROUTE");
            caravan.setDepartureTime(LocalDateTime.now());
            caravan.setUpdatedAt(LocalDateTime.now());
            caravanRepository.save(caravan);
            broadcastCaravanUpdate(caravan);
            return true;
        }
        return false;
    }

    public boolean stopCaravan(Long id) {
        Optional<Caravan> opt = caravanRepository.findById(id);
        if (opt.isPresent()) {
            Caravan caravan = opt.get();
            caravan.setStatus("RESTING");
            caravan.setUpdatedAt(LocalDateTime.now());
            caravanRepository.save(caravan);
            broadcastCaravanUpdate(caravan);
            return true;
        }
        return false;
    }
}
