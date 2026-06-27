package com.vps.manager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyPairGenerator;
import java.security.KeyPair;

public class MainActivity extends Activity {
    private EditText tokenInput, tailscaleInput;
    private TextView statusText, resultText;
    private Button copyBtn;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String TAG = "VPSManager";

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
        copyBtn.setOnClickListener(v -> copyResult());
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
                    copyBtn.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                String errorMsg = "❌ خطا:\n" + e.getMessage() + "\n\n📋 برای کپی کردن، دکمه کپی را بزنید.";
                mainHandler.post(() -> {
                    statusText.setText("❌ خطا در اجرا");
                    resultText.setText(errorMsg);
                    copyBtn.setVisibility(View.VISIBLE);
                    Log.e(TAG, "Full error: ", e);
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

    private void setupTailscaleSecret(String token, String repoFullName, String tailscaleKey) throws Exception {
        String urlKey = "https://api.github.com/repos/" + repoFullName + "/actions/secrets/public-key";
        HttpURLConnection connKey = (HttpURLConnection) new URL(urlKey).openConnection();
        connKey.setRequestMethod("GET");
        connKey.setRequestProperty("Authorization", "token " + token);
        connKey.setRequestProperty("Accept", "application/vnd.github.v3+json");

        int keyResponse = connKey.getResponseCode();
        if (keyResponse != 200) {
            String err = readErrorResponse(connKey);
            throw new Exception("خطا در دریافت public key: " + keyResponse + "\n" + err);
        }

        String keyJson = readResponse(connKey);
        JSONObject keyObj = new JSONObject(keyJson);
        String publicKeyBase64 = keyObj.getString("key");
        String keyId = keyObj.getString("key_id");

        Log.d(TAG, "Public key (base64): " + publicKeyBase64);
        Log.d(TAG, "Key ID: " + keyId);

        String encryptedValue = encryptWithRSA(publicKeyBase64, tailscaleKey);

        String url = "https://api.github.com/repos/" + repoFullName + "/actions/secrets/TAILSCALE_AUTHKEY";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "token " + token);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = "{\"encrypted_value\":\"" + encryptedValue + "\",\"key_id\":\"" + keyId + "\"}";
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(json);
        wr.flush();
        wr.close();

        int responseCode = conn.getResponseCode();
        if (responseCode != 201 && responseCode != 204) {
            String err = readErrorResponse(conn);
            throw new Exception("خطا در تنظیم Secret: " + responseCode + "\n" + err);
        }
    }

    private String encryptWithRSA(String publicKeyBase64, String plaintext) throws Exception {
        try {
            byte[] keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);
            
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new Exception("خطا در رمزنگاری: " + e.getMessage(), e);
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
