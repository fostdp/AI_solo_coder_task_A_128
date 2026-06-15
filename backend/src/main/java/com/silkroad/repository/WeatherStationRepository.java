package com.silkroad.repository;

import com.silkroad.entity.WeatherStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeatherStationRepository extends JpaRepository<WeatherStation, Long> {

    List<WeatherStation> findByIsActiveTrue();

    List<WeatherStation> findByRouteId(Long routeId);

    Optional<WeatherStation> findByStationCode(String stationCode);
}
