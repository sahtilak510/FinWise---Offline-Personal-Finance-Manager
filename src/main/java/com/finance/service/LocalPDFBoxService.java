package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class LocalPDFBoxService implements PDFBoxService {

    private final TransactionExtractor transactionExtractor;

    public LocalPDFBoxService(TransactionExtractor transactionExtractor) {
        this.transactionExtractor = transactionExtractor;
    }

    @Override
    public List<ImportedTransaction> parsePdf(InputStream inputStream, String originalFilename, User user) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return transactionExtractor.extractTransactions(text, originalFilename, user, "PDF");
        }
    }
}