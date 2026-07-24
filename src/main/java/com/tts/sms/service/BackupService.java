package com.tts.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final JavaMailSender mailSender;
    private final GoogleDriveService googleDriveService;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.backup.email}")
    private String backupEmail;

    @Value("${app.backup.email-enabled:false}")
    private boolean emailEnabled;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    private static final String DOMAIN_PREFIX = "team.ttsnashik.com";

    /**
     * Executes the full backup process (DB, uploads, logs), uploads to Google
     * Drive, and optionally emails status.
     * Temporary files are automatically cleaned up in a finally block.
     */
    public void performBackupAndSendEmail() throws Exception {
        log.info("========================================");
        log.info("STARTING GOOGLE DRIVE BACKUP PROCESS ({})", DOMAIN_PREFIX);
        log.info("========================================");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        File sqlDumpFile = new File(tempDir, DOMAIN_PREFIX + "_db_backup_" + timestamp + ".sql");
        File errFile = new File(tempDir, DOMAIN_PREFIX + "_db_backup_err_" + timestamp + ".txt");
        File dbZip = new File(tempDir, DOMAIN_PREFIX + "_db_backup_" + timestamp + ".zip");
        File uploadsZip = new File(tempDir, DOMAIN_PREFIX + "_uploads_backup_" + timestamp + ".zip");
        File logsZip = new File(tempDir, DOMAIN_PREFIX + "_logs_backup_" + timestamp + ".zip");

        Map<String, Map<String, String>> driveUploadResults = new HashMap<>();

        try {
            // 1. Generate MySQL Database Backup
            generateDbBackup(sqlDumpFile, errFile);
            log.info("mysqldump completed successfully. Raw SQL size: {} bytes", sqlDumpFile.length());

            // 2. Compress SQL dump into ZIP archive
            log.info("Creating Database ZIP → {}", dbZip.getAbsolutePath());
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dbZip))) {
                String sqlEntryName = DOMAIN_PREFIX + "_db_backup_" + timestamp + ".sql";
                zos.putNextEntry(new ZipEntry(sqlEntryName));
                java.nio.file.Files.copy(sqlDumpFile.toPath(), zos);
                zos.closeEntry();
            }
            log.info("Database ZIP created successfully. Zipped size: {} bytes", dbZip.length());

            // 3. Zip Uploads Folder (if directory exists and contains files)
            java.nio.file.Path uploadsPath = java.nio.file.Paths.get("uploads");
            boolean uploadsZipped = false;
            if (java.nio.file.Files.exists(uploadsPath) && java.nio.file.Files.isDirectory(uploadsPath)
                    && hasFiles(uploadsPath)) {
                log.info("Zipping uploads directory from: {}", uploadsPath.toAbsolutePath());
                createDirectoryZip(uploadsZip, uploadsPath, "uploads");
                log.info("Uploads directory zipped successfully to: {} (Size: {} bytes)", uploadsZip.getAbsolutePath(),
                        uploadsZip.length());
                uploadsZipped = uploadsZip.length() > 100;
            } else {
                log.warn("Uploads directory not found or empty at: {} — skipping uploads backup",
                        uploadsPath.toAbsolutePath());
            }

            // 4. Zip Logs Folder (if directory exists and contains files)
            java.nio.file.Path logsPath = java.nio.file.Paths.get("logs");
            boolean logsZipped = false;
            if (java.nio.file.Files.exists(logsPath) && java.nio.file.Files.isDirectory(logsPath)
                    && hasFiles(logsPath)) {
                log.info("Zipping logs directory from: {}", logsPath.toAbsolutePath());
                createDirectoryZip(logsZip, logsPath, "logs");
                log.info("Logs directory zipped successfully to: {} (Size: {} bytes)", logsZip.getAbsolutePath(),
                        logsZip.length());
                logsZipped = logsZip.length() > 100;
            } else {
                log.warn("Logs directory not found or empty at: {} — skipping logs backup", logsPath.toAbsolutePath());
            }

            // 5. Upload Backup Archives to Google Drive
            log.info("Uploading backup files to Google Drive...");
            if (dbZip.exists() && dbZip.length() > 0) {
                driveUploadResults.put("db", googleDriveService.uploadFile(dbZip, "application/zip"));
            }
            if (uploadsZipped && uploadsZip.exists() && uploadsZip.length() > 0) {
                driveUploadResults.put("uploads", googleDriveService.uploadFile(uploadsZip, "application/zip"));
            }
            if (logsZipped && logsZip.exists() && logsZip.length() > 0) {
                driveUploadResults.put("logs", googleDriveService.uploadFile(logsZip, "application/zip"));
            }
            log.info("Google Drive upload completed successfully.");

            // 6. Send Email Notification (Only if explicitly enabled, without attachments)
            if (emailEnabled) {
                sendBackupEmail(driveUploadResults, timestamp);
            } else {
                log.info(
                        "Email notification is disabled (app.backup.email-enabled=false). Google Drive backup completed without sending email.");
            }

            log.info("========================================");
            log.info("BACKUP COMPLETED SUCCESSFULLY ({})", DOMAIN_PREFIX);
            log.info("========================================");

        } catch (Exception e) {
            log.error("Backup process failed: {}", e.getMessage(), e);
            sendFailureEmail(e, timestamp);
            throw e;
        } finally {
            // 7. Clean up temporary files
            cleanupTempFile(sqlDumpFile);
            cleanupTempFile(errFile);
            cleanupTempFile(dbZip);
            cleanupTempFile(uploadsZip);
            cleanupTempFile(logsZip);
        }
    }

    private void generateDbBackup(File outputFile, File errorLog) throws Exception {
        Map<String, String> dbParams = parseJdbcUrl(dbUrl);
        String host = dbParams.get("host");
        String port = dbParams.get("port");
        String database = dbParams.get("database");

        log.info("Generating DB backup for host: {}, port: {}, database: {}", host, port, database);

        List<String> command = new ArrayList<>();
        command.add("mysqldump");
        command.add("-h" + host);
        command.add("-P" + port);
        command.add("-u" + dbUsername);
        if (dbPassword != null && !dbPassword.isEmpty()) {
            command.add("--password=" + dbPassword);
        }
        command.add("--single-transaction");
        command.add("--routines");
        command.add("--triggers");
        command.add("--no-tablespaces");
        command.add(database);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(ProcessBuilder.Redirect.to(outputFile));
        pb.redirectError(ProcessBuilder.Redirect.to(errorLog));

        Process process = pb.start();
        boolean finished = process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("The database backup process timed out (exceeded 90 seconds).");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            StringBuilder errorContent = new StringBuilder();
            if (errorLog.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(errorLog))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorContent.append(line).append("\n");
                    }
                }
            }
            String errMsg = errorContent.toString().trim();
            log.error("mysqldump process failed with exit code: {}. Errors: {}", exitCode, errMsg);
            throw new RuntimeException("mysqldump failed with exit code " + exitCode + ". Details: " + errMsg);
        }
    }

    private Map<String, String> parseJdbcUrl(String url) {
        Map<String, String> params = new HashMap<>();
        params.put("host", "localhost");
        params.put("port", "3306");
        params.put("database", "tts_crm");

        try {
            if (url != null && url.startsWith("jdbc:mysql://")) {
                String cleanUrl = url.substring("jdbc:mysql://".length());
                int paramIdx = cleanUrl.indexOf("?");
                if (paramIdx != -1) {
                    cleanUrl = cleanUrl.substring(0, paramIdx);
                }

                int slashIdx = cleanUrl.indexOf("/");
                if (slashIdx != -1) {
                    String dbName = cleanUrl.substring(slashIdx + 1);
                    params.put("database", dbName);

                    String hostPort = cleanUrl.substring(0, slashIdx);
                    int colonIdx = hostPort.indexOf(":");
                    if (colonIdx != -1) {
                        params.put("host", hostPort.substring(0, colonIdx));
                        params.put("port", hostPort.substring(colonIdx + 1));
                    } else {
                        params.put("host", hostPort);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse JDBC URL: {}", url, e);
        }
        return params;
    }

    private boolean hasFiles(java.nio.file.Path directory) {
        try (var stream = java.nio.file.Files.walk(directory)) {
            return stream.anyMatch(path -> !java.nio.file.Files.isDirectory(path));
        } catch (IOException e) {
            return false;
        }
    }

    private void createDirectoryZip(File zipFile, java.nio.file.Path directory, String prefix) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            java.nio.file.Files.walk(directory)
                    .filter(path -> !java.nio.file.Files.isDirectory(path))
                    .forEach(path -> {
                        String entryName = prefix + "/"
                                + directory.relativize(path).toString().replace(File.separatorChar, '/');
                        try {
                            zos.putNextEntry(new ZipEntry(entryName));
                            java.nio.file.Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            log.warn("Could not add file to ZIP: {} — {}", path, e.getMessage());
                        }
                    });
        }
    }

    private void sendBackupEmail(Map<String, Map<String, String>> driveResults, String timestamp) throws Exception {
        log.info("Sending backup success email notification to: {}", backupEmail);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(backupEmail);
        helper.setSubject("TechnoKraft Daily System Backup Summary - " + timestamp);

        StringBuilder emailBody = new StringBuilder();
        emailBody.append("<h3>TechnoKraft CRM System Backup Summary</h3>");
        emailBody.append("<p>An automated backup execution completed successfully on <b>").append(timestamp)
                .append("</b>.</p>");

        emailBody.append("<h4>☁️ Google Drive Backup Status:</h4>");
        emailBody.append("<ul>");
        appendDriveLinkInfo(emailBody, "Database ZIP Backup", driveResults.get("db"));
        appendDriveLinkInfo(emailBody, "Uploads Directory ZIP", driveResults.get("uploads"));
        appendDriveLinkInfo(emailBody, "Logs Directory ZIP", driveResults.get("logs"));
        emailBody.append("</ul>");

        emailBody.append(
                "<p><i>Note: Backup files are stored exclusively on Google Drive. No local files were attached.</i></p>");
        emailBody.append("<p>Best Regards,<br/>System Automation Service</p>");

        helper.setText(emailBody.toString(), true);

        mailSender.send(message);
        log.info("Backup notification email sent successfully.");
    }

    private void sendFailureEmail(Throwable t, String timestamp) {
        try {
            log.info("Sending backup failure email to: {}", backupEmail);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(backupEmail);
            helper.setSubject("❌ BACKUP FAILED: " + DOMAIN_PREFIX + " - " + timestamp);

            StringBuilder emailBody = new StringBuilder();
            emailBody.append("<h3>" + DOMAIN_PREFIX + " CRM System Backup Failed</h3>");
            emailBody.append("<p>An automated backup execution failed on <b>").append(timestamp).append("</b>.</p>");
            emailBody.append("<p><b>Error Details:</b></p>");
            emailBody.append("<pre style='color:red;'>").append(t.toString()).append("</pre>");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            emailBody.append("<p><b>Stack Trace:</b></p>");
            emailBody.append("<pre style='font-size:11px; background-color:#f8f9fa; padding:10px;'>")
                    .append(sw.toString()).append("</pre>");

            emailBody.append("<p>Best Regards,<br/>System Automation Service</p>");
            helper.setText(emailBody.toString(), true);
            mailSender.send(message);
            log.info("Backup failure email sent successfully.");
        } catch (Exception e) {
            log.error("Failed to send backup failure email: {}", e.getMessage(), e);
        }
    }

    private void appendDriveLinkInfo(StringBuilder sb, String title, Map<String, String> result) {
        if (result != null && "SUCCESS".equalsIgnoreCase(result.get("status"))) {
            sb.append("<li><b>").append(title).append(":</b> Uploaded to Drive - ")
                    .append("<a href='").append(result.get("webViewLink"))
                    .append("' target='_blank'>View on Google Drive</a>")
                    .append("</li>");
        } else if (result != null) {
            sb.append("<li><b>").append(title).append(":</b> <span style='color:red;'>Drive Upload Failed (")
                    .append(result.get("error")).append(")</span></li>");
        }
    }

    private void cleanupTempFile(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                log.info("Cleaned up temporary file: {}", file.getAbsolutePath());
            } else {
                log.warn("Failed to delete temporary file: {}", file.getAbsolutePath());
            }
        }
    }
}
