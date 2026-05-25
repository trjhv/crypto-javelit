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
        return "<a href=\"data:" + mimeType + ";base64," + b64 + "\" download=\"" + filename + "\" "
             + "style=\"display:inline-block;margin-top:8px;padding:9px 18px;"
             + "background:#1565c0;color:#fff;border-radius:6px;"
             + "text-decoration:none;font-weight:600;font-size:14px\">"
             + label + "</a>";
    }

    private static String copyableText(String text) {
        String escaped = text.replace("\\", "\\\\").replace("`", "\\`").replace("\"", "&quot;");
        return "<div style=\"position:relative;margin-top:6px\">"
             + "<button onclick=\"navigator.clipboard.writeText(`" + escaped + "`).then(()=>{"
             + "this.textContent='✅ Copied!';setTimeout(()=>this.textContent='📋 Copy',1500)})\" "
             + "style=\"position:absolute;top:6px;right:6px;padding:4px 10px;"
             + "background:#1565c0;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:12px\">"
             + "📋 Copy</button>"
             + "<pre style=\"background:#1e1e2e;color:#cdd6f4;padding:14px;"
             + "border-radius:6px;font-size:11px;overflow-x:auto;"
             + "white-space:pre-wrap;word-break:break-all;max-height:160px\">" + text + "</pre></div>";
    }

    private static String badge(String color, String label) {
        return "<span style=\"background:" + color + ";color:#fff;padding:3px 8px;"
             + "border-radius:4px;font-size:12px;font-weight:600\">" + label + "</span>";
    }

    // ── MAIN ──────────────────────────────────────────────────────────────────
    // KEY RULE: every widget must be rendered on EVERY run of main().
    // Never put widgets inside if/else — Javelit re-runs main() on each
    // interaction and will throw DuplicateWidgetIDException if the widget
    // tree changes shape between runs.
    // Show/hide OUTPUT (text, images, errors) inside if/else is fine.
    // ──────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {

        Jt.title("🔐 Crypto App").use();
        Jt.markdown("AES encryption & decryption for **text** and **images**. Enter a key, pick a tab.").use();
        Jt.divider().use();

        // ── Key row (always rendered) ─────────────────────────────────────────
        var keyRow = Jt.columns(2).use();                         // key: "key-row-cols"
        String aesMode  = Jt.radio("AES Strength",
                List.of("AES-128 (16-char key)", "AES-256 (32-char key)"))
                .use(keyRow.col(0));
        int keySize = aesMode.startsWith("AES-256") ? 32 : 16;

        String secretKey = Jt.textInput("🔑 Secret Key")
                .placeholder(keySize == 16 ? "mysecretkey12345" : "mysecretkey1234567890123456789012")
                .use(keyRow.col(1));

        // Key badge
        int keyLen = secretKey == null ? 0 : secretKey.length();
        String badgeColor = keyLen == 0 ? "#78909c"
                          : keyLen < keySize / 2 ? "#e53935"
                          : keyLen < keySize ? "#fb8c00"
                          : keyLen == keySize ? "#43a047"
                          : "#fb8c00";
        String badgeLabel = keyLen == 0 ? "no key" : keyLen < keySize ? "partial – will be padded"
                          : keyLen == keySize ? "perfect ✓" : "too long – trimmed";
        Jt.html(badge(badgeColor, badgeLabel)
                + " &nbsp;<small style='color:#888'>padded/trimmed to <b>" + keySize + " bytes</b></small>").use();

        boolean keyOk = secretKey != null && !secretKey.isBlank();
        if (!keyOk) Jt.warning("Enter a secret key above to begin.").use();

        Jt.divider().use();

        // ── Tabs ──────────────────────────────────────────────────────────────
        var tabs = Jt.tabs(List.of("📝 Text", "🖼️ Image")).use();

        // ══════════════════════════════════════════════════════════════════════
        // TEXT TAB — all widgets always rendered, output gated by button press
        // ══════════════════════════════════════════════════════════════════════
        var textTab = tabs.tab(0);

        // Both radio + both textAreas + both buttons ALWAYS rendered.
        // We hide the irrelevant pair using Javelit's labelVisibility trick:
        // actually we just always render and check the radio value.
        String textMode = Jt.radio("Text operation",
                List.of("🔒 Encrypt text", "🔓 Decrypt text"))
                .use(textTab);

        // Always render BOTH inputs (Javelit needs a stable widget tree).
        // We give them distinct labels so their keys never collide.
        String plainText = Jt.textArea("Plain text to encrypt")
                .placeholder("Type your secret message here…")
                .use(textTab);

        String cipherB64 = Jt.textArea("Encrypted Base64 to decrypt")
                .placeholder("Paste the Base64 cipher text here…")
                .use(textTab);

        // Always render BOTH buttons
        boolean encTextBtn = Jt.button("🔒 Encrypt Text").use(textTab);
        boolean decTextBtn = Jt.button("🔓 Decrypt Text").use(textTab);

        // Output — safe to gate in if/else because it is not a widget
        if (encTextBtn && textMode.startsWith("🔒")) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(textTab);
            } else if (plainText == null || plainText.isBlank()) {
                Jt.warning("Enter some text to encrypt.").use(textTab);
            } else {
                try {
                    byte[] enc = encrypt(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                         makeKey(secretKey, keySize));
                    String b64 = Base64.getEncoder().encodeToString(enc);
                    Jt.success("✅ Encrypted! (" + humanSize(plainText.length()) + " → " + humanSize(b64.length()) + ")").use(textTab);
                    Jt.subheader("Encrypted output (Base64):").use(textTab);
                    Jt.html(copyableText(b64)).use(textTab);
                    Jt.info("Switch to 'Decrypt text' mode and paste this to reverse.").use(textTab);
                } catch (Exception e) {
                    Jt.error("Encryption failed: " + e.getMessage()).use(textTab);
                }
            }
        }

        if (decTextBtn && textMode.startsWith("🔓")) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(textTab);
            } else if (cipherB64 == null || cipherB64.isBlank()) {
                Jt.warning("Paste the encrypted Base64 text in the second box above.").use(textTab);
            } else {
                try {
                    byte[] raw = Base64.getDecoder().decode(cipherB64.trim());
                    byte[] plain = decrypt(raw, makeKey(secretKey, keySize));
                    Jt.success("✅ Decrypted successfully!").use(textTab);
                    Jt.subheader("Decrypted text:").use(textTab);
                    Jt.text(new String(plain, java.nio.charset.StandardCharsets.UTF_8)).use(textTab);
                } catch (IllegalArgumentException e) {
                    Jt.error("Not valid Base64 – paste the full output from Encrypt.").use(textTab);
                } catch (Exception e) {
                    Jt.error("Decryption failed – wrong key or corrupted data. (" + e.getClass().getSimpleName() + ")").use(textTab);
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // IMAGE TAB — same pattern: all widgets always rendered
        // ══════════════════════════════════════════════════════════════════════
        var imgTab = tabs.tab(1);

        String imgMode = Jt.radio("Image operation",
                List.of("🔒 Encrypt image", "🔓 Decrypt image"))
                .use(imgTab);

        // Both uploaders always rendered with distinct labels
        Jt.markdown("**Upload image to encrypt** (PNG / JPG / BMP):").use(imgTab);
        List<JtUploadedFile> imgFiles = Jt.fileUploader("Select image file").use(imgTab);
        JtUploadedFile imgFile = imgFiles.isEmpty() ? null : imgFiles.getFirst();

        Jt.markdown("**Upload .enc file to decrypt:**").use(imgTab);
        List<JtUploadedFile> encFiles = Jt.fileUploader("Select enc file").use(imgTab);
        JtUploadedFile encFile = encFiles.isEmpty() ? null : encFiles.getFirst();

        // Both buttons always rendered
        boolean encImgBtn = Jt.button("🔒 Encrypt Image").use(imgTab);
        boolean decImgBtn = Jt.button("🔓 Decrypt Image").use(imgTab);

        // Output only
        if (encImgBtn && imgMode.startsWith("🔒")) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(imgTab);
            } else if (imgFile == null) {
                Jt.warning("Upload an image in the first uploader.").use(imgTab);
            } else {
                try {
                    byte[] imgBytes = imgFile.content();
                    byte[] encrypted = encrypt(imgBytes, makeKey(secretKey, keySize));

                    Jt.success("✅ Image encrypted!").use(imgTab);
                    Jt.markdown("**Original:** " + imgFile.filename() + " (" + humanSize(imgBytes.length) + ")").use(imgTab);
                    Jt.image(imgBytes).use(imgTab);
                    Jt.markdown("**Encrypted size:** " + humanSize(encrypted.length) + " — binary, not displayable.").use(imgTab);

                    String baseName = imgFile.filename().replaceAll("\\.[^.]+$", "");
                    Jt.html(downloadLink(encrypted, baseName + ".enc",
                            "application/octet-stream", "⬇️ Download " + baseName + ".enc")).use(imgTab);
                    Jt.info("Upload this .enc file (with the same key) using the second uploader to restore it.").use(imgTab);
                } catch (Exception e) {
                    Jt.error("Encryption failed: " + e.getMessage()).use(imgTab);
                }
            }
        }

        if (decImgBtn && imgMode.startsWith("🔓")) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(imgTab);
            } else if (encFile == null) {
                Jt.warning("Upload a .enc file in the second uploader.").use(imgTab);
            } else {
                try {
                    byte[] decrypted = decrypt(encFile.content(), makeKey(secretKey, keySize));
                    Jt.success("✅ Image decrypted! (" + humanSize(decrypted.length) + ")").use(imgTab);
                    Jt.subheader("Restored image:").use(imgTab);
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

        // ── Footer ────────────────────────────────────────────────────────────
        Jt.divider().use();
        Jt.markdown("> Built with [Javelit](https://javelit.io) 🚡 · AES-CBC · Random IV per operation").use();
    }
}
