package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentUploadService {
    List<ImportedTransaction> processFile(MultipartFile file, User user) throws Exception;
}
