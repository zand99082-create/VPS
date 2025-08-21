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

### 3. Add Workflow File
Create a new file in your forked repo:  
`.github/workflows/vps.yml`  

Paste the following code:

```yaml
on:
  schedule:
    - cron: '0 */6 * * *'  # Every 6 hours
  workflow_dispatch:

jobs:
  vps-session:
    runs-on: ubuntu-latest
    timeout-minutes: 350  # Just under 6 hours

    steps:
      - name: Checkout repo
        uses: actions/checkout@v4

      - name: Set hostname to itz_ytansh
        run: sudo hostnamectl set-hostname itz_ytansh

      - name: Download VPS backup (if any)
        uses: actions/download-artifact@v4
        with:
          name: vps-backup
          path: ./backup
        continue-on-error: true

      - name: Install prerequisites
        run: |
          sudo apt update
          sudo apt install -y tmate curl unzip sudo net-tools neofetch

      - name: Install Tailscale official script
        run: |
          curl -fsSL https://tailscale.com/install.sh | sh

      - name: Restore backup files
        run: |
          if [ -f ./backup/backup.zip ]; then
            unzip -o ./backup/backup.zip -d /
          else
            echo "No backup found, starting fresh"
          fi

      - name: Restore Tailscale state
        run: |
          if [ -f /opt/vps-backup/data/tailscaled.state ]; then
            sudo mkdir -p /var/lib/tailscale
            sudo cp /opt/vps-backup/data/tailscaled.state /var/lib/tailscale/tailscaled.state
            sudo chmod 600 /var/lib/tailscale/tailscaled.state
          fi

      - name: Start Tailscale
        run: |
          sudo tailscaled &
          sleep 8
          sudo tailscale up --authkey ${{ secrets.TAILSCALE_AUTHKEY }} --hostname=biralo || echo "Tailscale already up"

      - name: Create user itz_ytansh with sudo
        run: |
          if ! id -u itz_ytansh >/dev/null 2>&1; then
            sudo useradd -m -s /bin/bash itz_ytansh
            echo "itz_ytansh:itz_ytansh" | sudo chpasswd
            sudo usermod -aG sudo itz_ytansh
            echo "itz_ytansh ALL=(ALL) NOPASSWD:ALL" | sudo tee /etc/sudoers.d/itz_ytansh
          fi

      - name: Start tmate session for SSH access
        uses: mxschmitt/action-tmate@v3

      - name: Show Tailscale IP and tmate info
        run: |
          echo "🔗 Tailscale IP:"
          tailscale ip -4 || echo "Tailscale IP not found"
          echo ""
          echo "🔑 tmate SSH session:"
          cat $HOME/.tmate.sock/ssh

      - name: Sleep to keep VPS alive
        run: sleep 21600  # 6 hours

      - name: Backup VPS data and tailscale state
        run: |
          sudo mkdir -p /opt/vps-backup/data
          sudo cp /var/lib/tailscale/tailscaled.state /opt/vps-backup/data/
          sudo chown -R $USER:$USER /opt/vps-backup
          zip -r backup.zip /opt/vps-backup

      - name: Upload VPS backup artifact
        uses: actions/upload-artifact@v4
        with:
          name: vps-backup
          path: backup.zip
```

---

### 4. Start VPS
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
