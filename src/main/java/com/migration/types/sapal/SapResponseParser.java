package com.migration.types.sapal;

import com.migration.core.Hashing;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Streams the SOAP response of ZRfcMigrationOt (no DOM: big documents do not need to fit in memory).
 * First pass: Base64 lines go to a scratch file and ItSize rows are collected. Second pass: the
 * lines of the main component are decoded into the output file while hashing.
 */
final class SapResponseParser {

    static final class Result {
        final SapComponent component;
        final long size;
        final String sha256;

        Result(SapComponent component, long size, String sha256) {
            this.component = component;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    private SapResponseParser() {}

    static Result parse(Path xml, Path linesScratch, Path output) throws Exception {
        List<SapComponent> components = new ArrayList<>();
        int lineCount = 0;

        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = Files.newInputStream(xml);
             BufferedWriter lines = Files.newBufferedWriter(linesScratch, StandardCharsets.US_ASCII)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            boolean inSize = false;
            boolean inBinary = false;
            String compId = null, mime = null, crea = null, first = null, last = null;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String name = r.getLocalName();
                    if ("ItSize".equals(name)) inSize = true;
                    else if ("ItBinaryobject".equals(name)) inBinary = true;
                    else if (inSize && "item".equals(name)) compId = mime = crea = first = last = null;
                    else if (inSize && "CompId".equals(name)) compId = r.getElementText().trim();
                    else if (inSize && "Mimetype".equals(name)) mime = r.getElementText().trim();
                    else if (inSize && "CreaTime".equals(name)) crea = r.getElementText().trim();
                    else if (inSize && "FirstLine".equals(name)) first = r.getElementText().trim();
                    else if (inSize && "LastLine".equals(name)) last = r.getElementText().trim();
                    else if (inBinary && "Line".equals(name)) {
                        lines.write(r.getElementText().replaceAll("\\s+", ""));
                        lines.write('\n');
                        lineCount++;
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    String name = r.getLocalName();
                    if ("ItSize".equals(name)) inSize = false;
                    else if ("ItBinaryobject".equals(name)) inBinary = false;
                    else if (inSize && "item".equals(name) && first != null && last != null) {
                        components.add(new SapComponent(compId, mime, crea, Integer.parseInt(first), Integer.parseInt(last)));
                    }
                }
            }
            r.close();
        }
        if (lineCount == 0) throw new IllegalStateException("SAP returned no binary lines (ItBinaryobject is empty)");

        SapComponent main;
        if (components.isEmpty()) {
            // No ItSize rows: the whole binary is the document (Viega branch behaviour).
            main = new SapComponent("data", null, null, 1, lineCount);
        } else {
            main = components.stream().filter(SapComponent::isMain).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No main component (data/data1) in the SAP response"));
        }

        MessageDigest digest = Hashing.sha256();
        long size;
        try (BufferedReader reader = Files.newBufferedReader(linesScratch, StandardCharsets.US_ASCII);
             CountingOut counting = new CountingOut(new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(output)), digest))) {
            Base64LineDecoder decoder = new Base64LineDecoder(counting);
            String line;
            int n = 0;
            while ((line = reader.readLine()) != null) {
                n++;
                if (n < main.firstLine) continue;
                if (n > main.lastLine) break;
                decoder.line(line);
            }
            decoder.finish();
            counting.flush();
            size = counting.count;
        }
        return new Result(main, size, Hashing.hex(digest.digest()));
    }

    private static final class CountingOut extends OutputStream {
        private final OutputStream delegate;
        long count;

        CountingOut(OutputStream delegate) { this.delegate = delegate; }
        @Override public void write(int b) throws IOException { count++; delegate.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { count += len; delegate.write(b, off, len); }
        @Override public void flush() throws IOException { delegate.flush(); }
        @Override public void close() throws IOException { delegate.close(); }
    }
}
