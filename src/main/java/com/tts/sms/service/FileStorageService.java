package com.tts.sms.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-file-size:5MB}")
    private String maxFileSize;

    @Value("${app.upload.allowed-extensions:jpg,jpeg,png,gif,webp}")
    private String allowedExtensions;

    private Path uploadPath;
    private long maxFileSizeBytes;
    private List<String> allowedExtensionsList;

    @PostConstruct
    public void init() {
        try {
            // Create base upload directory
            uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("✅ Created base upload directory: {}", uploadPath);
            }

            // Create subdirectories for different entities
            createSubDirectory("employees");
            createSubDirectory("courses");
            createSubDirectory("admissions");

            maxFileSizeBytes = parseSize(maxFileSize);
            allowedExtensionsList = Arrays.asList(allowedExtensions.toLowerCase().split(","));

            log.info("📏 Max file size: {} bytes ({})", maxFileSizeBytes, maxFileSize);
            log.info("📝 Allowed extensions: {}", allowedExtensionsList);

        } catch (IOException ex) {
            log.error("❌ Could not create upload directory: {}", uploadDir, ex);
            throw new RuntimeException("Could not create upload directory!", ex);
        }
    }

    private void createSubDirectory(String subDir) throws IOException {
        Path subPath = uploadPath.resolve(subDir);
        if (!Files.exists(subPath)) {
            Files.createDirectories(subPath);
            log.info("✅ Created subdirectory: {}", subPath);
        }
    }

    /**
     * Save Base64 encoded image (for Employee photos)
     * @param base64Data - Base64 encoded image string
     * @param entityType - "employees" or "courses"
     * @return filename only (not full path)
     */
    public String saveBase64Image(String base64Data, String entityType) {
        try {
            if (base64Data == null || base64Data.isEmpty()) {
                log.warn("⚠️ Base64 data is null or empty");
                return null;
            }

            // Extract actual base64 data (remove data:image/...;base64, prefix)
            String imageData = extractBase64Data(base64Data);

            // Decode base64
            byte[] decodedBytes;
            try {
                decodedBytes = Base64.getDecoder().decode(imageData);
            } catch (IllegalArgumentException e) {
                log.error("❌ Invalid base64 encoding: {}", e.getMessage());
                throw new RuntimeException("Invalid image data encoding", e);
            }

            // Validate size
            if (decodedBytes.length > maxFileSizeBytes) {
                throw new RuntimeException("Image size exceeds maximum limit of " + maxFileSize);
            }

            // Determine extension from data URL
            String extension = detectImageExtension(base64Data);

            // Generate unique filename
            String filename = generateUniqueFilename(entityType, extension);

            // Resolve full path with subdirectory
            Path targetLocation = uploadPath.resolve(entityType).resolve(filename);

            // Write file to disk
            Files.write(targetLocation, decodedBytes);

            log.info("✅ Base64 image saved: {}/{}", entityType, filename);

            // Return ONLY filename (subdirectory will be handled by path resolution)
            return filename;

        } catch (IOException ex) {
            log.error("❌ Could not save base64 image: {}", ex.getMessage());
            throw new RuntimeException("Could not save image. Please try again!", ex);
        }
    }

    /**
     * Store MultipartFile (for Course images and file uploads)
     * @param file - MultipartFile from form
     * @param entityType - "employees" or "courses"
     * @return filename only
     */
    public String storeFile(MultipartFile file, String entityType) {
        try {
            validateFile(file);

            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String fileExtension = getFileExtension(originalFilename);
            String filename = generateUniqueFilename(entityType, fileExtension);

            Path targetLocation = uploadPath.resolve(entityType).resolve(filename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            log.info("✅ File stored: {}/{} (original: {})", entityType, filename, originalFilename);
            return filename;

        } catch (IOException ex) {
            log.error("❌ Could not store file: {}", ex.getMessage());
            throw new RuntimeException("Could not store file. Please try again!", ex);
        }
    }

    /**
     * Backward compatibility - uses default "employees" directory
     */
    public String storeFile(MultipartFile file) {
        return storeFile(file, "employees");
    }

    /**
     * Delete file from storage
     * @param filename - just the filename
     * @param entityType - "employees" or "courses"
     */
    public void deleteFile(String filename, String entityType) {
        try {
            if (filename == null || filename.trim().isEmpty()) {
                log.warn("⚠️ Attempted to delete file with null or empty filename");
                return;
            }

            Path filePath = uploadPath.resolve(entityType).resolve(filename).normalize();

            // Security check
            if (!filePath.startsWith(uploadPath.resolve(entityType))) {
                log.error("❌ Security violation: Attempted to delete file outside directory: {}", filename);
                throw new RuntimeException("Invalid file path");
            }

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("✅ File deleted: {}/{}", entityType, filename);
            } else {
                log.warn("⚠️ File not found for deletion: {}/{}", entityType, filename);
            }
        } catch (IOException ex) {
            log.error("❌ Could not delete file: {}", filename, ex);
        }
    }

    /**
     * Backward compatibility - uses default "employees" directory
     */
    public void deleteFile(String filename) {
        deleteFile(filename, "employees");
    }

    /**
     * Get file path for serving files
     */
    public Path getFilePath(String filename, String entityType) {
        return uploadPath.resolve(entityType).resolve(filename).normalize();
    }

    /**
     * Check if file exists
     */
    public boolean fileExists(String filename, String entityType) {
        if (filename == null || filename.trim().isEmpty()) return false;
        Path filePath = uploadPath.resolve(entityType).resolve(filename).normalize();
        return Files.exists(filePath);
    }

    /**
     * Generate unique filename
     */
    private String generateUniqueFilename(String prefix, String extension) {
        return prefix + "_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8) + extension;
    }

    /**
     * Extract base64 data from data URL
     */
    private String extractBase64Data(String base64String) {
        if (base64String.contains(",")) {
            String[] parts = base64String.split(",", 2);
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return base64String;
    }

    /**
     * Detect image extension from data URL
     */
    private String detectImageExtension(String base64String) {
        if (base64String.contains("data:image/")) {
            if (base64String.contains("image/png")) return ".png";
            if (base64String.contains("image/jpeg") || base64String.contains("image/jpg")) return ".jpg";
            if (base64String.contains("image/gif")) return ".gif";
            if (base64String.contains("image/webp")) return ".webp";
        }
        return ".jpg"; // Default
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) return ".jpg";
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? ".jpg" : filename.substring(dotIndex);
    }

    /**
     * Validate multipart file
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty or null");
        }

        if (!isValidImageFile(file)) {
            throw new RuntimeException("Invalid file type. Only " +
                    String.join(", ", allowedExtensionsList).toUpperCase() + " images are allowed.");
        }

        if (!isValidFileSize(file)) {
            throw new RuntimeException("File size exceeds maximum limit of " + maxFileSize);
        }
    }

    /**
     * Validate image file type
     */
    public boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) return false;

        List<String> validContentTypes = Arrays.asList(
                "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
        );

        if (!validContentTypes.contains(contentType.toLowerCase())) {
            return false;
        }

        String filename = file.getOriginalFilename();
        if (filename == null) return false;

        String extension = getFileExtension(filename).toLowerCase().replace(".", "");
        return allowedExtensionsList.contains(extension);
    }

    /**
     * Validate file size
     */
    public boolean isValidFileSize(MultipartFile file) {
        return file.getSize() <= maxFileSizeBytes;
    }

    /**
     * Parse size string to bytes
     */
    private long parseSize(String size) {
        size = size.toUpperCase().trim();

        if (size.endsWith("KB")) {
            return Long.parseLong(size.replace("KB", "").trim()) * 1024;
        } else if (size.endsWith("MB")) {
            return Long.parseLong(size.replace("MB", "").trim()) * 1024 * 1024;
        } else if (size.endsWith("GB")) {
            return Long.parseLong(size.replace("GB", "").trim()) * 1024 * 1024 * 1024;
        } else {
            return Long.parseLong(size);
        }
    }

    public String getUploadDir() {
        return uploadPath.toString();
    }
}