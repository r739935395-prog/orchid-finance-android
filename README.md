# Orchid Finance Android (Native)

Native Android app (Kotlin, Jetpack Compose, Material 3, Room/SQLite) implementing the accounting functionality of the Orchid Finance web app.

- Target: Samsung Galaxy S21+ 5G (Android 15, ARM64) - `minSdk 26`, `targetSdk/compileSdk 35`, `arm64-v8a` compatible
- Package: `com.orchid.finance`
- Local DB: `orchid.db` (Room), seeded chart of accounts/categories on first run
- Signed release keystore included for build reproducibility (`app/keystore/release.jks`, dev credentials in `app/build.gradle.kts`)

## Features (parity with web app)
- Setup/login (local auth, PBKDF2-SHA256)
- Dashboard KPIs and low stock
- Products & inventory (moving weighted-average cost), stock adjustments and movements logic
- Sales & purchase invoices (cash/partial/credit), cancellation with journal reversal and stock reversal
- Customers & suppliers with opening balances
- Cash/bank transactions, expenses
- Double-entry accounting: chart of accounts, journal entries, trial balance, general ledger, income statement, balance sheet, cash flow
- Reconciliations and locked accounting periods
- Exchange rates, settings (tax rate), audit log, database backup export/share

## Build (GitHub Actions - recommended)
This repository includes `.github/workflows/build-apk.yml`.

1. Push the `android/` project to a GitHub repository (or copy contents as repo root).
2. Run workflow **Build Android APK** (on push or manual dispatch).
3. Download artifact **`orchid-finance-release-apk`** containing `app-release.apk`.
4. The workflow verifies the APK signature with `apksigner`.

## Build locally (requires full Android SDK)
Prerequisites: JDK 17+, Gradle 8.7+, Android SDK Platform 35, Build-Tools 35.0.0.

```bash
cp local.properties.example local.properties
# edit sdk.dir=...
gradle :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

> Note: Building locally requires complete Android build-tools (including `d8`/`apksigner` jars). Minimal aarch64 static tool bundles may be insufficient for AGP/Gradle builds.
