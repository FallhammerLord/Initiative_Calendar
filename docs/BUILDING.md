# Getting Initiative on your phone

GitHub builds the app for you on every push. You don't need Android Studio for this.

## First install

1. **On your phone, open the latest build:**
   https://github.com/FallhammerLord/Initiative_Calendar/releases/latest
2. Under **Assets**, tap the file ending in `.apk`. It downloads.
3. Open the download. Android will say your browser isn't allowed to install apps. Tap **Settings**, switch on **Allow from this source**, then go back.
4. Tap **Install**. If Play Protect warns about an unknown app, tap **More details → Install anyway**. That warning appears for any app that didn't come from the Play Store.
5. **Make it your home screen.** Press the Home button. Android asks which home app to use; pick **Initiative** and choose **Always**.
   If it doesn't ask: **Settings → Apps → Default apps → Home app → Initiative**.

6. **Let the board appear after unlock.** The board shows a yellow line asking for "Display over other apps". Tap it, switch Initiative on, and press Back. Without this, unlocking returns you to whatever app was open, as usual.

## Getting back to the normal phone

- Swipe up on the board, or tap **Apps**, for every app you have installed.
- **Phone settings** opens the system Settings app.
- **Change home app** opens the screen where you pick your old launcher again. Use it if anything goes wrong.
- The lock screen doesn't change. Your pattern still unlocks the phone, and the board appears after.
- **Board after unlock** has an on/off switch at the bottom of the app drawer.

## Updating

Repeat steps 1, 2 and 4. The new version installs over the old one and keeps your data.

## If there's no release yet

Every build is also saved with its run on GitHub:

1. Open https://github.com/FallhammerLord/Initiative_Calendar/actions (sign in to GitHub first).
2. Tap the newest run with a green check.
3. Scroll to **Artifacts** and tap `initiative-<number>`. It downloads as a .zip.
4. Open the .zip and tap the `.apk` inside, then follow the install steps above.

## About the signing key

Android only installs an update over the old app when both copies are signed with the same key. That key is `app/initiative.keystore`, kept in the repo so every GitHub build uses it.

The repo is public, so anyone could sign an app with this key. They would still need to get that app onto your phone and install it, so the practical risk is low for a private app. If you'd rather keep the key out of the repo, it can move into a GitHub secret. That needs one fresh install, because the key would change.

## Android Studio (optional)

Android Studio lets you run the app in an emulator or install it over USB.

1. Install it from https://developer.android.com/studio
2. **File → Open** and pick this folder. Let it finish syncing.
3. Press the green **Run** arrow.

Builds from Android Studio use the same key, so they install over GitHub builds and the other way round.
