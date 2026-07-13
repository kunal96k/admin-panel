# System Daily Google Drive & Email Backup Guide

This document describes the automated daily backup system implemented in the **TechnoKraft Student Management System (SMS) CRM**.

---

## 1. Architecture Overview

The system executes daily automated backups that upload database SQL dumps and application archive zip files directly to **Google Drive** in the project-isolated **`TTS_Daily_Backups`** folder while simultaneously dispatching summary emails with view links and attachments.

### Key System Components
1. **`GoogleDriveService`**: Handles authentication via OAuth2 User Credentials (consuming personal Google Drive storage quota) or Service Accounts, uploading backup files to Google Drive.
2. **`BackupService`**: Core orchestrator responsible for executing `mysqldump`, zipping user upload files (`uploads/`) and system logs (`logs/`), uploading files prefixed with `tts_sms_` to Google Drive, sending summary emails, and clearing temporary storage.
3. **`BackupScheduler`**: Automated runner configured to trigger daily at **2:00 AM IST**.
4. **`BackupController`**: Secure REST API endpoint (`POST /api/auth/backup`) allowing authorized administrators to trigger backups on-demand.

### System Workflow
```
[Daily Backup Scheduler (02:00 AM IST)] ─────┐
                                              ├─> [BackupService] ─> Generates tts_sms_db_backup_*.sql & zips uploads/logs
[Manual Trigger Endpoint (POST /backup)] ─────┘        │
                                                       ├─> [GoogleDriveService] ─> Uploads to Google Drive Folder 'TTS_Daily_Backups'
                                                       └─> [MailSender] ─────────> Dispatches status email with links
```

---

## 2. Authentication & Credentials Setup Guide

The backup system supports **OAuth2 User Credentials** (recommended for personal `@gmail.com` accounts to utilize their 15 GB / 5 TB storage quota with zero cost) as well as **Service Accounts**.

### Project Configuration Details
* **Google Account**: `mr.dreamchaser11@gmail.com`
* **Target Google Drive Folder**: `TTS_Daily_Backups`
* **Target Folder ID**: `15Ltp1XqZ9qbw68v1PBeM8FSO3LmSaggW`

---

### 📋 How to Set Up Google Drive Backup in a New / Other Project (3 Minutes, $0 Cost)

To set up or reuse this Google Drive backup integration in any other Spring Boot project:

