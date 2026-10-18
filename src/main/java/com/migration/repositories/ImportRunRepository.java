package com.migration.repositories;

import com.migration.entities.ImportRunEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportRunRepository extends JpaRepository<ImportRunEntity, Long> {
    List<ImportRunEntity> findByMigTypeOrderByRunIdDesc(String migType, Pageable pageable);
    Optional<ImportRunEntity> findFirstByMigTypeAndRoleAndStatusInOrderByRunIdDesc(String migType, String role, List<String> statuses);
}
