# TrailSage AI Privacy Policy

TrailSage AI is offline-first. Tour packs and source notes work locally. AI runs on device whenever a verified model is installed. The app does not sell location data, include ads, or call paid AI APIs.

Optional Firebase Analytics, Crashlytics, Performance Monitoring, Remote Config, and Cloud Messaging are Spark-compatible services. Analytics, crash, and performance collection must remain disabled until the user opts in. Location is used for active tour triggers; online refresh uses public-source data.

### Trip Sharing (Opt-in)
If you choose to use the "Share this trip" feature, the active trip details (including name, route coordinates, directions, and stops with narration text) are serialized and uploaded to a public Firebase Firestore database. This shared trip is assigned a unique link. Anyone with access to the link will be able to view the trip. No personally identifiable information or telemetry is linked to shared trips. Sharing is entirely opt-in and user-initiated.

