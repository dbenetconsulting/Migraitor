package com.migration.types.sapal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Converts a (multi-page) TIFF, including CCITT G4 fax images, into a PDF. Same approach as the
 * Viega branch: standard reading first (TwelveMonkeys plugin), then rebuilding a valid TIFF header
 * around raw CCITT data when SAP sends it with a broken header. That rebuild guesses the page
 * height, so the result is flagged for review.
 */
final class TiffToPdfConverter {

    private static final Logger log = LoggerFactory.getLogger(TiffToPdfConverter.class);
    private static final int FAX_WIDTH = 1728;
    private static final int FAX_DPI = 200;
    private static final int[] FAX_HEIGHTS = {2200, 2339, 3508};

    static final class Result {
        final int pages;
        final boolean reconstructed;

        Result(int pages, boolean reconstructed) {
            this.pages = pages;
            this.reconstructed = reconstructed;
        }
    }

    private TiffToPdfConverter() {}

    static boolean isTiff(Path file) throws IOException {
        byte[] head = new byte[4];
        try (var in = Files.newInputStream(file)) {
            if (in.read(head) < 4) return false;
        }
        return (head[0] == 'I' && head[1] == 'I' && head[2] == 42 && head[3] == 0)
                || (head[0] == 'M' && head[1] == 'M' && head[2] == 0 && head[3] == 42);
    }

    static Result convert(Path tiff, Path pdf) throws IOException {
        byte[] data = Files.readAllBytes(tiff);
        boolean reconstructed = false;
        List<BufferedImage> images = readTiff(data);
        if (images.isEmpty()) {
            log.info("Standard TIFF reading failed, trying CCITT reconstruction");
            images = reconstructCcitt(data);
            reconstructed = !images.isEmpty();
        }
        if (images.isEmpty()) throw new IOException("No image could be read from the TIFF");

        try (PDDocument doc = new PDDocument()) {
            for (BufferedImage img : images) {
                PDPage page = new PDPage(new PDRectangle(img.getWidth(), img.getHeight()));
                doc.addPage(page);
                PDImageXObject xo = img.getColorModel().getPixelSize() == 1
                        ? CCITTFactory.createFromImage(doc, img)   // keeps fax pages small
                        : LosslessFactory.createFromImage(doc, img);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(xo, 0, 0, img.getWidth(), img.getHeight());
                }
            }
            doc.save(pdf.toFile());
        }
        return new Result(images.size(), reconstructed);
    }

    private static List<BufferedImage> readTiff(byte[] data) {
        List<BufferedImage> images = new ArrayList<>();
        ImageIO.scanForPlugins();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("TIFF");
            while (readers.hasNext() && images.isEmpty()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    int count = reader.getNumImages(true);
                    for (int i = 0; i < count; i++) images.add(reader.read(i));
                } catch (Exception e) {
                    images.clear();
                    iis.seek(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            log.debug("TIFF read failed: {}", e.getMessage());
        }
        return images;
    }

    private static List<BufferedImage> reconstructCcitt(byte[] tiff) {
        int start = 8;
        while (start < tiff.length && (tiff[start] & 0xFF) == 0xFF) start++;
        byte[] ccitt = Arrays.copyOfRange(tiff, start, tiff.length);
        for (int height : FAX_HEIGHTS) {
            try {
                List<BufferedImage> result = readTiff(buildCcittTiff(ccitt, FAX_WIDTH, height));
                if (!result.isEmpty()) return result;
            } catch (IOException e) {
                // try next height
            }
        }
        return List.of();
    }

    private static byte[] buildCcittTiff(byte[] ccitt, int width, int height) throws IOException {
        int entries = 13;
        int ifdSize = 2 + entries * 12 + 4;
        int dataOffset = 8 + ifdSize;
        ByteBuffer buf = ByteBuffer.allocate(8 + ifdSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        buf.putShort((short) entries);
        entry(buf, 256, 4, width);
        entry(buf, 257, 4, height);
        entry(buf, 258, 3, 1);
        entry(buf, 259, 3, 4);                        // CCITT G4
        entry(buf, 262, 3, 0);
        entry(buf, 273, 4, dataOffset);
        entry(buf, 278, 4, height);
        entry(buf, 279, 4, ccitt.length);
        entry(buf, 282, 5, dataOffset + ccitt.length);
        entry(buf, 283, 5, dataOffset + ccitt.length + 8);
        entry(buf, 284, 3, 1);
        entry(buf, 296, 3, 2);
        entry(buf, 292, 4, 0);
        buf.putInt(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(buf.array());
        out.write(ccitt);
        ByteBuffer res = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        res.putInt(FAX_DPI).putInt(1).putInt(FAX_DPI).putInt(1);
        out.write(res.array());
        return out.toByteArray();
    }

    private static void entry(ByteBuffer buf, int tag, int type, int value) {
        buf.putShort((short) tag).putShort((short) type).putInt(1).putInt(value);
    }
}
