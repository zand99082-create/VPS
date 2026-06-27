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

        // مخفی کردن فیلد Tailscale (دیگه نیاز نیست)
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
                    resultText.setText("🔑 لینک اتصال SSH:\n\n" + ssh +
                            "\n\n📌 مخزن: " + repo);
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
                "  workflow_dispatch:\n" +
                "jobs:\n" +
                "  vps:\n" +
                "    runs-on: ubuntu-latest\n" +
                "    steps:\n" +
                "      - name: Checkout\n" +
                "        uses: actions/checkout@v4\n" +
                "      - name: Install Tailscale\n" +
                "        run: |\n" +
                "          curl -fsSL https://tailscale.com/install.sh | sh\n" +
                "          sudo tailscale up --auth-key ${{ secrets.TAILSCALE_AUTHKEY }}\n" +
                "      - name: Start tmate\n" +
                "        uses: mxschmitt/action-tmate@v3\n" +
                "        with:\n" +
                "          limit-access-to-actor: true";

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
            return "✅ VPS workflow با موفقیت اجرا شد!\n" +
                    "📋 لینک SSH در لاگ‌های Actions ظاهر می‌شود.\n" +
                    "⏳ مدت زمان: 6 ساعت";
        } else {
            String err = readErrorResponse(conn);
            throw new Exception("خطا در اجرای workflow: " + code + "\n" + err);
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
