package com.migration.repositories;

import com.migration.entities.JobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface JobRepository extends JpaRepository<JobEntity, Long> {

    @Query("select j from JobEntity j where j.status = :status and j.startDate <= :now order by j.startDate")
    List<JobEntity> findReadyToRun(@Param("status") String status, @Param("now") LocalDateTime now);

    Page<JobEntity> findByJobNameContainingIgnoreCase(String jobName, Pageable pageable);

    List<JobEntity> findByStatusIn(Collection<String> statuses);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update JobEntity j set j.processedItems = j.processedItems + 1 where j.jobId = :jobId")
    void incrementProcessed(@Param("jobId") Long jobId);

    /** Atomically moves a scheduled job to INITIATED so it is picked only once. */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update JobEntity j set j.status = :to where j.jobId = :jobId and j.status = :from")
    int transition(@Param("jobId") Long jobId, @Param("from") String from, @Param("to") String to);
}
