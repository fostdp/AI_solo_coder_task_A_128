package com.silkroad.repository;

import com.silkroad.entity.Caravan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaravanRepository extends JpaRepository<Caravan, Long> {

    List<Caravan> findByStatus(String status);

    List<Caravan> findByRouteId(Long routeId);

    List<Caravan> findByWaterSupplyLitersLessThan(Double threshold);
}
