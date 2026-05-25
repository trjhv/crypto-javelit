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

    // ── AES ───────────────────────────────────────────────────────────────────

    private static SecretKey makeKey(String raw, int size) {
        byte[] k = new byte[size];
        byte[] s = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(s, 0, k, 0, Math.min(s.length, size));
        return new SecretKeySpec(k, "AES");
    }

    private static byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] enc = c.doFinal(data);
        byte[] out = new byte[16 + enc.length];
        System.arraycopy(iv, 0, out, 0, 16);
        System.arraycopy(enc, 0, out, 16, enc.length);
        return out;
    }

    private static byte[] decrypt(byte[] data, SecretKey key) throws Exception {
        if (data.length < 17) throw new IllegalArgumentException("File too short — not a valid .enc file.");
        byte[] iv = new byte[16]; System.arraycopy(data, 0, iv, 0, 16);
        byte[] ct = new byte[data.length - 16]; System.arraycopy(data, 16, ct, 0, ct.length);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return c.doFinal(ct);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.2f MB", b / 1048576.0);
    }

    // Uses JS Blob URL — works reliably inside iframes unlike data: URIs
    private static String blobDownloadBtn(byte[] data, String filename, String label) {
        String b64 = Base64.getEncoder().encodeToString(data);
        String id  = "dl_" + Math.abs(filename.hashCode());
        return "<div style='margin-top:12px'>"
             + "<button id='" + id + "' onclick=\"(function(){"
             + "var b=atob('" + b64 + "');"
             + "var u=new Uint8Array(b.length);"
             + "for(var i=0;i<b.length;i++)u[i]=b.charCodeAt(i);"
             + "var blob=new Blob([u],{type:'application/octet-stream'});"
             + "var a=document.createElement('a');a.href=URL.createObjectURL(blob);"
             + "a.download='" + filename + "';document.body.appendChild(a);a.click();"
             + "setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},1000);"
             + "})()\" "
             + "style='padding:10px 20px;background:#1565c0;color:#fff;border:none;"
             + "border-radius:6px;font-size:14px;font-weight:600;cursor:pointer'>"
             + label + "</button></div>";
    }

    private static String copyBtn(String text) {
        String esc = text.replace("\\", "\\\\").replace("`", "\\`");
        return "<div style='position:relative;margin-top:8px'>"
             + "<button onclick=\"navigator.clipboard.writeText(`" + esc + "`).then(()=>{"
             + "this.textContent='✅ Copied!';setTimeout(()=>this.textContent='📋 Copy',1500)})\" "
             + "style='position:absolute;top:6px;right:6px;padding:4px 10px;"
             + "background:#1565c0;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:12px'>"
             + "📋 Copy</button>"
             + "<pre style='background:#1e1e2e;color:#cdd6f4;padding:14px;border-radius:6px;"
             + "font-size:11px;white-space:pre-wrap;word-break:break-all;max-height:160px'>"
             + text + "</pre></div>";
    }

    // ── MAIN ──────────────────────────────────────────────────────────────────
    //
    // JAVELIT RULE: the widget tree (every .use() call) must be IDENTICAL on
    // every run of main(). Conditional widgets = DuplicateWidgetIDException.
    //
    // Strategy:
    //   • ALL input widgets rendered unconditionally every run.
    //   • Mode switching done via selectbox (always rendered).
    //   • Irrelevant widgets hidden visually using labelVisibility("HIDDEN")
    //     and disabled(true) — they still exist in the tree.
    //   • Only non-widget OUTPUT (success/error/image/html) goes in if/else.
    //
    // ──────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {

        Jt.title("🔐 Crypto App").use();
        Jt.markdown("AES-128 / AES-256 encryption & decryption for **text** and **images**.").use();
        Jt.divider().use();

        // ── Global key config (always rendered) ───────────────────────────────
        var keyRow = Jt.columns(2).use();

        String aesChoice = Jt.radio("AES Strength",
                List.of("AES-128  (16-char key)", "AES-256  (32-char key)"))
                .use(keyRow.col(0));
        int keySize = aesChoice.startsWith("AES-256") ? 32 : 16;

        String secretKey = Jt.textInput("🔑 Secret Key")
                .placeholder(keySize == 16 ? "mysecretkey12345" : "mysecretkey1234567890123456789012")
                .use(keyRow.col(1));

        boolean keyOk = secretKey != null && !secretKey.isBlank();
        if (!keyOk) Jt.warning("Enter a secret key to enable encryption / decryption.").use();

        Jt.divider().use();

        // ── Tabs ──────────────────────────────────────────────────────────────
        var tabs = Jt.tabs(List.of("📝 Text", "🖼️ Image")).use();

        // ══════════════════════════════════════════════════════════════════════
        // TEXT TAB
        // Mode is chosen via selectbox — both text areas always rendered but
        // the irrelevant one is visually hidden. Only one button shown at a time
        // via labelVisibility. Output in if/else (non-widget = safe).
        // ══════════════════════════════════════════════════════════════════════
        var T = tabs.tab(0);

        // Mode selector — always rendered, drives which action fires
        String textMode = Jt.selectbox("Text mode",
                List.of("🔒 Encrypt", "🔓 Decrypt"))
                .use(T);

        boolean textEncMode = textMode.startsWith("🔒");

        // Encrypt input — hidden (but still in tree) when in Decrypt mode
        String plainText = Jt.textArea("Message to encrypt")
                .placeholder("Type your secret message here…")
                .labelVisibility(textEncMode ? "VISIBLE" : "HIDDEN")
                .use(T);

        // Decrypt input — hidden when in Encrypt mode
        String cipherIn = Jt.textArea("Paste encrypted Base64 here")
                .placeholder("Paste the Base64 cipher text here…")
                .labelVisibility(textEncMode ? "HIDDEN" : "VISIBLE")
                .use(T);

        // Single action button — label changes with mode
        boolean textGo = Jt.button(textEncMode ? "🔒 Encrypt" : "🔓 Decrypt").use(T);

        // Output (non-widget — safe inside if/else)
        if (textGo) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(T);
            } else if (textEncMode) {
                if (plainText == null || plainText.isBlank()) {
                    Jt.warning("Enter some text to encrypt.").use(T);
                } else {
                    try {
                        byte[] enc = encrypt(
                                plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                makeKey(secretKey, keySize));
                        String b64 = Base64.getEncoder().encodeToString(enc);
                        Jt.success("✅ Encrypted! (" + humanSize(plainText.length())
                                 + " → " + humanSize(b64.length()) + ")").use(T);
                        Jt.subheader("Encrypted output (Base64):").use(T);
                        Jt.html(copyBtn(b64)).use(T);
                        Jt.info("Switch mode to 🔓 Decrypt, paste the text above, use the same key.").use(T);
                    } catch (Exception e) {
                        Jt.error("Encryption error: " + e.getMessage()).use(T);
                    }
                }
            } else {
                if (cipherIn == null || cipherIn.isBlank()) {
                    Jt.warning("Paste the Base64 encrypted text in the box above.").use(T);
                } else {
                    try {
                        byte[] raw = Base64.getDecoder().decode(cipherIn.trim());
                        byte[] plain = decrypt(raw, makeKey(secretKey, keySize));
                        Jt.success("✅ Decrypted successfully!").use(T);
                        Jt.subheader("Decrypted message:").use(T);
                        Jt.text(new String(plain, java.nio.charset.StandardCharsets.UTF_8)).use(T);
                    } catch (IllegalArgumentException e) {
                        Jt.error("Not valid Base64 — paste the full output from Encrypt mode.").use(T);
                    } catch (Exception e) {
                        Jt.error("Decryption failed — wrong key or wrong AES mode? ("
                                + e.getClass().getSimpleName() + ")").use(T);
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // IMAGE TAB
        // Same pattern — mode via selectbox, both uploaders always in tree,
        // irrelevant one hidden. Single action button. Output in if/else.
        // ══════════════════════════════════════════════════════════════════════
        var I = tabs.tab(1);

        String imgMode = Jt.selectbox("Image mode",
                List.of("🔒 Encrypt", "🔓 Decrypt"))
                .use(I);

        boolean imgEncMode = imgMode.startsWith("🔒");

        // Uploader 1 — for image to encrypt (hidden in decrypt mode)
        List<JtUploadedFile> imgUploads = Jt.fileUploader("Image to encrypt  (PNG / JPG / BMP)")
                .labelVisibility(imgEncMode ? "VISIBLE" : "HIDDEN")
                .use(I);
        JtUploadedFile imgFile = imgUploads.isEmpty() ? null : imgUploads.getFirst();

        // Uploader 2 — for .enc file to decrypt (hidden in encrypt mode)
        List<JtUploadedFile> encUploads = Jt.fileUploader("Encrypted .enc file to decrypt")
                .labelVisibility(imgEncMode ? "HIDDEN" : "VISIBLE")
                .use(I);
        JtUploadedFile encFile = encUploads.isEmpty() ? null : encUploads.getFirst();

        // Single action button
        boolean imgGo = Jt.button(imgEncMode ? "🔒 Encrypt Image" : "🔓 Decrypt Image").use(I);

        // Output (non-widget — safe inside if/else)
        if (imgGo) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(I);
            } else if (imgEncMode) {
                if (imgFile == null) {
                    Jt.warning("Upload an image using the uploader above.").use(I);
                } else {
                    try {
                        byte[] imgBytes = imgFile.content();
                        byte[] encrypted = encrypt(imgBytes, makeKey(secretKey, keySize));
                        String base = imgFile.filename().replaceAll("\\.[^.]+$", "");

                        Jt.success("✅ Image encrypted!  "
                                 + humanSize(imgBytes.length) + " → " + humanSize(encrypted.length)).use(I);
                        Jt.subheader("Original image:").use(I);
                        Jt.image(imgBytes).use(I);
                        Jt.info("Encrypted file is binary — not displayable. Click below to download.").use(I);
                        // Blob-based download — reliable inside Railway iframe
                        Jt.html(blobDownloadBtn(encrypted, base + ".enc",
                                "⬇️ Download  " + base + ".enc")).use(I);
                        Jt.info("Upload the .enc file (with the same key) in Decrypt mode to restore the image.").use(I);
                    } catch (Exception e) {
                        Jt.error("Encryption error: " + e.getMessage()).use(I);
                    }
                }
            } else {
                if (encFile == null) {
                    Jt.warning("Upload a .enc file using the uploader above.").use(I);
                } else {
                    try {
                        byte[] decrypted = decrypt(encFile.content(), makeKey(secretKey, keySize));
                        String base = encFile.filename().replaceAll("\\.enc$", "");
                        if (!base.matches(".*\\.(png|jpg|jpeg|bmp|gif|webp)$")) base += ".png";

                        Jt.success("✅ Image decrypted!  (" + humanSize(decrypted.length) + ")").use(I);
                        Jt.subheader("Restored image:").use(I);
                        Jt.image(decrypted).use(I);
                        Jt.html(blobDownloadBtn(decrypted, base, "⬇️ Download  " + base)).use(I);
                    } catch (IllegalArgumentException e) {
                        Jt.error("Invalid file: " + e.getMessage()).use(I);
                    } catch (Exception e) {
                        Jt.error("Decryption failed — wrong key or wrong AES mode? ("
                                + e.getClass().getSimpleName() + ")").use(I);
                    }
                }
            }
        }

        // ── Footer ────────────────────────────────────────────────────────────
        Jt.divider().use();
        Jt.markdown("> Built with [Javelit](https://javelit.io) · AES-CBC · Random IV per operation").use();
    }
}
