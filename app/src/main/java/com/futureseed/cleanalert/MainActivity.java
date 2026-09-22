package com.futureseed.cleanalert;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements OnMapReadyCallback {
    private static final int LOCATION_PERMISSION_REQUEST = 42;
    private static final int CAMERA_PERMISSION_REQUEST = 43;
    private static final int GALLERY_REQUEST = 700;
    private static final int CAMERA_REQUEST = 701;
    private static final int GOOGLE_SIGN_IN_REQUEST = 702;
    private GoogleSignInClient googleSignInClient;
    private static final String ADMIN_EMAIL = "admin@mc.com";
    private static final int REWARD_CAFE_COST = 200;
    private static final int REWARD_AMAZON_COST = 350;
    private static final String DEFAULT_LOCALITY = "Bengaluru";
    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String leaderboardScope = "locality";
    private String rewardsTab = "active";

    private int currentLayout = R.layout.activity_login;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String firebaseInitError;
    private Uri selectedPhotoUri;
    private Bitmap capturedPhotoBitmap;
    private boolean waitingForCameraPermission;
    private MapView mapView;
    private GoogleMap googleMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            FirebaseApp firebaseApp = FirebaseApp.initializeApp(this);
            if (firebaseApp != null || !FirebaseApp.getApps(this).isEmpty()) {
                auth = FirebaseAuth.getInstance();
                db = FirebaseFirestore.getInstance();
            } else {
                firebaseInitError = "Firebase configuration is missing";
            }
        } catch (Exception error) {
            firebaseInitError = "Firebase could not initialize";
        }

        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception error) {
            // default_web_client_id only exists once Google sign-in is enabled in Firebase
            // Console and google-services.json has been re-downloaded. Button stays inert
            // (with a clear toast) until then instead of crashing the app.
            googleSignInClient = null;
        }

        show(R.layout.activity_login);
        if (auth != null && db != null && auth.getCurrentUser() != null) {
            routeUser(auth.getCurrentUser());
        }
    }

    private void show(int layout) {
        currentLayout = layout;
        setContentView(layout);
        wireCurrentScreen();
        if (layout == R.layout.collector_map) setupLiveMap();
        if (layout == R.layout.collector_requests) loadCollectorRequests();
        if (layout == R.layout.admin_submissions) loadAdminSubmissions();
        if (layout == R.layout.resident_home) loadResidentHome();
        if (layout == R.layout.resident_points_streak) loadPointsStreak();
        if (layout == R.layout.resident_leaderboard) loadLeaderboard();
        if (layout == R.layout.resident_my_rewards) loadMyRewards();
        if (layout == R.layout.collector_dashboard) loadCollectorDashboard();
        if (layout == R.layout.admin_dashboard) loadAdminDashboard();
        if (layout == R.layout.activity_login) {
            View apple = findViewById(R.id.btnApple);
            if (apple != null) apple.setVisibility(View.GONE);
        }
    }

    private void wireCurrentScreen() {
        link(R.id.tvForgotPassword, () -> show(R.layout.activity_forgot_password));
        link(R.id.tvCreateAccount, () -> show(R.layout.activity_register_resident));
        link(R.id.tvLogin, () -> show(R.layout.activity_login));
        link(R.id.tvAlreadyAccount, () -> show(R.layout.activity_login));
        link(R.id.btnSignIn, this::signIn);
        link(R.id.btnGoogle, this::startGoogleSignIn);
        link(R.id.btnCreateAccount, this::createAccount);
        link(R.id.btnSubmitWaste, () -> show(R.layout.resident_submit_waste));
        link(R.id.btnPickPhoto, this::pickPhoto);
        link(R.id.btnTakePhoto, this::takePhoto);
        link(R.id.btnSubmitWasteFinal, this::submitWaste);
        link(R.id.btnHome, () -> show(R.layout.resident_home));
        link(R.id.tvCollector, () -> show(R.layout.activity_register_collector));
        link(R.id.tvResident, () -> show(R.layout.activity_register_resident));
        link(R.id.btnSendResetLink, this::sendPasswordReset);
        link(R.id.btnBackToLogin, () -> show(R.layout.activity_login));
        link(R.id.ivBack, () -> show(R.layout.activity_login));
        link(R.id.btnBack, () -> show(R.layout.activity_login));

        link(R.id.navDashboard, () -> show(R.layout.collector_dashboard));
        link(R.id.navMap, () -> show(R.layout.collector_map));
        link(R.id.navHistory, () -> show(R.layout.collection_history));
        link(R.id.navProfile, () -> show(R.layout.collector_profile));
        link(R.id.btnToggleStatus, this::toggleCollectorStatus);
        link(R.id.btnStartCollection, () -> show(R.layout.collector_requests));

        link(R.id.navHome, () -> show(R.layout.resident_home));
        link(R.id.navResHistory, () -> show(R.layout.resident_past_submissions));
        link(R.id.navRewards, () -> show(R.layout.resident_rewards));
        link(R.id.navResProfile, () -> show(R.layout.resident_profile));
        link(R.id.openPointsStreak, () -> show(R.layout.resident_points_streak));
        link(R.id.tvHomeStreak, () -> show(R.layout.resident_leaderboard));
        link(R.id.btnStartCollectionTwo, () -> show(R.layout.collector_requests));
        link(R.id.btnNavigate, () -> show(R.layout.collector_map));
        link(R.id.btnLogout, this::logout);
        link(R.id.adminLogout, this::logout);
        link(R.id.btnAdminSubmissions, () -> show(R.layout.admin_submissions));
        link(R.id.btnAdminBack, () -> show(R.layout.admin_dashboard));
        link(R.id.btnAdminQueueLogout, this::logout);
        link(R.id.btnCollectorBack, () -> show(R.layout.collector_dashboard));
        link(R.id.btnViewDetails, () -> show(R.layout.collector_profile));

        link(R.id.btnLocality, () -> setLeaderboardScope("locality"));
        link(R.id.btnCity, () -> setLeaderboardScope("city"));
        link(R.id.tabActive, () -> setRewardsTab("active"));
        link(R.id.tabRedeemed, () -> setRewardsTab("redeemed"));
        link(R.id.btnViewAll, () -> show(R.layout.resident_my_rewards));
        link(R.id.btnRedeemCafe, () -> redeemReward("Cafe Coffee Day Voucher", REWARD_CAFE_COST));
        link(R.id.btnRedeemAmazon, () -> redeemReward("Amazon Voucher", REWARD_AMAZON_COST));
        link(R.id.btnDone, () -> show(R.layout.resident_my_rewards));
        link(R.id.btnViewMyRewards, () -> show(R.layout.resident_my_rewards));
        link(R.id.btnBackToRewards, () -> show(R.layout.resident_rewards));
        link(R.id.btnRedeem, () -> redeemReward("Amazon Voucher", REWARD_AMAZON_COST));

        link(R.id.btnZoomIn, () -> { if (googleMap != null) googleMap.animateCamera(CameraUpdateFactory.zoomIn()); });
        link(R.id.btnZoomOut, () -> { if (googleMap != null) googleMap.animateCamera(CameraUpdateFactory.zoomOut()); });
        link(R.id.btnMyLocation, () -> enableMyLocation());
    }

    private void signIn() {
        if (auth == null) {
            toast(firebaseUnavailableMessage());
            return;
        }
        EditText email = findViewById(R.id.etEmail);
        EditText password = findViewById(R.id.etPassword);
        String emailText = email == null ? "" : email.getText().toString().trim();
        String passwordText = password == null ? "" : password.getText().toString();
        if (emailText.isEmpty() || passwordText.isEmpty()) {
            toast("Enter your email and password");
            return;
        }
        auth.signInWithEmailAndPassword(emailText, passwordText)
                .addOnSuccessListener(result -> routeUser(result.getUser()))
                .addOnFailureListener(error -> toast(error.getMessage() == null ? "Sign in failed" : error.getMessage()));
    }

    private void createAccount() {
        if (auth == null || db == null) {
            toast(firebaseUnavailableMessage());
            return;
        }
        EditText email = findViewById(R.id.etEmail);
        EditText password = findViewById(R.id.etPassword);
        EditText confirm = findViewById(R.id.etConfirmPassword);
        String emailText = email == null ? "" : email.getText().toString().trim();
        String passwordText = password == null ? "" : password.getText().toString();
        String confirmText = confirm == null ? passwordText : confirm.getText().toString();
        if (emailText.isEmpty() || passwordText.length() < 6) {
            toast("Use an email and a password with at least 6 characters");
            return;
        }
        if (!passwordText.equals(confirmText)) {
            toast("Passwords do not match");
            return;
        }
        String role = ADMIN_EMAIL.equalsIgnoreCase(emailText)
                ? "admin"
                : (currentLayout == R.layout.activity_register_collector ? "collector" : "resident");
        auth.createUserWithEmailAndPassword(emailText, passwordText)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) return;
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("uid", user.getUid());
                    profile.put("email", emailText);
                    profile.put("role", role);
                    profile.put("createdAt", System.currentTimeMillis());
                    String displayName = emailText.contains("@") ? emailText.substring(0, emailText.indexOf('@')) : emailText;
                    if ("resident".equals(role)) {
                        profile.put("points", 0L);
                        profile.put("streak", 0L);
                        profile.put("locality", DEFAULT_LOCALITY);
                        profile.put("name", displayName);
                    } else if ("collector".equals(role)) {
                        profile.put("status", "offline");
                        profile.put("name", displayName);
                    }
                    db.collection("users").document(user.getUid()).set(profile)
                            .addOnSuccessListener(done -> {
                                if ("resident".equals(role)) {
                                    Map<String, Object> board = new HashMap<>();
                                    board.put("name", displayName);
                                    board.put("points", 0L);
                                    board.put("streak", 0L);
                                    board.put("locality", DEFAULT_LOCALITY);
                                    db.collection("leaderboard").document(user.getUid()).set(board);
                                }
                                routeUser(user);
                            })
                            .addOnFailureListener(error -> toast("Account created, but profile setup failed"));
                })
                .addOnFailureListener(error -> toast(error.getMessage() == null ? "Account creation failed" : error.getMessage()));
    }

    private void sendPasswordReset() {
        if (auth == null) {
            toast(firebaseUnavailableMessage());
            return;
        }
        EditText email = findViewById(R.id.etEmail);
        String emailText = email == null ? "" : email.getText().toString().trim();
        if (emailText.isEmpty()) {
            toast("Enter your email address");
            return;
        }
        auth.sendPasswordResetEmail(emailText)
                .addOnSuccessListener(done -> show(R.layout.activity_password_reset))
                .addOnFailureListener(error -> toast(error.getMessage() == null ? "Could not send reset email" : error.getMessage()));
    }

    private void routeUser(FirebaseUser user) {
        if (user == null || db == null) return;
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    String role = snapshot.getString("role");
                    if ("admin".equals(role) || ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
                        show(R.layout.admin_dashboard);
                    } else if ("collector".equals(role)) {
                        show(R.layout.collector_dashboard);
                    } else {
                        show(R.layout.resident_home);
                    }
                })
                .addOnFailureListener(error -> toast("Could not load your profile"));
    }

    private void logout() {
        if (auth != null) auth.signOut();
        show(R.layout.activity_login);
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, GALLERY_REQUEST);
    }

    private void takePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            waitingForCameraPermission = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            toast("No camera app is available on this device.");
            return;
        }
        startActivityForResult(intent, CAMERA_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GOOGLE_SIGN_IN_REQUEST) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                toast("Google sign-in cancelled or failed (code " + e.getStatusCode() + ")");
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null) return;
        View preview = findViewById(R.id.ivWastePhoto);
        if (requestCode == GALLERY_REQUEST && data.getData() != null) {
            selectedPhotoUri = data.getData();
            capturedPhotoBitmap = null;
            if (preview instanceof android.widget.ImageView) {
                ((android.widget.ImageView) preview).setImageURI(selectedPhotoUri);
            }
            toast("Photo selected. Submit it for verification when ready.");
        } else if (requestCode == CAMERA_REQUEST && data.getExtras() != null) {
            Object image = data.getExtras().get("data");
            if (image instanceof Bitmap) {
                capturedPhotoBitmap = (Bitmap) image;
                selectedPhotoUri = null;
                if (preview instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) preview).setImageBitmap(capturedPhotoBitmap);
                }
                toast("Camera photo captured. Submit it for verification when ready.");
            }
        }
    }

    private void submitWaste() {
        if (auth == null || db == null) {
            toast("Firebase is not ready. Sync the project and try again.");
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Please sign in before submitting waste.");
            return;
        }
        if (selectedPhotoUri == null && capturedPhotoBitmap == null) {
            toast("Choose a photo or take one with the camera first.");
            return;
        }

        String submissionId = db.collection("submissions").document().getId();
        StorageReference photoRef = FirebaseStorage.getInstance().getReference()
                .child("submissions").child(user.getUid()).child(submissionId + ".jpg");
        UploadTask uploadTask;
        if (selectedPhotoUri != null) {
            uploadTask = photoRef.putFile(selectedPhotoUri);
        } else {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            capturedPhotoBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
            uploadTask = photoRef.putBytes(output.toByteArray());
        }
        uploadTask.continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return photoRef.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    Map<String, Object> submission = new HashMap<>();
                    submission.put("submissionId", submissionId);
                    submission.put("residentId", user.getUid());
                    submission.put("photoUrl", downloadUri.toString());
                    submission.put("status", "pending");
                    submission.put("points", 20);
                    submission.put("createdAt", System.currentTimeMillis());
                    db.collection("submissions").document(submissionId).set(submission)
                            .addOnSuccessListener(done -> {
                                selectedPhotoUri = null;
                                capturedPhotoBitmap = null;
                                show(R.layout.resident_submission_success);
                            })
                            .addOnFailureListener(error -> toast("Photo uploaded, but submission record failed"));
                })
                .addOnFailureListener(error -> toast(error.getMessage() == null ? "Photo upload failed" : error.getMessage()));
    }

    private void loadCollectorRequests() {
        LinearLayout list = findViewById(R.id.collectorRequestList);
        TextView status = findViewById(R.id.tvCollectorQueueStatus);
        if (list == null || db == null) return;
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        String myUid = user == null ? null : user.getUid();
        db.collection("submissions")
                .whereIn("status", java.util.Arrays.asList("pending", "assigned"))
                .limit(30).get()
                .addOnSuccessListener(snapshot -> {
                    list.removeAllViews();
                    int shown = 0;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String state = doc.getString("status");
                        String collectorId = doc.getString("collectorId");
                        boolean mine = myUid != null && myUid.equals(collectorId);
                        if ("assigned".equals(state) && !mine) continue; // claimed by someone else
                        addSubmissionCard(list, doc.getId(), doc.getString("residentId"), state, false);
                        shown++;
                    }
                    status.setText(shown == 0 ? "No pending collection requests right now." : shown + " request(s)");
                })
                .addOnFailureListener(error -> status.setText("Could not load requests. Check Firestore rules."));
    }

    private void loadAdminSubmissions() {
        LinearLayout list = findViewById(R.id.adminSubmissionList);
        TextView status = findViewById(R.id.tvAdminQueueStatus);
        if (list == null || db == null) return;
        db.collection("submissions").limit(50).get()
                .addOnSuccessListener(snapshot -> {
                    list.removeAllViews();
                    if (snapshot.isEmpty()) {
                        status.setText("No resident submissions yet.");
                        return;
                    }
                    status.setText(snapshot.size() + " submission(s) found");
                    snapshot.getDocuments().forEach(doc -> addSubmissionCard(list, doc.getId(), doc.getString("residentId"), doc.getString("status"), true));
                })
                .addOnFailureListener(error -> status.setText("Could not load submissions. Check Firestore rules."));
    }

    private void addSubmissionCard(LinearLayout list, String submissionId, String residentId, String state, boolean admin) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18, 16, 18, 16);
        card.setBackgroundResource(R.drawable.bg_white_card);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, 14);
        list.addView(card, cardParams);

        TextView title = new TextView(this);
        title.setText("Waste submission");
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView details = new TextView(this);
        details.setText("Resident: " + (residentId == null ? "Unknown" : residentId) + "\\nStatus: " + (state == null ? "pending" : state));
        details.setTextColor(Color.rgb(107, 114, 128));
        details.setTextSize(12);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
        detailsParams.setMargins(0, 8, 0, 12);
        card.addView(details, detailsParams);

        if (admin && "pending".equals(state)) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView approve = queueButton("Approve");
            TextView reject = queueButton("Reject");
            actions.addView(approve, new LinearLayout.LayoutParams(0, 44, 1));
            LinearLayout.LayoutParams rejectParams = new LinearLayout.LayoutParams(0, 44, 1);
            rejectParams.setMargins(10, 0, 0, 0);
            actions.addView(reject, rejectParams);
            card.addView(actions);
            approve.setOnClickListener(v -> updateSubmissionStatus(submissionId, "approved", true));
            reject.setOnClickListener(v -> updateSubmissionStatus(submissionId, "rejected", true));
        } else if (!admin && "pending".equals(state)) {
            TextView accept = queueButton("Accept collection");
            card.addView(accept, new LinearLayout.LayoutParams(-1, 44));
            accept.setOnClickListener(v -> updateSubmissionStatus(submissionId, "assigned", false));
        } else if (!admin && "assigned".equals(state)) {
            TextView complete = queueButton("Mark Completed");
            card.addView(complete, new LinearLayout.LayoutParams(-1, 44));
            complete.setOnClickListener(v -> updateSubmissionStatus(submissionId, "completed", false));
        }
    }

    private TextView queueButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setBackgroundResource(R.drawable.bg_button_green);
        return button;
    }

    private void updateSubmissionStatus(String submissionId, String status, boolean reviewed) {
        if (db == null) return;
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null) {
            toast("Please sign in again.");
            return;
        }
        if ("approved".equals(status)) {
            approveSubmissionAndAwardPoints(submissionId, user.getUid());
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        if (reviewed) {
            updates.put("reviewedBy", user.getUid());
            updates.put("reviewedAt", System.currentTimeMillis());
        } else if ("assigned".equals(status)) {
            updates.put("collectorId", user.getUid());
            updates.put("assignedAt", System.currentTimeMillis());
        } else if ("completed".equals(status)) {
            updates.put("completedAt", System.currentTimeMillis());
        }
        db.collection("submissions").document(submissionId).update(updates)
                .addOnSuccessListener(done -> {
                    toast("Submission updated: " + status);
                    show(currentLayout);
                })
                .addOnFailureListener(error -> toast("Update failed. Deploy the latest Firestore rules."));
    }

    private void approveSubmissionAndAwardPoints(String submissionId, String adminUid) {
        DocumentReference submissionRef = db.collection("submissions").document(submissionId);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot submissionSnap = transaction.get(submissionRef);
            Map<String, Object> submissionUpdates = new HashMap<>();
            submissionUpdates.put("status", "approved");
            submissionUpdates.put("reviewedBy", adminUid);
            submissionUpdates.put("reviewedAt", System.currentTimeMillis());
            transaction.update(submissionRef, submissionUpdates);

            String residentId = submissionSnap.getString("residentId");
            if (residentId != null) {
                Long pointsValue = submissionSnap.getLong("points");
                long awardedPoints = pointsValue == null ? 20L : pointsValue;
                DocumentReference residentRef = db.collection("users").document(residentId);
                DocumentSnapshot residentSnap = transaction.get(residentRef);
                long currentPoints = residentSnap.contains("points") ? residentSnap.getLong("points") : 0L;
                long currentStreak = residentSnap.contains("streak") ? residentSnap.getLong("streak") : 0L;
                String lastActivityDate = residentSnap.getString("lastActivityDate");
                String today = DAY_FORMAT.format(new Date());
                long newPoints = currentPoints + awardedPoints;
                long newStreak = computeStreak(lastActivityDate, today, currentStreak);

                Map<String, Object> residentUpdates = new HashMap<>();
                residentUpdates.put("points", newPoints);
                residentUpdates.put("streak", newStreak);
                residentUpdates.put("lastActivityDate", today);
                transaction.set(residentRef, residentUpdates, SetOptions.merge());

                Map<String, Object> boardUpdates = new HashMap<>();
                boardUpdates.put("points", newPoints);
                boardUpdates.put("streak", newStreak);
                transaction.set(db.collection("leaderboard").document(residentId), boardUpdates, SetOptions.merge());
            }
            return null;
        }).addOnSuccessListener(done -> {
            toast("Approved — points awarded to resident");
            show(currentLayout);
        }).addOnFailureListener(error -> toast("Approval failed. Deploy the latest Firestore rules."));
    }

    // Yesterday -> +1 streak. Today already logged -> unchanged. Anything else -> restart at 1.
    private long computeStreak(String lastActivityDate, String today, long currentStreak) {
        if (lastActivityDate == null) return 1L;
        if (lastActivityDate.equals(today)) return currentStreak == 0 ? 1L : currentStreak;
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(DAY_FORMAT.parse(today));
            cal.add(Calendar.DAY_OF_YEAR, -1);
            String yesterday = DAY_FORMAT.format(cal.getTime());
            if (lastActivityDate.equals(yesterday)) return currentStreak + 1;
        } catch (Exception ignored) {
        }
        return 1L;
    }

    private void loadResidentHome() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
            TextView greeting = findViewById(R.id.tvHomeGreeting);
            TextView pointsView = findViewById(R.id.tvHomePoints);
            TextView streakView = findViewById(R.id.tvHomeStreak);
            if (greeting != null) greeting.setText("Hello, " + capitalize(snapshot.getString("name")));
            if (pointsView != null) pointsView.setText(String.valueOf(safeLong(snapshot, "points")));
            if (streakView != null) streakView.setText(safeLong(snapshot, "streak") + " Day Streak");
        });

        LinearLayout list = findViewById(R.id.homeActivityList);
        if (list == null) return;
        db.collection("submissions").whereEqualTo("residentId", user.getUid()).limit(20).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> Long.compare(safeLong(b, "createdAt"), safeLong(a, "createdAt")));
                    list.removeAllViews();
                    if (docs.isEmpty()) {
                        list.addView(infoRow("No submissions yet", "Submit a photo to earn points"));
                        return;
                    }
                    for (int i = 0; i < Math.min(3, docs.size()); i++) {
                        DocumentSnapshot doc = docs.get(i);
                        list.addView(infoRow(describeStatus(doc.getString("status")) + " \u00b7 +" + safeLong(doc, "points") + " pts",
                                formatDate(safeLong(doc, "createdAt"))));
                    }
                });
    }

    private void loadPointsStreak() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
            TextView pointsView = findViewById(R.id.tvPoints);
            TextView streakView = findViewById(R.id.tvCurrentStreak);
            if (pointsView != null) pointsView.setText(String.valueOf(safeLong(snapshot, "points")));
            if (streakView != null) streakView.setText(safeLong(snapshot, "streak") + " Days");
        });

        for (int id : new int[]{R.id.activityWasteDisposal, R.id.activityBinSetup, R.id.activitySignupBonus}) {
            View sample = findViewById(id);
            if (sample != null) sample.setVisibility(View.GONE);
        }
        LinearLayout list = findViewById(R.id.liveActivityList);
        if (list == null) return;
        db.collection("submissions").whereEqualTo("residentId", user.getUid()).limit(30).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> Long.compare(safeLong(b, "createdAt"), safeLong(a, "createdAt")));
                    list.removeAllViews();
                    if (docs.isEmpty()) {
                        list.addView(infoRow("No activity yet", "Submit a photo to start earning points"));
                        return;
                    }
                    for (DocumentSnapshot doc : docs) {
                        list.addView(infoRow(describeStatus(doc.getString("status")) + " \u00b7 +" + safeLong(doc, "points") + " pts",
                                formatDate(safeLong(doc, "createdAt"))));
                    }
                });
    }

    private void setLeaderboardScope(String scope) {
        leaderboardScope = scope;
        TextView locality = findViewById(R.id.btnLocality);
        TextView city = findViewById(R.id.btnCity);
        if (locality != null && city != null) {
            boolean isLocality = "locality".equals(scope);
            locality.setBackgroundResource(isLocality ? R.drawable.bg_toggle_selected : 0);
            locality.setTextColor(Color.parseColor(isLocality ? "#222222" : "#777777"));
            city.setBackgroundResource(!isLocality ? R.drawable.bg_toggle_selected : 0);
            city.setTextColor(Color.parseColor(!isLocality ? "#222222" : "#777777"));
        }
        loadLeaderboard();
    }

    // NOTE: every resident currently gets the same default locality ("Bengaluru") at signup
    // because the registration screens (per the design) don't collect one, so this tab will
    // largely mirror City until residents' locality fields actually diverge.
    private void loadLeaderboard() {
        LinearLayout list = findViewById(R.id.liveLeaderboardList);
        View staticSample = findViewById(R.id.staticLeaderboardSample);
        if (list == null || db == null) return;
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        String myUid = user == null ? null : user.getUid();

        Query global = db.collection("leaderboard").orderBy("points", Query.Direction.DESCENDING).limit(20);
        if ("locality".equals(leaderboardScope) && myUid != null) {
            db.collection("users").document(myUid).get().addOnSuccessListener(me -> {
                String locality = me.getString("locality");
                Query scoped = locality == null ? global
                        : db.collection("leaderboard").whereEqualTo("locality", locality)
                            .orderBy("points", Query.Direction.DESCENDING).limit(20);
                renderLeaderboard(scoped, list, staticSample, myUid, locality);
            });
        } else {
            renderLeaderboard(global, list, staticSample, myUid, null);
        }
    }

    private void renderLeaderboard(Query query, LinearLayout list, View staticSample, String myUid, String scopeLabel) {
        query.get().addOnSuccessListener(snapshot -> {
            list.removeAllViews();
            if (staticSample != null) staticSample.setVisibility(View.GONE);
            List<DocumentSnapshot> docs = snapshot.getDocuments();
            if (docs.isEmpty()) {
                list.addView(infoRow(scopeLabel == null ? "No residents ranked yet" : "No one in " + scopeLabel + " yet",
                        "Submit a waste report to appear here"));
                return;
            }
            int rank = 1;
            for (DocumentSnapshot doc : docs) {
                list.addView(leaderboardRow(rank, capitalize(doc.getString("name")), safeLong(doc, "points"), doc.getId().equals(myUid)));
                rank++;
            }
        }).addOnFailureListener(error -> {
            list.removeAllViews();
            list.addView(infoRow("Could not load leaderboard",
                    "Firestore may need a composite index \u2014 check Logcat for a create-index link"));
        });
    }

    private void setRewardsTab(String tab) {
        rewardsTab = tab;
        TextView active = findViewById(R.id.tabActive);
        TextView redeemed = findViewById(R.id.tabRedeemed);
        if (active != null && redeemed != null) {
            boolean isActive = "active".equals(tab);
            active.setBackgroundResource(isActive ? R.drawable.bg_tab_selected : 0);
            active.setTextColor(Color.parseColor(isActive ? "#20B968" : "#888888"));
            redeemed.setBackgroundResource(!isActive ? R.drawable.bg_tab_selected : 0);
            redeemed.setTextColor(Color.parseColor(!isActive ? "#20B968" : "#888888"));
        }
        loadMyRewards();
    }

    private void loadMyRewards() {
        View cardAmazon = findViewById(R.id.cardAmazon);
        View cardCafe = findViewById(R.id.cardCafeCoffeeDay);
        View cardRedeemedSample = findViewById(R.id.cardRedeemed);
        LinearLayout liveList = findViewById(R.id.liveRedeemedList);
        boolean showActive = "active".equals(rewardsTab);
        if (cardAmazon != null) cardAmazon.setVisibility(showActive ? View.VISIBLE : View.GONE);
        if (cardCafe != null) cardCafe.setVisibility(showActive ? View.VISIBLE : View.GONE);
        if (cardRedeemedSample != null) cardRedeemedSample.setVisibility(View.GONE);
        if (liveList == null) return;
        liveList.removeAllViews();
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (showActive || db == null || user == null) return;
        db.collection("redemptions").whereEqualTo("residentId", user.getUid()).limit(30).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> Long.compare(safeLong(b, "createdAt"), safeLong(a, "createdAt")));
                    if (docs.isEmpty()) {
                        liveList.addView(infoRow("No rewards redeemed yet", "Redeem a reward from the Active tab"));
                        return;
                    }
                    for (DocumentSnapshot doc : docs) {
                        liveList.addView(infoRow(doc.getString("rewardName") + " \u00b7 Code " + doc.getString("code"),
                                formatDate(safeLong(doc, "createdAt"))));
                    }
                });
    }

    private void redeemReward(String rewardName, int cost) {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) {
            toast(firebaseUnavailableMessage());
            return;
        }
        DocumentReference userRef = db.collection("users").document(user.getUid());
        String letters = rewardName.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.US);
        String code = letters.substring(0, Math.min(3, letters.length())) + (System.currentTimeMillis() % 100000);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot me = transaction.get(userRef);
            long currentPoints = safeLong(me, "points");
            if (currentPoints < cost) throw new RuntimeException("Not enough points");
            long newPoints = currentPoints - cost;

            Map<String, Object> update = new HashMap<>();
            update.put("points", newPoints);
            transaction.set(userRef, update, SetOptions.merge());

            Map<String, Object> board = new HashMap<>();
            board.put("points", newPoints);
            transaction.set(db.collection("leaderboard").document(user.getUid()), board, SetOptions.merge());

            Map<String, Object> redemption = new HashMap<>();
            redemption.put("residentId", user.getUid());
            redemption.put("rewardName", rewardName);
            redemption.put("pointsCost", (long) cost);
            redemption.put("code", code);
            redemption.put("createdAt", System.currentTimeMillis());
            transaction.set(db.collection("redemptions").document(), redemption);
            return null;
        }).addOnSuccessListener(done -> {
            show(R.layout.resident_redeemed);
            TextView codeView = findViewById(R.id.tvCouponCode);
            if (codeView != null) codeView.setText(code);
        }).addOnFailureListener(error -> toast("Redemption failed: you need " + cost + " points"));
    }

    private void loadAdminDashboard() {
        if (db == null) return;
        db.collection("users").whereEqualTo("role", "resident").get()
                .addOnSuccessListener(snapshot -> {
                    TextView residents = findViewById(R.id.tvAdminResidents);
                    if (residents != null) residents.setText(String.valueOf(snapshot.size()));
                });
        db.collection("submissions").whereEqualTo("status", "pending").get()
                .addOnSuccessListener(snapshot -> {
                    TextView pending = findViewById(R.id.tvAdminPending);
                    if (pending != null) pending.setText(String.valueOf(snapshot.size()));
                });
    }

    private void loadCollectorDashboard() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
            TextView statusView = findViewById(R.id.tvStatus);
            TextView toggleBtn = findViewById(R.id.btnToggleStatus);
            boolean online = "online".equals(snapshot.getString("status"));
            if (statusView != null) statusView.setText(online ? "Online" : "Offline");
            if (toggleBtn != null) toggleBtn.setText(online ? "Go Offline" : "Go Online");
        });
        db.collection("submissions").whereEqualTo("collectorId", user.getUid()).limit(200).get()
                .addOnSuccessListener(snapshot -> {
                    TextView total = findViewById(R.id.tvTotalCollections);
                    TextView completed = findViewById(R.id.tvCompleted);
                    int completedCount = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if ("completed".equals(doc.getString("status"))) completedCount++;
                    }
                    if (total != null) total.setText(String.valueOf(snapshot.size()));
                    if (completed != null) completed.setText(String.valueOf(completedCount));
                });
    }

    private void toggleCollectorStatus() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        DocumentReference ref = db.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            boolean goingOnline = !"online".equals(snapshot.getString("status"));
            Map<String, Object> update = new HashMap<>();
            update.put("status", goingOnline ? "online" : "offline");
            ref.set(update, SetOptions.merge())
                    .addOnSuccessListener(done -> loadCollectorDashboard())
                    .addOnFailureListener(error -> toast("Could not update status"));
        });
    }

    private long safeLong(DocumentSnapshot doc, String field) {
        Long value = doc.getLong(field);
        return value == null ? 0L : value;
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return "Resident";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String describeStatus(String status) {
        if (status == null) return "Submitted";
        switch (status) {
            case "pending": return "Awaiting collector";
            case "assigned": return "Collector on the way";
            case "completed": return "Collected";
            case "approved": return "Approved";
            case "rejected": return "Rejected";
            default: return status;
        }
    }

    private String formatDate(long millis) {
        if (millis == 0) return "";
        return new SimpleDateFormat("d MMM, h:mm a", Locale.US).format(new Date(millis));
    }

    private LinearLayout infoRow(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_white_card);
        row.setPadding(18, 14, 18, 14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 10);
        row.setLayoutParams(params);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.rgb(17, 24, 39));
        titleView.setTextSize(14);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(Color.rgb(107, 114, 128));
        subtitleView.setTextSize(12);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(0, 4, 0, 0);
        row.addView(subtitleView, subtitleParams);
        return row;
    }

    private LinearLayout leaderboardRow(int rank, String name, long points, boolean isMe) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(18, 14, 18, 14);
        row.setBackgroundResource(R.drawable.bg_white_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 10);
        row.setLayoutParams(params);

        TextView rankView = new TextView(this);
        rankView.setText("#" + rank);
        rankView.setTextColor(Color.rgb(107, 114, 128));
        rankView.setTextSize(13);
        rankView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(rankView, new LinearLayout.LayoutParams(60, -2));

        TextView nameView = new TextView(this);
        nameView.setText(isMe ? name + " (You)" : name);
        nameView.setTextColor(Color.rgb(17, 24, 39));
        nameView.setTextSize(14);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1));

        TextView pointsView = new TextView(this);
        pointsView.setText(points + " pts");
        pointsView.setTextColor(Color.rgb(32, 185, 104));
        pointsView.setTextSize(13);
        pointsView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(pointsView);
        return row;
    }

    private void startGoogleSignIn() {
        if (googleSignInClient == null) {
            toast("Google Sign-In isn't configured yet \u2014 see SETUP notes");
            return;
        }
        startActivityForResult(googleSignInClient.getSignInIntent(), GOOGLE_SIGN_IN_REQUEST);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (auth == null || db == null) {
            toast(firebaseUnavailableMessage());
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnSuccessListener(result -> {
            FirebaseUser user = result.getUser();
            if (user == null) return;
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    routeUser(user);
                } else {
                    createGoogleResidentProfile(user);
                }
            }).addOnFailureListener(error -> toast("Could not load your profile"));
        }).addOnFailureListener(error -> toast("Google sign-in failed: " + error.getMessage()));
    }

    // First time signing in with Google always creates a resident account \u2014 there's no
    // role picker in the Google flow. If you need a collector/admin account, create it with
    // email/password first, then Google sign-in on that same email will just log in normally.
    private void createGoogleResidentProfile(FirebaseUser user) {
        String displayName = user.getDisplayName() != null ? user.getDisplayName()
                : (user.getEmail() != null ? user.getEmail() : "Resident");
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", user.getUid());
        profile.put("email", user.getEmail());
        profile.put("role", "resident");
        profile.put("createdAt", System.currentTimeMillis());
        profile.put("points", 0L);
        profile.put("streak", 0L);
        profile.put("locality", DEFAULT_LOCALITY);
        profile.put("name", displayName);
        db.collection("users").document(user.getUid()).set(profile)
                .addOnSuccessListener(done -> {
                    Map<String, Object> board = new HashMap<>();
                    board.put("name", displayName);
                    board.put("points", 0L);
                    board.put("streak", 0L);
                    board.put("locality", DEFAULT_LOCALITY);
                    db.collection("leaderboard").document(user.getUid()).set(board);
                    routeUser(user);
                })
                .addOnFailureListener(error -> toast("Signed in, but profile setup failed"));
    }

    private void setupLiveMap() {
        if (BuildConfig.MAPS_API_KEY.startsWith("YOUR_")) return;
        FrameLayout container = findViewById(R.id.mapContainer);
        if (container == null) return;
        View designMap = container.getChildAt(0);
        if (mapView == null) {
            if (designMap != null) container.removeView(designMap);
            mapView = new MapView(this);
            container.addView(mapView, 0);
            mapView.onCreate(null);
            mapView.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        LatLng bengaluru = new LatLng(12.9716, 77.5946);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(bengaluru, 13f));
        map.addMarker(new MarkerOptions().position(bengaluru).title("Current collection area"));
        map.addMarker(new MarkerOptions().position(new LatLng(12.975, 77.601)).title("Pickup request"));
        map.getUiSettings().setZoomControlsEnabled(false);
    }

    private void enableMyLocation() {
        if (googleMap == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        googleMap.setMyLocationEnabled(true);
    }

    private void link(int id, Runnable action) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(v -> action.run());
    }

    private String firebaseUnavailableMessage() {
        return firebaseInitError == null
                ? "Firebase is not ready. Check google-services.json and sync Gradle."
                : firebaseInitError + ". Check google-services.json and sync Gradle.";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (mapView != null) mapView.onDestroy(); super.onDestroy(); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean shouldOpen = waitingForCameraPermission;
            waitingForCameraPermission = false;
            if (granted && shouldOpen) takePhoto();
            else if (!granted) toast("Camera permission is required to take a waste photo.");
        } else if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enableMyLocation();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    public void onBackPressed() {
        if (currentLayout != R.layout.activity_login) show(R.layout.activity_login);
        else super.onBackPressed();
    }
}
