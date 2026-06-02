---
name: TrailSage AI
colors:
  surface: '#fbf9f4'
  surface-dim: '#dbdad5'
  surface-bright: '#fbf9f4'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f3ee'
  surface-container: '#f0eee9'
  surface-container-high: '#eae8e3'
  surface-container-highest: '#e4e2dd'
  on-surface: '#1b1c19'
  on-surface-variant: '#434843'
  inverse-surface: '#30312e'
  inverse-on-surface: '#f2f1ec'
  outline: '#737973'
  outline-variant: '#c3c8c1'
  surface-tint: '#4d6453'
  primary: '#061b0e'
  on-primary: '#ffffff'
  primary-container: '#1b3022'
  on-primary-container: '#819986'
  inverse-primary: '#b4cdb8'
  secondary: '#6b5c4c'
  on-secondary: '#ffffff'
  secondary-container: '#f4dfcb'
  on-secondary-container: '#716252'
  tertiary: '#241400'
  on-tertiary: '#ffffff'
  tertiary-container: '#3f2700'
  on-tertiary-container: '#c5871d'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d0e9d4'
  primary-fixed-dim: '#b4cdb8'
  on-primary-fixed: '#0b2013'
  on-primary-fixed-variant: '#364c3c'
  secondary-fixed: '#f4dfcb'
  secondary-fixed-dim: '#d7c3b0'
  on-secondary-fixed: '#241a0e'
  on-secondary-fixed-variant: '#524436'
  tertiary-fixed: '#ffddb3'
  tertiary-fixed-dim: '#ffb951'
  on-tertiary-fixed: '#291800'
  on-tertiary-fixed-variant: '#633f00'
  background: '#fbf9f4'
  on-background: '#1b1c19'
  surface-variant: '#e4e2dd'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  title-lg:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  container-padding-mobile: 20px
  container-padding-desktop: 40px
  gutter: 16px
  touch-target-min: 48px
  safe-area-bottom: 32px
---

## Brand & Style
The design system for this adventure-themed audio tour app is rooted in a "Modern Naturalist" aesthetic. It balances the rugged spirit of outdoor exploration with the sophisticated precision of AI-driven guidance. The target audience includes road-trippers, hikers, and curious travelers who value deep context and premium experiences.

The style is a hybrid of **Corporate Modern** (for reliability) and **Glassmorphism** (to maintain map visibility). It utilizes expansive white space, soft-focus background blurs, and high-contrast elements to ensure the UI feels calm and trustworthy, even in the middle of a remote expedition. The interface should evoke the feeling of a high-end physical field guide—tactile, organized, and invaluable.

## Colors
The palette is inspired by the transition from forest floors to sun-drenched peaks.
- **Deep Forest Green (#1B3022):** Used for primary actions, heavy text, and branding to establish a grounded, authoritative presence.
- **Sandstone Tan (#D9C5B2):** A functional secondary color for containers and subtle dividers that feels more organic than grey.
- **Sunrise Gold (#FFB84D):** Reserved for highlights, ratings, and "Active" state indicators to provide warmth.
- **Road-trip Blue (#4A90E2):** Used specifically for navigation cues, GPS paths, and interactive map elements.
- **Warm Off-White (#F9F7F2):** The primary background color to reduce eye strain outdoors compared to pure white.
- **Charcoal (#121212):** Used for Dark Mode and night-driving interfaces to preserve night vision.

## Typography
The typography system uses **Inter** for its exceptional legibility at all sizes, crucial for glanceable information while driving. 

- **Safety First:** Large scale headlines (Display and Headline-LG) are prioritized for turn-by-turn context and tour titles.
- **High Contrast:** All text must maintain a minimum 4.5:1 contrast ratio against background colors.
- **Tight Letter Spacing:** Used for large headings to maintain a modern, "Swiss" feel, while body copy maintains standard spacing for maximum readability in vibrating environments.

## Layout & Spacing
This design system uses a **Fluid Grid** model with a heavy emphasis on bottom-weighted controls for one-handed/mobile use.

- **Grid:** A 12-column grid for desktop, 8-column for tablet, and 4-column for mobile.
- **Rhythm:** An 8px linear scale (8, 16, 24, 32, 48, 64) governs all padding and margins.
- **Map-Forward Hierarchy:** The map is the persistent "Level 0" background. All UI elements exist as floating panels or sheets above this layer.
- **Safe Zones:** Generous margins (20px minimum on mobile) ensure text isn't obscured by phone mounts or vehicle dashboard bezels.

## Elevation & Depth
Depth is used to signify the transience of information.
- **Level 0 (Map):** The base layer.
- **Level 1 (Surface):** Standard cards using the Warm Off-White color with a very subtle 1px border (#D9C5B2) and no shadow.
- **Level 2 (Floating):** Floating Action Buttons (FABs) and active navigation panels use a medium-diffusion shadow (Y: 4, Blur: 12, Opacity: 10% Forest Green) to appear "lifted."
- **Level 3 (Glass):** Dynamic overlays (like audio scrubbers over map views) use a `backdrop-filter: blur(20px)` with a 70% opacity white fill, creating a sense of lightness and technical polish.

## Shapes
The shape language is friendly and approachable, utilizing significant corner radii to mirror the organic forms found in nature.
- **Large Containers:** Cards and bottom sheets use a `24px` (rounded-xl) radius.
- **Interactive Elements:** Buttons and input fields use a `12px` (rounded-lg) radius.
- **Media:** Progress bars and badge containers use "Full Pill" shapes for a distinct, modern look.

## Components
- **Destination Cards:** High-aspect-ratio images with a bottom-aligned gradient scrim. Title and "Time to Arrival" are overlaid in White/Sunrise Gold.
- **Audio Controls:** Oversized "Play/Pause" and "Skip 30s" buttons (minimum 64px width) to accommodate driving conditions. Visualizers use the Road-trip Blue accent.
- **Progress Bars:** Dual-layered bars for downloads; a Sandstone Tan track with a Deep Forest Green fill.
- **Attribution Badges:** Small, semi-transparent chips at the bottom of content blocks to credit Wikipedia/OSM without distracting from the narrative.
- **Input Fields:** Thick borders (2px) when focused, using Forest Green to indicate active state clearly in sunlight.
- **Map Markers:** Custom pin shapes with white halos to ensure visibility against varied map terrain (satellite vs. vector).