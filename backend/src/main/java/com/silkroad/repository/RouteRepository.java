package com.silkroad.repository;

import com.silkroad.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByDifficultyLevel(String difficulty);

    List<Route> findAllByOrderByIdAsc();
}
