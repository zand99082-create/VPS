package com.vps.manager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText tokenInput;
    private TextView statusText, resultText;
    private Button copyBtn;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tokenInput = findViewById(R.id.tokenInput);
        statusText = findViewById(R.id.statusText);
        resultText = findViewById(R.id.resultText);
        copyBtn = findViewById(R.id.copyBtn);

        findViewById(R.id.tailscaleInput).setVisibility(View.GONE);

        findViewById(R.id.oneClickBtn).setOnClickListener(v -> startOneClick());
        copyBtn.setOnClickListener(v -> copyResult());
    }

    private void startOneClick() {
        String token = tokenInput.getText().toString().trim();

        if (token.isEmpty()) {
            showToast("لطفاً توکن گیت‌هاب را وارد کنید");
            return;
        }

        statusText.setText("⚡ شروع فرآیند یک‌کلیک...");
        resultText.setText("");
        copyBtn.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                String repoName = "VPS-" + System.currentTimeMillis() / 1000;
                mainHandler.post(() -> statusText.setText("📁 ایجاد مخزن: " + repoName));
                String repo = createRepo(token, repoName);

                mainHandler.post(() -> statusText.setText("📂 ایجاد workflow..."));
                createWorkflow(token, repo);

                mainHandler.post(() -> statusText.setText("🚀 اجرای VPS..."));
                String ssh = runWorkflow(token, repo);

                mainHandler.post(() -> {
                    statusText.setText("✅ VPS آماده است! 🎉");
                    resultText.setText(ssh);
                    copyBtn.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                String errorMsg = "❌ خطا:\n" + e.getMessage() + "\n\n📋 برای کپی کردن، دکمه کپی را بزنید.";
                mainHandler.post(() -> {
                    statusText.setText("❌ خطا در اجرا");
                    resultText.setText(errorMsg);
                    copyBtn.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private String createRepo(String token, String name) throws Exception {
        URL url = new URL("https://api.github.com/user/repos");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "token " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = "{\"name\":\"" + name + "\",\"private\":false}";
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(json);
        wr.flush();
        wr.close();

        int code = conn.getResponseCode();
        if (code == 201) {
            String response = readResponse(conn);
            return new JSONObject(response).getString("full_name");
        } else {
            String err = readErrorResponse(conn);
            throw new Exception("خطا در ایجاد مخزن: " + code + "\n" + err);
        }
    }

    private void createWorkflow(String token, String repo) throws Exception {
        String workflow = "name: VPS Creator\n" +
            "on:\n" +
            "  schedule:\n" +
            "    - cron: '0 */6 * * *'\n" +
            "  workflow_dispatch:\n" +
            "jobs:\n" +
            "  vps-session:\n" +
            "    runs-on: ubuntu-latest\n" +
            "    timeout-minutes: 350\n" +
            "    steps:\n" +
            "      - name: Checkout repo\n" +
            "        uses: actions/checkout@v4\n" +
            "      - name: Set hostname to itz_ytansh\n" +
            "        run: sudo hostnamectl set-hostname itz_ytansh\n" +
            "      - name: Download VPS backup (if any)\n" +
            "        uses: actions/download-artifact@v4\n" +
            "        with:\n" +
            "          name: vps-backup\n" +
            "          path: ./backup\n" +
            "        continue-on-error: true\n" +
            "      - name: Install prerequisites\n" +
            "        run: |\n" +
            "          sudo apt update\n" +
            "          sudo apt install -y tmate curl unzip sudo net-tools neofetch\n" +
            "      - name: Install Tailscale official script\n" +
            "        run: |\n" +
            "          curl -fsSL https://tailscale.com/install.sh | sh\n" +
            "      - name: Restore backup files\n" +
            "        run: |\n" +
            "          if [ -f ./backup/backup.zip ]; then\n" +
            "            unzip -o ./backup/backup.zip -d /\n" +
            "          else\n" +
            "            echo \"No backup found, starting fresh\"\n" +
            "          fi\n" +
            "      - name: Restore Tailscale state\n" +
            "        run: |\n" +
            "          if [ -f /opt/vps-backup/data/tailscaled.state ]; then\n" +
            "            sudo mkdir -p /var/lib/tailscale\n" +
            "            sudo cp /opt/vps-backup/data/tailscaled.state /var/lib/tailscale/tailscaled.state\n" +
            "            sudo chmod 600 /var/lib/tailscale/tailscaled.state\n" +
            "          fi\n" +
            "      - name: Start Tailscale\n" +
            "        run: |\n" +
            "          sudo tailscaled &\n" +
            "          sleep 8\n" +
            "          sudo tailscale up --authkey ${{ secrets.TAILSCALE_AUTHKEY }} --hostname=biralo || echo \"Tailscale already up\"\n" +
            "      - name: Create user itz_ytansh with sudo\n" +
            "        run: |\n" +
            "          if ! id -u itz_ytansh >/dev/null 2>&1; then\n" +
            "            sudo useradd -m -s /bin/bash itz_ytansh\n" +
            "            echo \"itz_ytansh:itz_ytansh\" | sudo chpasswd\n" +
            "            sudo usermod -aG sudo itz_ytansh\n" +
            "            echo \"itz_ytansh ALL=(ALL) NOPASSWD:ALL\" | sudo tee /etc/sudoers.d/itz_ytansh\n" +
            "          fi\n" +
            "      - name: Start tmate session for SSH access\n" +
            "        uses: mxschmitt/action-tmate@v3\n" +
            "      - name: Show Tailscale IP and tmate info\n" +
            "        run: |\n" +
            "          echo \"🔗 Tailscale IP:\"\n" +
            "          tailscale ip -4 || echo \"Tailscale IP not found\"\n" +
            "          echo \"\"\n" +
            "          echo \"🔑 tmate SSH session:\"\n" +
            "          cat $HOME/.tmate.sock/ssh\n" +
            "      - name: Sleep to keep VPS alive\n" +
            "        run: sleep 21600\n" +
            "      - name: Backup VPS data and tailscale state\n" +
            "        run: |\n" +
            "          sudo mkdir -p /opt/vps-backup/data\n" +
            "          sudo cp /var/lib/tailscale/tailscaled.state /opt/vps-backup/data/\n" +
            "          sudo chown -R $USER:$USER /opt/vps-backup\n" +
            "          zip -r backup.zip /opt/vps-backup\n" +
            "      - name: Upload VPS backup artifact\n" +
            "        uses: actions/upload-artifact@v4\n" +
            "        with:\n" +
            "          name: vps-backup\n" +
            "          path: backup.zip";

        String encoded = Base64.encodeToString(workflow.getBytes(), Base64.NO_WRAP);
        String url = "https://api.github.com/repos/" + repo + "/contents/.github/workflows/vps.yml";

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "token " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = "{\"message\":\"Add VPS workflow\",\"content\":\"" + encoded + "\"}";
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(json);
        wr.flush();
        wr.close();

        int code = conn.getResponseCode();
        if (code != 201 && code != 200) {
            String err = readErrorResponse(conn);
            throw new Exception("خطا در ایجاد workflow: " + code + "\n" + err);
        }
    }

    private String runWorkflow(String token, String repo) throws Exception {
        String url = "https://api.github.com/repos/" + repo + "/actions/workflows/vps.yml/dispatches";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "token " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = "{\"ref\":\"main\"}";
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(json);
        wr.flush();
        wr.close();

        int code = conn.getResponseCode();
        if (code == 204) {
            Thread.sleep(30000);
            String runId = getLatestRunId(token, repo);
            if (runId != null) {
                String sshLink = getArtifactLink(token, repo, runId);
                if (sshLink != null && !sshLink.isEmpty()) {
                    return "🔑 لینک اتصال SSH:\n\n" + sshLink + "\n\n⏳ مدت زمان: 6 ساعت\n📌 مخزن: " + repo;
                }
            }
            return "✅ VPS workflow با موفقیت اجرا شد!\n📋 لینک SSH در حال آماده‌سازی است...\n⏳ مدت زمان: 6 ساعت\n📌 مخزن: " + repo;
        } else {
            String err = readErrorResponse(conn);
            throw new Exception("خطا در اجرای workflow: " + code + "\n" + err);
        }
    }

    private String getLatestRunId(String token, String repo) throws Exception {
        String url = "https://api.github.com/repos/" + repo + "/actions/runs?status=in_progress&per_page=1";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "token " + token);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

        String response = readResponse(conn);
        JSONObject obj = new JSONObject(response);
        JSONArray runs = obj.getJSONArray("workflow_runs");
        if (runs.length() > 0) {
            return runs.getJSONObject(0).getString("id");
        }
        return null;
    }

    private String getArtifactLink(String token, String repo, String runId) throws Exception {
        String url = "https://api.github.com/repos/" + repo + "/actions/runs/" + runId + "/artifacts";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "token " + token);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

        String response = readResponse(conn);
        JSONObject obj = new JSONObject(response);
        JSONArray artifacts = obj.getJSONArray("artifacts");

        if (artifacts.length() > 0) {
            String archiveUrl = artifacts.getJSONObject(0).getString("archive_download_url");
            HttpURLConnection dlConn = (HttpURLConnection) new URL(archiveUrl).openConnection();
            dlConn.setRequestMethod("GET");
            dlConn.setRequestProperty("Authorization", "token " + token);

            BufferedReader br = new BufferedReader(new InputStreamReader(dlConn.getInputStream()));
            String sshLink = br.readLine();
            br.close();
            return sshLink;
        }
        return null;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private String readErrorResponse(HttpURLConnection conn) throws Exception {
        if (conn.getErrorStream() == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void copyResult() {
        String text = resultText.getText().toString();
        if (text.isEmpty()) {
            showToast("متن‌ای برای کپی وجود ندارد");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Result", text);
        clipboard.setPrimaryClip(clip);
        showToast("📋 متن کپی شد!");
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }
}
