package com.migration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.migration.core.DocStatus;
import com.migration.core.JobService;
import com.migration.entities.JobEntity;
import com.migration.entities.MigrationEntity;
import com.migration.otcs.ConnectionService;
import com.migration.otcs.ConnectionService.Role;
import com.migration.repositories.JobRepository;
import com.migration.repositories.MigrationRepository;
import com.migration.types.cstocs.CsToCsImporter;
import com.migration.types.cstocs.CsToCsType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deichmann flow against fake Content Servers: CSV exports, match, job, upload, categories, hash check.
 */
@SpringBootTest
@ActiveProfiles("test")
class CsToCsEndToEndTest {

    static WireMockServer cs = new WireMockServer(options().dynamicPort());
    static final byte[] CONTENT = "hello migraitor".getBytes(StandardCharsets.UTF_8);

    @Autowired ConnectionService connections;
    @Autowired CsToCsImporter importer;
    @Autowired JobService jobs;
    @Autowired JobRepository jobRepo;
    @Autowired MigrationRepository migrations;

    @BeforeAll
    static void startServer() {
        cs.start();
        cs.stubFor(post(urlMatching(".*/api/v1/auth")).willReturn(okJson("{\"ticket\":\"T1\"}")));
        // source on-premise
        cs.stubFor(get(urlEqualTo("/src/api/v1/nodes/1001/content")).willReturn(ok().withBody(CONTENT).withHeader("Content-Type", "text/plain")));
        cs.stubFor(get(urlEqualTo("/src/api/v1/nodes/1002/content")).willReturn(ok().withBody(CONTENT)));
        // target cloud
        cs.stubFor(post(urlEqualTo("/tgt/api/v2/nodes")).inScenario("names").whenScenarioStateIs("Started")
                .withMultipartRequestBody(aMultipart().withName("name").withBody(equalTo("contract.pdf")))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"An item with the name 'contract.pdf' already exists.\"}")));
        cs.stubFor(post(urlEqualTo("/tgt/api/v2/nodes")).atPriority(10)
                .willReturn(okJson("{\"results\":{\"data\":{\"properties\":{\"id\":555}}}}")));
        cs.stubFor(post(urlMatching("/tgt/api/v2/nodes/555/categories")).willReturn(okJson("{}")));
        cs.stubFor(get(urlEqualTo("/tgt/api/v2/nodes/555/content")).willReturn(ok().withBody(CONTENT)));
    }

    @AfterAll
    static void stopServer() {
        cs.stop();
    }

    @Test
    void importMatchAndMigrate() throws Exception {
        connections.save(CsToCsType.CODE, Role.SOURCE, cs.baseUrl() + "/src", "ONPREM", "src-user", "pwd", false, null);
        connections.save(CsToCsType.CODE, Role.TARGET, cs.baseUrl() + "/tgt", "CLOUD", "tgt-user", "pwd", false, null);

        String source = "DATAID;EMPLOYEEID;NAME;DOCTYPE;DATASIZE;MIMETYPE;CREATEDATE;FOLDERNAME;PARENTID;DATEOFTERMINATION\n"
                + "1001;00001234;contract.pdf;;15;application/pdf;2019-03-01;Vertrag;77;\n"
                + "1002;1234;payslip.pdf;;15;application/pdf;2020-01-01;\"Lohn  und Gehalt\";78;2024-12-31\n"
                + "1003;9999;orphan.pdf;;15;application/pdf;2020-01-01;Vertrag;79;\n"
                + "1004;1234;nofolder.pdf;;15;application/pdf;2020-01-01;Sonstiges;80;\n";
        String target = "EMPLOYEEID,EMPLOYEENAME,DATAID,NAME,PARENTNAME\n"
                + "1234,Max Mustermann,5001,Vertrag,999 Altakte\n"
                + "1234,Max Mustermann,5002,lohn und gehalt,999 Altakte\n"
                + "1234,Max Mustermann,5003,Sonstiges,Personalakte\n";

        var src = importer.importCsv(Role.SOURCE, new StringReader(source), "tester");
        var tgt = importer.importCsv(Role.TARGET, new StringReader(target), "tester");
        assertThat(src.getStatus()).isEqualTo("DONE");
        assertThat(src.getRowCount()).isEqualTo(4);

        var summary = importer.match(src.getRunId(), tgt.getRunId());
        assertThat(summary.counts).containsEntry("MATCHED", 2).containsEntry("NO_EMPLOYEE", 1).containsEntry("NO_FOLDER", 1);

        // matching again must not stage anything twice
        var again = importer.match(src.getRunId(), tgt.getRunId());
        assertThat(again.counts).isEmpty();

        JobEntity job = jobs.start("wave 1", CsToCsType.CODE, 10, 2, "tester");
        for (int i = 0; i < 100 && jobs.isRunning(job.getJobId()) || i == 0; i++) Thread.sleep(100);
        for (int i = 0; i < 100 && !"FINALIZED".equals(jobRepo.findById(job.getJobId()).orElseThrow().getStatus()); i++) Thread.sleep(100);

        JobEntity done = jobRepo.findById(job.getJobId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo("FINALIZED");
        assertThat(done.getProcessedItems()).isEqualTo(2);

        List<MigrationEntity> docs = migrations.findByJob_JobIdAndStatusIn(job.getJobId(),
                List.of(DocStatus.MIGRATED.name(), DocStatus.MIGRATED_WITH_WARNINGS.name()));
        assertThat(docs).hasSize(2);
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.getTargetDocId()).isEqualTo(555);
            assertThat(d.getHashCompare()).isEqualTo("HASH EQUAL");
        });
        MigrationEntity contract = docs.stream().filter(d -> d.getFileName().startsWith("contract")).findFirst().orElseThrow();
        assertThat(contract.getFolderId()).isEqualTo(5001);
        assertThat(contract.getFileName()).matches("contract_\\d+\\.pdf");
        assertThat(contract.getStatus()).isEqualTo(DocStatus.MIGRATED_WITH_WARNINGS.name());

        cs.verify(2, postRequestedFor(urlEqualTo("/tgt/api/v2/nodes/555/categories"))
                .withRequestBody(containing("category_id=30447")));
    }
}
