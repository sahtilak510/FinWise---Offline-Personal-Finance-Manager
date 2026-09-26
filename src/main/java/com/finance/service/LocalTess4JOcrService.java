package com.finance.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Fully offline Tesseract OCR wrapper.
 *
 * <p>The engine needs its language data file ({@code eng.traineddata}) on a
 * real filesystem path. FinWise ships that file inside the app
 * ({@code classpath:/tessdata/eng.traineddata}, no download, no cloud) and
 * copies it once to the local {@code data/tessdata/} folder so it also works
 * when running from a packaged jar. A system Tesseract install
 * ({@code TESSDATA_PREFIX} or the usual Windows paths) is still honoured
 * first when present.</p>
 *
 * <p>Key hardening: the datapath is <em>validated before</em> the native call.
 * Previously a missing {@code eng.traineddata} made the native layer throw
 * {@code java.lang.Error: Invalid memory access}, which bypassed every
 * {@code catch (Exception)} and surfaced as a 500 HTML page ("Unable to
 * process document"). Native failures are now caught as {@link Throwable}
 * and degrade to an empty result so callers can respond with clean JSON.</p>
 */
@Service
public class LocalTess4JOcrService implements Tess4JOcrService {

    private static final Logger log = LoggerFactory.getLogger(LocalTess4JOcrService.class);

    private static final String TRAINED_DATA = "eng.traineddata";

    @Value("${app.tessdata-dir:data/tessdata}")
    private String tessdataDirectory;

    @Override
    public String extractText(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }

        String dataPath = resolveTessDataPath();
        if (dataPath == null) {
            log.warn("OCR skipped: eng.traineddata not found in data/tessdata, TESSDATA_PREFIX, "
                    + "system install or app bundle.");
            return "";
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("finwise-ocr-", ".png");
            Files.write(tempFile.toPath(), imageBytes);

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataPath);

            String result = tesseract.doOCR(tempFile);
            return result != null ? result.trim() : "";
        } catch (Throwable error) {
            log.error("OCR extraction failed", error);
            return "";
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public String extractText(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }

        String dataPath = resolveTessDataPath();
        if (dataPath == null) {
            log.warn("OCR skipped: eng.traineddata not found");
            return "";
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("finwise-ocr-", ".png");
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataPath);

            String result = tesseract.doOCR(tempFile);
            return result != null ? result.trim() : "";
        } catch (Throwable error) {
            log.error("OCR extraction failed", error);
            return "";
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public String extractText(File file) {
        if (file == null || !file.exists()) {
            return "";
        }

        String dataPath = resolveTessDataPath();
        if (dataPath == null) {
            log.warn("OCR skipped: eng.traineddata not found");
            return "";
        }

        try {
            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataPath);

            String result = tesseract.doOCR(file);
            return result != null ? result.trim() : "";
        } catch (Throwable error) {
            log.error("OCR extraction failed", error);
            return "";
        }
    }

    private String resolveTessDataPath() {
        String tessdataPrefix = System.getenv("TESSDATA_PREFIX");
        if (tessdataPrefix != null && !tessdataPrefix.isBlank()) {
            Path trainedData = Path.of(tessdataPrefix, TRAINED_DATA);
            if (Files.exists(trainedData)) {
                return tessdataPrefix;
            }
        }

        String configuredDirectory = tessdataDirectory == null || tessdataDirectory.isBlank()
                ? "data/tessdata"
                : tessdataDirectory;
        Path localTessdata = Path.of(configuredDirectory, TRAINED_DATA);
        if (Files.exists(localTessdata)) {
            return localTessdata.getParent().toString();
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                Path tesseractPath = Path.of(programFiles, "Tesseract-OCR", "tessdata", TRAINED_DATA);
                if (Files.exists(tesseractPath)) {
                    return tesseractPath.getParent().toString();
                }
            }
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            if (programFilesX86 != null) {
                Path tesseractPath = Path.of(programFilesX86, "Tesseract-OCR", "tessdata", TRAINED_DATA);
                if (Files.exists(tesseractPath)) {
                    return tesseractPath.getParent().toString();
                }
            }
        } else {
            Path tesseractPath = Path.of("/usr/share/tesseract-ocr", "4.00", "tessdata", TRAINED_DATA);
            if (Files.exists(tesseractPath)) {
                return tesseractPath.getParent().toString();
            }
            Path tesseractPath5 = Path.of("/usr/share/tesseract-ocr", "5", "tessdata", TRAINED_DATA);
            if (Files.exists(tesseractPath5)) {
                return tesseractPath5.getParent().toString();
            }
        }

        return null;
    }
}