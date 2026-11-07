# AGENTS.md

Working guide for anyone (human or agent) changing this repository. Read this before
touching code. It records the branch covenant, the build/QA setup, and the conventions
this fork has already committed to — not generic advice.

## What this repository is

**Densha**, a personal fork of [SubwayTooter](https://github.com/tateisu/SubwayTooter),
an Android Mastodon client (Kotlin + Jetpack Compose alongside classic views).

| Thing | Value |
|---|---|
| Fork remote | `origin` = `git@github.com:tribela/SubwayTooter.git` |
| Upstream remote | `upstream` = `https://github.com/tateisu/SubwayTooter` |
| Kotlin namespace | `jp.juggler.subwaytooter` (unchanged on purpose — changing it churns every import and collides with upstream PRs) |
| Application IDs | `io.github.tribela.densha` (fcm) / `io.github.tribela.densha.noFcm` (nofcm) |
| Variants | Flavors `nofcm` (use this) and `fcm` (broken without a matching `google-services.json`) |
| SDK | compile 36, target 35, min 26; JVM target 17 |
| Build | Gradle wrapper 8.14.3, JDK 17+ |

Fork-only work (branding, application id, package-derived plumbing) lives directly on
`personal`; it is not upstream-PR-able.

## Branch covenant

This is the part that matters most. It is a standing agreement, not a suggestion.

- `main` mirrors `upstream/main`. Never commit on it, never branch work off anything else.
- **`personal` is the linear trunk.** Implement features there: commit directly and push.
- **Upstream PRs are staged on `feature/<short-topic>` branches cut from `main`**, and the
  work is transferred by **cherry-picking the specific commits** from `personal`.
  Example:
  ```bash
  git switch -c feature/my-topic main
  git cherry-pick <sha1> <sha2>     # only the commits that belong upstream
  git push -u origin feature/my-topic
  git switch personal
  ```
- **Never merge a feature branch back into `personal`.** The two histories are not meant to
  meet; `personal` already has the work, and a merge drags `main` history into the trunk.
  (Older `--no-ff` merges exist in history; do not repeat that pattern.)
- Feature branches never carry fork-only changes: no branding, no application-id edits, no
  local dev/QA plumbing. Keep those off the PR.
- One topic per branch; one self-contained commit per fix or change inside it. If you add a
  follow-up fix on `personal`, cherry-pick it onto the same feature branch as its own commit.

## Build

`keystore.properties` is read **unconditionally** at configuration time by
`app/build.gradle.kts`, so every Gradle task fails without it — including debug builds.

```bash
# Compile only
./gradlew :app:compileNofcmDebugKotlin

# Debug APK
./gradlew :app:assembleNofcmDebug
# -> app/build/outputs/apk/nofcm/debug/app-nofcm-debug.apk
```

Do not ship an `fcm` build until a `google-services.json` for the new application id exists.
The one in the tree belongs to the upstream author's Firebase project; using it would route
push subscriptions through upstream infrastructure.

## Signing across machines (local-only secrets)

`keystore.properties` is gitignored (as are `*.keystore`/`*.jks`). Never commit a keystore,
a password, or a filled-in properties file.

```properties
# keystore.properties (repository root, not committed)
storeFile=/absolute/path/to/shared-release.keystore
storePassword=...
keyAlias=release
keyPassword=...
```

The shared key is the one already used by the other Android apps on these machines. Keep a
copy of it at a stable path on each machine and point `storeFile` at it. The keystore file,
its path, and the passwords are local only: nothing but this procedure is committed.

**Why this matters:** debug builds that are not given an explicit `signingConfig` fall back
to `~/.android/debug.keystore`, which every machine generates independently. A different
signature means `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, so moving
between machines forces an uninstall and loses app data (accounts, drafts, settings). The
fix is that all machines sign with the same key:

- `keystore.properties` points at the shared keystore (local to each machine, same content).
- The build's debug variant uses that signing config as well
  (`debug { signingConfig = signingConfigs.getByName("release") }` in `app/build.gradle.kts`),
  so a debug APK carries the same certificate on every machine.

A machine without `keystore.properties` cannot configure the project at all — that is
expected, not a regression.

Changing the key is a one-time uninstall per already-installed device; export app data from
Settings → Backup first if the accounts matter.

## QA environment

Feature QA runs on the emulator; every committed build is also installed on the phone.

### Emulator `densha-qa`

```bash
/home/jarm/Android/Sdk/emulator/emulator -avd densha-qa -scale 0.6 &
```
`emulator` is not on `PATH`; KVM works. The AVD is signed in to the local dev server.

### Local Mastodon server

- Checkout: `~/git/qdon.space` (Glitch/beta source of qdon.space). Database name `mastodon`
  (not `mastodon_development`) and already seeded.
- Start Valkey on 6379 before Rails (it is not enabled by default).
- `.env.development` needs `LOCAL_DOMAIN=localhost:3000` and
  `LIBRE_TRANSLATE_ENDPOINT=http://127.0.0.1:5000`.
- `RAILS_ENV=development bundle exec rails server -b 0.0.0.0 -p 3000`.
- The first `/api/v2/instance` after boot takes ~40 s while Rails compiles assets; the app's
  client times out around 30 s. Warm it with `curl -m 60 http://localhost:3000/api/v2/instance`
  before driving the app, or it looks like a hang.
- Login from the app: the login form rejects an explicit `http://` scheme and the OAuth
  browser path is pointless on an emulator — use **Enter access token** with a Doorkeeper
  token minted in `rails runner`, and keep `adb -s emulator-5554 reverse tcp:3000 tcp:3000`
  active.
- Cleartext HTTP for `localhost` / `10.0.2.2` is already permitted by
  `api/LocalDev.kt` + `app/src/debug/AndroidManifest.xml`.

### Phone

Wireless ADB (`adb devices` can list it with no USB cable). Install the nofcm debug APK:

```bash
adb install -r app/build/outputs/apk/nofcm/debug/app-nofcm-debug.apk
adb shell am start -W -n io.github.tribela.densha.noFcm/jp.juggler.subwaytooter.ActMain
adb exec-out screencap -p > /tmp/screen.png   # then actually look at the image
```

A lock screen is not a blocker — probe `adb devices` / `dumpsys window policy` / take a
screenshot before declaring the device unreachable or asking the user to unlock it.

## Conventions this fork has committed to

Treat these as product decisions, not preferences.

- **Account selection.** Act with the viewing account; offer "other account" as a separate
  entry (the shared `AccountPicker.pickAccount`) instead of forcing a picker. Long-press is
  the existing shortcut for "another account" on boost/favourite/reply-style actions.
- **Caching.** Never add app-level TTL caches. Send the request and respect
  `Cache-Control`/`ETag`/`Vary`; the shared OkHttp client has an HTTP cache. Mastodon marks
  authenticated endpoints `private, no-store`, so those are simply not cached.
- **Optimistic rendering.** When the app already knows the account/status JSON, seed the
  column and render it before the API answers (`ColumnExtra1` seeds, `bHasSeed` keeps the
  seed visible while `startLoading` runs). Verify by stalling the API — the first frame, not
  the eventually-loaded screen, is the thing under test.
- **Progress and errors.** Loading rows must show the live API call (`taskProgress`, e.g.
  `Acquiring: GET /api/v2/filters`), never a generic "loading" string. On failure keep known
  content and append `Couldn't load: <reason>` inline; the full-screen error is only for
  columns with no seed. Profile columns show header progress in the header and list progress
  in the list.
- **Dialogs.** Reuse the screen's existing component in place. The hashtag menu is the shared
  `ActionsDialog` with `compact = true` (84 px rows, `dlg_compact_action_row.xml`); groups are
  `group(caption) { ... }`. Never stand up a parallel dialog class or restyle another menu to
  imitate it. If a compact row is a `Button`, it must be `clickable=false`/`focusable=false`
  or it swallows the tap.
- **Impossible actions.** If the server would certainly reject a request (translating into
  the status's own language, a non-translatable visibility, a tag that cannot be followed
  from this account), hide or disable the control up front instead of letting the tap fail.
- **Branding.** Launcher label `Densha` in every locale, application id `io.github.tribela.densha*`,
  icon sources under `_ArtWork`/design SVG in `/tmp/densha-icons` history, splash points at
  `@mipmap/ic_launcher`. Do not reintroduce per-flavor launcher overrides.

## Commits

Messages follow the shape already in the history: `prefix: subject`, then a body that says why.

| Prefix | Used for |
|---|---|
| `tag:` | hashtag column/menu behavior |
| `timeline:` | timeline actions |
| `conversation:` | status detail and conversation windows |
| `status:` | status rendering, translation, status actions |
| `ui:` | visual changes |
| `perf:` | caching, request and render timing |
| `share:` | share/intent/FileProvider plumbing |
| `dev:` | build, tooling, docs |
| `branding:` | fork-only name, icon, application id |

- Lowercase prefix and imperative subject, no trailing period; the body explains why and names
  any user-visible consequence.
- One self-contained change per commit. A follow-up fix to a feature is its own commit on the
  same feature branch — never squashed into an unrelated commit and never a plain "fix typo"
  with no topic.
- Fork-only work stays on `personal`; a commit that belongs in an upstream PR must be
  cherry-pickable on its own, so keep branding and local-dev edits out of it.
- Commits written with an agent keep the tool's `Co-authored-by:` trailer. Do not add
  attribution for yourself beyond that.
- Never commit a secret: no keystore, no `keystore.properties`, no `google-services.json` that
  belongs to someone else's Firebase project.

## Before you call it done

1. `./gradlew :app:compileNofcmDebugKotlin` (or the relevant unit test task) passes.
2. Any changed UI was seen on `densha-qa` (and on the phone for a committed build) at the
   state the change is about — mid-load, error, and empty states included.
3. Dialogs/menus were actually tapped; confirm the server request or state change, not just
   that the row rendered.
4. If the work is upstream-PR-able, the cherry-picked `feature/<topic>` branch exists and the
   fork-only commits stayed on `personal`.
