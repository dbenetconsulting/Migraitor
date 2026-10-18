package com.migration.repositories;

import com.migration.entities.CsConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CsConnectionRepository extends JpaRepository<CsConnectionEntity, Long> {
    Optional<CsConnectionEntity> findByMigTypeAndRole(String migType, String role);
    List<CsConnectionEntity> findByMigTypeOrderByRole(String migType);
}