#### Step 1: Create OAuth 2.0 Credentials in Google Cloud Console
1. Go to [Google Cloud Console Credentials](https://console.cloud.google.com/apis/credentials).
2. Click **"+ CREATE CREDENTIALS"** → select **"OAuth client ID"**.
3. Select Application type: **Web application**.
4. Set Name: `SMS Backup Service`
5. Under **Authorized redirect URIs**, add:
   `https://developers.google.com/oauthplayground`
6. Click **CREATE** and copy your **Client ID** and **Client Secret**.
7. Go to **APIs & Services** → **OAuth consent screen** → under **Test users**, click **"+ ADD USERS"** and add your Gmail address (e.g. `mr.dreamchaser11@gmail.com`).

#### Step 2: Generate the Refresh Token via OAuth 2.0 Playground
1. Open [Google OAuth 2.0 Playground](https://developers.google.com/oauthplayground/).
2. Click the gear icon (⚙️ **OAuth 2.0 configuration**) at top right.
3. Check **"Use your own OAuth credentials"**.
4. Paste your **OAuth Client ID** and **OAuth Client Secret**.
5. In the left API list under Step 1, expand **Drive API v3** → check `https://www.googleapis.com/auth/drive.file`.
6. Click **Authorize APIs** and sign in with your Google account.
7. Click **Exchange authorization code for tokens** under Step 2.
8. Copy the generated **Refresh token**.

#### Step 3: Configure Environment Variables (`/etc/crm.env` & `application.yaml`)

Add the parameters to `/etc/crm.env` on your production server:
```bash
GOOGLE_DRIVE_BACKUP_ENABLED=true
GOOGLE_DRIVE_CLIENT_ID="YOUR_GOOGLE_DRIVE_CLIENT_ID"
GOOGLE_DRIVE_CLIENT_SECRET="YOUR_GOOGLE_DRIVE_CLIENT_SECRET"
GOOGLE_DRIVE_REFRESH_TOKEN="YOUR_GOOGLE_DRIVE_REFRESH_TOKEN"
GOOGLE_DRIVE_FOLDER_ID="15Ltp1XqZ9qbw68v1PBeM8FSO3LmSaggW"
```

In `application.yaml`:
```yaml
app:
  backup:
    email: ${BACKUP_EMAIL:kunalpatil192001@gmail.com}
    passcode: ${BACKUP_PASSCODE:Kunal@217}
    email-enabled: ${BACKUP_EMAIL_ENABLED:false}
    google-drive:
      enabled: ${GOOGLE_DRIVE_BACKUP_ENABLED:true}
      client-id: ${GOOGLE_DRIVE_CLIENT_ID:}
      client-secret: ${GOOGLE_DRIVE_CLIENT_SECRET:}
      refresh-token: ${GOOGLE_DRIVE_REFRESH_TOKEN:}
      credentials-path: ${GOOGLE_DRIVE_CREDENTIALS_PATH:credentials/global-email-monitor-e4ef0cd5ef08.json}
      folder-name: ${GOOGLE_DRIVE_FOLDER_NAME:TTS_SMS_Daily_Backups}
      folder-id: ${GOOGLE_DRIVE_FOLDER_ID:15Ltp1XqZ9qbw68v1PBeM8FSO3LmSaggW}
```

---

## 3. Environment & Configuration Reference

| Property | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `app.backup.email` | `BACKUP_EMAIL` | `kunalpatil192001@gmail.com` | Destination email recipient for status summary reports. |
| `app.backup.passcode` | `BACKUP_PASSCODE` | `Kunal@217` | Passcode for authorized manual REST API triggers. |
| `app.backup.email-enabled` | `BACKUP_EMAIL_ENABLED` | `false` | Toggles email notifications on (`true`) or off (`false`). Defaults to `false`. |
| `app.backup.google-drive.enabled` | `GOOGLE_DRIVE_BACKUP_ENABLED` | `true` | Toggles Google Drive integration on or off. |
| `app.backup.google-drive.client-id` | `GOOGLE_DRIVE_CLIENT_ID` | (configured) | OAuth2 Client ID for Google API authentication. |
| `app.backup.google-drive.client-secret` | `GOOGLE_DRIVE_CLIENT_SECRET` | (configured) | OAuth2 Client Secret for Google API authentication. |
| `app.backup.google-drive.refresh-token` | `GOOGLE_DRIVE_REFRESH_TOKEN` | (configured) | Long-lived OAuth2 Refresh Token for storage quota access. |
| `app.backup.google-drive.folder-id` | `GOOGLE_DRIVE_FOLDER_ID` | `15Ltp1XqZ9qbw68v1PBeM8FSO3LmSaggW` | Target Google Drive folder ID. |

---

## 4. Daily Backup Scheduler

Configured in `BackupScheduler.java` using Spring's `@Scheduled` annotation:

- **Cron Expression**: `0 0 2 * * ?`
- **Execution Time**: **Every night at 02:00 AM IST** (`Asia/Kolkata`).
- **Cost**: **100% Free** (Uses standard Google Drive quota and free API calls).

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
curl -X POST https://team.ttsnashik.com/api/auth/backup \
  -H "Content-Type: application/json" \
  -d '{"email": "kunalpatil192001@gmail.com", "passcode": "Kunal@217"}'
```

### 2. Using PowerShell (Windows)

```powershell
$body = @{
    email = "kunalpatil192001@gmail.com"
    passcode = "Kunal@217"
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://team.ttsnashik.com/api/auth/backup" -Method Post -Body $body -ContentType "application/json"
```

### 3. Using Windows Command Prompt (cmd.exe)

```cmd
curl -X POST https://team.ttsnashik.com/api/auth/backup -H "Content-Type: application/json" -d "{\"email\":\"kunalpatil192001@gmail.com\",\"passcode\":\"Kunal@217\"}"
```

---

## 6. Troubleshooting & Maintenance

1. **`403 Forbidden - Service Accounts do not have storage quota`**:
   - **Cause**: Google Cloud Service Accounts have 0 bytes internal storage quota and cannot store files in their root directory.
   - **Fix**: Use OAuth2 User Refresh Token authentication (Method 1) or configure `GOOGLE_DRIVE_FOLDER_ID` for a shared folder.

2. **`403 access_denied` on OAuth Consent Screen**:
   - **Cause**: The app is in Testing mode in Google Cloud Console.
   - **Fix**: Go to **APIs & Services** → **OAuth consent screen** → **Test users** and add the signing-in Gmail address to the approved list.

3. **`mysqldump` Subprocess Missing**:
   - Ensure `mysqldump` is installed on your operating system's PATH (`sudo apt-get install mysql-client` on Ubuntu/Debian).

4. **Mail Attachment & Proxy Timeouts**:
   - The backup process runs asynchronously in a background thread pool (`emailTaskExecutor`), preventing gateway timeouts on production Nginx servers.
