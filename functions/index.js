/**
 * CleanAlert Cloud Functions
 * ---------------------------------------------------------------------------
 * These are Firestore-triggered functions: they react to real writes the
 * Android app already makes (a submission's status changing, a reward being
 * redeemed) and send a real FCM push to the resident who owns that record.
 * Nothing here invents an event \u2014 if the app never writes the change,
 * no notification fires.
 *
 * Deploy with:
 *   cd functions && npm install
 *   firebase deploy --only functions
 *
 * Requires the Blaze (pay-as-you-go) plan on the Firebase project \u2014 Google
 * does not allow HTTPS-triggered or event-triggered functions on the free
 * Spark plan. This cannot be worked around from the client.
 */

const { onDocumentUpdated, onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

/** Look up a user's saved FCM token and send them a notification, if they have one. */
async function notifyUser(uid, title, body) {
  if (!uid) return;
  const userDoc = await db.collection("users").doc(uid).get();
  if (!userDoc.exists) return;
  const enabled = userDoc.get("notificationsEnabled");
  if (enabled === false) return; // resident turned notifications off in Settings
  const token = userDoc.get("fcmToken");
  if (!token) return;

  try {
    await messaging.send({ token, notification: { title, body } });
  } catch (err) {
    // A stale/uninstalled-app token is expected occasionally \u2014 log and move on,
    // don't fail the whole trigger over one bad token.
    console.warn(`FCM send failed for ${uid}:`, err.message);
  }
}

/**
 * Fires whenever a submission document is updated. Compares old vs new status
 * and notifies the resident only on the specific transitions that actually
 * mean something to them.
 */
exports.onSubmissionStatusChange = onDocumentUpdated("submissions/{submissionId}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (!before || !after || before.status === after.status) return;

  const residentId = after.residentId;
  const points = after.points || 0;

  switch (after.status) {
    case "approved":
      await notifyUser(residentId, "Submission approved", `You earned ${points} points \u2014 nice work keeping your street clean.`);
      break;
    case "rejected":
      await notifyUser(residentId, "Submission rejected", "Your waste submission wasn't approved. Check Past Submissions for details.");
      break;
    case "assigned":
      await notifyUser(residentId, "Collector on the way", "A collector has accepted your waste pickup request.");
      break;
    case "completed":
      await notifyUser(residentId, "Pickup completed", "Your waste has been collected. Thanks for using CleanAlert!");
      break;
    default:
      // no-op for any other status value
  }
});

/** Fires when a resident redeems a reward \u2014 sends them a confirmation. */
exports.onRewardRedeemed = onDocumentCreated("redemptions/{redemptionId}", async (event) => {
  const redemption = event.data.data();
  if (!redemption) return;
  await notifyUser(
    redemption.residentId,
    "Reward redeemed",
    `${redemption.rewardName || "Your reward"} is ready \u2014 code ${redemption.code || ""}.`
  );
});
