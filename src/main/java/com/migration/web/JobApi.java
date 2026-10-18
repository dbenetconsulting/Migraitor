package com.migration.web;

import com.migration.core.DocStatus;
import com.migration.core.JobScheduler;
import com.migration.core.JobService;
import com.migration.core.MigrationTypes;
import com.migration.entities.JobEntity;
import com.migration.entities.MigrationEntity;
import com.migration.entities.MigrationEventEntity;
import com.migration.repositories.JobRepository;
import com.migration.repositories.MigrationEventRepository;
import com.migration.repositories.MigrationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class JobApi {

    private final JobService jobService;
    private final JobScheduler scheduler;
    private final MigrationTypes types;
    private final JobRepository jobs;
    private final MigrationRepository migrations;
    private final MigrationEventRepository events;

    public JobApi(JobService jobService, JobScheduler scheduler, MigrationTypes types, JobRepository jobs,
                  MigrationRepository migrations, MigrationEventRepository events) {
        this.jobService = jobService;
        this.scheduler = scheduler;
        this.types = types;
        this.jobs = jobs;
        this.migrations = migrations;
        this.events = events;
    }

    @GetMapping("/types")
    public List<Map<String, Object>> types() {
        return types.all().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.code());
            m.put("name", t.displayName());
            m.put("target", t.target());
            m.put("supportsImport", t.supportsImport());
            return m;
        }).collect(Collectors.toList());
    }

    /** Home page: documents by status and active jobs. */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        migrations.countByStatus().forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("documents", byStatus);
        m.put("activeJobs", jobs.findByStatusIn(List.of("INITIATED", "RETRYING")));
        m.put("scheduledJobs", jobs.findByStatusIn(List.of("PENDING")).size());
        m.put("schedulerActive", scheduler.isActive());
        return m;
    }

    @GetMapping("/jobs")
    public Page<JobEntity> jobs(@RequestParam(defaultValue = "") String q,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return jobs.findByJobNameContainingIgnoreCase(q, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "jobId")));
    }

    @GetMapping("/jobs/{id}")
    public Map<String, Object> job(@PathVariable long id) {
        JobEntity job = jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("Job " + id + " not found"));
        Map<String, Long> counts = new LinkedHashMap<>();
        migrations.countByStatusForJob(id).forEach(c -> counts.put(c.getStatus(), c.getTotal()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("job", job);
        m.put("documents", counts);
        m.put("running", jobService.isRunning(id));
        return m;
    }

    public static class NewJob {
        @NotBlank public String name;
        @NotBlank public String migType;
        @Min(1) @Max(1_000_000) public int numItems = 100;
        @Min(1) @Max(4) public int logLevel = 1;
        /** Empty = start now. */
        public LocalDateTime startAt;
    }

    @PostMapping("/jobs")
    public JobEntity create(@Valid @RequestBody NewJob req, Principal user) {
        types.get(req.migType);
        String name = req.name.trim();
        return req.startAt == null
                ? jobService.start(name, req.migType, req.numItems, req.logLevel, user.getName())
                : jobService.schedule(name, req.migType, req.numItems, req.logLevel, user.getName(), req.startAt);
    }

    @PostMapping("/jobs/{id}/abort")
    public Map<String, Object> abort(@PathVariable long id) {
        return Map.of("aborted", jobService.abort(id));
    }

    @PostMapping("/jobs/{id}/retry")
    public Map<String, Object> retry(@PathVariable long id) {
        return Map.of("documents", jobService.retry(id));
    }

    @GetMapping("/jobs/{id}/events")
    public List<MigrationEventEntity> events(@PathVariable long id,
                                             @RequestParam(defaultValue = "false") boolean errorsOnly,
                                             @RequestParam(defaultValue = "200") int limit) {
        PageRequest page = PageRequest.of(0, Math.min(limit, 2000));
        return errorsOnly
                ? events.findByJobIdAndOutcomeNotInOrderByEventIdDesc(id, List.of("SUCCESS"), page)
                : events.findByJobIdOrderByEventIdDesc(id, page);
    }

    @GetMapping("/jobs/{id}/documents")
    public Page<Map<String, Object>> documents(@PathVariable long id,
                                               @RequestParam(defaultValue = "") String status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        PageRequest p = PageRequest.of(page, Math.min(size, 500));
        Page<MigrationEntity> docs;
        if (status.isBlank()) docs = migrations.findByJob_JobIdOrderByMigId(id, p);
        else if ("ERRORS".equals(status)) docs = migrations.findByJob_JobIdAndStatusInOrderByMigId(id, DocStatus.errorNames(), p);
        else docs = migrations.findByJob_JobIdAndStatusInOrderByMigId(id, List.of(status), p);
        return docs.map(JobApi::doc);
    }

    @GetMapping("/documents/{migId}/events")
    public List<MigrationEventEntity> documentEvents(@PathVariable long migId) {
        return events.findByMigIdOrderByEventIdDesc(migId, PageRequest.of(0, 200));
    }

    @GetMapping("/scheduler")
    public Map<String, Object> scheduler() {
        return Map.of("active", scheduler.isActive(), "pending", jobs.findByStatusIn(List.of("PENDING")));
    }

    @PutMapping("/scheduler")
    public Map<String, Object> setScheduler(@RequestBody Map<String, Boolean> body) {
        scheduler.setActive(Boolean.TRUE.equals(body.get("active")));
        return scheduler();
    }

    private static Map<String, Object> doc(MigrationEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("migId", d.getMigId());
        m.put("expedientId", d.getExpedientId());
        m.put("folderId", d.getFolderId());
        m.put("folderName", d.getFolderName());
        m.put("fileName", d.getFileName());
        m.put("smartDocType", d.getSmartDocType());
        m.put("size", d.getSize());
        m.put("mimeType", d.getMimeType());
        m.put("status", d.getStatus());
        m.put("targetDocId", d.getTargetDocId());
        m.put("hashCompare", d.getHashCompare());
        m.put("migrationDate", d.getMigrationDate());
        m.put("errorDetail", d.getErrorDetail());
        return m;
    }
}
