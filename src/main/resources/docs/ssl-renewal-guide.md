# SSL Certificate Renewal & Management Guide

This guide provides comprehensive instructions to inspect, manually renew, automate, and troubleshoot SSL/TLS certificates for **TTS Nashik** domains (`team.ttsnashik.com` and `finance.ttsnashik.com`).

---

## 1. Server & Certificate Matrix

| Domain | Service | Server IP | AWS Region | SSH User | Local PEM Key Path | Web Server |
|---|---|---|---|---|---|---|
| `team.ttsnashik.com` | CRM Application | `15.252.165.197` | Mumbai (`ap-south-1`) | `ec2-user` | `C:\Users\sidpe\Downloads\mumbai-yogesh.pem` | Nginx (`crm.conf`) |
| `finance.ttsnashik.com` | Finance Application | `18.141.231.119` | Singapore (`ap-southeast-1`) | `ec2-user` | `C:\Users\sidpe\Downloads\singapore-yogesh.pem` | Nginx (`finance.conf`) |

---

## 2. Current Certificate Schedule

> [!IMPORTANT]
> Let's Encrypt certificates are valid for **90 days**. The automated renewal window opens **30 days before expiration**.

* **Last Renewed:** September 15, 2026
* **Current Expiry Date:** **December 14, 2026 at ~09:30 AM IST**
* **Next Auto-Renewal Cycle Date:** **November 14, 2026**

---

## 3. Quick Remote Check (From Local Windows Machine)

You can check whether the certificate is active, expired, or how many days remain directly from PowerShell without SSH:

### Check `team.ttsnashik.com`
```powershell
$tcpClient = New-Object System.Net.Sockets.TcpClient("team.ttsnashik.com", 443)
$sslStream = New-Object System.Net.Security.SslStream($tcpClient.GetStream(), $false)
$sslStream.AuthenticateAsClient("team.ttsnashik.com")
$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($sslStream.RemoteCertificate)
[PSCustomObject]@{
    Domain        = "team.ttsnashik.com"
    Issuer        = $cert.Issuer
    ValidFrom     = $cert.NotBefore
    ValidTo       = $cert.NotAfter
    DaysRemaining = ($cert.NotAfter - (Get-Date)).Days
    HasExpired    = ((Get-Date) -gt $cert.NotAfter)
} | Format-List
$sslStream.Close()
$tcpClient.Close()
```

### Check `finance.ttsnashik.com`
```powershell
$tcpClient = New-Object System.Net.Sockets.TcpClient("finance.ttsnashik.com", 443)
$sslStream = New-Object System.Net.Security.SslStream($tcpClient.GetStream(), $false)
$sslStream.AuthenticateAsClient("finance.ttsnashik.com")
$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($sslStream.RemoteCertificate)
[PSCustomObject]@{
    Domain        = "finance.ttsnashik.com"
    Issuer        = $cert.Issuer
    ValidFrom     = $cert.NotBefore
    ValidTo       = $cert.NotAfter
    DaysRemaining = ($cert.NotAfter - (Get-Date)).Days
    HasExpired    = ((Get-Date) -gt $cert.NotAfter)
} | Format-List
$sslStream.Close()
$tcpClient.Close()
```

---

## 4. Manual Renewal Procedure

If you ever need to manually force renewal, follow the steps below for each server.

### Part A: Renewing `team.ttsnashik.com` (CRM Server)

1. **Connect via SSH:**
   ```bash
   ssh -i "C:\Users\sidpe\Downloads\mumbai-yogesh.pem" ec2-user@15.252.165.197
   ```

2. **Check Current Status:**
   ```bash
   sudo certbot certificates
   ```

3. **Test with Dry Run (Optional but recommended):**
   ```bash
   sudo certbot renew --dry-run
   ```

4. **Execute Renewal & Reload Nginx:**
   ```bash
   sudo certbot renew --no-random-sleep-on-renew && sudo nginx -t && sudo systemctl reload nginx
   ```

