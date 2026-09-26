package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("username", user.getUsername());
        profile.put("fullName", user.getFullName());
        profile.put("email", user.getEmail());
        profile.put("avatarUrl", user.getProfileImage() != null ? user.getProfileImage() : "");
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/image")
    public ResponseEntity<Map<String, Object>> uploadImage(Authentication auth,
                                                          @RequestParam("file") MultipartFile file) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No file selected."));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only image files are accepted."));
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        try {
            Path uploadsDir = Paths.get("data", "uploads", "profile");
            Files.createDirectories(uploadsDir);

            String originalFilename = file.getOriginalFilename();
            String originalName = originalFilename == null ? "profile" : originalFilename;
            String extension = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > -1 && dotIndex < originalName.length() - 1) {
                extension = originalName.substring(dotIndex);
            }
            String safeFilename = user.getId() + "_" + UUID.randomUUID() + extension;
            Path targetFile = uploadsDir.resolve(safeFilename);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

            if (user.getProfileImage() != null && !user.getProfileImage().isBlank()) {
                try {
                    Path currentImage = Paths.get("data", "uploads", "profile").resolve(user.getProfileImage().replace("/uploads/profile/", ""));
                    Files.deleteIfExists(currentImage);
                } catch (IOException ignored) {
                    // ignore stale file cleanup errors
                }
            }

            String avatarUrl = "/uploads/profile/" + safeFilename;
            user.setProfileImage(avatarUrl);
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Profile image updated.");
            response.put("avatarUrl", avatarUrl);
            return ResponseEntity.ok(response);
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Unable to save the profile image."));
        }
    }

    @DeleteMapping("/image")
    public ResponseEntity<Map<String, String>> deleteImage(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        if (user.getProfileImage() != null && !user.getProfileImage().isBlank()) {
            try {
                Path currentImage = Paths.get("data", "uploads", "profile").resolve(user.getProfileImage().replace("/uploads/profile/", ""));
                Files.deleteIfExists(currentImage);
            } catch (IOException ignored) {
                // ignore cleanup errors
            }
        }

        user.setProfileImage(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Profile image removed."));
    }
}
