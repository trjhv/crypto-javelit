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
        if (data.length < 17)
            throw new IllegalArgumentException("File too short — not a valid .enc file.");
        byte[] iv = new byte[16];
        System.arraycopy(data, 0, iv, 0, 16);
        byte[] ct = new byte[data.length - 16];
        System.arraycopy(data, 16, ct, 0, ct.length);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return c.doFinal(ct);
    }

    // ── UTILS ─────────────────────────────────────────────────────────────────

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.2f MB", b / 1048576.0);
    }

    // Blob-based download — works inside Railway iframe (data: URIs are blocked)
    private static String blobDownload(byte[] data, String filename) {
        String b64 = Base64.getEncoder().encodeToString(data);
        return "<button onclick=\"(function(){"
             + "var b=atob('" + b64 + "'),"
             + "u=new Uint8Array(b.length);"
             + "for(var i=0;i<b.length;i++)u[i]=b.charCodeAt(i);"
             + "var a=document.createElement('a');"
             + "a.href=URL.createObjectURL(new Blob([u],{type:'application/octet-stream'}));"
             + "a.download='" + filename + "';"
             + "document.body.appendChild(a);a.click();"
             + "setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},1000)"
             + "})()\" style=\"margin-top:12px;padding:10px 22px;background:#1565c0;"
             + "color:#fff;border:none;border-radius:6px;font-size:14px;"
             + "font-weight:600;cursor:pointer\">⬇️ Download " + filename + "</button>";
    }

    private static String copyBtn(String text) {
        String esc = text.replace("\\", "\\\\").replace("`", "\\`");
        return "<div style='position:relative;margin-top:8px'>"
             + "<button onclick=\"navigator.clipboard.writeText(`" + esc + "`)"
             + ".then(()=>{this.textContent='✅ Copied!';"
             + "setTimeout(()=>this.textContent='📋 Copy',1500)})\" "
             + "style='position:absolute;top:6px;right:6px;padding:4px 10px;"
             + "background:#1565c0;color:#fff;border:none;border-radius:4px;"
             + "cursor:pointer;font-size:12px'>📋 Copy</button>"
             + "<pre style='background:#1e1e2e;color:#cdd6f4;padding:14px;"
             + "border-radius:6px;font-size:11px;white-space:pre-wrap;"
             + "word-break:break-all;max-height:180px'>" + text + "</pre></div>";
    }

    // ── MAIN ──────────────────────────────────────────────────────────────────
    //
    // JAVELIT GOLDEN RULE: every .use() call must appear on EVERY run of main()
    // in the exact same order. Never put .use() inside if/else.
    // Only put non-widget OUTPUT (text, html, image, success, error) in if/else.
    //
    // ──────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {

        Jt.title("🔐 Crypto App").use();
        Jt.markdown("AES encryption & decryption for **text** and **images**.").use();
        Jt.divider().use();

        // ── Key config (always rendered) ──────────────────────────────────────
        var keyRow = Jt.columns(2).use();
        String aesChoice = Jt.radio("AES Strength",
                List.of("AES-128 (16-char key)", "AES-256 (32-char key)"))
                .use(keyRow.col(0));
        int keySize = aesChoice.startsWith("AES-256") ? 32 : 16;
        String secretKey = Jt.textInput("🔑 Secret Key")
                .placeholder(keySize == 16 ? "mysecretkey12345" : "mysecretkey1234567890123456789012")
                .use(keyRow.col(1));
        boolean keyOk = secretKey != null && !secretKey.isBlank();
        if (!keyOk) Jt.warning("Enter a secret key to begin.").use();

        Jt.divider().use();

        // ── Tabs ──────────────────────────────────────────────────────────────
        var tabs = Jt.tabs(List.of("📝 Text", "🖼️ Image")).use();

        // ══════════════════════════════════════════════════════════════════════
        // TEXT TAB
        // All widgets rendered unconditionally. Mode radio drives which
        // button click is handled. Both inputs always present in widget tree.
        // ══════════════════════════════════════════════════════════════════════
        var T = tabs.tab(0);

        // Mode radio — always rendered, always in same position
        String textMode = Jt.radio("Select mode",
                List.of("🔒 Encrypt text", "🔓 Decrypt text"))
                .use(T);
        boolean tEnc = textMode.equals("🔒 Encrypt text");

        // BOTH inputs always rendered — unique labels = unique widget keys
        String plainIn = Jt.textArea("✏️ Plain text  (type here to encrypt)")
                .placeholder("Type your secret message here…")
                .use(T);

        String cipherIn = Jt.textArea("🔡 Encrypted Base64  (paste here to decrypt)")
                .placeholder("Paste the Base64 cipher text here…")
                .use(T);

        // BOTH buttons always rendered — unique labels = unique widget keys
        boolean btnEncText = Jt.button("🔒 Encrypt →").use(T);
        boolean btnDecText = Jt.button("🔓 Decrypt →").use(T);

        // OUTPUT — non-widget, safe inside if/else
        if (btnEncText) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(T);
            } else if (!tEnc) {
                Jt.warning("Switch the mode to **🔒 Encrypt text** first.").use(T);
            } else if (plainIn == null || plainIn.isBlank()) {
                Jt.warning("Type some text in the plain text box above.").use(T);
            } else {
                try {
                    byte[] enc = encrypt(
                            plainIn.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            makeKey(secretKey, keySize));
                    String b64 = Base64.getEncoder().encodeToString(enc);
                    Jt.success("✅ Encrypted!  " + humanSize(plainIn.length())
                             + " → " + humanSize(b64.length())).use(T);
                    Jt.subheader("Result (Base64) — copy and paste into Decrypt box:").use(T);
                    Jt.html(copyBtn(b64)).use(T);
                } catch (Exception e) {
                    Jt.error("Encryption error: " + e.getMessage()).use(T);
                }
            }
        }

        if (btnDecText) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(T);
            } else if (tEnc) {
                Jt.warning("Switch the mode to **🔓 Decrypt text** first.").use(T);
            } else if (cipherIn == null || cipherIn.isBlank()) {
                Jt.warning("Paste the encrypted Base64 text in the second box above.").use(T);
            } else {
                try {
                    byte[] raw = Base64.getDecoder().decode(cipherIn.trim());
                    byte[] plain = decrypt(raw, makeKey(secretKey, keySize));
                    Jt.success("✅ Decrypted successfully!").use(T);
                    Jt.subheader("Decrypted message:").use(T);
                    Jt.text(new String(plain, java.nio.charset.StandardCharsets.UTF_8)).use(T);
                } catch (IllegalArgumentException e) {
                    Jt.error("Not valid Base64 — paste the full output from Encrypt.").use(T);
                } catch (Exception e) {
                    Jt.error("Decryption failed — wrong key or AES mode? ("
                            + e.getClass().getSimpleName() + ")").use(T);
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // IMAGE TAB
        // Same pattern — both uploaders always rendered, mode radio drives
        // which button is acted on.
        // ══════════════════════════════════════════════════════════════════════
        var I = tabs.tab(1);

        String imgMode = Jt.radio("Select mode",
                List.of("🔒 Encrypt image", "🔓 Decrypt image"))
                .use(I);
        boolean iEnc = imgMode.equals("🔒 Encrypt image");

        // Uploader A — for image files (PNG/JPG/BMP)
        Jt.markdown("**📁 Image uploader** — select a PNG / JPG / BMP to encrypt:").use(I);
        List<JtUploadedFile> imgUploads = Jt.fileUploader("Choose image").use(I);
        JtUploadedFile imgFile = imgUploads.isEmpty() ? null : imgUploads.getFirst();
        if (imgFile != null)
            Jt.markdown("`" + imgFile.filename() + "` — " + humanSize(imgFile.content().length)).use(I);

        // Uploader B — for .enc files
        Jt.markdown("**📁 Encrypted file uploader** — select a `.enc` file to decrypt:").use(I);
        List<JtUploadedFile> encUploads = Jt.fileUploader("Choose enc file").use(I);
        JtUploadedFile encFile = encUploads.isEmpty() ? null : encUploads.getFirst();
        if (encFile != null)
            Jt.markdown("`" + encFile.filename() + "` — " + humanSize(encFile.content().length)).use(I);

        // Both buttons always rendered
        boolean btnEncImg = Jt.button("🔒 Encrypt Image →").use(I);
        boolean btnDecImg = Jt.button("🔓 Decrypt Image →").use(I);

        // OUTPUT — non-widget, safe inside if/else
        if (btnEncImg) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(I);
            } else if (!iEnc) {
                Jt.warning("Switch the mode to **🔒 Encrypt image** first.").use(I);
            } else if (imgFile == null) {
                Jt.warning("Upload an image using the **Image uploader** above.").use(I);
            } else {
                try {
                    byte[] imgBytes = imgFile.content();
                    byte[] encrypted = encrypt(imgBytes, makeKey(secretKey, keySize));
                    String baseName = imgFile.filename().replaceAll("\\.[^.]+$", "");
                    String encFilename = baseName + ".enc";

                    Jt.success("✅ Image encrypted!  "
                             + humanSize(imgBytes.length) + " → "
                             + humanSize(encrypted.length)).use(I);
                    Jt.subheader("Original image:").use(I);
                    Jt.image(imgBytes).use(I);
                    Jt.markdown("Encrypted file is binary — not displayable as an image.").use(I);
                    Jt.html(blobDownload(encrypted, encFilename)).use(I);
                    Jt.info("Upload the downloaded `" + encFilename
                          + "` using the **Encrypted file uploader**, switch to Decrypt, use the same key.").use(I);
                } catch (Exception e) {
                    Jt.error("Encryption error: " + e.getMessage()).use(I);
                }
            }
        }

        if (btnDecImg) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(I);
            } else if (iEnc) {
                Jt.warning("Switch the mode to **🔓 Decrypt image** first.").use(I);
            } else if (encFile == null) {
                Jt.warning("Upload a `.enc` file using the **Encrypted file uploader** above.").use(I);
            } else {
                try {
                    byte[] decrypted = decrypt(encFile.content(), makeKey(secretKey, keySize));
                    String baseName = encFile.filename().replaceAll("\\.enc$", "");
                    if (!baseName.matches(".*\\.(png|jpg|jpeg|bmp|gif|webp)$")) baseName += ".png";

                    Jt.success("✅ Image decrypted!  (" + humanSize(decrypted.length) + ")").use(I);
                    Jt.subheader("Restored image:").use(I);
                    Jt.image(decrypted).use(I);
                    Jt.html(blobDownload(decrypted, baseName)).use(I);
                } catch (IllegalArgumentException e) {
                    Jt.error("Invalid file: " + e.getMessage()).use(I);
                } catch (Exception e) {
                    Jt.error("Decryption failed — wrong key or AES mode? ("
                            + e.getClass().getSimpleName() + ")").use(I);
                }
            }
        }

        // ── Footer ────────────────────────────────────────────────────────────
        Jt.divider().use();
        Jt.markdown("> Built with [Javelit](https://javelit.io) · AES-CBC · Random IV per operation").use();
    }
}
