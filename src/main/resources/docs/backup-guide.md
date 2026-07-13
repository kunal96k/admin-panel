# System Daily Google Drive & Email Backup Guide

This document describes the automated daily backup system implemented in the **TechnoKraft Student Management System (SMS) CRM**.

---

## 1. Architecture Overview

The system executes daily automated backups that upload database SQL dumps and application archive zip files directly to **Google Drive** in the project-isolated **`TTS_SMS_Daily_Backups`** folder while simultaneously dispatching summary emails with view links and attachments.

### Key System Components
1. **`GoogleDriveService`**: Authenticates with Google Cloud using a Service Account and handles uploads to the project-specific Google Drive folder `TTS_SMS_Daily_Backups`.
2. **`BackupService`**: Core orchestrator responsible for executing `mysqldump`, zipping user upload files (`uploads/`) and system logs (`logs/`), uploading files prefixed with `tts_sms_` to Google Drive, sending summary emails, and clearing temporary storage.
3. **`BackupScheduler`**: Automated runner configured to trigger daily at **2:00 AM IST**.
4. **`BackupController`**: Secure REST API endpoint (`POST /api/auth/backup`) allowing authorized administrators to trigger backups on-demand.

### System Workflow
```
[Daily Backup Scheduler (02:00 AM IST)] ─────┐
                                              ├─> [BackupService] ─> Generates tts_sms_db_backup_*.sql & zips uploads/logs
[Manual Trigger Endpoint (POST /backup)] ─────┘        │
                                                       ├─> [GoogleDriveService] ─> Uploads to folder 'TTS_SMS_Daily_Backups'
                                                       └─> [MailSender] ─────────> Dispatches status email with links
```

---

## 2. Credentials Requirement & Reusability Guide

### ⚠️ Is the JSON Credentials File Required?
> [!CAUTION]
> **YES, IT IS STRICTLY MANDATORY.**
> The file `src/main/resources/credentials/global-email-monitor-e4ef0cd5ef08.json` contains the Google Service Account private key required to authenticate with the Google Drive API.
> **Without this file, Google Drive backup uploads will FAIL.**

---

### Service Account & Project Details

* **Service Account Email**: `sms-backup-bot@global-email-monitor.iam.gserviceaccount.com`
* **Google Cloud Project ID**: `global-email-monitor`
* **Local Project Credentials Path**: `src/main/resources/credentials/global-email-monitor-e4ef0cd5ef08.json`
* **Target Google Drive Folder**: `TTS_SMS_Daily_Backups`

---

### 📋 How to Set Up this Backup System in a New / Other Project

To reuse this Google Drive backup setup in any other project:

1. **Copy Credentials File**:
   Copy `global-email-monitor-e4ef0cd5ef08.json` into the new project's resources directory:
   `src/main/resources/credentials/global-email-monitor-e4ef0cd5ef08.json`

