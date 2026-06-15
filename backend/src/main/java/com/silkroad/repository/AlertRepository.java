package com.silkroad.repository;

import com.silkroad.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByIsActiveTrueOrderByTriggeredAtDesc();

    List<Alert> findByRouteIdAndIsActiveTrue(Long routeId);

    List<Alert> findByCaravanIdOrderByTriggeredAtDesc(Long caravanId);

    List<Alert> findBySeverityAndIsActiveTrue(String severity, Boolean active);
}
