package com.tts.sms.service;

import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfReader;
import com.tts.sms.model.Course;
import com.tts.sms.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to manage course brochures and consolidate multiple course PDF brochures
 * into a single unified PDF document for Meta WhatsApp delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseBrochureService {

    private final CourseRepository courseRepository;

    @Value("${app.upload.courses.brochures-dir:./uploads/courses/brochures}")
    private String brochuresDir;

    @Value("${app.base-url:https://team.ttsnashik.com}")
    private String baseUrl;

    /**
     * Finds and consolidates PDF brochures for the given list of courses.
     *
     * @param courseNames List of course names
     * @return Public URL to a single consolidated PDF, or null if no real PDFs exist.
     */
    public String getConsolidatedBrochureUrl(List<String> courseNames) {
        if (courseNames == null || courseNames.isEmpty()) {
            return null;
        }

        List<File> matchingPdfs = findMatchingBrochureFiles(courseNames);
        if (matchingPdfs.isEmpty()) {
            log.info("[BROCHURE] No matching PDF brochures found on disk for courses: {}", courseNames);
            return null;
        }

        if (matchingPdfs.size() == 1) {
            File singlePdf = matchingPdfs.get(0);
            log.info("[BROCHURE] Found 1 brochure: {}", singlePdf.getName());
            return buildPublicUrl("brochures/" + singlePdf.getName());
        }

        // Multiple brochures: Merge into ONE consolidated PDF
        try {
            File consolidatedDir = new File(brochuresDir, "consolidated");
            if (!consolidatedDir.exists() && !consolidatedDir.mkdirs()) {
                log.warn("[BROCHURE] Could not create consolidated brochure directory: {}", consolidatedDir.getAbsolutePath());
            }

            String consolidatedFileName = generateConsolidatedFileName(courseNames, matchingPdfs);
            File outputFile = new File(consolidatedDir, consolidatedFileName);

            if (outputFile.exists() && outputFile.length() > 0) {
                log.info("[BROCHURE] Reusing existing consolidated brochure: {}", outputFile.getName());
                return buildPublicUrl("brochures/consolidated/" + outputFile.getName());
            }

            log.info("[BROCHURE] Merging {} brochures into {}", matchingPdfs.size(), outputFile.getName());
            mergePdfs(matchingPdfs, outputFile);

            return buildPublicUrl("brochures/consolidated/" + outputFile.getName());

        } catch (Exception e) {
            log.error("[BROCHURE] Failed to merge PDF brochures: {}", e.getMessage(), e);
            // Fall back to first valid PDF if merge fails
            return buildPublicUrl("brochures/" + matchingPdfs.get(0).getName());
        }
    }

    /**
     * Resolves matching real PDF files on disk for the provided course names.
     */
    private List<File> findMatchingBrochureFiles(List<String> courseNames) {
        File dir = new File(brochuresDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<File> result = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        for (String course : courseNames) {
            if (course == null || course.isBlank()) {
                continue;
            }

            // 1. Check Course entity from repository
            try {
                Optional<Course> courseOpt = courseRepository.findByCourseNameAndIsActiveTrue(course.trim());
                if (courseOpt.isPresent() && courseOpt.get().getBrochurePath() != null) {
                    File entityBrochure = new File(dir, courseOpt.get().getBrochurePath());
                    if (entityBrochure.exists() && entityBrochure.length() > 0) {
                        if (addedNames.add(entityBrochure.getName())) {
                            result.add(entityBrochure);
                            continue;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[BROCHURE] Could not check course entity for '{}': {}", course, e.getMessage());
            }

            // 2. Fallback: fuzzy matching on disk
            String normalizedCourse = normalizeName(course);
            for (File file : files) {
                if (file.length() <= 0) {
                    continue; // Skip 0-byte files
                }
                String normalizedFileName = normalizeName(stripPdfExtension(file.getName()));
                if (normalizedFileName.contains(normalizedCourse) || normalizedCourse.contains(normalizedFileName)) {
                    if (addedNames.add(file.getName())) {
                        result.add(file);
                        break;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Merges multiple PDF files into one using iText 5.
     */
    private void mergePdfs(List<File> pdfFiles, File outputFile) throws Exception {
        Document document = new Document();
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            PdfCopy copy = new PdfCopy(document, fos);
            document.open();

            for (File pdf : pdfFiles) {
                PdfReader reader = new PdfReader(pdf.getAbsolutePath());
                int n = reader.getNumberOfPages();
                for (int page = 1; page <= n; page++) {
                    copy.addPage(copy.getImportedPage(reader, page));
                }
                copy.freeReader(reader);
                reader.close();
            }

            document.close();
        }
        log.info("[BROCHURE] Consolidated brochure created successfully: {} ({} bytes)",
                outputFile.getAbsolutePath(), outputFile.length());
    }

    /**
     * Generates a deterministic hash-based filename prefixed by the 1st course name.
     */
    private String generateConsolidatedFileName(List<String> courseNames, List<File> files) {
        String prefix = "Course";
        if (courseNames != null && !courseNames.isEmpty() && courseNames.get(0) != null) {
            String firstCourse = courseNames.get(0).trim();
            if (firstCourse.contains(",")) {
                firstCourse = firstCourse.split(",")[0].trim();
            }
            prefix = CourseService.formatBrochureFileName(firstCourse, "").replace("_Brochure", "");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String sortedNames = files.stream()
                    .map(File::getName)
                    .sorted()
                    .collect(Collectors.joining(";"));
            byte[] hash = md.digest(sortedNames.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return prefix + "_Course_Brochure_" + sb + ".pdf";
        } catch (Exception e) {
            return prefix + "_Course_Brochure_" + System.currentTimeMillis() + ".pdf";
        }
    }

    public String buildPublicUrl(String relativePath) {
        String cleanBase = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://team.ttsnashik.com";
        return cleanBase + "/uploads/courses/" + relativePath;
    }

    private String normalizeName(String str) {
        return str.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String stripPdfExtension(String fileName) {
        if (fileName.toLowerCase().endsWith(".pdf")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
