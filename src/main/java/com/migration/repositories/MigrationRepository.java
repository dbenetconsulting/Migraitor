package com.migration.repositories;

import com.migration.entities.MigrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MigrationRepository extends JpaRepository<MigrationEntity, Long> {

    List<MigrationEntity> findByJob_JobIdAndStatus(Long jobId, String status);

    List<MigrationEntity> findByJob_JobIdAndStatusIn(Long jobId, Collection<String> statuses);

    org.springframework.data.domain.Page<MigrationEntity> findByJob_JobIdOrderByMigId(Long jobId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<MigrationEntity> findByJob_JobIdAndStatusInOrderByMigId(Long jobId, Collection<String> statuses, org.springframework.data.domain.Pageable pageable);

    @Query("select m.status as status, count(m) as total from MigrationEntity m where m.job.jobId = :jobId group by m.status")
    List<StatusCount> countByStatusForJob(@Param("jobId") Long jobId);

    @Query("select upper(m.status) as status, count(m) as total from MigrationEntity m group by upper(m.status)")
    List<StatusCount> countByStatus();

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update MigrationEntity m set m.status = :to where m.status = :from and m.targetDocId is null")
    int resetNotUploaded(@Param("from") String from, @Param("to") String to);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update MigrationEntity m set m.status = :to where m.status = :from and m.targetDocId is not null")
    int markUploaded(@Param("from") String from, @Param("to") String to);

    interface StatusCount {
        String getStatus();
        long getTotal();
    }
}
