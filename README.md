# 🇵🇰 PSX KMI Tracker — Android App

Tracks every stock listed in PSX's **KMI30** and **KMI All Share** indices, fetches
in the background even when the app is closed and the phone is locked, and fires
a notification when any individual stock moves by a threshold you choose (1%–10%,
up or down). Includes a home-screen widget showing the two index levels.

---

## ⚠️ Please read before using

**Data source & Terms of Use.** This app scrapes `dps.psx.com.pk`, PSX's public
Data Portal — there is no free public API for this data. PSX's Terms of Use
explicitly restrict automated/bot access and redistribution of their market
data feed, and carry a formal legal notice about civil/criminal enforcement for
unauthorized dissemination. This app is built for **your own personal,
non-commercial, single-device use** only — don't share/redistribute the data
it pulls, and don't run it at high frequency or at scale. If you want a fully
licensed feed, PSX's Market Data Team can be reached at
`marketdatarequest@psx.com.pk`.

**Not investment advice.** Nothing here recommends buying, selling, or holding
anything — it's just a price/notification tool.

**Not build-verified.** This was written and reviewed carefully, including
against a live fetch of the actual PSX pages it targets, but it was **not**
compiled in a real Android Studio + emulator/device environment (this
generation environment has no Android SDK). Please build and test on a real
device before relying on it — see "Known risk areas" below for what's most
likely to need a tweak.

---

## How it works

1. **`MarketRepository`** fetches `https://dps.psx.com.pk/market-watch` — a single
   page listing every PSX symbol with its sector, index memberships ("LISTED IN"
   column, e.g. `KMI30,KMIALLSHR`), price, and % change. One request gets the
   *entire* tracked universe instead of hundreds of per-stock calls.
2. It also fetches `https://dps.psx.com.pk/indices` for the KMI30 / KMI All Share
   **index-level** values shown on the widget.
3. **`MarketFetchWorker`** (WorkManager, periodic every ~15 min — WorkManager's
   floor, and PSX's own data is delayed 5 min anyway so faster polling wouldn't
   help) runs this fetch in the background, compares each stock's % change
   against your enabled thresholds, and posts a notification for anything that
   newly crosses one.
4. **`MarketHours`** pauses fetching outside the PSX trading session (Mon–Thu
   09:15–15:30, Fri 09:15–12:00 & 14:30–16:30, Asia/Karachi) so it isn't
   polling all night. Manual refreshes (pull-to-refresh / widget ↻ button)
   always work regardless of market hours.
5. **`NotifiedState`** de-dupes: a stock only re-notifies if it climbs to a
   *higher* threshold tier than what you were already notified about that
   trading day — so a stock sitting at +4% doesn't re-ping you every 15
   minutes, but a jump from +4% to +6% would.
6. **`BootReceiver`** reschedules everything after a phone restart.
7. Notifications use `IMPORTANCE_HIGH` + `VISIBILITY_PUBLIC`, so they show as
   heads-up alerts and on the lock screen (subject to your phone's own lock
   screen notification settings).

## Setting your alert thresholds

Open the app → tick any of the 1%–10% chips. Each applies **both directions**
(up or down) to **every** stock in KMI30 or KMI All Share — e.g. ticking 5%
notifies you the moment any tracked stock is up 5%+ *or* down 5%+ from previous
close.

## Reliable background delivery

Android's battery optimization (Doze) can delay or drop background work on
some phones, especially with the screen off for long periods. The app shows a
one-tap prompt ("For reliable alerts while your screen is off…") to exempt
itself from battery optimization — worth doing on Xiaomi/Oppo/Vivo/Huawei
phones in particular, which are aggressive about killing background apps.
You may also need to disable any manufacturer-specific "app hibernation" /
"autostart" restrictions for this app in your phone's settings.

## Known risk areas (things to double-check if something breaks)

- **HTML structure changes.** The parser in `MarketRepository.parseMarketWatch`
  reads columns by matching header text ("SYMBOL", "LISTED IN", "CURRENT",
  etc.) rather than hard-coded CSS classes, specifically so it's more
  resilient to PSX's styling changes — but if they restructure the table
  itself (e.g. move to a JS-rendered/AJAX table like some other pages on the
  site), this will need rework. If notifications stop coming, check Logcat
  for `MarketRepository` / `MarketFetchWorker` tags first.
- **Symbol-cell parsing.** Some rows show a status badge next to the symbol
  (e.g. `HASCOL NC`, `AKBL XD`). The parser takes only the `<a>` link text as
  the symbol, ignoring the badge — verify this still holds if you see garbled
  symbols in the list.
- **Market hours edge cases.** Public holidays aren't accounted for — the app
  will simply get an empty/failed fetch on holidays and skip alerting (no
  crash), but if you want holidays truly skipped ahead of time, add dates to
  `MarketHours`.

---

## Project structure

```
app/src/main/java/com/psxtracker/widget/
├── MainActivity.kt          # Threshold picker + live stock list + market status
├── MarketRepository.kt      # OkHttp + Jsoup fetch/parse of PSX Data Portal pages
├── MarketFetchWorker.kt     # WorkManager background job: fetch → compare → notify
├── MarketHours.kt           # PSX trading-session check (Asia/Karachi)
├── NotificationHelper.kt    # Notification channel + per-stock/grouped alerts
├── KmiWidgetProvider.kt     # Home-screen widget (index-level summary)
├── StockListAdapter.kt      # RecyclerView adapter for the in-app stock list
├── Store.kt                 # DataStore: thresholds, notify-dedupe, widget cache
├── Models.kt                # StockQuote / IndexQuote data classes
└── BootReceiver.kt          # Reschedule background work after reboot
```

## How to Build & Run

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34, JDK 17
- Physical device or emulator (API 26+)

### Steps
1. `File → Open` → select the `PSXMarketWidget` folder, let Gradle sync.
2. Run on a device (▶). Grant the notification permission when prompted.
3. In-app, tick the % thresholds you want.
4. Long-press your home screen → Widgets → "PSX KMI Indices" → drag it on.
5. Optional but recommended: tap the battery-optimization hint in the app.

## Dependencies

```gradle
androidx.work:work-runtime-ktx:2.9.0
com.squareup.okhttp3:okhttp:4.12.0
org.jsoup:jsoup:1.17.2
kotlinx-coroutines-android:1.7.3
androidx.datastore:datastore-preferences:1.0.0
androidx.recyclerview:recyclerview:1.3.2
androidx.swiperefreshlayout:swiperefreshlayout:1.1.0
com.google.android.material:material:1.11.0
```
