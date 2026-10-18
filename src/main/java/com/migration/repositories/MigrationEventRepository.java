package com.migration.repositories;

import com.migration.entities.MigrationEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MigrationEventRepository extends JpaRepository<MigrationEventEntity, Long> {

    List<MigrationEventEntity> findByJobIdOrderByEventIdDesc(Long jobId, Pageable pageable);

    List<MigrationEventEntity> findByJobIdAndOutcomeNotInOrderByEventIdDesc(Long jobId, Collection<String> outcomes, Pageable pageable);

    List<MigrationEventEntity> findByMigIdOrderByEventIdDesc(Long migId, Pageable pageable);
}
