package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;

import java.io.InputStream;
import java.util.List;

public interface PDFBoxService {
    List<ImportedTransaction> parsePdf(InputStream inputStream, String originalFilename, User user) throws Exception;
}