2. **Create & Share Google Drive Folder**:
   - Open [Google Drive](https://drive.google.com/).
   - Create a new folder for the project (e.g., `PROJECT_NAME_Daily_Backups`).
   - Share the new folder with **`sms-backup-bot@global-email-monitor.iam.gserviceaccount.com`** as **Editor**.

3. **Configure `application.yaml` in the New Project**:
   ```yaml
   app:
     backup:
       email: ${BACKUP_EMAIL:kunalpatil192001@gmail.com}
       passcode: ${BACKUP_PASSCODE:Kunal@217}
       google-drive:
         enabled: ${GOOGLE_DRIVE_BACKUP_ENABLED:true}
         credentials-path: ${GOOGLE_DRIVE_CREDENTIALS_PATH:credentials/global-email-monitor-e4ef0cd5ef08.json}
         folder-name: ${GOOGLE_DRIVE_FOLDER_NAME:PROJECT_NAME_Daily_Backups}
   ```

4. **Isolate File Names**:
   In `BackupService.java`, prefix generated backup files with the project code (e.g., `project_name_db_backup_*.sql`).

---

## 3. Environment & Configuration Reference

Configuration parameters defined in `application.yaml`:

| Property | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `app.backup.email` | `BACKUP_EMAIL` | `kunalpatil192001@gmail.com` | Destination email recipient for status summary reports. |
| `app.backup.passcode` | `BACKUP_PASSCODE` | `Kunal@217` | Passcode for authorized manual REST API triggers. |
| `app.backup.google-drive.enabled` | `GOOGLE_DRIVE_BACKUP_ENABLED` | `true` | Toggles Google Drive integration on or off. |
| `app.backup.google-drive.credentials-path` | `GOOGLE_DRIVE_CREDENTIALS_PATH` | `credentials/global-email-monitor-e4ef0cd5ef08.json` | Relative path to Service Account JSON key. |
| `app.backup.google-drive.folder-name` | `GOOGLE_DRIVE_FOLDER_NAME` | `TTS_SMS_Daily_Backups` | Dedicated destination folder for this project. |

---

## 4. Daily Backup Scheduler

Configured in `BackupScheduler.java` using Spring's `@Scheduled` annotation:

- **Cron Expression**: `0 0 2 * * ?`
- **Execution Time**: **Every night at 02:00 AM IST** (`Asia/Kolkata`).
- **Cost**: **100% Free** (Google Drive API free quota allows 1M requests/day; free personal Drive accounts include 15 GB storage).

### Testing the Scheduler Locally
To test scheduled execution without waiting until 2:00 AM:
1. Open `BackupScheduler.java`.
2. Temporarily set the cron expression to run every 10 seconds:
   ```java
   @Scheduled(cron = "*/10 * * * * ?", zone = "Asia/Kolkata")
   ```
3. Boot up the Spring Boot application locally and observe the console logs for Google Drive upload confirmations.
4. **Remember to revert** the cron back to `0 0 2 * * ?` prior to pushing updates to production.

---

## 5. API Reference: Manual On-Demand Backup

For instant backups on demand:

* **Endpoint**: `/api/auth/backup`
* **Method**: `POST`
* **Content-Type**: `application/json`

### Request Body Format
```json
{
  "email": "kunalpatil192001@gmail.com",
  "passcode": "Kunal@217"
}
```

### 1. Using `curl` (Linux / macOS / Git Bash)

```bash
curl -X POST http://localhost:8080/api/auth/backup \
  -H "Content-Type: application/json" \
  -d '{"email": "kunalpatil192001@gmail.com", "passcode": "Kunal@217"}'
```

### 2. Using PowerShell (Windows)

```powershell
$body = @{
    email = "kunalpatil192001@gmail.com"
    passcode = "Kunal@217"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/backup" -Method Post -Body $body -ContentType "application/json"
```

### 3. Using Windows Command Prompt (cmd.exe)

```cmd
curl -X POST http://localhost:8080/api/auth/backup -H "Content-Type: application/json" -d "{\"email\":\"kunalpatil192001@gmail.com\",\"passcode\":\"Kunal@217\"}"
```

---

## 6. Troubleshooting & Maintenance

1. **`mysqldump` Subprocess Missing**:
   - Ensure `mysqldump` is installed on your operating system's PATH (`sudo apt-get install mysql-client` on Ubuntu/Debian).

2. **Google Drive Authorization Issues**:
   - If uploads fail with `404 Not Found` or access denied, verify that `TTS_SMS_Daily_Backups` (or your new project folder) is shared with `sms-backup-bot@global-email-monitor.iam.gserviceaccount.com` as **Editor**.

3. **Mail Attachment & Proxy Timeouts**:
   - The backup process runs asynchronously in a background thread pool (`emailTaskExecutor`), preventing gateway timeouts on production Nginx servers.

4. **Temporary Storage Cleanup**:
   - The application automatically cleans up temporary `.sql` and `.zip` files in the system temp directory inside a `finally` block upon backup completion.
