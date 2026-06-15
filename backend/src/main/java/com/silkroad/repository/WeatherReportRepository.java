package com.silkroad.repository;

import com.silkroad.entity.WeatherReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WeatherReportRepository extends JpaRepository<WeatherReport, Long> {

    List<WeatherReport> findTop10ByStationIdOrderByReportTimeDesc(Long stationId);

    List<WeatherReport> findByStationIdAndReportTimeBetween(Long stationId, LocalDateTime start, LocalDateTime end);

    WeatherReport findFirstByStationIdOrderByReportTimeDesc(Long stationId);

    List<WeatherReport> findByReportTimeBetweenOrderByReportTimeAsc(LocalDateTime start, LocalDateTime end);
}
