# finr

finr is an Android app that reads on-device SMS messages, extracts financial transactions, stores normalized transaction records locally, and presents them as transaction, daily summary, and monthly summary cards.

## Current version

- `versionCode`: `7`
- `versionName`: `0.7`

## Core capabilities

- Reads device SMS messages and filters for relevant financial senders.
- Extracts transaction details such as amount, direction, date, time, category mapping, channel, and bank metadata.
- Stores transactions in a local Room database.
- Supports manual transaction creation and transaction editing.
- Groups activity into daily and monthly summaries.
- Exports filtered transaction data to CSV.
- Supports Light, Dark, and Midnight themes.
- Schedules a daily summary notification.

## Data model overview

- `transactions`: normalized transaction records stored locally.
- `categories`: seeded category and sub-category classification table.
- sender metadata is also used to validate eligible SMS senders before transaction parsing.

## Build

Debug build:

```bash
./gradlew assembleDebug
```

Release bundle:

```bash
./gradlew bundleRelease
```

Release signing expects these environment variables or Gradle properties:

- `PS_UPLOAD_STORE_FILE`
- `PS_UPLOAD_STORE_PASSWORD`
- `PS_UPLOAD_KEY_ALIAS`
- `PS_UPLOAD_KEY_PASSWORD`

## Changes since v0.6

- Bumped app package and release line forward to `0.7`.
- Refined transaction card layout to prioritize amount, banking metadata, category chips, and directional icons.
- Reworked the edit transaction flow so tapping a card opens edit directly.
- Redesigned the edit transaction screen to better match the current app card language and selection flow.
- Tightened category and sub-category chip styling in edit and card surfaces.
- Updated filtered date view spacing and removed the fixed month-year header from the filtered view.
- Improved status bar handling so system icons remain visible on light Add/Edit screens and stay light in Dark and Midnight themes.
- Added and wired custom font usage for targeted headers and labels.
- Updated light-theme summary card styling while preserving theme-specific behavior in Dark and Midnight.
- Removed monthly summary borders and retained outcome-based shadow behavior.
- Continued hardening around sender validation, bank inference, and transaction metadata display.

## Notes

- The app processes SMS locally on the device.
- Release builds are blocked if signing configuration is missing.
