package com.tts.sms.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoogleDriveService {

    @Value("${app.backup.google-drive.enabled:true}")
    private boolean googleDriveEnabled;

    @Value("${app.backup.google-drive.credentials-path:credentials/global-email-monitor-e4ef0cd5ef08.json}")
    private String credentialsPath;

    @Value("${app.backup.google-drive.folder-name:TTS_Daily_Backups}")
    private String targetFolderName;

    private static final String APPLICATION_NAME = "TechnoKraft SMS Backup Service";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    /**
     * Initializes and returns an authenticated Google Drive client instance.
     */
    private Drive getDriveService() throws Exception {
        Resource resource = new ClassPathResource(credentialsPath);
        if (!resource.exists()) {
            resource = new FileSystemResource(credentialsPath);
        }

        if (!resource.exists()) {
            throw new IllegalStateException("Google Drive Service Account Credentials file not found at: " + credentialsPath);
        }

        try (InputStream in = resource.getInputStream()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE));

            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
    }

    /**
     * Finds the ID of an existing target folder shared with or owned by the service account.
     */
    public String findFolderId(Drive service, String folderName) {
        try {
            String query = "mimeType='" + FOLDER_MIME_TYPE + "' and name='" + folderName + "' and trashed=false";
            FileList result = service.files().list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .execute();

            List<File> files = result.getFiles();
            if (files != null && !files.isEmpty()) {
                String folderId = files.get(0).getId();
                log.info("Found Google Drive folder '{}' with ID: {}", folderName, folderId);
                return folderId;
            }
        } catch (Exception e) {
            log.warn("Failed to query Google Drive folder ID for '{}': {}", folderName, e.getMessage());
        }
        return null;
    }

    /**
     * Uploads a single file to the designated Google Drive folder.
     */
    public Map<String, String> uploadFile(java.io.File localFile, String mimeType) {
        Map<String, String> uploadResult = new HashMap<>();
        uploadResult.put("fileName", localFile.getName());
        uploadResult.put("fileSize", String.valueOf(localFile.length()));

        if (!googleDriveEnabled) {
            log.info("Google Drive upload is disabled in configuration.");
            uploadResult.put("status", "DISABLED");
            return uploadResult;
        }

        try {
            log.info("Starting upload of {} ({}) to Google Drive...", localFile.getName(), localFile.length());
            Drive service = getDriveService();
            String parentFolderId = findFolderId(service, targetFolderName);

            File fileMetadata = new File();
            fileMetadata.setName(localFile.getName());

            if (parentFolderId != null) {
                fileMetadata.setParents(Collections.singletonList(parentFolderId));
            } else {
                log.info("Target folder '{}' not found via query. Uploading to Drive root/shared scope.", targetFolderName);
            }

            FileContent mediaContent = new FileContent(mimeType, localFile);
            File uploadedFile = service.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, webViewLink, size")
                    .execute();

            log.info("Successfully uploaded {} to Google Drive. File ID: {}, View Link: {}",
                    uploadedFile.getName(), uploadedFile.getId(), uploadedFile.getWebViewLink());

            uploadResult.put("status", "SUCCESS");
            uploadResult.put("fileId", uploadedFile.getId());
            uploadResult.put("webViewLink", uploadedFile.getWebViewLink());

        } catch (Exception e) {
            log.error("Failed to upload file {} to Google Drive: {}", localFile.getName(), e.getMessage(), e);
            uploadResult.put("status", "FAILED");
            uploadResult.put("error", e.getMessage());
        }

        return uploadResult;
    }
}
