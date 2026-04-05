# Google Play Data Safety Notes

Use this as working input for the Play Console Data safety form. Final answers depend on your legal/privacy stance.

## Data Types

- Personal info:
  first name
- Health and fitness:
  weight, calorie budget, meal history, calorie intake
- Photos and videos:
  meal photos captured or attached by the user
- App activity:
  coach conversation content, meal logging activity

## Collection Model

- Most data is stored locally on device
- Optional Google Drive backup sends user data to the user's own Google Drive account when enabled
- No third-party cloud AI inference endpoint is used for calorie estimation

## Processing Purposes

- App functionality
- Analytics: currently none in codebase
- Fraud/security: none in codebase
- Personalization: limited to on-device summaries/coaching based on local history

## Sharing

- No evidence in code of selling or sharing data with data brokers
- Google Drive backup is user-directed transfer to the user's own storage, not advertising or broker sharing

## Security

- Data is stored in app-private local storage
- Google Drive backup uses Google account authorization and app-private Drive storage
- Cleartext traffic is disabled in release configuration

## Before Submission

- Make sure the public privacy policy matches the exact Play Console answers
- Recheck if any analytics, crash reporting, or remote config SDKs are added later
