# TrailSage AI Privacy Policy

TrailSage AI is designed from the ground up as an offline-first mobile application. Tour packages, source notes, local AI RAG queries, and audio voice synthesis run entirely on-device whenever a verified model package is installed.

### AdMob Advertising & Rewards System (Opt-in Rewards)
To support the development and maintenance of TrailSage AI, the application integrates Google AdMob (Google Mobile Ads SDK) to show ads:
- **Ad Displays:** Banner ads are displayed at the bottom of standard screens, and random interstitial ads may appear during transitions.
- **Rewarded Ad Credits:** Users can voluntarily watch rewarded ads to earn credits (1 ad = 1 credit), up to a limit of 6 ads per day.
- **Disabling Ads:** Users can spend up to 6 credits per day to disable all ads in the application. Each credit disables ads for 40 minutes (up to a maximum of 4 hours per day). Ad-free status and available credits are tracked locally on your device.
- **Data Collection:** Google AdMob may collect and process device identifiers, IP addresses, location data (if GPS permissions are enabled), and other diagnostic or usage logs to serve and measure ads. All data collected by AdMob is subject to Google's Privacy Policy.

### Firebase Analytics & Telemetry (Opt-in)
Optional Firebase Analytics, Crashlytics, Performance Monitoring, Remote Config, and Cloud Messaging are included. Telemetry collection is completely disabled by default and will not execute until you provide explicit consent during setup or in Settings. You can revoke consent at any time to immediately terminate reporting.

### Trip Sharing (Opt-in)
If you choose to use the "Share this trip" feature, the active trip details (including name, route coordinates, directions, and stops with narration text) are serialized and uploaded to a public Firebase Firestore database. This shared trip is assigned a unique link. Anyone with access to the link will be able to view the trip. No personally identifiable information or telemetry is linked to shared trips. Sharing is entirely opt-in and user-initiated.
