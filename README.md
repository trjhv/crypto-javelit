🔐 Crypto App — AES Text & Image Encryption/Decryption
A Javelit web app (Streamlit for Java) that encrypts and decrypts text and images using AES-128/CBC.
Built from the original Java-Projects-Collections/Crypto project, now as a deployable interactive web app.
---
✨ Features
🔒 Text encryption – encrypt any text, get Base64 output
🔓 Text decryption – paste Base64 back, get original text
🖼️ Image encryption – upload PNG/JPG/BMP, get encrypted Base64
🖼️ Image decryption – paste Base64, get the original image back
AES-128/CBC with random IV (secure, no fixed IV)
Single-file Java app, zero frontend code
---
🚀 Quick Start (Local)
Prerequisites
Java 21+ (`java --version`)
JBang (recommended) — install: https://www.jbang.dev/download/
Run locally
```bash
# Install Javelit CLI via JBang (one-time)
jbang app install javelit@javelit

# Run the app directly from this repo
javelit run App.java
```
The app opens in your browser automatically at `http://localhost:7080`.
> **No JBang?** Download the Javelit jar and run:
> ```bash
> curl -L -o javelit.jar https://repo1.maven.org/maven2/io/javelit/javelit/0.88.0/javelit-0.88.0-all.jar
> java -jar javelit.jar run App.java
> ```
---
☁️ Deploy to Railway (one-click)
Javelit's officially recommended cloud platform is Railway.
Fork this repo to your GitHub account
Click → Deploy to Railway
Paste your GitHub repo URL when prompted
Click Deploy — done! 🎉
Railway gives you a public HTTPS URL in ~60 seconds.
> Railway's free tier covers light usage. No credit card needed to start.
---
📖 How It Works
```
┌─────────────────────────────────────┐
│          Javelit Web UI             │
│  ┌──────────┐   ┌────────────────┐  │
│  │   Text   │   │     Image      │  │
│  │ Encrypt  │   │    Encrypt     │  │
│  │ Decrypt  │   │    Decrypt     │  │
│  └──────────┘   └────────────────┘  │
└────────────────┬────────────────────┘
                 │
         ┌───────▼────────┐
         │   AES-128/CBC  │
         │  Random IV (16B)│
         │  PKCS5Padding   │
         └────────────────┘
```
Encryption flow:
User enters a 16-char secret key (padded/truncated to 16 bytes)
A random 16-byte IV is generated for each operation
AES/CBC encrypts the data; IV is prepended to the output
Result is Base64-encoded for easy copy-paste
Decryption flow:
Base64 is decoded → first 16 bytes extracted as IV
AES/CBC decrypts the remaining bytes using the same key + IV
Original bytes are returned as text or displayed as image
---
🗂️ Project Structure
```
crypto-javelit/
├── App.java        ← the entire app (single file!)
└── README.md
```
---
🛠️ Tech Stack
Layer	Tech
UI	Javelit 0.88.0
Crypto	Java `javax.crypto` AES
Runtime	Java 21+
Deploy	Railway (via GitHub)
---
⚠️ Security Notes
Key is padded to 16 bytes with zeros — use exactly 16 chars for best security
AES-128/CBC with a random IV per operation — secure for demo/educational use
For production, consider AES-256 with a proper KDF (e.g., PBKDF2)
---
📜 License
MIT — use freely, give credit.
