package com.silkroad.repository;

import com.silkroad.entity.TerrainGrid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TerrainGridRepository extends JpaRepository<TerrainGrid, Long> {

    List<TerrainGrid> findByTerrainType(String terrainType);

    List<TerrainGrid> findByPassabilityGreaterThanEqual(Double passability);
}
