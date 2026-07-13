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

    /**
     * Executes the full backup process (DB, uploads, logs), uploads to Google Drive, and optionally emails status.
     * Temporary files are automatically cleaned up in a finally block.
     */
    public void performBackupAndSendEmail() throws Exception {
        log.info("Starting backup process...");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        
        File sqlFile = new File(tempDir, "tts_sms_db_backup_" + timestamp + ".sql");
        File uploadsZip = new File(tempDir, "tts_sms_uploads_backup_" + timestamp + ".zip");
        File logsZip = new File(tempDir, "tts_sms_logs_backup_" + timestamp + ".zip");

        Map<String, Map<String, String>> driveUploadResults = new HashMap<>();

        try {
            // 1. Generate MySQL Database Backup
            generateDbBackup(sqlFile);

            // 2. Zip Uploads Folder
            File uploadsDir = new File("uploads");
            if (uploadsDir.exists() && uploadsDir.isDirectory()) {
                log.info("Zipping uploads directory from: {}", uploadsDir.getAbsolutePath());
                zipDirectory(uploadsDir, uploadsZip);
                log.info("Uploads directory zipped successfully to: {} (Size: {} bytes)", uploadsZip.getAbsolutePath(), uploadsZip.length());
            } else {
                log.warn("Uploads directory not found at: {}", uploadsDir.getAbsolutePath());
            }

            // 3. Zip Logs Folder
            File logsDir = new File("logs");
            if (logsDir.exists() && logsDir.isDirectory()) {
                log.info("Zipping logs directory from: {}", logsDir.getAbsolutePath());
                zipDirectory(logsDir, logsZip);
                log.info("Logs directory zipped successfully to: {} (Size: {} bytes)", logsZip.getAbsolutePath(), logsZip.length());
            } else {
                log.warn("Logs directory not found at: {}", logsDir.getAbsolutePath());
            }

            // 4. Upload Files to Google Drive
            if (sqlFile.exists() && sqlFile.length() > 0) {
                driveUploadResults.put("db", googleDriveService.uploadFile(sqlFile, "application/sql"));
            }
            if (uploadsZip.exists() && uploadsZip.length() > 0) {
                driveUploadResults.put("uploads", googleDriveService.uploadFile(uploadsZip, "application/zip"));
            }
            if (logsZip.exists() && logsZip.length() > 0) {
                driveUploadResults.put("logs", googleDriveService.uploadFile(logsZip, "application/zip"));
            }

            // 5. Send Email Notification (Only if enabled)
            if (emailEnabled) {
                sendBackupEmail(sqlFile, uploadsZip, logsZip, driveUploadResults, timestamp);
            } else {
                log.info("Email notification is disabled (app.backup.email-enabled=false). Google Drive backup completed without sending email.");
            }

        } finally {
            // 6. Clean up temporary files
            cleanupTempFile(sqlFile);
            cleanupTempFile(uploadsZip);
            cleanupTempFile(logsZip);
        }
    }

    private void generateDbBackup(File outputFile) throws Exception {
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
            command.add("-p" + dbPassword);
        }
        command.add(database);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(ProcessBuilder.Redirect.to(outputFile));
        
        File errorLog = File.createTempFile("mysqldump_err", ".log");
        pb.redirectError(ProcessBuilder.Redirect.to(errorLog));

        try {
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                StringBuilder errorContent = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(errorLog))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorContent.append(line).append("\n");
                    }
                }
                String errMsg = errorContent.toString();
                log.error("mysqldump process failed with exit code: {}. Errors: {}", exitCode, errMsg);
                throw new RuntimeException("mysqldump failed with exit code " + exitCode + ". Error: " + errMsg);
            }
            log.info("Database backup created successfully: {} (Size: {} bytes)", outputFile.getAbsolutePath(), outputFile.length());
        } finally {
            if (errorLog.exists()) {
                errorLog.delete();
            }
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

    private void zipDirectory(File sourceFolder, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipFolderContents(sourceFolder, sourceFolder, zos);
        }
    }

    private void zipFolderContents(File rootFolder, File sourceFolder, ZipOutputStream zos) throws IOException {
        File[] files = sourceFolder.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[4096];
        for (File file : files) {
            if (file.isDirectory()) {
                zipFolderContents(rootFolder, file, zos);
            } else {
                String relativePath = rootFolder.toURI().relativize(file.toURI()).getPath();
                relativePath = relativePath.replace('\\', '/');
                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private void sendBackupEmail(File sqlFile, File uploadsZip, File logsZip, Map<String, Map<String, String>> driveResults, String timestamp) throws Exception {
        log.info("Sending backup email to: {}", backupEmail);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(backupEmail);
        helper.setSubject("TechnoKraft Daily System Backup - " + timestamp);

        StringBuilder emailBody = new StringBuilder();
        emailBody.append("<h3>TechnoKraft CRM System Backup Summary</h3>");
        emailBody.append("<p>An automated backup execution completed on <b>").append(timestamp).append("</b>.</p>");

        emailBody.append("<h4>☁️ Google Drive Backup Status:</h4>");
        emailBody.append("<ul>");
        appendDriveLinkInfo(emailBody, "Database SQL Backup", driveResults.get("db"));
        appendDriveLinkInfo(emailBody, "Uploads Directory Zip", driveResults.get("uploads"));
        appendDriveLinkInfo(emailBody, "Logs Directory Zip", driveResults.get("logs"));
        emailBody.append("</ul>");

        emailBody.append("<h4>📎 Local Email Attachments:</h4>");
        emailBody.append("<ul>");
        
        if (sqlFile.exists() && sqlFile.length() > 0) {
            helper.addAttachment(sqlFile.getName(), new FileSystemResource(sqlFile));
            emailBody.append("<li><b>Database SQL Backup:</b> ").append(sqlFile.getName()).append(" (").append(sqlFile.length()).append(" bytes)</li>");
        } else {
            emailBody.append("<li><span style='color:red;'>Database SQL Backup: Failed to generate</span></li>");
        }

        if (uploadsZip.exists() && uploadsZip.length() > 0) {
            helper.addAttachment(uploadsZip.getName(), new FileSystemResource(uploadsZip));
            emailBody.append("<li><b>Uploads Folder Zip:</b> ").append(uploadsZip.getName()).append(" (").append(uploadsZip.length()).append(" bytes)</li>");
        }

        if (logsZip.exists() && logsZip.length() > 0) {
            helper.addAttachment(logsZip.getName(), new FileSystemResource(logsZip));
            emailBody.append("<li><b>Logs Folder Zip:</b> ").append(logsZip.getName()).append(" (").append(logsZip.length()).append(" bytes)</li>");
        }
        
        emailBody.append("</ul>");
        emailBody.append("<p>Best Regards,<br/>System Automation Service</p>");

        helper.setText(emailBody.toString(), true);

        mailSender.send(message);
        log.info("Backup notification email sent successfully.");
    }

    private void appendDriveLinkInfo(StringBuilder sb, String title, Map<String, String> result) {
        if (result != null && "SUCCESS".equalsIgnoreCase(result.get("status"))) {
            sb.append("<li><b>").append(title).append(":</b> Uploaded to Drive - ")
              .append("<a href='").append(result.get("webViewLink")).append("' target='_blank'>View on Google Drive</a>")
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
