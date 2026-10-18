package com.migration.repositories;

import com.migration.entities.TargetFolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TargetFolderRepository extends JpaRepository<TargetFolderEntity, Long> {
    List<TargetFolderEntity> findByRunId(Long runId);
}
