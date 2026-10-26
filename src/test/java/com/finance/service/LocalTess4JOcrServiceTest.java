package com.finance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTess4JOcrServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesBundledLanguageDataToLocalDirectory() throws IOException {
        LocalTess4JOcrService service = new LocalTess4JOcrService();
        Path destination = temporaryDirectory.resolve("tessdata");

        String dataPath = service.copyBundledTessData(destination);

        assertEquals(destination.toString(), dataPath);
        assertTrue(Files.isRegularFile(destination.resolve("eng.traineddata")));
        assertTrue(Files.size(destination.resolve("eng.traineddata")) > 0);
    }
}
