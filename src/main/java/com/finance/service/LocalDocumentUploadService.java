package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocalDocumentUploadService implements DocumentUploadService {

    private final DocumentProcessor documentProcessor;

    @Override
    public List<ImportedTransaction> processFile(MultipartFile file, User user) throws Exception {
        return documentProcessor.process(file, user);
    }
}
