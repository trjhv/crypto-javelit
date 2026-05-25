///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.javelit:javelit:0.86.0

import io.javelit.core.Jt;
import io.javelit.core.JtUploadedFile;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

public class App {

    // ── AES HELPERS ───────────────────────────────────────────────────────────

    private static SecretKey makeKey(String raw, int keySizeBytes) {
        byte[] keyBytes = new byte[keySizeBytes];
        byte[] src = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, keySizeBytes));
        return new SecretKeySpec(keyBytes, "AES");
    }

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

    // ── UTILITY ───────────────────────────────────────────────────────────────

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

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

    private static String copyableText(String text) {
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

    private static String keyStrengthBadge(int len, int needed) {
        String color, label;
        if (len == 0)              { color = "#78909c"; label = "no key entered"; }
        else if (len < needed / 2) { color = "#e53935"; label = "weak (too short)"; }
        else if (len < needed)     { color = "#fb8c00"; label = "partial (will be padded)"; }
        else if (len == needed)    { color = "#43a047"; label = "perfect length ✓"; }
        else                       { color = "#fb8c00"; label = "too long (trimmed to " + needed + " chars)"; }
        return """
            <span style="background:%s;color:#fff;padding:3px 8px;border-radius:4px;
                          font-size:12px;font-weight:600">%s</span>
            """.formatted(color, label);
    }

    // ── MAIN ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {

        Jt.title("🔐 Crypto App").use();
        Jt.markdown("""
            AES encryption & decryption for **text** and **images**.  
            Choose your key strength, enter a key, then encrypt or decrypt.
            """).use();
        Jt.divider().use();

        // ── Key configuration ─────────────────────────────────────────────────
        var cols = Jt.columns(2).use();

        String aesMode = Jt.radio("AES Strength", List.of("AES-128 (16-char key)", "AES-256 (32-char key)"))
                .use(cols.col(0));
        int keySize = aesMode.startsWith("AES-256") ? 32 : 16;

        String secretKey = Jt.textInput("🔑 Secret Key")
                .placeholder(keySize == 16 ? "mysecretkey12345" : "mysecretkey1234567890123456789012")
                .use(cols.col(1));

        int keyLen = (secretKey == null) ? 0 : secretKey.length();
        Jt.html(keyStrengthBadge(keyLen, keySize) +
                " &nbsp;<small style='color:#888'>Key padded/trimmed to <b>" + keySize + " bytes</b></small>"
        ).use();

        boolean keyOk = secretKey != null && !secretKey.isBlank();
        if (!keyOk) {
            Jt.warning("⚠️  Enter a secret key above to start.").use();
        }

        Jt.divider().use();

        // ── Tabs ──────────────────────────────────────────────────────────────
        var tabs = Jt.tabs(List.of("📝 Text", "🖼️ Image")).use();

        // ══════════════════════════════════════════════════════════════════════
        // TAB 1 – TEXT
        // FIX: unique label "Text Mode" avoids key collision with "Image Mode"
        // ══════════════════════════════════════════════════════════════════════
        var textTab = tabs.tab(0);

        String textMode = Jt.radio("Text Mode", List.of("🔒 Encrypt", "🔓 Decrypt"))
                .use(textTab);
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
                        Jt.success("✅ Encrypted! (%s → %s)"
                                .formatted(humanSize(plainText.length()), humanSize(b64.length()))).use(textTab);
                        Jt.subheader("Encrypted output (Base64):").use(textTab);
                        Jt.html(copyableText(b64)).use(textTab);
                        Jt.info("💡 Copy this and paste it in Decrypt mode to reverse.").use(textTab);
                    } catch (Exception e) {
                        Jt.error("Encryption failed: " + e.getMessage()).use(textTab);
                    }
                }
            }

        } else {

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
                        Jt.error("Invalid Base64 – paste the full encrypted output.").use(textTab);
                    } catch (Exception e) {
                        Jt.error("Decryption failed – wrong key or corrupted data. (" + e.getClass().getSimpleName() + ")").use(textTab);
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // TAB 2 – IMAGE
        // FIX: unique label "Image Mode" avoids key collision with "Text Mode"
        // ══════════════════════════════════════════════════════════════════════
        var imgTab = tabs.tab(1);

        String imgMode = Jt.radio("Image Mode", List.of("🔒 Encrypt", "🔓 Decrypt"))
                .use(imgTab);
        boolean isImgEncrypt = imgMode.startsWith("🔒");

        if (isImgEncrypt) {

            Jt.info("Upload a PNG, JPG, or BMP. Encrypted file downloads as .enc — upload it in Decrypt mode to restore.").use(imgTab);

            // FIX: fileUploader returns List<JtUploadedFile>, not a single object
            List<JtUploadedFile> imgFiles = Jt.fileUploader("Upload image to encrypt").use(imgTab);
            JtUploadedFile imgFile = imgFiles.isEmpty() ? null : imgFiles.getFirst();

            if (imgFile != null) {
                // FIX: .filename() not .name(), .content() not .bytes()
                Jt.markdown("**File:** `" + imgFile.filename() + "` · " + humanSize(imgFile.content().length)).use(imgTab);
            }

            boolean go = Jt.button("🔒 Encrypt Image").use(imgTab);

            if (go) {
                if (!keyOk) {
                    Jt.error("Enter a secret key first.").use(imgTab);
                } else if (imgFile == null) {
                    Jt.warning("Upload an image first.").use(imgTab);
                } else {
                    try {
                        byte[] imgBytes = imgFile.content();
                        SecretKey key = makeKey(secretKey, keySize);
                        byte[] encrypted = encrypt(imgBytes, key);

                        var imgCols = Jt.columns(2).use(imgTab);
                        Jt.subheader("Original").use(imgCols.col(0));
                        Jt.image(imgBytes).use(imgCols.col(0));
                        Jt.text(humanSize(imgBytes.length)).use(imgCols.col(0));

                        Jt.subheader("Encrypted").use(imgCols.col(1));
                        Jt.markdown("Binary data — not displayable. Download and keep safe.").use(imgCols.col(1));
                        Jt.text("Size: " + humanSize(encrypted.length)).use(imgCols.col(1));

                        Jt.success("✅ Image encrypted!").use(imgTab);
                        String baseName = imgFile.filename().replaceAll("\\.[^.]+$", "");
                        Jt.html(downloadLink(encrypted, baseName + ".enc",
                                "application/octet-stream", "⬇️ Download " + baseName + ".enc")).use(imgTab);
                        Jt.info("💡 Upload this .enc file (same key) in Decrypt mode to restore the image.").use(imgTab);

                    } catch (Exception e) {
                        Jt.error("Encryption failed: " + e.getMessage()).use(imgTab);
                    }
                }
            }

        } else {

            Jt.info("Upload the .enc file from the Encrypt step. Use the exact same key.").use(imgTab);

            // FIX: fileUploader returns List<JtUploadedFile>
            List<JtUploadedFile> encFiles = Jt.fileUploader("Upload .enc file to decrypt").use(imgTab);
            JtUploadedFile encFile = encFiles.isEmpty() ? null : encFiles.getFirst();

            if (encFile != null) {
                Jt.markdown("**File:** `" + encFile.filename() + "` · " + humanSize(encFile.content().length)).use(imgTab);
            }

            boolean go = Jt.button("🔓 Decrypt Image").use(imgTab);

            if (go) {
                if (!keyOk) {
                    Jt.error("Enter a secret key first.").use(imgTab);
                } else if (encFile == null) {
                    Jt.warning("Upload a .enc file first.").use(imgTab);
                } else {
                    try {
                        SecretKey key = makeKey(secretKey, keySize);
                        byte[] decrypted = decrypt(encFile.content(), key);

                        Jt.success("✅ Image decrypted! (" + humanSize(decrypted.length) + ")").use(imgTab);
                        Jt.subheader("Decrypted image:").use(imgTab);
                        Jt.image(decrypted).use(imgTab);

                        String baseName = encFile.filename().replaceAll("\\.enc$", "");
                        if (!baseName.matches(".*\\.(png|jpg|jpeg|bmp|gif|webp)$")) baseName += ".png";
                        Jt.html(downloadLink(decrypted, baseName, "image/png", "⬇️ Download " + baseName)).use(imgTab);

                    } catch (IllegalArgumentException e) {
                        Jt.error("Invalid file: " + e.getMessage()).use(imgTab);
                    } catch (Exception e) {
                        Jt.error("Decryption failed – wrong key or wrong AES mode. (" + e.getClass().getSimpleName() + ")").use(imgTab);
                    }
                }
            }
        }

        // ── Footer ────────────────────────────────────────────────────────────
        Jt.divider().use();
        Jt.markdown("> Built with [Javelit](https://javelit.io) 🚡 · AES-CBC · Random IV per operation").use();
    }
}
