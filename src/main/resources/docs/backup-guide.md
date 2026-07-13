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

## 2. Authentication Methods & Credentials Setup

The backup service supports **two authentication methods**:

### Method 1: OAuth2 User Refresh Token (Recommended for personal `@gmail.com` accounts)
* Uses your personal Google account storage quota (15 GB free).
* Bypasses Service Account 0-byte quota restrictions completely.

#### Step-by-Step OAuth2 Setup (3 Minutes, $0 Cost):
1. **Create OAuth Client ID**:
   - Go to [Google Cloud Credentials](https://console.cloud.google.com/apis/credentials).
   - Click **"+ CREATE CREDENTIALS"** → **"OAuth client ID"**.
   - Select **Web application**.
   - Add Authorized redirect URI: `https://developers.google.com/oauthplayground`
   - Click **Create** and copy your **Client ID** and **Client Secret**.

2. **Generate Refresh Token**:
   - Go to [Google OAuth 2.0 Playground](https://developers.google.com/oauthplayground/).
   - Click **OAuth 2.0 configuration** (⚙️ gear icon at top right).
   - Check **"Use your own OAuth credentials"**.
   - Enter your **OAuth Client ID** and **OAuth Client Secret**.
   - In the left API list, expand **Drive API v3** → select `https://www.googleapis.com/auth/drive.file`.
   - Click **Authorize APIs** and log in with your Google account (`mr.dreamchaser11@gmail.com`).
   - Click **Exchange authorization code for tokens** and copy the generated **Refresh token**.

3. **Configure Environment Variables (`/etc/crm.env`)**:
   ```bash
   GOOGLE_DRIVE_CLIENT_ID="<YOUR_CLIENT_ID>"
   GOOGLE_DRIVE_CLIENT_SECRET="<YOUR_CLIENT_SECRET>"
   GOOGLE_DRIVE_REFRESH_TOKEN="<YOUR_REFRESH_TOKEN>"
   GOOGLE_DRIVE_FOLDER_ID="15Ltp1XqZ9qbw68v1PBeM8FSO3LmSaggW"
   ```

---

### Method 2: Service Account Credentials (Fallback)
* **Service Account Email**: `sms-backup-bot@global-email-monitor.iam.gserviceaccount.com`
* **Local Credentials File**: `src/main/resources/credentials/global-email-monitor-e4ef0cd5ef08.json`
* Requires the destination Google Drive folder to be shared directly with the Service Account email as **Editor**.

---

### 📋 How to Set Up this Backup System in a New / Other Project

To reuse this Google Drive backup setup in any other project:

1. **Copy Credentials File or Set OAuth2 Tokens**:
   Set `GOOGLE_DRIVE_CLIENT_ID`, `GOOGLE_DRIVE_CLIENT_SECRET`, and `GOOGLE_DRIVE_REFRESH_TOKEN` in environment variables.

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
         client-id: ${GOOGLE_DRIVE_CLIENT_ID:}
         client-secret: ${GOOGLE_DRIVE_CLIENT_SECRET:}
         refresh-token: ${GOOGLE_DRIVE_REFRESH_TOKEN:}
         credentials-path: ${GOOGLE_DRIVE_CREDENTIALS_PATH:credentials/global-email-monitor-e4ef0cd5ef08.json}
         folder-name: ${GOOGLE_DRIVE_FOLDER_NAME:PROJECT_NAME_Daily_Backups}
         folder-id: ${GOOGLE_DRIVE_FOLDER_ID:15Ltp1XqZ9qbw68v1PBeM8FSO3LmSaggW}
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

1. **`403 Forbidden - Service Accounts do not have storage quota`**:
   - **Root Cause**: Service Accounts in Google Cloud have **0 bytes of internal storage quota**. They cannot store files in their own root directory. Files MUST be uploaded inside a Google Drive folder owned by a regular Google account that has been shared with the Service Account email (`sms-backup-bot@global-email-monitor.iam.gserviceaccount.com`) as **Editor**.
   - **Fix 1 (Share Folder)**: In your personal Google Drive, ensure the target folder (`TTS_SMS_Daily_Backups` or `TTS_Daily_Backups`) is shared with `sms-backup-bot@global-email-monitor.iam.gserviceaccount.com` as **Editor**.
   - **Fix 2 (Explicit Folder ID - Recommended)**: Pass the explicit Folder ID in `application.yaml` or environment variable `GOOGLE_DRIVE_FOLDER_ID`:
     - Open the folder in Google Drive.
     - Copy the ID from the browser URL: `https://drive.google.com/drive/folders/<YOUR_FOLDER_ID>`
     - Set `GOOGLE_DRIVE_FOLDER_ID=<YOUR_FOLDER_ID>` in `/etc/crm.env` or `application.yaml`.

2. **`mysqldump` Subprocess Missing**:
   - Ensure `mysqldump` is installed on your operating system's PATH (`sudo apt-get install mysql-client` on Ubuntu/Debian).

3. **Mail Attachment & Proxy Timeouts**:
   - The backup process runs asynchronously in a background thread pool (`emailTaskExecutor`), preventing gateway timeouts on production Nginx servers.

4. **Temporary Storage Cleanup**:
   - The application automatically cleans up temporary `.sql` and `.zip` files in the system temp directory inside a `finally` block upon backup completion.
