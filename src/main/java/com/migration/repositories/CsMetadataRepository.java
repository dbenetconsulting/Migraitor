package com.migration.repositories;

import com.migration.entities.CsMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CsMetadataRepository extends JpaRepository<CsMetadataEntity, Long> {

    List<CsMetadataEntity> findByMigIdIn(Collection<Long> migIds);

    @Query("select c.docId from CsMetadataEntity c where c.docId in :docIds")
    List<Integer> findExistingDocIds(@Param("docIds") Collection<Integer> docIds);
}