> [!NOTE]
> Passing `--no-random-sleep-on-renew` prevents Certbot from intentionally pausing for a random delay before requesting the certificate.

---

### Part B: Renewing `finance.ttsnashik.com` (Finance Server)

1. **Connect via SSH:**
   ```bash
   ssh -i "C:\Users\sidpe\Downloads\singapore-yogesh.pem" ec2-user@18.141.231.119
   ```

2. **Check Current Status:**
   ```bash
   sudo certbot certificates
   ```

3. **Execute Renewal & Reload Nginx:**
   ```bash
   sudo certbot renew --no-random-sleep-on-renew && sudo nginx -t && sudo systemctl reload nginx
   ```

---

## 5. Automated Background Renewal (Systemd Timer)

On both Amazon Linux / RHEL instances, automated renewal is scheduled using `certbot-renew.timer`.

### Verify Timer is Active
Run this on the server:
```bash
sudo systemctl status certbot-renew.timer
```

Output should show:
```
Active: active (waiting)
Triggers: ● certbot-renew.service
```

### Enable Timer (If disabled)
```bash
sudo systemctl enable --now certbot-renew.timer
```

### Ensure Nginx Automatically Reloads on Auto-Renewal
Certbot renewal configs are located at:
* `/etc/letsencrypt/renewal/team.ttsnashik.com.conf`
* `/etc/letsencrypt/renewal/finance.ttsnashik.com.conf`

To verify Nginx reloads after every automatic renewal, ensure a deploy hook exists or add one:
```bash
# Test renewal with nginx reload hook
sudo certbot renew --deploy-hook "systemctl reload nginx"
```

---

## 6. Key Configuration Files & Paths Reference

### CRM Server (`15.252.165.197`)
* **Nginx Configuration:** `/etc/nginx/conf.d/crm.conf`
* **Certbot Live Symlinks:**
  * Full Chain: `/etc/letsencrypt/live/team.ttsnashik.com/fullchain.pem`
  * Private Key: `/etc/letsencrypt/live/team.ttsnashik.com/privkey.pem`
* **Renewal Settings:** `/etc/letsencrypt/renewal/team.ttsnashik.com.conf`
* **Certbot Logs:** `/var/log/letsencrypt/letsencrypt.log`

### Finance Server (`18.141.231.119`)
* **Nginx Configuration:** `/etc/nginx/conf.d/finance.conf`
* **Certbot Live Symlinks:**
  * Full Chain: `/etc/letsencrypt/live/finance.ttsnashik.com/fullchain.pem`
  * Private Key: `/etc/letsencrypt/live/finance.ttsnashik.com/privkey.pem`
* **Renewal Settings:** `/etc/letsencrypt/renewal/finance.ttsnashik.com.conf`
* **Certbot Logs:** `/var/log/letsencrypt/letsencrypt.log`

---

## 7. Troubleshooting Common Issues

### 1. `NET::ERR_CERT_DATE_INVALID` in Browser
* **Cause:** The certificate reached its expiration date before renewal completed.
* **Resolution:** Run the manual renewal command in Section 4 and reload Nginx (`sudo systemctl reload nginx`). Clear your browser cache or open in Incognito.

### 2. Certbot Fails Challenge (Connection Refused or Timeout)
* **Cause:** Port 80 (HTTP) is blocked by AWS EC2 Security Group or Nginx is stopped.
* **Resolution:**
  * Ensure AWS Security Group allows inbound traffic on **Port 80** and **Port 443** from `0.0.0.0/0`. Let's Encrypt requires Port 80 for HTTP-01 verification.
  * Verify Nginx status: `sudo systemctl status nginx`.

### 3. Permission Denied (publickey) on SSH
* **Cause:** Wrong PEM key path or incorrect key permissions.
* **Resolution:**
  * Ensure path to key is correct (e.g. `C:\Users\sidpe\Downloads\mumbai-yogesh.pem`).
  * If executing from WSL/Linux, adjust file permissions: `chmod 400 key.pem`.
