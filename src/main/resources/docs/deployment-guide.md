# Production Deployment Guide: TTS Student Management System (CRM)

This guide provides step-by-step instructions to compile, package, upload, deploy, and verify the SMS CRM application on the AWS EC2 server using the `crm.service` configuration.

---

## 1. Deployment Overview

* **Local Environment:** Windows/macOS/Linux with Maven & Java 21.
* **Target Server Host:** `3.110.90.204`
* **Target Server User:** `ec2-user`
* **Private Key File:** `mumbai-yogesh.pem`
* **Target Systemd Service:** `crm.service`
* **Packaging Format:** `war` (specified in [pom.xml](file:///c:/Users/TechnoKraft/Desktop/backup/project_tts_backup/tts-sms/sms/pom.xml))

---

## 2. Step 1: Package the Application Locally

Before uploading, compile and build the production-ready package. Make sure you are in the root directory of the `sms` project.

> [!TIP]
> Always run tests locally before packaging, or skip them if they are already verified to save build time.

### Using Maven Wrapper (Recommended)
* **Windows (PowerShell):**
  ```powershell
  .\mvnw.cmd clean package -DskipTests
  ```
* **Windows (Command Prompt):**
  ```cmd
  mvnw.cmd clean package -DskipTests
  ```
* **Linux / macOS:**
  ```bash
  ./mvnw clean package -DskipTests
  ```

### Using Global Maven (If installed and in PATH)
```bash
mvn clean package -DskipTests
```

This compiles the code and generates the deployable artifact in the `target/` directory:
* **Generated File:** `target/sms-0.0.1-SNAPSHOT.war`

---

## 3. Step 2: Upload the Package to the AWS EC2 Server

Use Secure Copy Protocol (`scp`) to upload the built `.war` file to the remote server. Ensure that your terminal is in the folder containing `mumbai-yogesh.pem` (or specify the correct absolute path to the key).

> [!IMPORTANT]
> If you are on Linux or macOS, ensure your private key file has the correct permissions:
> `chmod 400 mumbai-yogesh.pem`

### Upload Command
Run the following command in your local terminal:

```bash
scp -i "mumbai-yogesh.pem" target/sms-0.0.1-SNAPSHOT.war ec2-user@3.110.90.204:/opt/apps/crm/crm_updated.war
```

> [!NOTE]
> * The WAR is deployed directly as `/opt/apps/crm/crm_updated.war` on the server — the filename must remain `crm_updated.war` as that is what `crm.service` references.
> * The `scp` command replaces the file in-place; the service restart in the next step picks up the new binary.

---

## 4. Step 3: Access the Server via SSH

Connect to your AWS EC2 instance using the secure shell command:

```bash
ssh -i "mumbai-yogesh.pem" ec2-user@3.110.90.204
```

---

## 5. Step 4: Restart the Systemd Service

Once connected to the server, you need to restart the application service so it loads the newly uploaded package.

### Reload Systemd Configurations
If you modified the `crm.service` file itself, reload systemd first:
```bash
systemctl daemon-reload
```

### Restart the Service
Execute the restart command:
```bash
systemctl restart crm.service
```

### Enable the Service (If not already enabled)
Ensure that the service starts automatically on system reboots:
```bash
systemctl enable crm.service
```

---

## 6. Step 5: Verify Deployment and Monitor Logs

Verify that the service is running correctly and track startup progression.

### Check Service Status
Confirm the service transitioned to an active (running) state:
```bash
systemctl status crm.service
```

### View Live Systemd Journal Logs
Monitor the system console output for the `crm.service` unit:
```bash
journalctl -u crm.service -f -n 100
```
* `-u crm.service`: Filters logs to show only the `crm.service` unit.
* `-f`: Follows the logs in real-time.
* `-n 100`: Displays the last 100 log entries.

### View Live Application File Logs
If the application writes to a dedicated log file (like `sms-application.log`), view it directly:
```bash
tail -f /var/www/tts-sms/logs/sms-application.log
```

---

## 7. Troubleshooting Deployment Issues

1. **Permission Denied (publickey) on SSH/SCP:**
   * Double-check that your terminal directory contains the `mumbai-yogesh.pem` file.
   * If on Linux/macOS, verify permissions using `ls -l mumbai-yogesh.pem` (must be read-only for owner).

2. **Application Port Conflict:**
   * If the service fails to start, verify if another process is using the application port (typically `8080` or `8081`):
     ```bash
     ss -lntp | grep -E "8080|8081"
     ```
   * Terminate the conflicting process if necessary.

3. **Active/Inactive Status Loop:**
   * If the systemd service starts but immediately dies, check the log stack trace using `journalctl -u crm.service -e`. Common causes include:
     * Missing or incorrect environment variables (e.g., database connection credentials).
     * Port already in use.
     * Java runtime version mismatch.

---

## 8. Step 6: Deploying Custom Error and Maintenance Pages

To ensure a smooth user experience, Nginx should handle standard HTTP errors (like 404 Not Found) and backend connection failures (like 502 Bad Gateway / 503 Maintenance) directly without hitting the Spring Boot application server.

### 1. Upload Static Pages to the Server
Upload your custom static HTML files (e.g., `maintenance.html`, `404.html`) to the web directory on the server:

```bash
# Upload maintenance page to /var/www/tts-sms/static/
scp -i "mumbai-yogesh.pem" src/main/resources/static/maintenance.html ec2-user@3.110.90.204:/var/www/tts-sms/static/maintenance.html
```

### 2. Configure Nginx Server Blocks
Connect to the server and update your Nginx configuration (typically in `/etc/nginx/conf.d/` or `/etc/nginx/sites-available/default`):

```nginx
server {
    listen 80;
    server_name 3.110.90.204;

    # Specify local root where Nginx holds static files
    root /var/www/tts-sms/static;

    # Map error status codes to their respective static pages
    error_page 404 /404.html;
    error_page 502 503 504 /maintenance.html;

    # Direct proxy location to the Spring Boot App
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # CRITICAL: Intercept proxy errors so Nginx handles them directly
        proxy_intercept_errors on;
    }

    # Ensure static error pages are served directly by Nginx (no proxy loop)
    location = /404.html {
        internal;
    }

    location = /maintenance.html {
        internal;
    }
}
```

### 3. Verify and Reload Nginx
After modifying the server block configuration, verify syntax and reload the Nginx daemon:
```bash
# Check syntax config
sudo nginx -t

# Reload configuration gracefully
sudo systemctl reload nginx
```
