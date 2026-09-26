package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DefaultDocumentProcessor implements DocumentProcessor {

    private final OpenCVPreprocessor openCVPreprocessor;
    private final Tess4JOcrService tess4JOcrService;
    private final PDFBoxService pdfBoxService;
    private final ApachePoiService apachePoiService;
    private final TransactionExtractor transactionExtractor;

    public DefaultDocumentProcessor(OpenCVPreprocessor openCVPreprocessor,
                                    Tess4JOcrService tess4JOcrService,
                                    PDFBoxService pdfBoxService,
                                    ApachePoiService apachePoiService,
                                    TransactionExtractor transactionExtractor) {
        this.openCVPreprocessor = openCVPreprocessor;
        this.tess4JOcrService = tess4JOcrService;
        this.pdfBoxService = pdfBoxService;
        this.apachePoiService = apachePoiService;
        this.transactionExtractor = transactionExtractor;
    }

    @Override
    public List<ImportedTransaction> process(MultipartFile file, User user) throws Exception {
        if (file == null || file.isEmpty()) {
            return new ArrayList<>();
        }

        String fileName = file.getOriginalFilename();
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            byte[] imageBytes = file.getBytes();
            byte[] preprocessed = openCVPreprocessor.preprocess(imageBytes);
            String text = tess4JOcrService.extractText(preprocessed);
            if (text == null || text.isBlank()) {
                text = tess4JOcrService.extractText(imageBytes);
            }
            return transactionExtractor.extractTransactions(text, fileName, user, "IMAGE");
        }

        if (lowerName.endsWith(".pdf")) {
            return pdfBoxService.parsePdf(file.getInputStream(), fileName, user);
        }

        if (lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx")) {
            return apachePoiService.parseWorkbook(file.getInputStream(), fileName, user);
        }

        if (lowerName.endsWith(".csv")) {
            return apachePoiService.parseCsv(file.getInputStream(), fileName, user);
        }

        throw new IllegalArgumentException("Unsupported file type: " + fileName);
    }
}
