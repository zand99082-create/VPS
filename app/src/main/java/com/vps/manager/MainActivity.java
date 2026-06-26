package com.vps.manager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText tokenInput, tailscaleInput;
    private TextView statusText, resultText;
    private Button copyBtn;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentSSH = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        tokenInput = findViewById(R.id.tokenInput);
        tailscaleInput = findViewById(R.id.tailscaleInput);
        statusText = findViewById(R.id.statusText);
        resultText = findViewById(R.id.resultText);
        copyBtn = findViewById(R.id.copyBtn);
        
        findViewById(R.id.oneClickBtn).setOnClickListener(v -> startOneClick());
        copyBtn.setOnClickListener(v -> copySSH());
    }

    private void startOneClick() {
        String token = tokenInput.getText().toString().trim();
        String tailscaleKey = tailscaleInput.getText().toString().trim();
        
        if (token.isEmpty()) {
            showToast("لطفاً توکن گیت‌هاب را وارد کنید");
            return;
        }
        if (tailscaleKey.isEmpty()) {
            showToast("لطفاً کلید Tailscale را وارد کنید");
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
                
                mainHandler.post(() -> statusText.setText("🔑 تنظیم Secret..."));
                setupTailscaleSecret(token, repo, tailscaleKey);
                
                mainHandler.post(() -> statusText.setText("📂 ایجاد workflow..."));
                createWorkflow(token, repo);
                
                mainHandler.post(() -> statusText.setText("🚀 اجرای VPS..."));
                String ssh = runWorkflow(token, repo);
                
                mainHandler.post(() -> {
                    statusText.setText("✅ VPS آماده است! 🎉");
                    resultText.setText("🔑 لینک اتصال SSH:\n\n" + ssh + 
                                      "\n\n📌 مخزن: " + repo);
                    currentSSH = ssh;
                    copyBtn.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                mainHandler.post(() -> statusText.setText("❌ خطا: " + e.getMessage()));
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
        
        if (conn.getResponseCode() == 201) {
            String response = readResponse(conn);
            return new JSONObject(response).getString("full_name");
        } else {
            throw new Exception("خطا در ایجاد مخزن: " + conn.getResponseCode());
        }
    }

    private void setupTailscaleSecret(String token, String repoFullName, String tailscaleKey) throws Exception {
        String url = "https://api.github.com/repos/" + repoFullName + "/actions/secrets/TAILSCALE_AUTHKEY";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "token " + token);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        String encodedKey = android.util.Base64.encodeToString(tailscaleKey.getBytes(), android.util.Base64.NO_WRAP);
        String json = "{\"encrypted_value\":\"" + encodedKey + "\",\"key_id\":\"\"}";
        
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(json);
        wr.flush();
        wr.close();
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 201 && responseCode != 204) {
            throw new Exception("خطا در تنظیم Secret: " + responseCode);
        }
    }

    private void createWorkflow(String token, String repo) throws Exception {
        String workflow = "name: VPS Creator\n" +
                         "on:\n" +
                         "  workflow_dispatch:\n" +
                         "jobs:\n" +
                         "  vps:\n" +
                         "    runs-on: ubuntu-latest\n" +
                         "    steps:\n" +
                         "      - name: Checkout\n" +
                         "        uses: actions/checkout@v4\n" +
                         "      - name: Start tmate\n" +
                         "        uses: mxschmitt/action-tmate@v3";
        
        String encoded = android.util.Base64.encodeToString(workflow.getBytes(), android.util.Base64.NO_WRAP);
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
            throw new Exception("خطا در ایجاد workflow: " + code);
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
        
        if (conn.getResponseCode() == 204) {
            return "✅ VPS workflow با موفقیت اجرا شد!\n" +
                   "📋 لینک SSH در لاگ‌های Actions ظاهر می‌شود.\n" +
                   "⏳ مدت زمان: 6 ساعت";
        } else {
            throw new Exception("خطا در اجرای workflow: " + conn.getResponseCode());
        }
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void copySSH() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("SSH", currentSSH);
        clipboard.setPrimaryClip(clip);
        showToast("📋 لینک SSH کپی شد!");
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }
}
