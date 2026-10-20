# e-schools-diary-signer

Android app for manually checking and signing the weekly parent diary at `diary.e-schools.by`, with an optional Friday schedule.

## Security

- Never commit login credentials, passwords, access tokens, or real account data.
- The app is designed to keep credentials on-device in encrypted preferences.
- The repository is public, so treat every committed file as public.

## Build APK

GitHub Actions workflow: **Build Android APK** (`.github/workflows/build-apk.yml`). On a successful run, download the `e-schools-diary-signer-debug` artifact from the workflow run.

## Important limitations

This project is an independent client based on the API flow observed in a supplied Python script. The API contract and signing response must be validated against a test account before relying on automated signing. Android may delay background work; exact alarms require permission on supported Android versions.

## Schedule

The intended weekly schedule is Friday at 19:00 in `Europe/Minsk`. Verify device battery/background restrictions and notification/alarm permissions during testing.
