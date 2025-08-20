# 🚀 How to Create VPS (IPv4) in GitHub Free

This project allows you to create a **temporary VPS with IPv4** using **GitHub Actions + Tailscale + tmate**.  
It runs for up to **6 hours per session**, and auto-restores backups on each rerun.  

---

## 📌 Features
- Free VPS powered by GitHub Actions  
- IPv4 address provided via **Tailscale**  
- Persistent backups between runs  
- Root user access with full `sudo` permissions  
- Connect with **SSH (tmate)** or directly via **Tailscale IP**  

---

## ⚙️ Setup Instructions

### 1. Fork this Repository
Click **Fork** to make your own copy.

### 2. Add Secret
- Go to **Settings > Secrets and variables > Actions**  
- Add new **Repository Secret**:
  ```
  Name: TAILSCALE_AUTHKEY
  Value: <your-tailscale-auth-key>
  ```
  👉 Generate a key from [Tailscale Admin Console](https://login.tailscale.com/admin/authkeys).

### 3. Start VPS
- Go to **Actions** tab in your forked repo  
- Select **Continuous Persistent VPS** workflow  
- Click **Run workflow**  

---

## 🔑 Default Login
- **Root User:** `itz_ytansh`  
- **Root Password:** `itz_ytansh`

(You can change inside the workflow YAML if needed.)

---

## 🔗 Connect to VPS
You can connect in two ways:

### ✅ Method 1: SSH via tmate
Once workflow starts, scroll to job logs.  
Look for output like:
```
🔑 tmate SSH session:
ssh <something>@tmate.io
```
Copy and paste into your terminal or use PuTTY/Termius.

### ✅ Method 2: Connect via Tailscale IPv4
- Install [Tailscale](https://tailscale.com/download) on your PC/phone.  
- Log in with the same account used for `TAILSCALE_AUTHKEY`.  
- In job logs, find:
  ```
  🔗 Tailscale IP: 100.x.x.x
  ```
- SSH to it:
  ```bash
  ssh itz_ytansh@100.x.x.x
  ```

---

## 🛠️ Notes
- Each run lasts **6 hours max** (GitHub Actions limit).  
- Backup (including Tailscale state) is auto-saved and restored on next run.  
- Works best for testing **Pterodactyl**, **SSL at localhost**, or other VPS setups.  

---

## 📚 References
- [Tailscale](https://tailscale.com/)  
- [tmate](https://tmate.io/)  
- [Get SSL at localhost](https://github.com/How2MCoffc/Get-SSL-at-localhost)  
- [Pterodactyl](https://pterodactyl.io/)  

---

## ❤️ Credits
Made with 💻 by **ITZ_YTANSH**  
Subscribe on YouTube 👉 [Itz_YtAnsh](https://www.youtube.com/@ITZ_YT_ANSH_OFFICIAL)
