package com.finance.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebAppManifestController {

    private static final MediaType WEB_APP_MANIFEST = MediaType.parseMediaType("application/manifest+json");

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Resource> manifest() {
        return ResponseEntity.ok()
                .contentType(WEB_APP_MANIFEST)
                .body(new ClassPathResource("static/manifest.webmanifest"));
    }
}
