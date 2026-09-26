package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;

import java.io.InputStream;
import java.util.List;

public interface ApachePoiService {
    List<ImportedTransaction> parseWorkbook(InputStream inputStream, String originalFilename, User user) throws Exception;

    List<ImportedTransaction> parseCsv(InputStream inputStream, String originalFilename, User user) throws Exception;
}
