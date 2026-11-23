# Guava Doctor — Smart Guava Leaf Disease Scanner (v3.0)

**Guava Doctor** is an Android application built with Kotlin and Jetpack Compose for instant on-device guava leaf health screening, explainable visual highlighting, cloud-synced orchard history & insights, and bilingual English/Bangla care guidance.

## Key Capabilities
- **On-Device Leaf AI Screening:** Pre-scan quality & non-leaf detection, MobileNetV2 FP16 feature extraction, StandardScaler, and Linear SVM classification (`Anthracnose`, `Healthy`, `Nutrient Stress`, `Pest Damage`, `Sooty Mold`).
- **Explainable Visual Focus:** Original, Heatmap, and interactive Overlay views highlighting affected leaf regions.
- **Streamlined Home & Navigation:** Card-based Home with Quick Scan, Gallery Upload, Disease Guide, Quick Scan Tips, a dedicated Orchard Insights card above Recent Scans, and a bottom-bar Profile tab for signed-in growers.
- **Firebase Auth & Cloud Sync:** Google Sign-In (Credential Manager + automatic Firebase OAuth fallback), Email/Password login & Forgot Password, Bangladesh cascading location profile (Division → District → Upazila), and account-protected History & Insights synced via Cloud Firestore.
- **Built-In In-App Updates & Sharing:** Floating Material 3 Force Update modal with language-matched What's New dropdown, live APK download progress, and **Share App** on the About page.
- **Privacy & Bilingual Support:** Full English and Bangla UI with Android Photo Picker and a dedicated Privacy Policy screen.

## GitHub Actions Release & In-App Updates
1. Push this repository to `https://github.com/Mahfuz1912/guava-doctor`.
2. Configure the following repository secrets in **Settings → Secrets and variables → Actions**:
   - `SIGNING_KEYSTORE_BASE64` — Base64-encoded release `.jks` keystore
   - `STORE_PASSWORD` — Keystore password
   - `KEY_PASSWORD` — Upload key password
   - `GOOGLE_SERVICES_JSON_BASE64` — Base64-encoded Firebase `app/google-services.json` (must include an OAuth Web Client with `client_type: 3`)
3. Current version in `app/build.gradle.kts` is **`versionCode = 6`** / **`versionName = "3.0"`**.
4. Trigger a release by either:
   - Running **Actions → Build and Release APK → Run workflow** (default tag: `v3.0`), or
   - Pushing a version tag: `git tag v3.0 && git push origin v3.0`.
5. GitHub Actions validates Firebase config, runs the full unit test suite, builds and verifies the signed release APK, and publishes `guava-doctor-v3.0.apk` under **Releases** for automatic in-app update detection.

## Developer
- **Md. Sabit Al Mahfuz** — Daffodil International University
- Email: `smahfuz945@gmail.com`
- GitHub: `https://github.com/Mahfuz1912`
- Portfolio: `https://mahfuzportfolio.vercel.app`
