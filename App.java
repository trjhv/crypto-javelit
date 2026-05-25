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

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.2f MB", b / 1048576.0);
    }

    // ── HTML HELPERS ──────────────────────────────────────────────────────────

    // Copy button: uses a hidden textarea so special chars (+ / = newlines) are safe
    private static String copyWidget(String id, String text) {
        // Store text in a hidden textarea, read it back with JS — no escaping issues
        return "<textarea id=\"" + id + "\" style=\"position:absolute;left:-9999px\">"
             + text + "</textarea>"
             + "<div style=\"background:#1e1e2e;color:#cdd6f4;padding:12px 12px 12px 12px;"
             + "border-radius:6px;font-size:11px;white-space:pre-wrap;word-break:break-all;"
             + "max-height:180px;overflow-y:auto;position:relative;margin-top:8px\">"
             + "<button onclick=\"var t=document.getElementById('" + id + "');"
             + "t.style.display='block';t.select();document.execCommand('copy');"
             + "t.style.display='none';this.textContent='✅ Copied!';"
             + "setTimeout(()=>this.textContent='📋 Copy',1800)\" "
             + "style=\"position:absolute;top:6px;right:6px;padding:4px 10px;"
             + "background:#1565c0;color:#fff;border:none;border-radius:4px;"
             + "cursor:pointer;font-size:12px\">📋 Copy</button>"
             + text + "</div>";
    }

    // Download button: writes a hidden <a> into DOM and clicks it — avoids inline b64 in JS
    private static String downloadWidget(String id, byte[] data, String filename) {
        String b64 = Base64.getEncoder().encodeToString(data);
        // Split into chunks to avoid browser/JVM string limits in the HTML attribute
        // Store b64 in a hidden element, assemble the URL in JS
        return "<span id=\"" + id + "b\" style=\"display:none\">" + b64 + "</span>"
             + "<button onclick=\""
             + "var b64=document.getElementById('" + id + "b').textContent;"
             + "var bin=atob(b64);"
             + "var bytes=new Uint8Array(bin.length);"
             + "for(var i=0;i<bin.length;i++){bytes[i]=bin.charCodeAt(i);}"
             + "var blob=new Blob([bytes],{type:'application/octet-stream'});"
             + "var url=URL.createObjectURL(blob);"
             + "var a=document.createElement('a');"
             + "a.href=url;a.download='" + filename + "';"
             + "document.body.appendChild(a);a.click();"
             + "setTimeout(function(){URL.revokeObjectURL(url);a.remove();},2000);"
             + "\" style=\"margin-top:12px;padding:10px 22px;background:#1565c0;"
             + "color:#fff;border:none;border-radius:6px;font-size:14px;"
             + "font-weight:600;cursor:pointer\">⬇️ Download " + filename + "</button>";
    }

    // ── MAIN ──────────────────────────────────────────────────────────────────
    //
    // JAVELIT RULE: every .use() must appear unconditionally on every run,
    // in the same order, with the same label. Never put .use() inside if/else.
    // Only non-widget output (success/error/html/image/text) goes in if/else.
    //
    // ──────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {

        Jt.title("🔐 Crypto App").use();
        Jt.markdown("AES-128/256 encryption & decryption for **text** and **images**.").use();
        Jt.divider().use();

        // ── Key row ───────────────────────────────────────────────────────────
        var keyRow = Jt.columns(2).use();
        String aesChoice = Jt.radio("AES Strength",
                List.of("AES-128  (16-char key)", "AES-256  (32-char key)"))
                .use(keyRow.col(0));
        int keySize = aesChoice.startsWith("AES-256") ? 32 : 16;
        String secretKey = Jt.textInput("🔑 Secret Key")
                .placeholder(keySize == 16 ? "mysecretkey12345"
                                           : "mysecretkey1234567890123456789012")
                .use(keyRow.col(1));
        boolean keyOk = secretKey != null && !secretKey.isBlank();
        if (!keyOk) Jt.warning("Enter a secret key to begin.").use();
        Jt.divider().use();

        // ── Tabs ──────────────────────────────────────────────────────────────
        var tabs = Jt.tabs(List.of("📝 Text", "🖼️ Image")).use();

        // ══════════════════════════════════════════════════════════════════════
        // TEXT TAB
        // All .use() calls unconditional. Mode radio is always rendered.
        // Both inputs always rendered — they have different labels so different keys.
        // Both buttons always rendered — fixed labels, different keys.
        // Output (success/error) gated by button press + mode check.
        // ══════════════════════════════════════════════════════════════════════
        var T = tabs.tab(0);

        // Mode selector — always present, drives which button does something
        String textMode = Jt.radio("── Text Mode ──",
                List.of("🔒 Encrypt", "🔓 Decrypt"))
                .use(T);
        boolean tEnc = textMode.equals("🔒 Encrypt");

        // Separator so user knows which input belongs to which mode
        Jt.markdown(tEnc
                ? "**Step 1:** Type your message below, then click **Encrypt Text**."
                : "**Step 1:** Paste the Base64 cipher below, then click **Decrypt Text**.")
                .use(T);

        // Plain text input — always rendered, unique label
        String plainIn = Jt.textArea("✏️  Plain text to encrypt")
                .placeholder("Type your secret message here…")
                .use(T);

        // Cipher input — always rendered, unique label
        String cipherIn = Jt.textArea("🔡  Encrypted Base64 to decrypt")
                .placeholder("Paste the Base64 cipher text here…")
                .use(T);

        // Both buttons — always rendered, fixed unique labels
        boolean btnEncText = Jt.button("🔒 Encrypt Text").use(T);
        boolean btnDecText = Jt.button("🔓 Decrypt Text").use(T);

        // ── Text output (non-widget, safe in if/else) ─────────────────────────
        if (btnEncText) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(T);
            } else if (!tEnc) {
                Jt.warning("Switch mode to **🔒 Encrypt** (radio above) then click Encrypt Text.").use(T);
            } else if (plainIn == null || plainIn.isBlank()) {
                Jt.warning("Type something in the **Plain text** box above.").use(T);
            } else {
                try {
                    byte[] enc = encrypt(
                            plainIn.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            makeKey(secretKey, keySize));
                    String b64 = Base64.getEncoder().encodeToString(enc);
                    Jt.success("✅ Encrypted!  " + humanSize(plainIn.length())
                             + " → " + humanSize(b64.length())).use(T);
                    Jt.subheader("Encrypted output — copy and paste into Decrypt box:").use(T);
                    Jt.html(copyWidget("txt_enc_out", b64)).use(T);
                } catch (Exception e) {
                    Jt.error("Encryption error: " + e.getMessage()).use(T);
                }
            }
        }

        if (btnDecText) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(T);
            } else if (tEnc) {
                Jt.warning("Switch mode to **🔓 Decrypt** (radio above) then click Decrypt Text.").use(T);
            } else if (cipherIn == null || cipherIn.isBlank()) {
                Jt.warning("Paste the Base64 cipher in the **Encrypted Base64** box above.").use(T);
            } else {
                try {
                    byte[] raw   = Base64.getDecoder().decode(cipherIn.trim());
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
        // Same pattern. Both uploaders always rendered. Both buttons fixed labels.
        // ══════════════════════════════════════════════════════════════════════
        var I = tabs.tab(1);

        String imgMode = Jt.radio("── Image Mode ──",
                List.of("🔒 Encrypt", "🔓 Decrypt"))
                .use(I);
        boolean iEnc = imgMode.equals("🔒 Encrypt");

        Jt.markdown(iEnc
                ? "**Step 1:** Upload your image below → **Step 2:** Click **Encrypt Image**."
                : "**Step 1:** Upload the `.enc` file below → **Step 2:** Click **Decrypt Image**.")
                .use(I);

        // Uploader A — image files, always rendered
        Jt.markdown("📁 **Image uploader** (PNG / JPG / BMP — for encryption):").use(I);
        List<JtUploadedFile> imgUploads = Jt.fileUploader("Choose image file").use(I);
        JtUploadedFile imgFile = imgUploads.isEmpty() ? null : imgUploads.getFirst();
        if (imgFile != null)
            Jt.markdown("Selected: `" + imgFile.filename()
                      + "` (" + humanSize(imgFile.content().length) + ")").use(I);

        // Uploader B — enc files, always rendered
        Jt.markdown("📁 **Encrypted file uploader** (`.enc` file — for decryption):").use(I);
        List<JtUploadedFile> encUploads = Jt.fileUploader("Choose enc file").use(I);
        JtUploadedFile encFile = encUploads.isEmpty() ? null : encUploads.getFirst();
        if (encFile != null)
            Jt.markdown("Selected: `" + encFile.filename()
                      + "` (" + humanSize(encFile.content().length) + ")").use(I);

        // Both buttons — always rendered, fixed unique labels
        boolean btnEncImg = Jt.button("🔒 Encrypt Image").use(I);
        boolean btnDecImg = Jt.button("🔓 Decrypt Image").use(I);

        // ── Image output (non-widget, safe in if/else) ────────────────────────
        if (btnEncImg) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(I);
            } else if (!iEnc) {
                Jt.warning("Switch mode to **🔒 Encrypt** (radio above) then click Encrypt Image.").use(I);
            } else if (imgFile == null) {
                Jt.warning("Upload an image using the **Image uploader** above.").use(I);
            } else {
                try {
                    byte[] imgBytes  = imgFile.content();
                    byte[] encrypted = encrypt(imgBytes, makeKey(secretKey, keySize));
                    String base      = imgFile.filename().replaceAll("\\.[^.]+$", "");
                    String encName   = base + ".enc";

                    Jt.success("✅ Encrypted!  " + humanSize(imgBytes.length)
                             + " → " + humanSize(encrypted.length)).use(I);
                    Jt.markdown("**Original image:**").use(I);
                    Jt.image(imgBytes).use(I);
                    Jt.markdown("Encrypted file is binary — use the download button below.").use(I);
                    Jt.html(downloadWidget("img_enc", encrypted, encName)).use(I);
                    Jt.info("After downloading, upload the `" + encName
                          + "` using the **Encrypted file uploader**, switch mode to 🔓 Decrypt.").use(I);
                } catch (Exception e) {
                    Jt.error("Encryption error: " + e.getMessage()).use(I);
                }
            }
        }

        if (btnDecImg) {
            if (!keyOk) {
                Jt.error("Enter a secret key first.").use(I);
            } else if (iEnc) {
                Jt.warning("Switch mode to **🔓 Decrypt** (radio above) then click Decrypt Image.").use(I);
            } else if (encFile == null) {
                Jt.warning("Upload a `.enc` file using the **Encrypted file uploader** above.").use(I);
            } else {
                try {
                    byte[] decrypted = decrypt(encFile.content(), makeKey(secretKey, keySize));
                    String base      = encFile.filename().replaceAll("\\.enc$", "");
                    if (!base.matches(".*\\.(png|jpg|jpeg|bmp|gif|webp)$")) base += ".png";

                    Jt.success("✅ Decrypted!  (" + humanSize(decrypted.length) + ")").use(I);
                    Jt.subheader("Restored image:").use(I);
                    Jt.image(decrypted).use(I);
                    Jt.html(downloadWidget("img_dec", decrypted, base)).use(I);
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
