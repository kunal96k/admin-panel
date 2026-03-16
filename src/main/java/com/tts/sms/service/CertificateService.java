package com.tts.sms.service;

import com.tts.sms.dto.CertificateCSVImportDTO;
import com.tts.sms.dto.CertificateDTO;
import com.tts.sms.model.Certificate;
import com.tts.sms.repository.CertificateRepository;
import com.tts.sms.repository.CourseRepository;
import com.tts.sms.repository.AdmissionRepository;
import com.tts.sms.repository.FeesRepository;
import com.tts.sms.repository.FeeInstallmentRepository;
import com.tts.sms.model.Admission;
import com.tts.sms.model.Fees;
import com.tts.sms.model.FeeInstallment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final CertificateCSVService csvService;
    private final EmailTemplateService emailTemplateService;
    private final CourseRepository courseRepository;
    private final AutoCertificateService autoCertificateService;
    private final AdmissionRepository admissionRepository;
    private final FeesRepository feesRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;

    /**
     * Send certificate email with pre-rendered image from frontend
     */
    public void sendCertificateEmailWithImage(Long id, String email, byte[] certificateImage) {
        Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found with id: " + id));

        if (!certificate.getStatus().equals("Issued")) {
            throw new RuntimeException("Certificate must be issued before sending email");
        }

        if (certificateImage == null || certificateImage.length == 0) {
            throw new RuntimeException("Certificate image is required");
        }

        emailTemplateService.sendCertificateEmail(
                email,
                certificate.getStudentName(),
                certificate.getCertificateNo(),
                certificate.getCourseName(),
                certificate.getGrade(),
                certificate.getIssueDate(),
                certificate.getCourseFromDate(),
                certificate.getCourseToDate(),
                certificate.getBatch(),
                certificateImage);

        log.info(" Certificate email sent to: {} for certificate: {}",
                email, certificate.getCertificateNo());
    }

    /**
     * Get certificates with pagination and filters
     */
    @Transactional(readOnly = true)
    public Page<CertificateDTO> getCertificates(
            String course, String status, String search, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Certificate> certificatePage;

        boolean hasCourse = course != null && !course.isEmpty();
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasSearch = search != null && !search.trim().isEmpty();

        if (hasSearch) {
            if (hasCourse && hasStatus) {
                certificatePage = certificateRepository.searchCertificatesByCourseAndStatus(
                        search.trim(), course, status, pageable);
            } else if (hasCourse) {
                certificatePage = certificateRepository.searchCertificatesByCourse(
                        search.trim(), course, pageable);
            } else if (hasStatus) {
                certificatePage = certificateRepository.searchCertificatesByStatus(
                        search.trim(), status, pageable);
            } else {
                certificatePage = certificateRepository.searchCertificates(search.trim(), pageable);
            }
        } else {
            if (hasCourse && hasStatus) {
                certificatePage = certificateRepository.findByCourseNameAndStatusAndIsActiveTrue(
                        course, status, pageable);
            } else if (hasCourse) {
                certificatePage = certificateRepository.findByCourseNameAndIsActiveTrue(course, pageable);
            } else if (hasStatus) {
                certificatePage = certificateRepository.findByStatusAndIsActiveTrue(status, pageable);
            } else {
                certificatePage = certificateRepository.findByIsActiveTrue(pageable);
            }
        }

        return certificatePage.map(this::convertToDTO);
    }

    /**
     * Get certificate statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", certificateRepository.countByIsActiveTrue());
        stats.put("issued", certificateRepository.countByStatusAndIsActiveTrue("Issued"));
        stats.put("pending", certificateRepository.countByStatusAndIsActiveTrue("Pending"));
        return stats;
    }

    /**
     * Get certificate by ID
     */
    @Transactional(readOnly = true)
    public CertificateDTO getCertificateById(Long id) {
        Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found with id: " + id));
        return convertToDTO(certificate);
    }

    /**
     * Delete certificate (hard delete - permanently removes from DB)
     */
    public void deleteCertificate(Long id) {
        Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found with id: " + id));

        String regNo = certificate.getRegistrationNo();
        String studentName = certificate.getStudentName();

        certificateRepository.delete(certificate);
        certificateRepository.flush();
        log.info("Certificate hard-deleted: {} for student: {}", regNo, studentName);
    }

    /**
     * Export certificates to CSV
     */
    @Transactional(readOnly = true)
    public byte[] exportCertificatesToCSV() {
        List<Certificate> certificates = certificateRepository.findByIsActiveTrue();

        if (certificates.isEmpty()) {
            throw new RuntimeException("No certificates available to export");
        }

        List<CertificateDTO> dtos = certificates.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return csvService.generateCertificateCSV(dtos);
    }

    /**
     * Generate unique certificate number
     */
    private String generateCertificateNumber() {
        String year = String.valueOf(Year.now().getValue());
        long count = certificateRepository.countByIsActiveTrue() + 1;
        return String.format("CERT%s%04d", year, count);
    }

    /**
     * Generate batch name from admission date
     */
    private String generateBatchName(LocalDate admissionDate) {
        if (admissionDate == null) {
            admissionDate = LocalDate.now();
        }
        int year = admissionDate.getYear();
        int month = admissionDate.getMonthValue();
        String quarter = month <= 3 ? "Q1" : month <= 6 ? "Q2" : month <= 9 ? "Q3" : "Q4";
        return String.format("Batch-%s-%d", quarter, year);
    }

    /**
     * Convert entity to DTO - UPDATED WITH COURSE INFO
     */
    private CertificateDTO convertToDTO(Certificate certificate) {
        CertificateDTO dto = CertificateDTO.builder()
                .id(certificate.getId())
                .registrationNo(certificate.getRegistrationNo())
                .certificateNo(certificate.getCertificateNo())
                .studentName(certificate.getStudentName())
                .studentEmail(certificate.getStudentEmail())
                .courseName(certificate.getCourseName())
                .batch(certificate.getBatch())
                .grade(certificate.getGrade())
                .issueDate(certificate.getIssueDate())
                .courseFromDate(certificate.getCourseFromDate())
                .courseToDate(certificate.getCourseToDate())
                .status(certificate.getStatus())
                .notes(certificate.getNotes())
                .isActive(certificate.getIsActive())
                .createdAt(certificate.getCreatedAt())
                .updatedAt(certificate.getUpdatedAt())
                .build();

        if (certificate.getCourse() != null) {
            dto.setCourseImagePath(certificate.getCourse().getCourseImagePath());
        }

        return dto;
    }

    /**
     * Get filtered statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getFilteredStatistics(String course, String status, String search) {
        List<Certificate> filtered = getFilteredCertificates(course, status, search);

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", (long) filtered.size());
        stats.put("issued", filtered.stream().filter(c -> "Issued".equals(c.getStatus())).count());
        stats.put("pending", filtered.stream().filter(c -> !"Issued".equals(c.getStatus())).count());

        return stats;
    }

    private List<Certificate> getFilteredCertificates(String course, String status, String search) {
        List<Certificate> all = certificateRepository.findByIsActiveTrue();

        return all.stream()
                .filter(c -> course == null || course.isEmpty() || c.getCourseName().equals(course))
                .filter(c -> status == null || status.isEmpty() || c.getStatus().equals(status))
                .filter(c -> search == null || search.trim().isEmpty() ||
                        matchesSearch(c, search.trim().toLowerCase()))
                .collect(Collectors.toList());
    }

    private boolean matchesSearch(Certificate c, String search) {
        return (c.getRegistrationNo() != null && c.getRegistrationNo().toLowerCase().contains(search)) ||
                (c.getCertificateNo() != null && c.getCertificateNo().toLowerCase().contains(search)) ||
                (c.getStudentName() != null && c.getStudentName().toLowerCase().contains(search)) ||
                (c.getCourseName() != null && c.getCourseName().toLowerCase().contains(search)) ||
                (c.getBatch() != null && c.getBatch().toLowerCase().contains(search));
    }

    /**
     * Generate certificate image programmatically
     */
    @Deprecated
    private byte[] generateCertificateImage(Certificate certificate) {
        try {
            // Load template image
            File templateFile = new File("src/main/resources/static/assets/images/TTS-Certificate.jpg");
            BufferedImage template = ImageIO.read(templateFile);

            Graphics2D g2d = template.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Set font and color
            Font nameFont = new Font("Century Gothic", Font.BOLD, 50);
            g2d.setFont(nameFont);
            g2d.setColor(Color.decode("#e01d02"));

            // Draw student name
            g2d.drawString(certificate.getStudentName(), 285, 550);

            // Draw course name
            g2d.drawString(certificate.getCourseName(), 285, 715);

            // Draw dates and cert number
            Font smallFont = new Font("Century Gothic", Font.BOLD, 25);
            g2d.setFont(smallFont);
            g2d.setColor(Color.BLACK);
            g2d.drawString(certificate.getIssueDate().toString(), 1135, 1226);
            g2d.drawString(certificate.getCertificateNo(), 550, 1279);

            g2d.dispose();

            // Convert to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(template, "jpg", baos);
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Error generating certificate image", e);
            return null;
        }
    }

    /**
     * Import certificates from CSV - BATCH PROCESSING WITH PROPER ERROR HANDLING
     */
    @Transactional
    public Map<String, Object> importCertificatesFromCSV(MultipartFile file) throws IOException {
        log.info("📥 IMPORTING: Certificates from CSV - {}", file.getOriginalFilename());

        List<CertificateCSVImportDTO> importData = csvService.parseCertificatesCSV(file);
        int successCount = 0;
        int errorCount = 0;
        List<String> errorMessages = new ArrayList<>();

        for (CertificateCSVImportDTO dto : importData) {
            try {
                importSingleCertificate(dto);
                successCount++;
                log.debug(" Imported: {} - {}", dto.getRegistrationNo(), dto.getStudentName());

            } catch (Exception e) {
                errorCount++;
                String errorMsg = String.format("Row %s (%s): %s",
                        dto.getRegistrationNo(), dto.getStudentName(), e.getMessage());
                errorMessages.add(errorMsg);
                log.error("❌ Error importing: {}", errorMsg);
            }
        }

        log.info(" Import complete: {} success, {} errors out of {} total",
                successCount, errorCount, importData.size());

        Map<String, Object> result = new HashMap<>();
        result.put("success", successCount);
        result.put("errors", errorCount);
        result.put("total", importData.size());
        if (!errorMessages.isEmpty()) {
            result.put("errorDetails", errorMessages);
        }
        return result;
    }

    /**
     * Import single certificate with individual transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void importSingleCertificate(CertificateCSVImportDTO dto) {
        Certificate certificate = new Certificate();

        String regNo = dto.getRegistrationNo();
        if (regNo == null || regNo.trim().isEmpty()) {
            throw new RuntimeException("Registration number is required");
        }
        certificate.setRegistrationNo(regNo);

        certificate.setStudentName(dto.getStudentName() != null ? dto.getStudentName() : "N/A");
        certificate.setCourseName(dto.getCourseName() != null ? dto.getCourseName() : "N/A");

        if (dto.getCourseName() != null) {
            courseRepository.findByCourseNameAndIsActiveTrue(dto.getCourseName())
                    .ifPresent(certificate::setCourse);
        }

        if (dto.getCertificateNo() != null && !dto.getCertificateNo().trim().isEmpty()) {
            certificate.setCertificateNo(dto.getCertificateNo());
        }

        if (dto.getBatch() != null && !dto.getBatch().trim().isEmpty()) {
            certificate.setBatch(dto.getBatch());
        } else {
            certificate.setBatch(generateBatchName(LocalDate.now()));
        }

        certificate.setGrade(dto.getGrade());
        certificate.setIssueDate(dto.getIssueDate());

        String status = dto.getStatus();
        if (status == null || status.trim().isEmpty()) {
            status = "Not Issued";
        }
        certificate.setStatus(status);
        certificate.setIsActive(true);

        certificateRepository.saveAndFlush(certificate);
    }

    public void sendCertificateEmail(Long id, String email) {
        Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found with id: " + id));

        if (!certificate.getStatus().equals("Issued")) {
            throw new RuntimeException("Certificate must be issued before sending email");
        }

        // Generate certificate image
        byte[] certificateImage = generateCertificateImageBytes(certificate);

        if (certificateImage == null || certificateImage.length == 0) {
            throw new RuntimeException("Failed to generate certificate image");
        }

        emailTemplateService.sendCertificateEmail(
                email,
                certificate.getStudentName(),
                certificate.getCertificateNo(),
                certificate.getCourseName(),
                certificate.getGrade(),
                certificate.getIssueDate(),
                certificate.getCourseFromDate(),
                certificate.getCourseToDate(),
                certificate.getBatch(),
                certificateImage);

        log.info(" Certificate email sent to: {} for certificate: {}", email, certificate.getCertificateNo());
    }

    /**
     * Generate certificate image as byte array - FIXED WITH EXACT COORDINATES
     */
    private byte[] generateCertificateImageBytes(Certificate certificate) {
        try {
            // Load template - try multiple locations
            File templateFile = new File("src/main/resources/static/assets/images/TTS-Certificate.jpg");
            if (!templateFile.exists()) {
                templateFile = new File(
                        "src/main/resources/static/assets/images/TTS_Certificate-Picsart-AiImageEnhancer_1_rvk0mb.jpg");
            }

            BufferedImage template;
            if (templateFile.exists()) {
                template = ImageIO.read(templateFile);
                log.info(" Template loaded: {}x{}", template.getWidth(), template.getHeight());
            } else {
                log.warn(" Template not found, using blank canvas");
                template = new BufferedImage(1754, 1240, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = template.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 1754, 1240);
                g.dispose();
            }

            Graphics2D g2d = template.createGraphics();

            // Enable anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // EXACT COORDINATES - DO NOT CHANGE
            final int STUDENT_NAME_X = 285;
            final int STUDENT_NAME_Y = 550;
            final int COURSE_NAME_X = 285;
            final int COURSE_NAME_Y = 715;
            final int DATE_X = 1135;
            final int DATE_Y = 1226;
            final int CERT_NO_X = 550;
            final int CERT_NO_Y = 1279;
            final int LOGO_X = 1350;
            final int LOGO_Y = 1225;
            final int LOGO_SIZE = 100;
            final int MAX_TEXT_WIDTH = 1000;

            // ========== 1. STUDENT NAME ==========
            String studentName = certificate.getStudentName() != null ? certificate.getStudentName().toUpperCase() : "";

            Font nameFont = new Font("Century Gothic", Font.BOLD, 50);
            g2d.setFont(nameFont);
            g2d.setColor(Color.decode("#e01d02"));

            // Wrap long names
            List<String> nameLines = wrapTextSimple(g2d, studentName, MAX_TEXT_WIDTH);
            int yPos = STUDENT_NAME_Y;
            for (String line : nameLines) {
                g2d.drawString(line, STUDENT_NAME_X, yPos);
                yPos += 60;
            }

            // ========== 2. COURSE NAME ==========
            String courseName = certificate.getCourseName() != null ? certificate.getCourseName().toUpperCase() : "";

            Font courseFont = new Font("Century Gothic", Font.BOLD, 50);
            g2d.setFont(courseFont);
            g2d.setColor(Color.decode("#e01d02"));

            // Wrap long course names
            List<String> courseLines = wrapTextSimple(g2d, courseName, MAX_TEXT_WIDTH);
            yPos = COURSE_NAME_Y;
            for (String line : courseLines) {
                g2d.drawString(line, COURSE_NAME_X, yPos);
                yPos += 60;
            }

            // ========== 3. ISSUE DATE ==========
            Font dateFont = new Font("Century Gothic", Font.BOLD, 25);
            g2d.setFont(dateFont);
            g2d.setColor(Color.BLACK);

            String issueDate = certificate.getIssueDate() != null
                    ? certificate.getIssueDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    : "";
            g2d.drawString(issueDate, DATE_X, DATE_Y);

            // ========== 4. CERTIFICATE NUMBER ==========
            String certNo = certificate.getCertificateNo() != null ? certificate.getCertificateNo() : "";
            String certNoDisplay = certNo;

            g2d.setFont(dateFont);
            g2d.setColor(Color.BLACK);
            g2d.drawString(certNoDisplay, CERT_NO_X, CERT_NO_Y);

            // ========== 5. COURSE LOGO ==========
            if (certificate.getCourse() != null && certificate.getCourse().getCourseImagePath() != null) {
                try {
                    String logoPath = "src/main/resources/static/uploads/courses/" +
                            certificate.getCourse().getCourseImagePath();
                    File logoFile = new File(logoPath);

                    if (logoFile.exists()) {
                        BufferedImage courseLogo = ImageIO.read(logoFile);
                        Image scaledLogo = courseLogo.getScaledInstance(LOGO_SIZE, LOGO_SIZE, Image.SCALE_SMOOTH);
                        g2d.drawImage(scaledLogo, LOGO_X, LOGO_Y, null);
                        log.debug(" Course logo drawn");
                    } else {
                        log.warn(" Course logo not found: {}", logoPath);
                    }
                } catch (Exception e) {
                    log.warn(" Could not load course logo", e);
                }
            }

            g2d.dispose();

            // Convert to JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(template, "jpg", baos);
            byte[] result = baos.toByteArray();

            log.info(" Certificate generated: {} bytes", result.length);
            return result;

        } catch (IOException e) {
            log.error("❌ Error generating certificate image", e);
            return null;
        }
    }

    /**
     * Simple text wrapping - splits text into lines that fit within maxWidth
     */
    private List<String> wrapTextSimple(Graphics2D g2d, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return lines;
        }

        FontMetrics fm = g2d.getFontMetrics();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            int width = fm.stringWidth(testLine);

            if (width <= maxWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * Fit a font to make a text fit in available width.
     * Tries from maxFont down until minFont.
     */
    private Font fitFontToWidth(Graphics2D g2d, String family, int style, int maxFont, int minFont, String text,
            int maxWidth) {
        if (text == null || text.isEmpty()) {
            return new Font(family, style, Math.max(minFont, Math.min(maxFont, 24)));
        }
        for (int size = maxFont; size >= minFont; size -= 2) {
            Font testFont = new Font(family, style, size);
            g2d.setFont(testFont);
            FontMetrics fm = g2d.getFontMetrics(testFont);
            int width = fm.stringWidth(text);
            if (width <= maxWidth) {
                return testFont;
            }
        }
        // fallback to minFont
        return new Font(family, style, minFont);
    }

    /**
     * Wrap text into multiple lines using the current font on g2d and respecting
     * the max width.
     */
    private List<String> wrapText(Graphics2D g2d, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            String test = current.length() == 0 ? w : current + " " + w;
            FontMetrics fm = g2d.getFontMetrics();
            if (fm.stringWidth(test) <= maxWidth) {
                current = new StringBuilder(test);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                // if single word is longer than maxWidth, split the word (simple char chop)
                if (g2d.getFontMetrics().stringWidth(w) > maxWidth) {
                    StringBuilder piece = new StringBuilder();
                    for (char c : w.toCharArray()) {
                        if (g2d.getFontMetrics().stringWidth(piece.toString() + c) <= maxWidth) {
                            piece.append(c);
                        } else {
                            lines.add(piece.toString());
                            piece = new StringBuilder().append(c);
                        }
                    }
                    if (piece.length() > 0) {
                        current = new StringBuilder(piece.toString());
                    } else {
                        current = new StringBuilder();
                    }
                } else {
                    current = new StringBuilder(w);
                }
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    /**
     * Issue certificate with unique certificate number
     */
    public CertificateDTO issueCertificate(Long id, CertificateDTO dto) {
        Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found with id: " + id));

        // Generate UNIQUE certificate number
        String certNo = dto.getCertificateNo();

        // If AUTO-GENERATED or empty, generate new unique number
        if (certNo == null || certNo.isEmpty() || certNo.equals("AUTO-GENERATED")) {
            certNo = generateUniqueCertificateNumber();
        } else {
            // If user provided a number, validate it's unique (skip if it's the same as
            // current)
            if (!certNo.equals(certificate.getCertificateNo())) {
                if (certificateRepository.existsByCertificateNoAndIsActiveTrue(certNo)) {
                    throw new RuntimeException("Certificate number already exists: " + certNo);
                }
            }
        }

        certificate.setCertificateNo(certNo);

        // Update editable fields: studentName, courseName, batch
        if (dto.getStudentName() != null && !dto.getStudentName().trim().isEmpty()) {
            String newName = dto.getStudentName().trim();
            if (!newName.equals(certificate.getStudentName())) {
                String oldName = certificate.getStudentName();
                certificate.setStudentName(newName);
                // Propagate name change to Admission and Fees (Bi-directional sync)
                syncStudentDataAcrossModules(certificate.getRegistrationNo(), newName);
            }
        }

        if (dto.getCourseName() != null && !dto.getCourseName().trim().isEmpty()) {
            String newCourseName = dto.getCourseName().trim();
            // If course changed, re-link the Course entity for logo support
            if (!newCourseName.equals(certificate.getCourseName())) {
                certificate.setCourseName(newCourseName);
                courseRepository.findByCourseNameAndIsActiveTrue(newCourseName)
                        .ifPresentOrElse(
                                certificate::setCourse,
                                () -> certificate.setCourse(null)
                        );
            }
        }

        if (dto.getBatch() != null && !dto.getBatch().trim().isEmpty()) {
            certificate.setBatch(dto.getBatch().trim());
        }

        certificate.setGrade(dto.getGrade());
        certificate.setIssueDate(dto.getIssueDate());
        certificate.setCourseFromDate(dto.getCourseFromDate());
        certificate.setCourseToDate(dto.getCourseToDate());
        certificate.setNotes(dto.getNotes());
        certificate.setStatus("Issued");

        Certificate savedCertificate = certificateRepository.save(certificate);
        log.info("Certificate issued/updated: {} for student: {} course: {}",
                savedCertificate.getCertificateNo(), savedCertificate.getStudentName(),
                savedCertificate.getCourseName());

        // TRIGGER ADMISSION STATUS UPDATE
        autoCertificateService.updateAdmissionStatusAfterCertificate(
                savedCertificate.getRegistrationNo());

        return convertToDTO(savedCertificate);
    }

    /**
     * Propagate student name changes from Certificate module back to Admissions and Fees.
     * This ensures the "Global Sync" requested by the user.
     */
    private void syncStudentDataAcrossModules(String regNo, String newName) {
        try {
            // 1. Update Admission
            Admission admission = admissionRepository.findByRegistrationNumberAndIsDeletedFalse(regNo);
            if (admission != null) {
                admission.setFullName(newName);
                admissionRepository.save(admission);
                log.info("Synced name '{}' to Admission for regNo: {}", newName, regNo);
            }

            // 2. Update Fees
            Fees fee = feesRepository.findByRegistrationNumberAndIsDeletedFalse(regNo).orElse(null);
            if (fee != null) {
                fee.setStudentName(newName);
                feesRepository.save(fee);
                log.info("Synced name '{}' to Fees for regNo: {}", newName, regNo);
            }
            
            // 3. Update ALL other certificates for this student
            List<Certificate> otherCerts = certificateRepository.findByRegistrationNoAndIsActiveTrue(regNo);
            for (Certificate other : otherCerts) {
                if (!newName.equals(other.getStudentName())) {
                    other.setStudentName(newName);
                    certificateRepository.save(other);
                }
            }
        } catch (Exception e) {
            log.error("Failed to propagate name change for regNo: {}", regNo, e);
        }
    }

    /**
     * Generate UNIQUE certificate number with date-time format:
     * CERT20241205143025001
     */
    private String generateUniqueCertificateNumber() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String dateTimePart = now.format(formatter);

        int maxAttempts = 100;
        int attempt = 0;

        while (attempt < maxAttempts) {
            // Format: CERT20241205143025001
            String certNo = String.format("CERT%s%03d", dateTimePart, attempt + 1);

            // Check if this number already exists
            if (!certificateRepository.existsByCertificateNoAndIsActiveTrue(certNo)) {
                log.info(" Generated unique certificate number: {}", certNo);
                return certNo;
            }

            attempt++;
            log.warn(" Certificate number {} already exists, trying next...", certNo);
        }

        // Fallback: Add milliseconds
        String fallbackCertNo = String.format("CERT%s%d", dateTimePart, System.currentTimeMillis() % 1000);
        log.warn(" Using millisecond-based certificate number: {}", fallbackCertNo);
        return fallbackCertNo;
    }

}