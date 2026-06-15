package com.silkroad.repository;

import com.silkroad.entity.WaterSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WaterSourceRepository extends JpaRepository<WaterSource, Long> {

    List<WaterSource> findByIsPermanentTrue();

    List<WaterSource> findByReliability(String reliability);
}
