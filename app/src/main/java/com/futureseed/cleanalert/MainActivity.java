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
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.AggregateField;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.messaging.FirebaseMessaging;
import android.os.Build;
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

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
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
    private static final int NOTIFICATION_PERMISSION_REQUEST = 44;
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
    private String selectedWasteType = "Dry Recyclable";
    private Double capturedLat;
    private Double capturedLng;
    private String capturedAddress;
    private FusedLocationProviderClient fusedLocationClient;
    private String meName, meEmail, meRole, meLocality;
    private LatLng selectedTarget;
    private LocationCallback broadcastCallback;
    private boolean broadcastingLocation = false;
    private ListenerRegistration liveCollectorsListener;
    private final Map<String, com.google.android.gms.maps.model.Marker> liveCollectorMarkers = new HashMap<>();
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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
        if (currentLayout == R.layout.collector_map) teardownMap();
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
        if (layout == R.layout.resident_submit_waste) captureLocationForSubmission();
        if (layout == R.layout.resident_past_submissions) loadPastSubmissions("all");
        if (layout == R.layout.collector_profile) loadCollectorProfile();
        if (layout == R.layout.collection_history) loadCollectionHistory(null);
        if (layout == R.layout.resident_rewards) loadRewardsCatalog();
        if (layout == R.layout.resident_profile) loadResidentProfile();
        if (layout == R.layout.resident_collection_alert) loadCollectionAlert();
        applyIdentity();
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
        link(R.id.btnEditProfile, () -> showEditProfile(R.layout.resident_profile));
        link(R.id.btnNotifications, () -> showNotificationSettings(R.layout.resident_profile));
        link(R.id.btnSettings, () -> showSettings(R.layout.resident_profile));
        link(R.id.btnRemarkFeedback, () -> showFeedback(R.layout.collector_profile));
        link(R.id.btnChangePassword, () -> showChangePassword(R.layout.collector_profile));
        link(R.id.btnShiftSettings, () -> showSettings(R.layout.collector_profile));
        link(R.id.btnAdminUsers, this::showAdminUsers);
        link(R.id.btnAdminAssignRoutes, this::showAdminAssignRoutes);
        link(R.id.btnAdminRewards, this::showAdminRewards);
        link(R.id.btnAdminReports, this::showAdminReports);
        link(R.id.btnGoogle, this::startGoogleSignIn);
        link(R.id.btnCreateAccount, this::createAccount);
        link(R.id.btnSubmitWaste, () -> show(R.layout.resident_submit_waste));
        link(R.id.btnPickPhoto, this::pickPhoto);
        link(R.id.btnTakePhoto, this::takePhoto);
        link(R.id.btnSubmitWasteFinal, this::submitWaste);
        link(R.id.catDry, () -> setWasteCategory("Dry Recyclable"));
        link(R.id.catOrganic, () -> setWasteCategory("Organic (Wet)"));
        link(R.id.catEwaste, () -> setWasteCategory("E-Waste"));
        link(R.id.catOther, () -> setWasteCategory("Other"));
        link(R.id.filterAll, () -> loadPastSubmissions("all"));
        link(R.id.filterOrganic, () -> loadPastSubmissions("organic"));
        wireCollectionHistorySearch();
        link(R.id.btnHome, () -> show(R.layout.resident_home));
        link(R.id.tvCollector, () -> show(R.layout.activity_register_collector));
        link(R.id.tvResident, () -> show(R.layout.activity_register_resident));
        link(R.id.btnSendResetLink, this::sendPasswordReset);
        link(R.id.btnBackToLogin, () -> show(R.layout.activity_login));
        link(R.id.ivBack, () -> show(R.layout.activity_login));
        link(R.id.btnBack, this::goBack);

        link(R.id.navDashboard, () -> show(R.layout.collector_dashboard));
        link(R.id.navMap, () -> show(R.layout.collector_map));
        link(R.id.navHistory, () -> show(R.layout.collection_history));
        link(R.id.navProfile, () -> show(R.layout.collector_profile));
        link(R.id.btnToggleStatus, this::toggleCollectorStatus);


        link(R.id.navHome, () -> show(R.layout.resident_home));
        link(R.id.navResHistory, () -> show(R.layout.resident_past_submissions));
        link(R.id.navRewards, () -> show(R.layout.resident_rewards));
        link(R.id.navResProfile, () -> show(R.layout.resident_profile));
        link(R.id.openPointsStreak, () -> show(R.layout.resident_points_streak));
        link(R.id.tvHomeStreak, () -> show(R.layout.resident_leaderboard));
        link(R.id.btnNavigate, this::openNavigation);
        link(R.id.btnLogout, this::logout);
        link(R.id.adminLogout, this::logout);
        link(R.id.btnAdminSubmissions, () -> { adminListMode = "submissions"; show(R.layout.admin_submissions); });
        link(R.id.btnAdminBack, () -> show(R.layout.admin_dashboard));
        link(R.id.btnAdminQueueLogout, this::logout);
        link(R.id.btnCollectorBack, () -> show(R.layout.collector_dashboard));
        link(R.id.btnViewDetails, () -> show(R.layout.collector_profile));

        link(R.id.btnLocality, () -> setLeaderboardScope("locality"));
        link(R.id.btnCity, () -> setLeaderboardScope("city"));
        link(R.id.tabActive, () -> setRewardsTab("active"));
        link(R.id.tabRedeemed, () -> setRewardsTab("redeemed"));
        link(R.id.btnViewAll, () -> show(R.layout.resident_my_rewards));
        link(R.id.btnDone, () -> show(R.layout.resident_my_rewards));
        link(R.id.btnViewMyRewards, () -> show(R.layout.resident_my_rewards));
        link(R.id.btnBackToRewards, () -> show(R.layout.resident_rewards));

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
                    if (ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) role = "admin";
                    meRole = role == null ? "resident" : role;
                    meEmail = user.getEmail();
                    meName = displayName(snapshot, user.getEmail());
                    meLocality = snapshot.getString("locality");
                    registerFcmToken(user);
                    if ("admin".equals(meRole)) {
                        show(R.layout.admin_dashboard);
                    } else if ("collector".equals(meRole)) {
                        show(R.layout.collector_dashboard);
                    } else {
                        show(R.layout.resident_home);
                    }
                })
                .addOnFailureListener(error -> toast("Could not load your profile"));
    }

    private String displayName(DocumentSnapshot snapshot, String email) {
        String name = snapshot.getString("name");
        if (name != null && !name.trim().isEmpty()) return name.trim();
        if (email != null && email.contains("@")) return email.substring(0, email.indexOf('@'));
        return email == null ? "User" : email;
    }

    private void applyIdentity() {
        View root = findViewById(android.R.id.content);
        if (root != null) applyIdentityTo(root);
    }

    private void applyIdentityTo(View v) {
        if (v instanceof TextView && "avatarInitials".equals(v.getTag())) {
            ((TextView) v).setText(initials(meName, meEmail));
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) applyIdentityTo(group.getChildAt(i));
        }
    }

    private String initials(String name, String email) {
        String source = name != null && !name.isEmpty() ? name : (email == null ? "?" : email);
        String[] parts = source.trim().split("[\\s@._]+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length && out.length() < 2; i++) {
            if (!parts[i].isEmpty()) out.append(parts[i].charAt(0));
        }
        return out.length() == 0 ? "?" : out.toString().toUpperCase(Locale.US);
    }

    private void registerFcmToken(FirebaseUser user) {
        CleanAlertMessagingService.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            Map<String, Object> update = new HashMap<>();
            update.put("fcmToken", token);
            db.collection("users").document(user.getUid()).set(update, SetOptions.merge());
        });
    }

    private void goBack() {
        show("resident".equals(meRole) ? R.layout.resident_home : R.layout.activity_login);
    }

    private void logout() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if ("collector".equals(meRole) && user != null && db != null) {
            Map<String, Object> off = new HashMap<>();
            off.put("status", "offline");
            db.collection("users").document(user.getUid()).set(off, SetOptions.merge());
            db.collection("collectorLocations").document(user.getUid()).set(off, SetOptions.merge());
        }
        stopLocationBroadcast();
        if (auth != null) auth.signOut();
        meName = meEmail = meRole = meLocality = null;
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

    private void setWasteCategory(String category) {
        selectedWasteType = category;
        java.util.Map<Integer, String> ids = new java.util.LinkedHashMap<>();
        ids.put(R.id.catDry, "Dry Recyclable");
        ids.put(R.id.catOrganic, "Organic (Wet)");
        ids.put(R.id.catEwaste, "E-Waste");
        ids.put(R.id.catOther, "Other");
        for (Map.Entry<Integer, String> entry : ids.entrySet()) {
            TextView chip = findViewById(entry.getKey());
            if (chip == null) continue;
            boolean selected = entry.getValue().equals(category);
            chip.setBackgroundResource(selected ? R.drawable.bg_toggle_selected : 0);
            chip.setTextColor(Color.parseColor(selected ? "#222222" : "#777777"));
        }
    }

    private void captureLocationForSubmission() {
        capturedLat = null;
        capturedLng = null;
        capturedAddress = null;
        setWasteCategory(selectedWasteType);
        TextView statusView = findViewById(R.id.tvLocationStatus);
        if (statusView != null) statusView.setText("Getting your location\u2026");

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        if (fusedLocationClient == null) {
            if (statusView != null) statusView.setText("Location services unavailable on this device");
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        if (statusView != null) statusView.setText("Location unavailable \u2014 turn on GPS and try again");
                        return;
                    }
                    capturedLat = location.getLatitude();
                    capturedLng = location.getLongitude();
                    resolveAddress(location, statusView);
                })
                .addOnFailureListener(error -> {
                    if (statusView != null) statusView.setText("Could not get location: " + error.getMessage());
                });
    }

    private void resolveAddress(Location location, TextView statusView) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            @SuppressWarnings("deprecation")
            List<Address> results = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (results != null && !results.isEmpty()) {
                Address address = results.get(0);
                StringBuilder line = new StringBuilder();
                if (address.getThoroughfare() != null) line.append(address.getThoroughfare());
                else if (address.getSubLocality() != null) line.append(address.getSubLocality());
                else if (address.getLocality() != null) line.append(address.getLocality());
                if (address.getLocality() != null && line.indexOf(address.getLocality()) < 0) {
                    if (line.length() > 0) line.append(", ");
                    line.append(address.getLocality());
                }
                capturedAddress = line.length() > 0 ? line.toString() : address.getAddressLine(0);
            }
        } catch (Exception ignored) {
            // Geocoder can be unavailable on some devices/emulators \u2014 fall back to raw coordinates below.
        }
        if (capturedAddress == null) {
            capturedAddress = String.format(Locale.US, "%.4f, %.4f", capturedLat, capturedLng);
        }
        if (statusView != null) statusView.setText("Location: " + capturedAddress);
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
                    submission.put("wasteType", selectedWasteType);
                    if (capturedLat != null) submission.put("latitude", capturedLat);
                    if (capturedLng != null) submission.put("longitude", capturedLng);
                    if (capturedAddress != null) submission.put("address", capturedAddress);
                    db.collection("submissions").document(submissionId).set(submission)
                            .addOnSuccessListener(done -> {
                                selectedPhotoUri = null;
                                capturedPhotoBitmap = null;
                                capturedLat = null;
                                capturedLng = null;
                                capturedAddress = null;
                                selectedWasteType = "Dry Recyclable";
                                show(R.layout.resident_submission_success);
                            })
                            .addOnFailureListener(error -> toast("Photo uploaded, but submission record failed"));
                })
                .addOnFailureListener(error -> toast(error.getMessage() == null ? "Photo upload failed" : error.getMessage()));
    }

    private void loadCollectorRequests() {
        loadCollectorRequestsInto(findViewById(R.id.collectorRequestList), findViewById(R.id.tvCollectorQueueStatus), 30);
    }

    private void loadCollectorRequestsInto(LinearLayout list, TextView status, int max) {
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
                        if (shown >= max) break;
                        String state = doc.getString("status");
                        String collectorId = doc.getString("collectorId");
                        boolean mine = myUid != null && myUid.equals(collectorId);
                        if ("assigned".equals(state) && !mine) continue; // claimed by someone else
                        addSubmissionCard(list, doc.getId(), doc.getString("residentId"), state, false);
                        shown++;
                    }
                    if (status != null) status.setText(shown == 0 ? "No pending collection requests right now." : shown + " request(s)");
                    if (shown == 0) list.addView(infoRow("No collections assigned yet", "New pickups will show up here"));
                })
                .addOnFailureListener(error -> {
                    if (status != null) status.setText("Could not load requests. Check Firestore rules.");
                });
    }

    private String adminListMode = "submissions";

    private void showAdminUsers() { adminListMode = "users"; show(R.layout.admin_submissions); }
    private void showAdminAssignRoutes() { adminListMode = "assign_routes"; show(R.layout.admin_submissions); }
    private void showAdminRewards() { adminListMode = "rewards"; show(R.layout.admin_submissions); }
    private void showAdminReports() { adminListMode = "reports"; show(R.layout.admin_submissions); }

    private void loadAdminSubmissions() {
        TextView title = findViewById(R.id.tvAdminListTitle);
        switch (adminListMode) {
            case "users":
                if (title != null) title.setText("Users & Collectors");
                loadAdminUsers();
                return;
            case "assign_routes":
                if (title != null) title.setText("Assign Collectors Route");
                loadAdminAssignRoutesList();
                return;
            case "rewards":
                if (title != null) title.setText("Rewards Catalogue");
                loadAdminRewardsList();
                return;
            case "reports":
                if (title != null) title.setText("Reports");
                loadAdminReports();
                return;
            default:
                if (title != null) title.setText("Review Submissions");
        }
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

    private void loadAdminUsers() {
        LinearLayout list = findViewById(R.id.adminSubmissionList);
        TextView status = findViewById(R.id.tvAdminQueueStatus);
        if (list == null || db == null) return;
        db.collection("users").limit(200).get()
                .addOnSuccessListener(snapshot -> {
                    list.removeAllViews();
                    List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> String.valueOf(a.getString("role")).compareTo(String.valueOf(b.getString("role"))));
                    if (status != null) status.setText(docs.size() + " user(s)");
                    if (docs.isEmpty()) list.addView(infoRow("No users yet", ""));
                    for (DocumentSnapshot doc : docs) {
                        String role = doc.getString("role");
                        list.addView(infoRow(capitalize(doc.getString("name")) + "  •  " + (role == null ? "resident" : role),
                                String.valueOf(doc.getString("email"))));
                    }
                })
                .addOnFailureListener(error -> { if (status != null) status.setText("Could not load users."); });
    }

    private void loadAdminAssignRoutesList() {
        LinearLayout list = findViewById(R.id.adminSubmissionList);
        TextView status = findViewById(R.id.tvAdminQueueStatus);
        if (list == null || db == null) return;
        db.collection("users").whereEqualTo("role", "collector").get()
                .addOnSuccessListener(snapshot -> {
                    list.removeAllViews();
                    List<DocumentSnapshot> collectors = snapshot.getDocuments();
                    if (status != null) status.setText(collectors.size() + " collector(s) registered");
                    if (collectors.isEmpty()) {
                        list.addView(infoRow("No collectors found", "Register a collector account first"));
                        return;
                    }
                    for (DocumentSnapshot doc : collectors) {
                        String collectorUid = doc.getId();
                        String name = capitalize(doc.getString("name"));
                        String email = doc.getString("email");
                        addAdminCollectorRouteRow(list, collectorUid, name, email);
                    }
                })
                .addOnFailureListener(error -> { if (status != null) status.setText("Could not load collectors list."); });
    }

    private void addAdminCollectorRouteRow(LinearLayout list, String collectorUid, String name, String email) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_white_card);
        card.setPadding(18, 16, 18, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 12);
        card.setLayoutParams(params);

        TextView nameView = new TextView(this);
        nameView.setText(name + " (" + (email == null ? "" : email) + ")");
        nameView.setTextColor(Color.rgb(17, 24, 39));
        nameView.setTextSize(15);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(nameView);

        TextView routeInfo = new TextView(this);
        routeInfo.setText("Loading route…");
        routeInfo.setTextColor(Color.rgb(107, 114, 128));
        routeInfo.setTextSize(12);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.setMargins(0, 6, 0, 10);
        card.addView(routeInfo, infoParams);

        TextView assignBtn = queueButton("Assign / Edit Route");
        card.addView(assignBtn, new LinearLayout.LayoutParams(-1, 44));

        list.addView(card);

        db.collection("collectorRoutes").document(collectorUid).get()
                .addOnSuccessListener(routeDoc -> {
                    if (routeDoc.exists() && routeDoc.contains("area")) {
                        String area = routeDoc.getString("area");
                        String streets = routeDoc.getString("streets");
                        String shift = routeDoc.getString("shift");
                        routeInfo.setText("Route: " + area + "\nStreets: " + (streets == null ? "None" : streets) + "\nShift: " + (shift == null ? "Not set" : shift));
                        assignBtn.setOnClickListener(v -> showAssignRouteDialog(collectorUid, name, area, streets, shift));
                    } else {
                        routeInfo.setText("No route assigned yet");
                        assignBtn.setOnClickListener(v -> showAssignRouteDialog(collectorUid, name, "", "", ""));
                    }
                })
                .addOnFailureListener(err -> {
                    routeInfo.setText("No route assigned yet");
                    assignBtn.setOnClickListener(v -> showAssignRouteDialog(collectorUid, name, "", "", ""));
                });
    }

    private void showAssignRouteDialog(String collectorUid, String collectorName, String currentArea, String currentStreets, String currentShift) {
        android.widget.LinearLayout box = dialogFieldBox();
        android.widget.EditText areaField = dialogField(box, "Route Area / Sector Name", currentArea);
        android.widget.EditText streetsField = dialogField(box, "Covered Streets / Landmarks", currentStreets);
        android.widget.EditText shiftField = dialogField(box, "Shift Timings (e.g., 7 AM - 1 PM)", currentShift);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Assign Route: " + collectorName)
                .setView(box)
                .setPositiveButton("Save Route", (d, w) -> {
                    String area = areaField.getText().toString().trim();
                    String streets = streetsField.getText().toString().trim();
                    String shift = shiftField.getText().toString().trim();
                    if (area.isEmpty()) {
                        toast("Enter a route area / sector name");
                        return;
                    }
                    Map<String, Object> routeData = new HashMap<>();
                    routeData.put("collectorUid", collectorUid);
                    routeData.put("collectorName", collectorName);
                    routeData.put("area", area);
                    routeData.put("streets", streets);
                    routeData.put("shift", shift);
                    routeData.put("updatedAt", System.currentTimeMillis());
                    routeData.put("updatedBy", meEmail);

                    db.collection("collectorRoutes").document(collectorUid).set(routeData, SetOptions.merge())
                            .addOnSuccessListener(done -> {
                                toast("Route updated for " + collectorName);
                                loadAdminAssignRoutesList();
                            })
                            .addOnFailureListener(error -> toast("Failed to update route. Check Firestore rules."));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadAdminRewardsList() {
        LinearLayout list = findViewById(R.id.adminSubmissionList);
        TextView status = findViewById(R.id.tvAdminQueueStatus);
        if (list == null || db == null) return;
        list.removeAllViews();
        TextView addButton = queueButton("+ Add reward");
        list.addView(addButton, new LinearLayout.LayoutParams(-1, 46));
        addButton.setOnClickListener(v -> showAddRewardDialog());
        db.collection("rewards").limit(100).get()
                .addOnSuccessListener(snapshot -> {
                    if (status != null) status.setText(snapshot.size() + " reward(s)");
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        list.addView(adminRewardRow(doc.getId(), doc.getString("name"), safeLong(doc, "pointsRequired"),
                                !Boolean.FALSE.equals(doc.getBoolean("active"))));
                    }
                })
                .addOnFailureListener(error -> { if (status != null) status.setText("Could not load rewards."); });
    }

    private void showAddRewardDialog() {
        android.widget.LinearLayout box = dialogFieldBox();
        android.widget.EditText nameField = dialogField(box, "Reward name", null);
        android.widget.EditText descField = dialogField(box, "Description", null);
        android.widget.EditText pointsField = dialogField(box, "Points required", null);
        pointsField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add reward")
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    String name = nameField.getText().toString().trim();
                    long points;
                    try { points = Long.parseLong(pointsField.getText().toString().trim()); }
                    catch (Exception e) { toast("Enter a valid points number"); return; }
                    if (name.isEmpty()) { toast("Enter a name"); return; }
                    Map<String, Object> reward = new HashMap<>();
                    reward.put("name", name);
                    reward.put("description", descField.getText().toString().trim());
                    reward.put("pointsRequired", points);
                    reward.put("active", true);
                    reward.put("createdAt", System.currentTimeMillis());
                    db.collection("rewards").document().set(reward)
                            .addOnSuccessListener(done -> { toast("Reward added"); loadAdminRewardsList(); })
                            .addOnFailureListener(error -> toast("Could not add reward"));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout adminRewardRow(String rewardId, String name, long points, boolean active) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_white_card);
        row.setPadding(18, 16, 18, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 10, 0, 0);
        row.setLayoutParams(params);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = new TextView(this);
        nameView.setText((name == null ? "Reward" : name) + (active ? "" : "  (inactive)"));
        nameView.setTextColor(active ? Color.rgb(17, 24, 39) : Color.rgb(156, 163, 175));
        nameView.setTextSize(14);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(nameView);
        TextView costView = new TextView(this);
        costView.setText(points + " points");
        costView.setTextColor(Color.rgb(107, 114, 128));
        costView.setTextSize(12);
        col.addView(costView);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        TextView toggle = queueButton(active ? "Deactivate" : "Activate");
        toggle.setOnClickListener(v -> {
            Map<String, Object> update = new HashMap<>();
            update.put("active", !active);
            db.collection("rewards").document(rewardId).set(update, SetOptions.merge())
                    .addOnSuccessListener(done -> loadAdminRewardsList());
        });
        row.addView(toggle, new LinearLayout.LayoutParams(-2, 44));
        return row;
    }

    // Real aggregate counts computed from Firestore — no charts library, no invented numbers.
    private void loadAdminReports() {
        LinearLayout list = findViewById(R.id.adminSubmissionList);
        TextView status = findViewById(R.id.tvAdminQueueStatus);
        if (list == null || db == null) return;
        list.removeAllViews();
        if (status != null) status.setText("Live counts from Firestore");

        countInto(list, db.collection("users").whereEqualTo("role", "resident"), "Total residents");
        countInto(list, db.collection("users").whereEqualTo("role", "collector"), "Total collectors");
        countInto(list, db.collection("submissions"), "Total waste submissions");
        countInto(list, db.collection("submissions").whereEqualTo("status", "pending"), "Pending review");
        countInto(list, db.collection("submissions").whereEqualTo("status", "completed"), "Collections completed");
        countInto(list, db.collection("redemptions"), "Rewards redeemed");

        db.collection("submissions").whereEqualTo("status", "approved")
                .aggregate(AggregateField.sum("points")).get(AggregateSource.SERVER)
                .addOnSuccessListener(r -> list.addView(infoRow("Total points awarded", String.valueOf(r.get(AggregateField.sum("points"))))))
                .addOnFailureListener(error -> list.addView(infoRow("Total points awarded", "unavailable")));
    }

    private void countInto(LinearLayout list, Query query, String label) {
        query.count().get(AggregateSource.SERVER)
                .addOnSuccessListener(r -> list.addView(infoRow(label, String.valueOf(r.getCount()))))
                .addOnFailureListener(error -> list.addView(infoRow(label, "unavailable")));
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
        if ("locality".equals(leaderboardScope) && meLocality != null) {
            Query scoped = db.collection("leaderboard").whereEqualTo("locality", meLocality)
                    .orderBy("points", Query.Direction.DESCENDING).limit(20);
            renderLeaderboard(scoped, list, staticSample, myUid, meLocality);
        } else {
            renderLeaderboard(global, list, staticSample, myUid,
                    "locality".equals(leaderboardScope) ? "your locality (not set yet)" : null);
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
        LinearLayout liveActive = findViewById(R.id.liveActiveRewardsList);
        LinearLayout liveRedeemed = findViewById(R.id.liveRedeemedList);
        boolean showActive = "active".equals(rewardsTab);
        if (liveActive != null) liveActive.setVisibility(showActive ? View.VISIBLE : View.GONE);
        if (liveRedeemed != null) liveRedeemed.setVisibility(showActive ? View.GONE : View.VISIBLE);

        if (showActive) {
            renderRewardCatalog(liveActive);
            return;
        }
        if (liveRedeemed == null) return;
        liveRedeemed.removeAllViews();
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (db == null || user == null) return;
        db.collection("redemptions").whereEqualTo("residentId", user.getUid()).limit(30).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> Long.compare(safeLong(b, "createdAt"), safeLong(a, "createdAt")));
                    if (docs.isEmpty()) {
                        liveRedeemed.addView(infoRow("No rewards redeemed yet", "Redeem a reward from the Active tab"));
                        return;
                    }
                    for (DocumentSnapshot doc : docs) {
                        liveRedeemed.addView(infoRow(doc.getString("rewardName") + " \u00b7 Code " + doc.getString("code"),
                                formatDate(safeLong(doc, "createdAt"))));
                    }
                });
    }

    private void loadRewardsCatalog() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user != null && db != null) {
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
                TextView balance = findViewById(R.id.tvRewardsBalance);
                if (balance != null) balance.setText(String.valueOf(safeLong(snapshot, "points")));
            });
        }
        renderRewardCatalog(findViewById(R.id.liveRewardsCatalog));
    }

    // Shared by the main Rewards screen and the My Rewards "Active" tab \u2014 both show
    // whatever reward documents actually exist in Firestore, nothing invented.
    private void renderRewardCatalog(LinearLayout container) {
        if (container == null || db == null) return;
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        long[] myPoints = {0L};
        Runnable renderWithPoints = () -> db.collection("rewards").whereEqualTo("active", true).limit(30).get()
                .addOnSuccessListener(snapshot -> {
                    container.removeAllViews();
                    List<DocumentSnapshot> docs = snapshot.getDocuments();
                    if (docs.isEmpty()) {
                        container.addView(infoRow("No rewards available right now", "Check back later"));
                        return;
                    }
                    for (DocumentSnapshot doc : docs) {
                        container.addView(rewardCard(doc.getId(), doc.getString("name"), doc.getString("description"),
                                safeLong(doc, "pointsRequired"), myPoints[0]));
                    }
                })
                .addOnFailureListener(error -> {
                    container.removeAllViews();
                    container.addView(infoRow("Could not load rewards", "Check Firestore rules/connection"));
                });
        if (user != null) {
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
                myPoints[0] = safeLong(snapshot, "points");
                renderWithPoints.run();
            }).addOnFailureListener(error -> renderWithPoints.run());
        } else {
            renderWithPoints.run();
        }
    }

    private LinearLayout rewardCard(String rewardId, String name, String description, long cost, long myPoints) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_white_card);
        card.setPadding(18, 16, 18, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 10);
        card.setLayoutParams(params);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = new TextView(this);
        nameView.setText(name == null ? "Reward" : name);
        nameView.setTextColor(Color.rgb(17, 24, 39));
        nameView.setTextSize(14);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(nameView);
        if (description != null && !description.isEmpty()) {
            TextView descView = new TextView(this);
            descView.setText(description);
            descView.setTextColor(Color.rgb(107, 114, 128));
            descView.setTextSize(12);
            textCol.addView(descView);
        }
        TextView costView = new TextView(this);
        costView.setText(cost + " Points");
        costView.setTextColor(Color.rgb(32, 185, 104));
        costView.setTextSize(12);
        costView.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(costView);
        card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView redeemBtn = new TextView(this);
        boolean canAfford = myPoints >= cost;
        redeemBtn.setText("Redeem Now");
        redeemBtn.setTextSize(12);
        redeemBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        redeemBtn.setPadding(24, 14, 24, 14);
        redeemBtn.setBackgroundResource(R.drawable.bg_toggle_selected);
        redeemBtn.setTextColor(Color.parseColor(canAfford ? "#20B968" : "#AAAAAA"));
        if (canAfford) {
            redeemBtn.setOnClickListener(v -> redeemReward(rewardId, name == null ? "Reward" : name, (int) cost));
        }
        card.addView(redeemBtn);
        return card;
    }

    private void redeemReward(String rewardId, String rewardName, int cost) {
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
            redemption.put("rewardId", rewardId);
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
        LinearLayout pending = findViewById(R.id.liveDashboardPending);
        if (pending != null) loadCollectorRequestsInto(pending, null, 2);
        loadCollectorRoute();
    }

    private void loadCollectorRoute() {
        TextView areaView = findViewById(R.id.tvRouteArea);
        TextView streetsView = findViewById(R.id.tvRouteStreets);
        TextView shiftView = findViewById(R.id.tvRouteShift);
        if (areaView == null || db == null) return;

        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null) return;

        db.collection("collectorRoutes").document(user.getUid()).get()
                .addOnSuccessListener(routeDoc -> {
                    if (routeDoc.exists() && routeDoc.contains("area")) {
                        String area = routeDoc.getString("area");
                        String streets = routeDoc.getString("streets");
                        String shift = routeDoc.getString("shift");
                        areaView.setText(area == null || area.isEmpty() ? "Assigned Route" : area);
                        if (streetsView != null) streetsView.setText(streets == null || streets.isEmpty() ? "No street details specified" : "Streets: " + streets);
                        if (shiftView != null) shiftView.setText(shift == null || shift.isEmpty() ? "" : "Shift: " + shift);
                    } else {
                        areaView.setText("No route assigned yet");
                        if (streetsView != null) streetsView.setText("Contact your administrator to receive your daily collection route.");
                        if (shiftView != null) shiftView.setText("");
                    }
                })
                .addOnFailureListener(err -> {
                    areaView.setText("No route assigned yet");
                    if (streetsView != null) streetsView.setText("Contact your administrator to receive your daily collection route.");
                    if (shiftView != null) shiftView.setText("");
                });
    }

    private void toggleCollectorStatus() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        DocumentReference ref = db.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            boolean goingOnline = !"online".equals(snapshot.getString("status"));
            if (goingOnline && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
                toast("Location permission is needed to go online");
                return;
            }
            Map<String, Object> update = new HashMap<>();
            update.put("status", goingOnline ? "online" : "offline");
            ref.set(update, SetOptions.merge())
                    .addOnSuccessListener(done -> {
                        Map<String, Object> locUpdate = new HashMap<>();
                        locUpdate.put("status", goingOnline ? "online" : "offline");
                        locUpdate.put("name", snapshot.getString("name"));
                        db.collection("collectorLocations").document(user.getUid()).set(locUpdate, SetOptions.merge());
                        if (goingOnline) startLocationBroadcast(); else stopLocationBroadcast();
                        loadCollectorDashboard();
                    })
                    .addOnFailureListener(error -> toast("Could not update status"));
        });
    }

    // Pushes this collector's real position to Firestore every ~15s while they're online.
    // No simulated movement, no fixed coordinate \u2014 whatever the device's GPS actually reports.
    @SuppressWarnings("MissingPermission")
    private void startLocationBroadcast() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null || fusedLocationClient == null || broadcastingLocation) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        DocumentReference ref = db.collection("collectorLocations").document(user.getUid());
        broadcastCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;
                Map<String, Object> update = new HashMap<>();
                update.put("latitude", location.getLatitude());
                update.put("longitude", location.getLongitude());
                update.put("locationUpdatedAt", System.currentTimeMillis());
                update.put("status", "online");
                ref.set(update, SetOptions.merge());
            }
        };
        LocationRequest request = new LocationRequest.Builder(15000)
                .setMinUpdateIntervalMillis(10000)
                .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                .build();
        fusedLocationClient.requestLocationUpdates(request, broadcastCallback, getMainLooper());
        broadcastingLocation = true;
    }

    private void stopLocationBroadcast() {
        if (fusedLocationClient != null && broadcastCallback != null) {
            fusedLocationClient.removeLocationUpdates(broadcastCallback);
        }
        broadcastCallback = null;
        broadcastingLocation = false;
    }

    // Live markers for every OTHER online collector, kept in sync via a real-time Firestore
    // listener \u2014 markers actually move as their location documents update, nothing scripted.
    private void listenForLiveCollectors() {
        if (googleMap == null || db == null) return;
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        String myUid = user == null ? null : user.getUid();
        if (liveCollectorsListener != null) liveCollectorsListener.remove();
        liveCollectorsListener = db.collection("collectorLocations")
                .whereEqualTo("status", "online")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || googleMap == null) return;
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (doc.getId().equals(myUid)) continue; // don't mark yourself, that's the blue dot
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        if (lat == null || lng == null) continue;
                        seen.add(doc.getId());
                        String label = capitalize(doc.getString("name"));
                        com.google.android.gms.maps.model.Marker marker = liveCollectorMarkers.get(doc.getId());
                        LatLng pos = new LatLng(lat, lng);
                        if (marker == null) {
                            marker = googleMap.addMarker(new MarkerOptions().position(pos).title(label + " (online)")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                            liveCollectorMarkers.put(doc.getId(), marker);
                        } else {
                            marker.setPosition(pos);
                        }
                    }
                    // remove markers for collectors who went offline or stopped reporting
                    java.util.Iterator<Map.Entry<String, com.google.android.gms.maps.model.Marker>> it = liveCollectorMarkers.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, com.google.android.gms.maps.model.Marker> entry = it.next();
                        if (!seen.contains(entry.getKey())) {
                            entry.getValue().remove();
                            it.remove();
                        }
                    }
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
        profile.put("name", displayName);
        db.collection("users").document(user.getUid()).set(profile)
                .addOnSuccessListener(done -> {
                    Map<String, Object> board = new HashMap<>();
                    board.put("name", displayName);
                    board.put("points", 0L);
                    board.put("streak", 0L);
                    db.collection("leaderboard").document(user.getUid()).set(board);
                    routeUser(user);
                })
                .addOnFailureListener(error -> toast("Signed in, but profile setup failed"));
    }

    private void loadPastSubmissions(String filter) {
        TextView all = findViewById(R.id.filterAll);
        TextView organic = findViewById(R.id.filterOrganic);
        if (all != null && organic != null) {
            boolean isAll = "all".equals(filter);
            all.setBackgroundResource(isAll ? R.drawable.bg_toggle_selected : 0);
            all.setTextColor(Color.parseColor(isAll ? "#222222" : "#777777"));
            organic.setBackgroundResource(!isAll ? R.drawable.bg_toggle_selected : 0);
            organic.setTextColor(Color.parseColor(!isAll ? "#222222" : "#777777"));
        }
        LinearLayout live = findViewById(R.id.liveSubmissionList);
        View staticSample = findViewById(R.id.staticSubmissionList);
        if (live == null || db == null) return;
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null) return;
        db.collection("submissions").whereEqualTo("residentId", user.getUid()).limit(100).get()
                .addOnSuccessListener(snapshot -> {
                    if (staticSample != null) staticSample.setVisibility(View.GONE);
                    List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> Long.compare(safeLong(b, "createdAt"), safeLong(a, "createdAt")));
                    live.removeAllViews();
                    int shown = 0;
                    for (DocumentSnapshot doc : docs) {
                        String type = doc.getString("wasteType");
                        if ("organic".equals(filter) && !"Organic (Wet)".equals(type)) continue;
                        live.addView(submissionRow(type == null ? "Waste submission" : type,
                                formatDate(safeLong(doc, "createdAt")), safeLong(doc, "points"), doc.getString("status")));
                        shown++;
                    }
                    if (shown == 0) live.addView(infoRow("No submissions yet", "Submit a photo to earn points"));
                })
                .addOnFailureListener(error -> {
                    if (staticSample != null) staticSample.setVisibility(View.GONE);
                    live.removeAllViews();
                    live.addView(infoRow("Could not load submissions", "Check Firestore rules/connection"));
                });
    }

    private LinearLayout submissionRow(String type, String date, long points, String status) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_white_card);
        row.setPadding(18, 16, 18, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 10);
        row.setLayoutParams(params);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView typeView = new TextView(this);
        typeView.setText(type);
        typeView.setTextColor(Color.rgb(17, 24, 39));
        typeView.setTextSize(14);
        typeView.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(typeView);
        TextView dateView = new TextView(this);
        dateView.setText(date);
        dateView.setTextColor(Color.rgb(107, 114, 128));
        dateView.setTextSize(12);
        col.addView(dateView);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.END);
        TextView pointsView = new TextView(this);
        pointsView.setText("+" + points + " Pts");
        pointsView.setTextColor(Color.rgb(32, 185, 104));
        pointsView.setTextSize(13);
        pointsView.setTypeface(null, android.graphics.Typeface.BOLD);
        rightCol.addView(pointsView);
        TextView statusView = new TextView(this);
        statusView.setText(describeStatus(status));
        statusView.setTextColor(Color.rgb(107, 114, 128));
        statusView.setTextSize(11);
        rightCol.addView(statusView);
        row.addView(rightCol);
        return row;
    }

    private void loadResidentProfile() {
        if (meName != null) {
            TextView name = findViewById(R.id.tvName);
            TextView email = findViewById(R.id.tvEmail);
            TextView fullName = findViewById(R.id.tvFullName);
            TextView emailAddress = findViewById(R.id.tvEmailAddress);
            TextView location = findViewById(R.id.tvLocation);
            if (name != null) name.setText(capitalize(meName));
            if (email != null) email.setText(meEmail);
            if (fullName != null) fullName.setText(capitalize(meName));
            if (emailAddress != null) emailAddress.setText(meEmail);
            if (location != null) location.setText(meLocality == null ? "Not set" : meLocality);
        }
    }

    // The design's "collection alert" screen has no data model of its own — it's a push
    // notification landing spot. This shows the resident's most recent active pickup instead
    // of inventing a collector/location that doesn't exist.
    private void loadCollectionAlert() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        db.collection("submissions").whereEqualTo("residentId", user.getUid())
                .whereEqualTo("status", "assigned").limit(10).get()
                .addOnSuccessListener(snapshot -> {
                    TextView location = findViewById(R.id.tvLocation);
                    TextView collectorName = findViewById(R.id.tvCollectorName);
                    TextView collectorId = findViewById(R.id.tvCollectorId);
                    if (snapshot.isEmpty()) {
                        if (location != null) location.setText("No active pickup right now");
                        if (collectorName != null) collectorName.setText("—");
                        if (collectorId != null) collectorId.setText("");
                        return;
                    }
                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    String collectorUid = doc.getString("collectorId");
                    if (location != null) location.setText(doc.getString("address") == null ? "Address not captured" : doc.getString("address"));
                    if (collectorUid == null) return;
                    db.collection("users").document(collectorUid).get().addOnSuccessListener(c -> {
                        if (collectorName != null) collectorName.setText(capitalize(c.getString("name")));
                        if (collectorId != null) collectorId.setText("Collector ID: COL-" + collectorUid.substring(0, Math.min(6, collectorUid.length())).toUpperCase(Locale.US));
                    });
                });
    }

    private void openNavigation() {
        if (selectedTarget == null) {
            toast("Tap a pin on the map first");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("google.navigation:q=" + selectedTarget.latitude + "," + selectedTarget.longitude)));
        } catch (Exception e) {
            toast("No navigation app available");
        }
    }

    private void showEditProfile(int returnLayout) {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        android.widget.LinearLayout box = dialogFieldBox();
        android.widget.EditText nameField = dialogField(box, "Full name", meName);
        android.widget.EditText localityField = dialogField(box, "Locality", meLocality);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Edit profile")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = nameField.getText().toString().trim();
                    String newLocality = localityField.getText().toString().trim();
                    Map<String, Object> update = new HashMap<>();
                    if (!newName.isEmpty()) update.put("name", newName);
                    if (!newLocality.isEmpty()) update.put("locality", newLocality);
                    db.collection("users").document(user.getUid()).set(update, SetOptions.merge())
                            .addOnSuccessListener(done -> {
                                if (!newName.isEmpty()) meName = newName;
                                if (!newLocality.isEmpty()) meLocality = newLocality;
                                if (!newName.isEmpty() && "resident".equals(meRole)) {
                                    Map<String, Object> board = new HashMap<>();
                                    board.put("name", newName);
                                    if (!newLocality.isEmpty()) board.put("locality", newLocality);
                                    db.collection("leaderboard").document(user.getUid()).set(board, SetOptions.merge());
                                }
                                toast("Profile updated");
                                show(returnLayout);
                            })
                            .addOnFailureListener(error -> toast("Could not update profile"));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showChangePassword(int returnLayout) {
        if (auth == null || meEmail == null) return;
        auth.sendPasswordResetEmail(meEmail)
                .addOnSuccessListener(done -> toast("Password reset link sent to " + meEmail))
                .addOnFailureListener(error -> toast("Could not send reset email: " + error.getMessage()));
    }

    private void showNotificationSettings(int returnLayout) {
        showSettings(returnLayout);
    }

    private void showSettings(int returnLayout) {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
            boolean enabled = !Boolean.FALSE.equals(snapshot.getBoolean("notificationsEnabled"));
            android.widget.Switch toggle = new android.widget.Switch(this);
            toggle.setText("Push notifications");
            toggle.setChecked(enabled);
            toggle.setPadding(40, 30, 40, 30);
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Notification settings")
                    .setView(toggle)
                    .setPositiveButton("Save", (d, w) -> {
                        Map<String, Object> update = new HashMap<>();
                        update.put("notificationsEnabled", toggle.isChecked());
                        db.collection("users").document(user.getUid()).set(update, SetOptions.merge());
                        toast("Saved");
                    })
                    .setNegativeButton("Close", null)
                    .show();
        });
    }

    private void showFeedback(int returnLayout) {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        android.widget.EditText field = new android.widget.EditText(this);
        field.setHint("What's on your mind?");
        field.setMinLines(3);
        field.setPadding(40, 30, 40, 10);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Remark & feedback")
                .setView(field)
                .setPositiveButton("Send", (d, w) -> {
                    String message = field.getText().toString().trim();
                    if (message.isEmpty()) return;
                    Map<String, Object> feedback = new HashMap<>();
                    feedback.put("uid", user.getUid());
                    feedback.put("role", meRole);
                    feedback.put("message", message);
                    feedback.put("createdAt", System.currentTimeMillis());
                    db.collection("feedback").document().set(feedback)
                            .addOnSuccessListener(done -> toast("Thanks — feedback sent"))
                            .addOnFailureListener(error -> toast("Could not send feedback"));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private android.widget.LinearLayout dialogFieldBox() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(48, 20, 48, 10);
        return box;
    }

    private android.widget.EditText dialogField(android.widget.LinearLayout box, String hint, String initial) {
        android.widget.EditText field = new android.widget.EditText(this);
        field.setHint(hint);
        if (initial != null) field.setText(initial);
        box.addView(field);
        return field;
    }

    private void loadCollectorProfile() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
            TextView nameView = findViewById(R.id.tvCollectorName);
            TextView idView = findViewById(R.id.tvCollectorId);
            String name = capitalize(snapshot.getString("name"));
            if (nameView != null) nameView.setText(name);
            // The Firebase Auth UID is the only backend-issued unique identifier this app has
            // for a collector \u2014 deriving a display ID from it (rather than inventing one on
            // the client) keeps this traceable back to a real account.
            if (idView != null) idView.setText("Collector ID: COL-" + user.getUid().substring(0, Math.min(6, user.getUid().length())).toUpperCase(Locale.US));
        });
    }

    private void wireCollectionHistorySearch() {
        android.widget.EditText search = findViewById(R.id.etSearchStreet);
        if (search == null) return;
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                loadCollectionHistory(s.toString().trim());
            }
        });
    }

    private void loadCollectionHistory(String searchTerm) {
        LinearLayout live = findViewById(R.id.liveHistoryList);
        View staticSample = findViewById(R.id.staticHistorySample);
        if (live == null || db == null) return;
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null) return;
        db.collection("submissions").whereEqualTo("collectorId", user.getUid())
                .whereEqualTo("status", "completed").limit(100).get()
                .addOnSuccessListener(snapshot -> {
                    if (staticSample != null) staticSample.setVisibility(View.GONE);
                    List<DocumentSnapshot> docs = new java.util.ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> Long.compare(safeLong(b, "completedAt"), safeLong(a, "completedAt")));
                    live.removeAllViews();
                    int shown = 0;
                    for (DocumentSnapshot doc : docs) {
                        String address = doc.getString("address");
                        String label = address == null ? "Location not captured" : address;
                        if (searchTerm != null && !searchTerm.isEmpty()
                                && !label.toLowerCase(Locale.US).contains(searchTerm.toLowerCase(Locale.US))) continue;
                        live.addView(historyRow(label, formatDate(safeLong(doc, "completedAt"))));
                        shown++;
                    }
                    if (shown == 0) {
                        live.addView(infoRow(searchTerm != null && !searchTerm.isEmpty() ? "No matches for \u201c" + searchTerm + "\u201d" : "No collection history yet",
                                "Completed pickups will appear here"));
                    }
                })
                .addOnFailureListener(error -> {
                    if (staticSample != null) staticSample.setVisibility(View.GONE);
                    live.removeAllViews();
                    live.addView(infoRow("Could not load history", "Check Firestore rules/connection"));
                });
    }

    private LinearLayout historyRow(String location, String time) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_white_card);
        row.setPadding(18, 16, 18, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 10);
        row.setLayoutParams(params);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView locView = new TextView(this);
        locView.setText(location);
        locView.setTextColor(Color.rgb(17, 24, 39));
        locView.setTextSize(14);
        locView.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(locView);
        TextView timeView = new TextView(this);
        timeView.setText("Collected at " + time);
        timeView.setTextColor(Color.rgb(107, 114, 128));
        timeView.setTextSize(12);
        col.addView(timeView);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        TextView statusView = new TextView(this);
        statusView.setText("Completed");
        statusView.setTextColor(Color.rgb(32, 185, 104));
        statusView.setTextSize(12);
        statusView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(statusView);
        return row;
    }

    private void setupLiveMap() {
        FrameLayout container = findViewById(R.id.mapContainer);
        TextView status = findViewById(R.id.tvMapStatus);
        if (container == null) return;
        String key = BuildConfig.MAPS_API_KEY;
        if (key == null || key.isEmpty() || key.startsWith("YOUR_")) {
            if (status != null) status.setText("Google Maps API key is not configured.\nAdd MAPS_API_KEY to local.properties and rebuild.");
            return;
        }
        teardownMap();
        mapView = new MapView(this);
        container.addView(mapView, 0);
        // The Activity is already started/resumed, so the MapView must be brought up manually.
        mapView.onCreate(null);
        mapView.onStart();
        mapView.onResume();
        mapView.getMapAsync(this);
    }

    private void teardownMap() {
        if (liveCollectorsListener != null) {
            liveCollectorsListener.remove();
            liveCollectorsListener = null;
        }
        liveCollectorMarkers.clear();
        if (mapView != null) {
            mapView.onPause();
            mapView.onStop();
            mapView.onDestroy();
            mapView = null;
        }
        googleMap = null;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        TextView mapStatus = findViewById(R.id.tvMapStatus);
        if (mapStatus != null) mapStatus.setVisibility(View.GONE);
        map.getUiSettings().setZoomControlsEnabled(false);
        enableMyLocation();
        centerMapOnRealLocation();
        loadRealMapMarkers();
        listenForLiveCollectors();
        map.setOnMarkerClickListener(marker -> {
            selectedTarget = marker.getPosition();
            return false;
        });
    }

    private void centerMapOnRealLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || fusedLocationClient == null) {
            return; // no fake fallback coordinate \u2014 map just stays at its default view until we have a real fix
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && googleMap != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(location.getLatitude(), location.getLongitude()), 14f));
            }
        });
    }

    // Shows real pending submissions (unclaimed waste reports anywhere) and this collector's
    // own assigned/completed ones \u2014 only for submissions that actually have captured
    // coordinates. Nothing here is invented; submissions without real lat/lng are skipped.
    private void loadRealMapMarkers() {
        if (googleMap == null || db == null) return;
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        String myUid = user == null ? null : user.getUid();
        db.collection("submissions")
                .whereIn("status", java.util.Arrays.asList("pending", "assigned"))
                .limit(50).get()
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        if (lat == null || lng == null) continue; // no fake coordinates
                        String collectorId = doc.getString("collectorId");
                        boolean mine = myUid != null && myUid.equals(collectorId);
                        String state = doc.getString("status");
                        if ("assigned".equals(state) && !mine) continue;
                        String type = doc.getString("wasteType");
                        MarkerOptions marker = new MarkerOptions()
                                .position(new LatLng(lat, lng))
                                .title((type == null ? "Waste report" : type) + " \u2014 " + state)
                                .icon(BitmapDescriptorFactory.defaultMarker(mine
                                        ? BitmapDescriptorFactory.HUE_GREEN : BitmapDescriptorFactory.HUE_ORANGE));
                        googleMap.addMarker(marker);
                    }
                });
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
    @Override protected void onDestroy() {
        stopLocationBroadcast();
        if (liveCollectorsListener != null) liveCollectorsListener.remove();
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

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
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (currentLayout == R.layout.resident_submit_waste) {
                if (granted) captureLocationForSubmission();
                else {
                    TextView statusView = findViewById(R.id.tvLocationStatus);
                    if (statusView != null) statusView.setText("Location permission denied \u2014 submission will save without a location");
                }
            } else if (granted) {
                enableMyLocation();
            }
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
