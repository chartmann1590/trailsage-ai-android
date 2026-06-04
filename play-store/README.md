# Google Play Store Listing — TrailSage AI

Complete store-listing asset kit for **TrailSage AI** (`com.charles.trailsage`).

## Contents

```
play-store/
├─ listing/
│  ├─ title.txt              App title (29/30 chars)
│  ├─ short_description.txt  Short description (69/80 chars)
│  ├─ long_description.txt   Full description (2360/4000 chars)
│  ├─ keywords.txt           ASO discovery terms
│  ├─ tags.txt               Suggested Console tags + ASO terms
│  ├─ category.txt           Primary/secondary category
│  └─ data_safety.txt        Play Data Safety declaration guide
├─ graphics/
│  ├─ icon.png               512×512 — app launcher icon (adaptive icon, exact match)
│  ├─ feature_image.png      1024×500 — feature graphic
│  └─ screenshots/
│     ├─ phone/              5× 1008×2126 — real Pixel 8 Pro captures
│     ├─ tablet_7in/         5× 1200×1920
│     └─ tablet_10in/        5× 1600×2560
└─ promo/
   └─ promo_video.mp4        1080×1920, ~40s, AI voiceover + burn-in captions
```

## Notes

- **Icon** is rendered directly from the app's adaptive icon vectors
  (`ic_launcher_foreground.xml` / `ic_launcher_background.xml`) so it matches the
  installed launcher icon exactly.
- **Phone screenshots** are genuine captures from a Google Pixel 8 Pro. The in-app
  ad banner was removed from the captures (standard, non-misleading store practice);
  unmodified originals are kept in `screenshots/phone/_raw_with_ads/`.
- **Tablet screenshots** frame the real phone captures on a branded backdrop at the
  required 7" and 10" sizes.
- **Promo video** narration uses a natural neural TTS voice with on-screen captions.
  Play accepts a YouTube URL for the listing's promo video — upload `promo_video.mp4`
  to YouTube and paste the link in the Console.
- **Privacy/ads accuracy:** the app integrates Google AdMob (with an optional ad-free
  rewards tier) and optional, off-by-default Firebase diagnostics. All copy here is
  written to match that reality — see `listing/data_safety.txt` before completing the
  Console Data Safety form.

## Asset spec compliance

| Asset | Requirement | This kit |
| --- | --- | --- |
| Icon | 512×512 PNG (32-bit) | ✅ 512×512 RGBA |
| Feature graphic | 1024×500 PNG/JPG | ✅ 1024×500 |
| Phone screenshots | 2–8, 320–3840px | ✅ 5 × 1008×2126 |
| 7" tablet | up to 8, 320–3840px | ✅ 5 × 1200×1920 |
| 10" tablet | up to 8, 320–3840px | ✅ 5 × 1600×2560 |
