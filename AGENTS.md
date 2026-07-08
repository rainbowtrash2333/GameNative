# GameNative (fork)

基于 [GameNative](https://github.com/utkarshdalal/GameNative) 二次开发。保留核心引擎（Wine/Proton/Winlator 容器运行、Steam/Epic/GOG 下载、云存档），**重写 UI，简化为直接导入 ZIP 游戏包**。GPL 3.0。

**核心原则：尽量不修改上游源代码** — 新代码放到 `runtime/` 包，只通过 intent / event / 接口与上游交互。方便 merge 上游更新。

## Setup & Build

```bash
# Prerequisites: JDK 17 (Temurin), Android SDK 36, NDK 27.3.13750724
# Gradle wrapper: 8.12.1 | Kotlin: 2.1.21 | AGP: 8.8.0

# Secrets (put in local.properties — never commit):
#   POSTHOG_API_KEY, POSTHOG_HOST, STEAMGRIDDB_API_KEY, CLOUD_PROJECT_NUMBER
#   META_APP_ID, PRODUCT_SKU (modernXr only)

# Build all release bundles
./gradlew :app:bundleLegacyRelease :app:bundleModernRelease :app:bundleLegacyXrRelease

# Run unit tests (this is the CI gate — runs on PR to master)
./gradlew :app:testLegacyDebugUnitTest :app:testModernDebugUnitTest

# Lint (hard gate — ignoreFormatFailures = false)
./gradlew :app:lintKotlin        # check
./gradlew :app:formatKotlin       # auto-fix
```

## Architecture

Single-Activity app with Compose navigation, **fork 的架构分层**:

```
PluviaApp (@HiltAndroidApp, SplitCompatApplication)  ← 上游代码，尽量不改
  ├── MainActivity (@AndroidEntryPoint)               ← 上游代码，尽量不改
  │     └── PluviaMain() — root @Composable           ← 上游代码（未来会全替换）
  │           └── NavHost routes
  └── RuntimeMainActivity (no Hilt) ← LAUNCHER         ← fork 新代码
        └── HomeScreen() — 游戏列表 + ZIP 导入          ← fork 新代码
             ├── GameManager (扫描/导入/删除 ZIP 游戏)
             ├── GameLauncher (通过 Intent 启动游戏)
             └── GameConfig / InstalledGame (数据模型)
```

- **`runtime/` 包** (`app/src/main/java/app/gamenative/runtime/`) — **本文档所有的自定义代码**。GameManager 扫描 `filesDir/games/{gameId}/` 下的 ZIP 导入的游戏，GameLauncher 构建 intent 启动上游的 MainActivity 来运行游戏。
- **`MainActivity`** (`MainActivity.kt`) — 上游的 `@AndroidEntryPoint` Activity，通过 `LAUNCH_GAME` intent 和 `app_id` extra 接收启动请求。**不改它**。
- **ZIP 导入方案**：用户通过 SAF 选择 `game.zip`（内含 `game_config.json` + 游戏文件），GameManager 解压到 `filesDir/games/{gameId}/`，GameLauncher 构建 intent 启动上游引擎。
- **Source root**: `app/src/main/java/app/gamenative/`
- **Native root**: `app/src/main/cpp/` (CMake files exist but native is shipped as **prebuilt jniLibs** — all cmake blocks are commented out in `build.gradle.kts`)
- **Dynamic feature**: `ubuntufs/` (delivers Ubuntu filesystem at runtime via SplitCompat)

## Modules & Flavors

- **`:app`** — main monolith (namespace `app.gamenative`)
- **`:ubuntufs`** — dynamic feature (namespace `app.ubuntufs`, depends on `:app`). Only declares `release-signed` and `release-gold` build types (no plain `release`).

4 product flavors, all in `androidApi` dimension:

| Flavor | minSdk | targetSdk | ABIs | MODERN_ANDROID | XR | MODERN_XR | PRELOAD_BIONIC_SO |
|--------|--------|-----------|------|----------------|----|-----------|-------------------|
| `legacy` | 26 | 28 | arm64-v8a, armeabi-v7a | false | no | false | libredirect-bionic.so |
| `legacyXr` | 26 | 28 | arm64-v8a, armeabi-v7a | false | yes | false | libredirect-bionic.so |
| `modern` | 29 | 36 | arm64-v8a | true | no | false | libredirect-bionic-wx.so |
| `modernXr` | 29 | 36 | arm64-v8a | true | yes | true | libredirect-bionic-wx.so |

`MODERN_ANDROID`, `XR_BUILD`, `MODERN_XR`, and `GOLD` are BuildConfig booleans used throughout for conditional logic.

**Source sets per flavor:**
- `src/nonXr/java/` — shared source for all non-XR flavors (legacy, modern)
- `src/legacy/assets/`, `src/modern/assets/` — flavor-specific assets
- `src/legacy/jniLibs/`, `src/modern/jniLibs/` — flavor-specific native libs (XR flavors only)

Build types: `debug`, `release`, `release-signed` (dual keystore), `release-gold` (`.gold` suffix, gold icon).

**Required build-tools:** CI installs `build-tools;34.0.0` for bundletool (`tools/bundletool-all-1.17.2.jar`) and apksigner (dual-sign with `--lineage`).

## Key Packages

| Package | Purpose |
|---------|---------|
| `service/steam/` | Steam auth, downloads, cloud saves, achievements, workshop, input config |
| `service/epic/` | Epic OAuth, installs, cloud saves, overlay |
| `service/gog/` | GOG auth, installs, cloud saves |
| `service/amazon/` | Amazon auth, installs, manifest parsing |
| `runtime/` | GameManager, GameLauncher, GameConfig — core launch engine |
| `gamefixes/` | 31 per-game compatibility fixes + 8 type definitions, base class, and registry |
| `workshop/` | Steam Workshop mod management + symlink compatibility overrides |
| `ui/screen/xserver/` | In-game X11 rendering UI (~6.3k lines across 4 files) |
| `ui/screen/library/` | Game library grid/list with per-store detail screens |
| `db/` | Room database `pluvia.db` v23 — 14 entities, 14 DAOs, auto-migrations 8→23 |
| `di/` | 2 Hilt modules: `DatabaseModule`, `AppThemeModule` (both `@SingletonComponent`) |
| `events/` | Typed event bus: `AndroidEvent` (26 types) + `SteamEvent` + `EventDispatcher` |
| `utils/` | ~81 utility files — container ops, Steam utils, downloads, custom game scanner, manifest installer |

> 除 `runtime/` 外，以上所有包均为上游代码，**尽量只读**。

## DI & Database

- **Hilt**: 2 explicit `@Module` classes only (`DatabaseModule`, `AppThemeModule`). Everything else uses `@HiltViewModel` + `@AndroidEntryPoint` constructor injection.
- **Room**: `PluviaDatabase` at `db/PluviaDatabase.kt`. DAOs in `db/dao/` (14 DAOs). Converters in `db/converters/`. Serializers in `db/serializers/`. Schema exports to `app/schemas/`.

## Testing

- **Framework**: JUnit 4, Robolectric 4.14 (for Android resource access in unit tests), Mockito 5, MockK 1.13, MockWebServer 5.1
- **Unit tests**: `app/src/test/java/` (68 files). CI gate: `:app:testLegacyDebugUnitTest :app:testModernDebugUnitTest`
- **Instrumented**: `app/src/androidTest/java/` (5 files). Runner: `AndroidJUnitRunner`. Not in CI.
- **Test resources**: `app/src/test/resources/epic/` — Epic manifest fixtures.
- **Robolectric needs**: `testOptions { unitTests { isIncludeAndroidResources = true } }` is enabled.

## Lint & Style

- **ktlint** via `org.jmailen.kotlinter` plugin (not a standalone .ktlint file). Rules in `.editorconfig`.
- Android Studio code style. Max line 140. Trailing commas allowed. Wildcard imports allowed.
- `kotlinter { ignoreFormatFailures = false }` — lint failures break the build.
- Android Lint: `ExtraTranslation` disabled (pre-existing harm from appcompat locale files). Other lint errors would fail `lintVital*` release tasks.
- 16 languages configured in `resourceConfigurations` (en, es, da, pt-rBR, zh-rTW, zh-rCN, fr, de, uk, it, ro, pl, ru, ko, ja, and future additions).

## CI

| Workflow | Trigger | Action |
|----------|---------|--------|
| `pluvia-pr-check.yml` | PR to master | Runs unit tests (`testLegacyDebugUnitTest` + `testModernDebugUnitTest`) |
| `app-release-signed.yml` | Push to master | Builds 3 release bundles, dual-signs APKs, posts to Discord |
| `tagged-release.yml` | Tag `v*` | Builds, dual-signs, creates GitHub prerelease with 3 APKs |
| `adhoc-signed-build.yml` | Manual dispatch (owner only) | Same build for any branch/PR |
| `issues-contributors-only.yml` | Issue opened | Auto-closes issues from non-contributors |

## Native / NDK

NDK 27.3.13750724. All `externalNativeBuild { cmake }` blocks in `app/build.gradle.kts` are **commented out** — native libs ship as **prebuilt jniLibs**. The CMake files are in `app/src/main/cpp/` for local development only:

- `virglrenderer/` — GPU virtualization (VirGL)
- `proot/` — user-space chroot
- `winlator/` — Wine/Proton compatibility layer
- `steambootstrap/` — Steam client native bootstrap (source withheld from repo)
- `lsfg-vk-android/` — Lossless Scaling Frame Generation Vulkan layer (submodule)
- `extras/adrenotools/` — Adreno GPU tools (submodule)
- `patchelf/`, `asurfacerenderer/`, `evshim/`, `xconnectorpatch/`

Git submodules:
- `app/src/main/cpp/extras/adrenotools` → `Pipetto-crypto/libadrenotools`
- `app/src/main/cpp/lsfg-vk-android` → `GameNative/lsfg-vk-android`

## Important Constraints

- **永远不修改上游源代码**（`service/`, `gamefixes/`, `workshop/`, `ui/`, `db/`, `di/`, `events/`, `utils/`, `MainActivity.kt`, `PluviaApp.kt`）。新代码必须只放在 `runtime/` 包。只能通过 intent / event / 接口与上游交互。
- **ZIP 导入的游戏通过 intent 启动上游引擎**：`GameLauncher` 构建 `app.gamenative.LAUNCH_GAME` intent 发送给 `MainActivity`。Intent extra 必须严格遵守 `IntentLaunchManager` 的预期：
  - `"app_id"` (Int) — 游戏数字 ID
  - `"game_source"` (String) — 如 `"CUSTOM"`
  - `"container_config"` (JSON, 可选) — `ContainerData` 格式
- **Never commit secrets**. `local.properties` is gitignored. `app/keystores/` is gitignored except `.gitkeep`.
- **GitHub issues are auto-closed** (non-contributors). Direct users to Discord. Do not open issues.
- **ProGuard**: `-dontobfuscate` is set. Keep rules for JavaSteam, SpongyCastle, Meta Horizon, Timber.
- **JavaSteam** uses `SNAPSHOT` version (`1.8.0.1-21-SNAPSHOT`) with `isChanging = true`. Maven snapshots repo in `settings.gradle.kts` (Sonatype). For local builds, set `val localBuild = true` in `app/build.gradle.kts` dependencies block.
- **Steam bootstrap source** (`steam_bootstrap.c`) is withheld from the public repo per `.gitignore` — out of respect for Valve's proprietary internals.
- **Contributing**: Must discuss in Discord `#code-changes` before opening a PR. Scope is core/stability/compatibility only. New features, UI changes, and cosmetic changes are out of scope unless explicitly approved.
- **No pre-commit hooks**.
- **`app/src/main/assets/recommendations.json`** is gitignored — generated/updated elsewhere.
- **`manifest.json`** (root) declares downloadable drivers (Turnip, DXVK, VKD3D, Proton, FEX, WOWBox64). Debug builds copy it into assets via `copyDebugManifest` task.
- **`keyvalues/`** contains Steam appinfo test data for compatibility testing. Not part of the build.
- **`docs/`** has API specs (JSON) for Epic, GOG, Amazon. Reference only.
- **`tools/bundletool-all-1.17.2.jar`** is committed and used in CI to extract universal APKs from AABs.

## Version Catalog (key versions)

| Dependency | Version |
|------------|---------|
| AGP | 8.8.0 |
| Kotlin | 2.1.21 |
| KSP | 2.1.21-2.0.2 |
| Hilt | 2.55 |
| Room | 2.8.4 |
| Compose BOM | 2025.01.01 |
| Navigation Compose | 2.8.6 |
| ktlint plugin | 5.0.1 |
| Secrets Gradle | 2.0.1 |

## Debug-only Notes

- `PostHogAndroidConfig` personProfiles is `ALWAYS` (every event is an identified one).
- StrictMode `detectLeakedClosableObjects()` is enabled in debug builds.
- `LeakCanary` is commented out in `build.gradle.kts` but can be uncommented for local debugging (requires excluding junit from `debugImplementation`).
- `system/lib[64]/libjpeg.so` is preloaded at app startup (required by dlopen'd native libs).
