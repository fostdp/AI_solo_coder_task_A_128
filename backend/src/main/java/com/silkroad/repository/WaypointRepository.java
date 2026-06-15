package com.silkroad.repository;

import com.silkroad.entity.Waypoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaypointRepository extends JpaRepository<Waypoint, Long> {

    List<Waypoint> findByRouteIdOrderByWaypointOrderAsc(Long routeId);

    List<Waypoint> findByIsOasisTrue();

    Optional<Waypoint> findByRouteIdAndWaypointOrder(Long routeId, Integer order);
}
