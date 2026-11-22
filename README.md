# Ascend

A personal RPG for real life, for Android. Your runs, lifts, sleep, food, reading, studying,
money work and time off TikTok/YouTube turn into XP, levels and badges.

## Install on your phone (no computer needed)

1. On your phone, open this repo on github.com (log in), tap **Releases**, open the newest **Ascend build**.
2. Tap **Ascend.apk** to download it.
3. Open the downloaded file. Android will ask to allow installs from your browser: tap **Settings**, turn on **Allow from this source**, go back, tap **Install**.
4. To update later, do the same with a newer build. Your data stays (same signing key every build).

If Play Protect warns you, tap **More details → Install anyway**. It warns because the app isn't from the Play Store.

## Turn on the TikTok/YouTube budget and lock

In the app, open the **Focus** tab and follow the steps:

1. **Usage access**: lets the app count minutes on TikTok and YouTube.
2. **App lock** (Obsessed and New life modes): turn on "Ascend app lock" in Accessibility.
   On Android 13+, if it says *Restricted setting*: Settings → Apps → Ascend → ⋮ (top right) → **Allow restricted settings**, then try again.

## What's inside

- Profile: level, title, XP, HP, difficulty (Soft, Easy, Hardcore, Obsessed, New life).
- 8 skills with their own level and HP: running, gym, sleep, weight gain, reading, studying, money, focus.
- Daily quests from your schedule. They complete themselves when you log.
- Running plan: 3 runs a week (speed, recovery, long), +10% a week max, deload every 4th week,
  5K time trial in week 10 (then every 12 weeks), goals for sub-19 5K, first half marathon, sub-3 marathon.
- Strength plan: 2 sessions (A: squat, press, pull-ups, split squat, plank / B: deadlift, bench, pull-ups, single-leg RDL, knee raises)
  with weight suggestions (+2.5 kg when you hit all reps).
- Bike commutes count in weekly load. Winter mode (Dec-Feb): XP x1.2, treadmill and indoor bike count.
- Penalties (by mode): HP loss, at 0 HP the skill drops a level, one penalty quest per bad day, TikTok/YouTube locked until it's done,
  weekly chest lost after too many misses. Food and sleep are never punishments. Rest days and deload weeks never count as misses.
- Momentum streak that never resets to zero (freezes + keep 75%).
- Auto personal records, 30 badges from real life, 6-week seasons of fresh goals, monthly recap, "you vs 3 months ago".
- Charts: weekly km, pace, body weight, strength.
- All data stays on the phone. Export/import a JSON backup in Settings.

## Build it yourself

GitHub Actions builds `Ascend.apk` on every push (see `.github/workflows/build.yml`).
Locally: `./gradlew assembleRelease` with the Android SDK installed.

The rules of the game are plain Kotlin in `app/src/main/java/com/ascend/app/domain` with tests in `app/src/test`.

## Fonts

Rajdhani (Indian Type Foundry) and Orbitron (Matt McInerney), both under the SIL Open Font License 1.1, from Google Fonts.
