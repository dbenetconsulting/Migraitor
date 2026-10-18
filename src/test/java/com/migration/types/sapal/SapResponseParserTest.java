package com.migration.types.sapal;

import com.migration.core.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SapResponseParserTest {

    @TempDir Path dir;

    @Test
    void decodesOnlyTheMainComponent() throws Exception {
        byte[] main = "MAIN-DOCUMENT-CONTENT-1234567890".getBytes(StandardCharsets.US_ASCII);
        String b64 = Base64.getEncoder().encodeToString(main);
        String l1 = b64.substring(0, 20), l2 = b64.substring(20);
        String other = Base64.getEncoder().encodeToString("NOTE".getBytes(StandardCharsets.US_ASCII));
        String xml = "<soap:Envelope xmlns:soap='http://schemas.xmlsoap.org/soap/envelope/'><soap:Body><n0:ZRfcMigrationOtResponse xmlns:n0='urn:x'>"
                + "<ItBinaryobject><item><Line>" + other + "</Line></item><item><Line>" + l1 + "</Line></item><item><Line>" + l2 + "</Line></item></ItBinaryobject>"
                + "<ItSize><item><CompId>note</CompId><Mimetype>text/plain</Mimetype><FirstLine>1</FirstLine><LastLine>1</LastLine></item>"
                + "<item><CompId>data</CompId><Mimetype>application/pdf</Mimetype><CreaTime>20190301101112</CreaTime><FirstLine>2</FirstLine><LastLine>3</LastLine></item></ItSize>"
                + "</n0:ZRfcMigrationOtResponse></soap:Body></soap:Envelope>";
        Path in = Files.writeString(dir.resolve("r.xml"), xml);
        Path out = dir.resolve("out.bin");

        SapResponseParser.Result r = SapResponseParser.parse(in, dir.resolve("lines"), out);

        assertThat(Files.readAllBytes(out)).isEqualTo(main);
        assertThat(r.size).isEqualTo(main.length);
        assertThat(r.component.mimeType).isEqualTo("application/pdf");
        assertThat(r.sha256).isEqualTo(Hashing.hex(Hashing.sha256().digest(main)));
        assertThat(SapAlNoDepType.fromSapTime(r.component.creaTime)).isEqualTo("2019-03-01 10:11:12");
    }

    @Test
    void soapEnvelopeEscapesValues() {
        assertThat(SapArchiveLinkClient.envelope("A<B", "R&1", "HR")).contains("<IvArcdocid>A&lt;B</IvArcdocid>").contains("<IvArchid>R&amp;1</IvArchid>");
    }
}
