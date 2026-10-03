# Build a ready-to-install APK without Android Studio

1. Create a GitHub repository.
2. Upload this whole project, preserving the `.github/workflows/build-apk.yml` file.
3. Push to the `main` branch (or open **Actions → Build ShopBill APK → Run workflow**).
4. GitHub Actions builds `app-debug.apk`.
5. Open the completed workflow run and download the **ShopBill-debug-apk** artifact.
6. Extract it and install `app-debug.apk` on the Android phone.

The app is offline-first and does not need a backend/API for its normal billing operations.
