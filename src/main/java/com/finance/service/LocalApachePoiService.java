package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class LocalApachePoiService implements ApachePoiService {

    private final TransactionExtractor transactionExtractor;

    public LocalApachePoiService(TransactionExtractor transactionExtractor) {
        this.transactionExtractor = transactionExtractor;
    }

    @Override
    public List<ImportedTransaction> parseWorkbook(InputStream inputStream, String originalFilename, User user) throws Exception {
        List<ImportedTransaction> records = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                StringBuilder line = new StringBuilder();
                for (Cell cell : row) {
                    line.append(getCellString(cell)).append(" ");
                }
                String text = line.toString();
                if (text.isBlank()) {
                    continue;
                }
                records.addAll(transactionExtractor.extractTransactions(text, originalFilename, user, "EXCEL"));
            }
        }
        return records;
    }

    @Override
    public List<ImportedTransaction> parseCsv(InputStream inputStream, String originalFilename, User user) throws Exception {
        List<ImportedTransaction> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                if (line == null || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                StringBuilder cleaned = new StringBuilder();
                for (String part : parts) {
                    cleaned.append(part.trim()).append(" ");
                }
                String text = cleaned.toString();
                if (!text.isBlank()) {
                    records.addAll(transactionExtractor.extractTransactions(text, originalFilename, user, "CSV"));
                }
            }
        }
        return records;
    }

    private String getCellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
