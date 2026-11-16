[![English](https://img.shields.io/badge/README-English-24292f?style=for-the-badge)](./README.md) [![한국어](https://img.shields.io/badge/README-%ED%95%9C%EA%B5%AD%EC%96%B4-24292f?style=for-the-badge)](./README.ko.md)

# YeaAnBus · 예안버스

A **personal Android app** for checking bus stops, routes, arrival information, and vehicle locations in the Yecheon–Andong area.

It uses a Material 3 UI with four color themes, light/dark modes, card-style lists, and adjustable text size.
No fake stops or arrival data are inserted at runtime. Andong works without a key; Yecheon/TAGO fallback requests require your own Public Data Portal API key.

Andong and Yecheon stops/routes are searched together without a region-selection step. Nearby stops are also merged across both areas and sorted by distance.
Each result row shows its region so stops or route numbers with the same name can be distinguished.

> **Public source snapshot:** This repository is intended for portfolio/code review. It contains no personal TAGO key, Naver client ID, signing credentials, private APK, or unverified historical screenshots. Andong BIS can work without a TAGO key; Yecheon/TAGO requires your own authorized key. Current UI screenshots will be added after Android device validation.

## Run

1. In Android Studio, open **the folder containing this README**.
2. Install Android SDK Platform 35 and Build Tools. Set the Gradle JDK to **17 or 21**.
3. Replace **`"api키 삽입"`** in `app/src/main/java/kr/yeaan/bus/config/ApiKeys.kt` with your own TAGO Decoding key. Android Studio writes the SDK path to `local.properties`.
4. After Gradle Sync, run `app` on an Android 8.0 (API 26) or newer device/emulator.
5. Search for **Andong Station** or **Yecheon Terminal**, or use the location button to select a nearby stop. There is no region-selection step. Save favorites with the star icon.

Windows:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

macOS/Linux:

```sh
chmod +x gradlew
./gradlew assembleDebug testDebugUnitTest lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`  
Tests: `app/build/reports/tests/testDebugUnitTest/index.html`  
Lint: `app/build/reports/lint-results-debug.html`

## API Key

Apply for access and approval for the following **four services** on Korea's Public Data Portal:

- [TAGO Bus Stop Information](https://www.data.go.kr/data/15098534/openapi.do)
- [TAGO Bus Route Information](https://www.data.go.kr/data/15098529/openapi.do)
- [TAGO Bus Arrival Information](https://www.data.go.kr/data/15098530/openapi.do)
- [TAGO Bus Location Information](https://www.data.go.kr/data/15098533/openapi.do)

**Key file:** `app/src/main/java/kr/yeaan/bus/config/ApiKeys.kt`

```kotlin
object ApiKeys {
    const val TAGO_API_KEY = "api키 삽입"
}
```

Replace the string directly with your own key. This file is excluded from Git.

A freshly cloned project does not include the local key file. Copy
`app/src/keyTemplate/java/kr/yeaan/bus/config/ApiKeys.kt` to the path above, edit it, and run Gradle Sync.
If the local file is missing, Gradle selects the keyless template so CI and Andong BIS requests can still build and run.

Keep the SDK path and Naver Map Client ID (`NAVER_MAP_CLIENT_ID`) in `local.properties`.

Use the **Decoding key**. Retrofit encodes the query string once.
Rebuild the app after changing the key. The app can still build and use Andong BIS without a key.
Yecheon availability is checked dynamically from the TAGO city-code list. If TAGO does not provide the region, the app explains why.

Local `ApiKeys.kt`, `local.properties`, build outputs, and signing keys are excluded from Git. The keyless `src/keyTemplate` is tracked and should not be edited. HTTP logging is not installed, and request URLs/API keys are not printed in error messages.

An API key embedded in an Android client can still be extracted from the APK. This structure prevents **source exposure through Git**, but public distribution that requires a hidden secret should use a server proxy instead. Do not publish an APK containing a personal key in this repository.

## Data Providers and Fallback Requests

The current public read-only HTTPS paths used by [Andong BIS](https://bus.andong.go.kr/route?tab=route) were verified on 2026-09-08.

| Purpose | Public Path |
|---|---|
| Stop list | `/api/bus/stops` |
| Route list | `/api/hp/busRoute` |
| Arrivals by stop | `/api/bus/selectBusStopList?stopId=...` |
| Vehicle GPS | `/api/gnss/all` |
| Stops for a route | `/api/bus/routes/{routeId}/stops` |
| Road geometry | `/api/bus/selectBusVertex?routeId=...` |

The currently public queries do not require a separate BIS key. These paths are not a versioned official developer-contract API and may change when the site is redesigned. They are intended for personal lookup and should be used in accordance with the operator's policies.

- Andong: prefer BIS, fall back to TAGO on request failure. A successful empty arrival list is treated as “no current service information.”
- Yecheon: TAGO. City codes are not arbitrarily hard-coded.
- Search and nearby-stop queries request both regions and merge the results. If one region fails or the TAGO key is missing, results from the other region remain available and the unavailable region is explained.
- Stop ID systems stay separated by provider. A fallback match is made only when the app can confirm **the same stop number within 45 m, or one unique stop with the same name**.
- BIS vehicle matching is confirmed by the actual vehicle license plate. A vehicle is not attached merely because the route number matches, which could otherwise select a bus traveling in the opposite direction.
- TAGO vehicles are attached only when they can be uniquely identified by stop order within the route and remaining-stop count. Circular routes or multiple candidates are not assigned an arbitrary position.
- TAGO location queries are limited to at most four routes nearest in arrival order. The full arrival list is still displayed.
- “No arrival information” is normal late at night, after service ends, or on unsupported routes. The response time and cache state are displayed.
- Real authenticated TAGO requests require the user's key. During development, MockWebServer validates responses, fallback logic, and encoding without a key.

## Location Prediction and Refresh Policy

`BusPredictor` keeps the latest real GPS point, speed, direction, and observation time for each vehicle.

BIS data with server timestamps is parsed explicitly in Korea time. TAGO has no server observation timestamp, so response-receipt time is used; therefore upstream data delay cannot be detected perfectly.

- Speed is stabilized using GPS differences/provider speed plus a one-dimensional Kalman filter.
- Unrealistic GPS jumps, reversed/duplicate timestamps, and invalid coordinates are rejected.
- For a selected route with validated road geometry, the vehicle is projected along the road.
- Without geometry, movement is extrapolated along the recent heading for at most 12 seconds; with geometry, at most 30 seconds, then prediction stops.
- New observations are aligned over 1.5 seconds with smoothstep. Large discontinuities reset the location instead of showing a long incorrect movement animation.
- GPS older than 60 seconds is marked as an old position. Network-error cache is not displayed as a current arrival prediction.
- Map-marker rendering is capped at 30 fps and does not trigger API calls.

| State | Default Polling Interval |
|---|---:|
| Far bus / no information | 30 s |
| About 1–2 km away or ETA ≤ 5 min | 10 s |
| About 500 m away or ETA ≤ 90 s | 5 s |
| About 120 m away or ETA ≤ 35 s | 3 s |
| Connection failure | 60 s → up to 120 s |

The distance 10 seconds into the future is estimated from speed so refresh thresholds can be entered early.

A refresh cycle requests BIS arrivals and, when necessary, GPS. TAGO may also require initial city-code and route-stop lookups.
Concurrent refreshes are merged with a Mutex, and the same stop result is reused for at least 3 seconds.
Search uses a 350 ms debounce. Basic stop/route data is cached for 24 hours.
Real-time network work runs only while Home, Arrival, or Map is active; it is canceled on the search screen, in the background, or when the screen is off.

Arrival prediction uses remaining road distance (or straight-line distance × 1.18 when road distance is unavailable), recent speed, the route's session-learned average speed (initially 6 m/s), 16 seconds per intermediate stop, and an 8-second buffer.

If the API provides an ETA, 70% of that value is retained and 30% of the internal prediction is blended in, with correction limits. These coefficients are **heuristics prior to field calibration** and accuracy is not guaranteed.

## Storage and Notifications

Room stores:

- Favorites and the 12 most recent lookups
- Latest real vehicle locations
- Arrival snapshots, search cache, and route cache
- Arrival-error cache displayed for at most 10 minutes; vehicle records cleaned after 1 day and general cache after 7 days

Device location is requested only once when the user presses the button. It is not separately sent to a server or stored in the database.
Search and favorites continue to work if location permission is denied.
The map uses the Naver Map SDK, so map-loading network information is sent to the map provider as required by that service.

`ArrivalWork.enqueueCheck` and `ArrivalCheckWorker` provide a **one-time condition-check structure** for future notifications.
They are not scheduled by default and do not currently post notifications. The returned data only indicates whether arrival is within 5 minutes and includes the ETA.
Permission UI, notification posting, and duplicate-prevention policy for precise 5-minute alerts are future work.
Because periodic WorkManager jobs have a minimum interval of 15 minutes, WorkManager is not used for 3–30 second live refresh.

## Map

The app uses Naver Map Android SDK 3.23.3. In Naver Cloud's newer **Maps** product, enable Dynamic Map and register Android package `kr.yeaan.bus`.

Add the following value to `local.properties` and rebuild:

```properties
NAVER_MAP_CLIENT_ID=YOUR_Client_ID
```

A Client Secret is not required. The newer Maps `NCP_KEY_ID` authentication flow is used.
The default Naver logo and copyright attribution provided by the SDK are retained.

The map instance is reused while the screen remains active, and bus refreshes update marker coordinates only.
Authentication or usage-limit errors are displayed on the map and are not retried in an automatic loop.

Manage billing limits in the Naver console under **Limits and Alerts**.
Recommended project limits are 200,000 requests/day and 5,000,000/month with overage disabled.
Whether account-level free allocation applies and how much other apps under the same account consume must still be checked.
Because limit enforcement can be delayed, application code alone cannot guarantee zero billing.
Nearby place search is not currently implemented; the base map's own building/business labels are used.
Historical OpenStreetMap screenshots are intentionally excluded from this public source snapshot; current UI captures need device verification.

## Technology and Structure

Kotlin 2.0.21 · Jetpack Compose · Material 3 · MVVM · Coroutines/Flow · Retrofit 2.11 · OkHttp 4.12 · Room 2.6.1/KSP · WorkManager 2.10 · Gradle Kotlin DSL/Wrapper 8.9 · AGP 8.7.3.

```text
app/src/main/java/kr/yeaan/bus/
├── algorithm/          GPS/speed filter/map projection/ETA/refresh policy (pure Kotlin)
├── data/
│   ├── local/          Room entities/DAO/Database
│   ├── model/          provider-independent domain models
│   ├── remote/         Retrofit/BIS/TAGO response parsing
│   └── repository/     provider priority/fallback/cache/storage
├── location/           one-time location acquisition and cancellation
├── notification/       WorkManager extension point
├── ui/
│   ├── BusViewModel.kt StateFlow/search/screen state/refresh lifecycle
│   ├── components/     shared lists/empty states/notices
│   ├── screens/        Home/Search/BusArrival/Map
│   └── theme/          color/typography/shape
├── MainActivity.kt     Compose entry/permissions/navigation
└── YeaAnBusApp.kt      dependency assembly
app/src/test/           automated algorithm/API/repository tests
app/src/androidTest/    emulator UI verification
app/schemas/            Room schemas
```

The User tab lets you select Ocean Blue, Forest, Lavender, or Sunset Orange themes plus system/light/dark display mode.
To open a route on the map, tap a bus row in the arrival list.

## Validation

As of 2026-09-08, 37 unit tests passed. Before the integrated-search change, two real BIS screen tests were also verified along with background map tiles and bus markers. Build/Lint results and screen-verification scope for each change are documented in the validation record below.

`testDebugUnitTest` verifies GPS/ETA/refresh intervals, TAGO single/array/empty responses, prevention of double API-key encoding, timestamp parsing, caching, and fallback behavior without a real key or external network.

Emulator UI:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

To include real BIS search:

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.liveBis=true"
```

Live UI tests require a working BIS response and internet access.

Screenshots preferentially capture the Compose app window itself and save files such as `yeaan-qa-*.png` in the test device's Download directory. If app-window capture times out, a system screenshot is saved for diagnostics.

Development validation results and remaining limitations are recorded in [docs/VALIDATION.md](docs/VALIDATION.md).

## User Tab and Personal Settings

- Four color themes: Ocean Blue, Forest, Lavender, Sunset Orange
- Follow system / Light / Dark display modes
- Default / Large (115%) / Larger (130%) text sizes, applied in addition to the phone's font-size setting
- Data saver: limits automatic bus polling to at least 30-second intervals. Error retry backoff remains active and manual refresh is still available.
- Keep map screen awake: active only while viewing the map
- Open and remove favorites directly

Settings are stored in device SharedPreferences and persist after restart.
The User tab stops real-time bus polling.
Data Saver does not replace map-provider billing limits.
Home notices, search fields, stop lists, and arrival lists follow the selected theme.

### Selecting Stops and Buses on the Map

The Map tab can be opened without first searching for a stop.
It loads the complete Andong/Yecheon stop list from the existing daily cache and attaches markers only for stops inside the visible map area.
Moving the map reveals stops in the newly visible area.
Tapping a stop opens a bottom panel where an arriving bus can be selected.

After selecting a bus, its vehicle location and route line are displayed.
If Andong BIS provides road geometry, the route is shown as a solid line.
If road geometry is unavailable, stop order is connected with a dashed line.
The dashed line is not used as road geometry and does not invent a vehicle position when GPS is unavailable.
Use the All Buses button to return to showing buses for the selected stop.
Partial stop-list failures can be retried.

At the user's request, this change is built only through the installable package stage; final map-screen and physical-device verification is left to the user.

### Favorite Commute Dashboard, Widget, and Lock Screen

- New favorites are limited to three stops. Existing stored data with more than three favorites is not deleted; Home and widgets display the first three.
- Home displays up to three upcoming buses per favorite stop and refreshes every 30 seconds while the screen is active (60 seconds with Data Saver). Failures and lookup times are displayed per stop.
- User tab → Add widget to Home screen. The widget displays estimated arrival and lookup time and supports manual refresh plus a 15-minute WorkManager schedule. Actual execution may be delayed by power-saving/network state.
- User tab → Start live commute view. With notification permission, a foreground service refreshes the notification and widget together for up to one hour. It can be stopped from the notification or User tab. Lock-screen visibility follows the phone's notification settings; this is not a dedicated lock-screen widget.
- Arrival-summary-only API requests and a shared 30-second cache are used. If no widget is added, periodic widget work is not scheduled. GPS and route geometry are not requested for summary-only views.
- At the user's request, final screen/physical-device testing is not performed.

### Three Transparent Widget Sizes and Per-Widget Settings

Small (one stop), medium (up to two), and large (up to three) widgets are provided.
Add them from the User tab size buttons or the launcher widget list, then use each widget's settings button to choose favorite stops.
Selection is stored per widget ID, so multiple widgets of the same size can show different stops.
A stop removed from favorites disappears from the widget and is not automatically replaced.

The background is transparent, and text can be white or dark.
Small and medium widgets show the nearest bus for each stop; large shows two.
Exact launcher cell dimensions vary, but each widget can expand beyond its minimum size.
Existing large widgets remain intact and display existing favorites until configured.

### Theme Colors and Rounded Cards

Tapping Theme in the User tab opens a bottom color picker.
The selected theme is applied to backgrounds, cards, the Home header, and arrival highlights.
Cards and rows use rounded corners and subtle shadows.
Color changes are stored immediately and the sheet closes with Done.

For this change, the APK is built but screen testing is not run.

Widgets do not display lookup timestamps.
Stop names use large bold text, route numbers use the secondary color, and estimated arrival time uses mint (or dark teal with the dark-text option).
A refresh notice is shown only when a request fails and older data is being displayed.


## License

Code in this repository is available under the [MIT License](LICENSE). Third-party Android SDKs and data providers retain their own licenses and terms.
