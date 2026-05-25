///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.javelit:javelit:0.86.0

import io.javelit.core.Jt;
import io.javelit.core.UploadedFile;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Crypto App – AES-128 / AES-256 Text & Image Encryption/Decryption
 * Built with Javelit (Streamlit for Java)
 * Run:    javelit run App.java
 * Deploy: push to GitHub → Railway one-click
 */
public class App {

    // ──────────────────────────────────────────────────────────────────────────
    // AES HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Build a SecretKey from the user's raw string.
     * keySizeBytes = 16 (AES-128) or 32 (AES-256).
     * Shorter keys are zero-padded; longer keys are trimmed.
     */
    private static SecretKey makeKey(String raw, int keySizeBytes) {
        byte[] keyBytes = new byte[keySizeBytes];
        byte[] src = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, keySizeBytes));
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypt bytes with AES/CBC/PKCS5Padding.
     * Format: [ 16-byte IV ][ cipher bytes ]
     */
    private static byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data);
        byte[] result = new byte[16 + encrypted.length];
        System.arraycopy(iv, 0, result, 0, 16);
        System.arraycopy(encrypted, 0, result, 16, encrypted.length);
        return result;
    }

    /**
     * Decrypt bytes produced by encrypt().
     * Expects: [ 16-byte IV ][ cipher bytes ]
     */
    private static byte[] decrypt(byte[] data, SecretKey key) throws Exception {
        if (data.length < 17) throw new IllegalArgumentException("Data too short – not a valid .enc file.");
        byte[] iv = new byte[16];
        System.arraycopy(data, 0, iv, 0, 16);
        byte[] cipherText = new byte[data.length - 16];
        System.arraycopy(data, 16, cipherText, 0, cipherText.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(cipherText);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UTILITY
    // ──────────────────────────────────────────────────────────────────────────

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    /** Render a download link via an HTML <a> tag with a data URI. */
    private static String downloadLink(byte[] data, String filename, String mimeType, String label) {
        String b64 = Base64.getEncoder().encodeToString(data);
        return """
            <a href="data:%s;base64,%s" download="%s"
               style="display:inline-block;margin-top:8px;padding:9px 18px;
                      background:#1565c0;color:#fff;border-radius:6px;
                      text-decoration:none;font-weight:600;font-size:14px">
               %s
            </a>
            """.formatted(mimeType, b64, filename, label);
    }

    /** Render a copy-to-clipboard button + pre for encrypted text. */
    private static String copyableText(String text) {
        // escape for JS string literal
        String escaped = text.replace("\\", "\\\\").replace("`", "\\`");
        return """
            <div style="position:relative;margin-top:6px">
              <button onclick="navigator.clipboard.writeText(`%s`).then(()=>{
                    this.textContent='✅ Copied!';
                    setTimeout(()=>this.textContent='📋 Copy',1500)
                  })"
                style="position:absolute;top:6px;right:6px;padding:4px 10px;
                       background:#1565c0;color:#fff;border:none;border-radius:4px;
                       cursor:pointer;font-size:12px">
                📋 Copy
              </button>
              <pre style="background:#1e1e2e;color:#cdd6f4;padding:14px;
                          border-radius:6px;font-size:11px;overflow-x:auto;
                          white-space:pre-wrap;word-break:break-all;max-height:160px">%s</pre>
            </div>
            """.formatted(escaped, text);
    }

    /** Key-strength badge: color + label based on char count vs AES key size. */
    private static String keyStrengthBadge(int len, int needed) {
        String color, label;
        if (len == 0)          { color = "#78909c"; label = "no key entered"; }
        else if (len < needed / 2) { color = "#e53935"; label = "weak (too short)"; }
        else if (len < needed)     { color = "#fb8c00"; label = "partial (will be padded)"; }
        else if (len == needed)    { color = "#43a047"; label = "perfect length ✓"; }
        else                       { color = "#fb8c00"; label = "too long (will be trimmed to " + needed + " chars)"; }
        return """
            <span style="background:%s;color:#fff;padding:3px 8px;border-radius:4px;
                          font-size:12px;font-weight:600">%s</span>
            """.formatted(color, label);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MAIN APP
    // ──────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {

        // ── Header ────────────────────────────────────────────────────────────
        Jt.title("🔐 Crypto App").use();
        Jt.markdown("""
            AES encryption & decryption for **text** and **images**.  
            Choose your key strength, enter a key, then encrypt or decrypt.
            """).use();
        Jt.divider("header-div").use();

        // ── Key configuration ─────────────────────────────────────────────────
        var cols = Jt.columns(2).use();

        String aesMode = Jt.radio("AES Strength", List.of("AES-128 (16-char key)", "AES-256 (32-char key)"))
                .use(cols.col(0));
        int keySize = aesMode.startsWith("AES-256") ? 32 : 16;

        String secretKey = Jt.textInput("🔑 Secret Key")
                .placeholder(keySize == 16 ? "mysecretkey12345" : "mysecretkey1234567890123456789012")
                .use(cols.col(1));

        // Key strength indicator
        int keyLen = (secretKey == null) ? 0 : secretKey.length();
        Jt.html(keyStrengthBadge(keyLen, keySize) +
                " &nbsp;<small style='color:#888'>Key will be padded/trimmed to <b>" + keySize + " bytes</b></small>"
        ).use();

        boolean keyOk = secretKey != null && !secretKey.isBlank();
        if (!keyOk) {
            Jt.warning("⚠️  Enter a secret key above to start.").use();
        }

        Jt.divider("tabs-div").use();

        // ── Tabs ──────────────────────────────────────────────────────────────
        var tabs = Jt.tabs(List.of("📝 Text", "🖼️ Image")).use();

        // ══════════════════════════════════════════════════════════════════════
        // TAB 1 – TEXT
        // ══════════════════════════════════════════════════════════════════════
        var textTab = tabs.tab(0);

        var textMode = Jt.radio("Mode", List.of("🔒 Encrypt", "🔓 Decrypt")).use(textTab);
        boolean isTextEncrypt = textMode.startsWith("🔒");

        if (isTextEncrypt) {

            String plainText = Jt.textArea("Plain text to encrypt")
                    .placeholder("Type your secret message here…")
                    .use(textTab);

            boolean go = Jt.button("🔒 Encrypt Text").use(textTab);

            if (go) {
                if (!keyOk) {
                    Jt.error("Enter a secret key first.").use(textTab);
                } else if (plainText == null || plainText.isBlank()) {
                    Jt.warning("Enter some text to encrypt.").use(textTab);
                } else {
                    try {
                        SecretKey key = makeKey(secretKey, keySize);
                        byte[] encrypted = encrypt(
                                plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8), key);
                        String b64 = Base64.getEncoder().encodeToString(encrypted);

                        Jt.success("✅ Encrypted successfully! (%s → %s)"
                                .formatted(humanSize(plainText.length()), humanSize(b64.length()))).use(textTab);
                        Jt.subheader("Encrypted output (Base64):").use(textTab);
                        Jt.html(copyableText(b64)).use(textTab);
                        Jt.info("💡 Copy this output and paste it in Decrypt mode to reverse the operation.").use(textTab);

                    } catch (Exception e) {
                        Jt.error("Encryption failed: " + e.getMessage()).use(textTab);
                    }
                }
            }

        } else { // Decrypt text

            String cipherB64 = Jt.textArea("Encrypted text (Base64)")
                    .placeholder("Paste the Base64-encoded cipher text here…")
                    .use(textTab);

            boolean go = Jt.button("🔓 Decrypt Text").use(textTab);

            if (go) {
                if (!keyOk) {
                    Jt.error("Enter a secret key first.").use(textTab);
                } else if (cipherB64 == null || cipherB64.isBlank()) {
                    Jt.warning("Paste the encrypted Base64 text above.").use(textTab);
                } else {
                    try {
                        SecretKey key = makeKey(secretKey, keySize);
                        byte[] cipherBytes = Base64.getDecoder().decode(cipherB64.trim());
                        byte[] plain = decrypt(cipherBytes, key);
                        String result = new String(plain, java.nio.charset.StandardCharsets.UTF_8);

                        Jt.success("✅ Decrypted successfully!").use(textTab);
                        Jt.subheader("Decrypted text:").use(textTab);
                        Jt.text(result).use(textTab);

                    } catch (IllegalArgumentException e) {
                        Jt.error("Invalid Base64 – make sure you pasted the full encrypted output.").use(textTab);
                    } catch (Exception e) {
                        Jt.error("Decryption failed – wrong key or corrupted data. (" + e.getClass().getSimpleName() + ")").use(textTab);
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // TAB 2 – IMAGE
        // ══════════════════════════════════════════════════════════════════════
        var imgTab = tabs.tab(1);

        var imgMode = Jt.radio("Mode", List.of("🔒 Encrypt", "🔓 Decrypt")).use(imgTab);
        boolean isImgEncrypt = imgMode.startsWith("🔒");

        if (isImgEncrypt) {

            // ── Encrypt image ─────────────────────────────────────────────────
            Jt.info("📂 Upload a PNG, JPG, or BMP. The encrypted file downloads as **image.enc** — upload it in Decrypt mode to restore.").use(imgTab);

            UploadedFile imgFile = Jt.fileUploader("Upload image to encrypt").use(imgTab);

            if (imgFile != null) {
                Jt.markdown("**File:** `" + imgFile.name() + "` · " + humanSize(imgFile.bytes().length)).use(imgTab);
            }

            boolean go = Jt.button("🔒 Encrypt Image").use(imgTab);

            if (go) {
                if (!keyOk) {
                    Jt.error("Enter a secret key first.").use(imgTab);
                } else if (imgFile == null) {
                    Jt.warning("Upload an image first.").use(imgTab);
                } else {
                    try {
                        byte[] imgBytes = imgFile.bytes();
                        SecretKey key = makeKey(secretKey, keySize);
                        byte[] encrypted = encrypt(imgBytes, key);

                        // Side-by-side: original image + result info
                        var imgCols = Jt.columns(2).use(imgTab);

                        Jt.subheader("Original").use(imgCols.col(0));
                        Jt.image(imgBytes).use(imgCols.col(0));
                        Jt.text(humanSize(imgBytes.length)).use(imgCols.col(0));

                        Jt.subheader("Encrypted").use(imgCols.col(1));
                        Jt.markdown("""
                            The encrypted file is **binary** — it cannot be displayed as an image.  
                            Download it below and keep it safe.
                            """).use(imgCols.col(1));
                        Jt.text("Encrypted size: " + humanSize(encrypted.length)).use(imgCols.col(1));

                        Jt.success("✅ Image encrypted!").use(imgTab);

                        // Download .enc file
                        String baseName = imgFile.name().replaceAll("\\.[^.]+$", "");
                        Jt.html(downloadLink(encrypted, baseName + ".enc",
                                "application/octet-stream", "⬇️ Download " + baseName + ".enc")).use(imgTab);

                        Jt.info("💡 Upload the `.enc` file (with the same key) in **Decrypt** mode to get the original image back.").use(imgTab);

                    } catch (Exception e) {
                        Jt.error("Encryption failed: " + e.getMessage()).use(imgTab);
                    }
                }
            }

        } else {

            // ── Decrypt image ─────────────────────────────────────────────────
            Jt.info("📂 Upload the **`.enc` file** produced by the Encrypt step. Use the same key that was used to encrypt.").use(imgTab);

            UploadedFile encFile = Jt.fileUploader("Upload .enc file to decrypt").use(imgTab);

            if (encFile != null) {
                Jt.markdown("**File:** `" + encFile.name() + "` · " + humanSize(encFile.bytes().length)).use(imgTab);
            }

            boolean go = Jt.button("🔓 Decrypt Image").use(imgTab);

            if (go) {
                if (!keyOk) {
                    Jt.error("Enter a secret key first.").use(imgTab);
                } else if (encFile == null) {
                    Jt.warning("Upload an .enc file first.").use(imgTab);
                } else {
                    try {
                        SecretKey key = makeKey(secretKey, keySize);
                        byte[] decrypted = decrypt(encFile.bytes(), key);

                        Jt.success("✅ Image decrypted! (" + humanSize(decrypted.length) + ")").use(imgTab);
                        Jt.subheader("Decrypted image:").use(imgTab);
                        Jt.image(decrypted).use(imgTab);

                        // Download restored image
                        String baseName = encFile.name().replaceAll("\\.enc$", "");
                        if (!baseName.matches(".*\\.(png|jpg|jpeg|bmp|gif|webp)$")) baseName += ".png";
                        Jt.html(downloadLink(decrypted, baseName, "image/png", "⬇️ Download " + baseName)).use(imgTab);

                    } catch (IllegalArgumentException e) {
                        Jt.error("Invalid file – is this a .enc file from the Encrypt step? " + e.getMessage()).use(imgTab);
                    } catch (Exception e) {
                        Jt.error("Decryption failed – wrong key, wrong AES mode, or file is corrupted. ("
                                + e.getClass().getSimpleName() + ")").use(imgTab);
                    }
                }
            }
        }

        // ── Footer ────────────────────────────────────────────────────────────
        Jt.divider("footer").use();
        Jt.markdown("""
            > Built with [Javelit](https://javelit.io) 🚡 · AES-CBC · Random IV per operation · [Source on GitHub](#)
            """).use();
    }
}
