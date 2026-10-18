package com.migration.repositories;

import com.migration.entities.SapAlMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SapAlMetadataRepository extends JpaRepository<SapAlMetadataEntity, Long> {
    List<SapAlMetadataEntity> findByMigIdIn(Collection<Long> migIds);
}
