# CleanAlert Android Project

CleanAlert is a waste-reporting and collection app connecting residents, collectors, and administrators.

## Implemented workflow

- Email/password Firebase Authentication.
- Role-aware profiles for resident, collector, and admin.
- Resident home screen.
- Resident gallery photo selection and phone camera capture.
- Firebase Storage photo upload.
- Firestore submission records with pending status and reward points.
- Collector request queue with collection acceptance.
- Admin review queue with approve/reject actions.
- Collector map screen with Google Maps integration.
- Password reset and logout flows.
- Local Firestore and Storage security rules.

## Open in Android Studio

1. Open `C:\CleanAlert` as a project.
2. Use the Gradle wrapper included in the project.
3. Use Android Studio's bundled JDK 21. The project pins this through `gradle.properties` with `org.gradle.java.home`.
4. Run the `app` configuration on an emulator or connected Android device.

## Firebase setup

1. Download the Android configuration from Firebase Project Settings.
2. Place it at exactly:

   `C:\CleanAlert\app\google-services.json`

3. Ensure the Windows account running Android Studio has read permission for the file.
4. Enable Email/Password authentication in Firebase Authentication.
5. Create Firestore Database and Firebase Storage in the Firebase Console.
6. Deploy the local rules after reviewing them:

   ```text
   firebase deploy --only firestore:rules,storage
   ```

7. Create the admin account using `admin@mc.com` through the app. Set the password privately; no password is stored in this project.

The Firebase Android package must be `com.futureseed.cleanalert`.

## Google Maps setup

Add the Maps key only to `C:\CleanAlert\local.properties`:

```text
MAPS_API_KEY=YOUR_KEY
```

The key is excluded from source control. Restrict it in Google Cloud Console to:

- Android application package: `com.futureseed.cleanalert`
- The SHA-1 certificate used to build/install the APK
- Maps SDK for Android

## College-demo test checklist

1. Resident account signs in.
2. Resident opens Submit Waste.
3. Resident chooses a gallery image or captures a camera image.
4. Submission uploads and creates a Firestore record with `pending` status.
5. Collector account opens the collection request queue.
6. Collector accepts the request; Firestore status becomes `assigned`.
7. Admin account opens Review Submissions.
8. Admin approves or rejects the submission.
9. Verify the resident submission document and Storage image in Firebase Console.
10. Test logout, relaunch, denied camera permission, missing photo, and no-network failure.
11. Verify that the collector map renders on a physical device or configured emulator.

## Current validation status

Verified in the current environment:

- Firebase Google Services processing succeeds for `com.futureseed.cleanalert`.
- `MainActivity.java` compiles successfully.
- Android resources and manifests process successfully.
- The locked root `app\google-services.json` is not readable by the sandbox, so the debug build currently uses the matching copy at `app\src\debug\google-services.json`.
- A project-local Android SDK mirror is configured through ignored `local.properties` to avoid the same Windows ACL problem in the installed SDK archives.

The remaining unverified step is final APK packaging: the sandbox build reaches `dexBuilderDebug` but is terminated/stalls before producing an APK. Run the build from Android Studio or a normal PowerShell session on the Windows user account with sufficient permissions:

```text
.\gradlew.bat clean assembleDebug --no-daemon
```

The generated APK should be under:

`C:\CleanAlert\app\build\outputs\apk\debug\app-debug.apk`

Do not paste `google-services.json`, Maps keys, or any other private configuration into chat. Restrict the Firebase/Maps key to the Android package, signing certificate, and required APIs.

## Known mini-project limitations

- Live background collector tracking is not implemented.
- Push notifications require Firebase Cloud Messaging setup and a server/cloud function trigger.
- The app currently uses one activity with XML screen navigation to keep the college project simple.
