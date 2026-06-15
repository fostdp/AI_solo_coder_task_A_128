package com.silkroad.repository;

import com.silkroad.entity.SeasonalRiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonalRiskProfileRepository extends JpaRepository<SeasonalRiskProfile, Long> {

    List<SeasonalRiskProfile> findByRouteId(Long routeId);

    List<SeasonalRiskProfile> findBySeason(String season);

    Optional<SeasonalRiskProfile> findByRouteIdAndSeason(Long routeId, String season);
}
