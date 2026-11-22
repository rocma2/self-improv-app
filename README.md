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
- Train tab: today's session as a ranked mission with one START button, Training Points (TP) for every run, gym session and bike ride with 3 weekly chests that stop at 100% of the plan, the week as a tappable path, personal-best chips and an activity log.
- Charts: weekly km, pace, body weight, strength.
- Ranks E to S grow exponentially: each rank needs 3x the total XP of the last (D 2,500 · C 7,500 · B 22,500 · A 67,500 · S 202,500). Rank up gives +5 stat points and 500 gold.
- Skills radar: an octagon of your 8 skills. Tap one to see its own pentagon of 5 sub-stats from your real logs (for running: endurance, speed, volume, discipline, health) and the one to improve next.
- Daily check-in track (7 days, rewards grow, never resets) and hidden quests on random days for finishing everything.
- New systems unlock one at a time: Gates at level 2, Shop at level 4, Job change at level 10.
- RPG layer (Gates tab):
  - Stat points: +3 per level and +1 for each fully completed daily quest. Spend them on STR, AGI, VIT, INT, WIL.
  - Gates: 3 a week built from your plan. Enter one, hit the target before Sunday. The red gate pays double but costs HP if it collapses. Running and gym gates never ask for more than the plan.
  - Bosses are your big goals: 5 km under 19:00, half marathon, marathon (sealed until the half), 72 kg, squat 1.25x bodyweight, screen time.
  - Job change at level 10: pass a 7-day trial, pick Striker, Vanguard, Sage or Warden (+15% XP on 2 skills).
  - Gold, mystery boxes, a shop (freeze crystals, elixirs, rest scrolls, shields, XP potions) and your own real-life rewards bought with gold.
  - Skip pass: turn one run or gym session into a rest day. 400 gold, doubling for each skip in the last 4 weeks, max 1 a week, and that day gives no daily-quest reward.
  - Dungeon: a no-phone focus timer for studying, reading or money work, with a clear bonus.
- All data stays on the phone. Export/import a JSON backup in Settings.

## Build it yourself

GitHub Actions builds `Ascend.apk` on every push (see `.github/workflows/build.yml`).
Locally: `./gradlew assembleRelease` with the Android SDK installed.

The rules of the game are plain Kotlin in `app/src/main/java/com/ascend/app/domain` with tests in `app/src/test`.

## Fonts

Rajdhani (Indian Type Foundry) and Orbitron (Matt McInerney), both under the SIL Open Font License 1.1, from Google Fonts.
