# Guava Doctor — Offline Guava Leaf Disease Scanner

**Guava Doctor** is an offline-first Android application built with Kotlin and Jetpack Compose for real-time guava leaf health screening, explainable visual highlighting, local scan history, orchard insights, and bilingual English/Bangla care guidance.

## Key Capabilities
- **On-Device Leaf Analysis:** Pre-scan quality & non-leaf detection, MobileNetV2 FP16 feature extraction, StandardScaler, and Linear SVM classification (`Anthracnose`, `Healthy`, `Nutrient Stress`, `Pest Damage`, `Sooty Mold`).
- **Explainable Visual Focus:** Original, Heatmap, and interactive Overlay views showing affected leaf regions.
- **Offline Orchard History & Insights:** Local Room database with search, disease/date filters, field notes, and 7-day scanning summaries.
- **Privacy First:** 100% on-device processing with Android Photo Picker, zero cloud uploads, and a full Privacy Policy screen.

## Free Deployment & In-App Updates (GitHub Releases)
1. Push this repository to `https://github.com/Mahfuz1912/guava-doctor`.
2. Add repository secrets (`SIGNING_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) in **Settings → Secrets and variables → Actions** for signed release APKs.
3. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then push a tag such as `git tag v1.0 && git push origin v1.0` (or `v2.0`).
4. GitHub Actions runs tests and publishes the APK under **Releases**, which the built-in Force Update system detects automatically.

## Developer
- **Md. Sabit Al Mahfuz** — Daffodil International University
- Email: `smahfuz945@gmail.com`
- GitHub: `https://github.com/Mahfuz1912`
- Portfolio: `https://mahfuzportfolio.vercel.app`
