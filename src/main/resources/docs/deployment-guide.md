# Production Deployment Guide: TTS Student Management System (CRM)

This guide provides step-by-step instructions to compile, package, upload, deploy, and verify the SMS CRM application on the AWS EC2 server using the `crm.service` configuration.

---

## 1. Deployment Overview

* **Local Environment:** Windows/macOS/Linux with Maven & Java 21.
* **Target Server Host:** `ec2-13-206-199-164.ap-south-1.compute.amazonaws.com`
* **Target Server User:** `ec2-user`
* **Private Key File:** `mumbai-key.pem`
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

Use Secure Copy Protocol (`scp`) to upload the built `.war` file to the remote server. Ensure that your terminal is in the folder containing `mumbai-key.pem` (or specify the correct absolute path to the key).

> [!IMPORTANT]
> If you are on Linux or macOS, ensure your private key file has the correct permissions:
> `chmod 400 mumbai-key.pem`

### Upload Command
Run the following command in your local terminal:

```bash
scp -i "mumbai-key.pem" target/sms-0.0.1-SNAPSHOT.war ec2-user@ec2-13-206-199-164.ap-south-1.compute.amazonaws.com:/opt/apps/crm/crm_updated.war
```

> [!NOTE]
> * The WAR is deployed directly as `/opt/apps/crm/crm_updated.war` on the server — the filename must remain `crm_updated.war` as that is what `crm.service` references.
> * The `scp` command replaces the file in-place; the service restart in the next step picks up the new binary.

---

## 4. Step 3: Access the Server via SSH

Connect to your AWS EC2 instance using the secure shell command:

```bash
ssh -i "mumbai-key.pem" ec2-user@ec2-13-206-199-164.ap-south-1.compute.amazonaws.com
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
   * Double-check that your terminal directory contains the `mumbai-key.pem` file.
   * If on Linux/macOS, verify permissions using `ls -l mumbai-key.pem` (must be read-only for owner).

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
