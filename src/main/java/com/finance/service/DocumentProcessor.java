package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentProcessor {
    List<ImportedTransaction> process(MultipartFile file, User user) throws Exception;
}
