package com.migration.repositories;

import com.migration.entities.SourceDocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SourceDocumentRepository extends JpaRepository<SourceDocumentEntity, Long> {

    Page<SourceDocumentEntity> findByRunIdOrderById(Long runId, Pageable pageable);

    Page<SourceDocumentEntity> findByRunIdAndMatchStatusInOrderById(Long runId, Collection<String> statuses, Pageable pageable);

    @Query("select s.matchStatus as status, count(s) as total from SourceDocumentEntity s where s.runId = :runId group by s.matchStatus")
    List<MigrationRepository.StatusCount> countByMatchStatus(@Param("runId") Long runId);
}
