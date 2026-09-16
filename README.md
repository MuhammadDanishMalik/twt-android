# TWT for Android

The Android client for Talks With Tanha — a Kotlin + Jetpack Compose port of the
iOS app.

**This is a UI and client-logic port, not a rewrite.** Android talks to the *same*
Firebase project as iOS: same Auth, same Firestore, same collections, same
security rules, same push tokens. A member who signs up on iPhone signs in on
Android and sees their own data. Nothing server-side needs building or changing.

Firebase project: `twt-database-9be63`
Package name: `com.orixto.twt` (Kotlin namespace `com.talkswithtanha.twt`)

---

## Getting it running

### 1. `google-services.json` — required

The build deliberately does **not** fail without it (see `app/build.gradle.kts`),
so a fresh clone compiles and installs for anyone. The app then shows a
"Firebase is not configured" screen instead of crashing.

To connect it for real:

1. Firebase console → project **twt-database-9be63** → Project settings
2. **Add app → Android**, package name exactly `com.orixto.twt`
3. Download `google-services.json` into `app/`
4. Rebuild. The Google Services plugin applies itself on the next sync.

The iOS `GoogleService-Info.plist` is **not** interchangeable.

The file is gitignored on purpose: it carries this project's API key and app id,
so each developer downloads their own rather than sharing one through git.

### 2. SHA-1 fingerprints — required for Google sign-in

Email/password works without this. Google sign-in does not: the token comes back
rejected with an error that does not mention fingerprints.

```sh
./gradlew signingReport      # debug fingerprint
```

Add the SHA-1 to the Android app in the Firebase console, then **re-download
`google-services.json`** — the OAuth client it generates is what
`default_web_client_id` resolves to. Add the release fingerprint (and Play App
Signing's, once uploaded) before shipping.

### 3. Firestore indexes

Any query that filters on one field and orders by another needs a composite
index. **The emulator does not enforce them**, so these pass locally and fail
live — and only once the collection is non-empty, which means they pass every
test until real data arrives.

Two of the three Android needs are already declared in the iOS repo's
`firestore.indexes.json`:

| Collection | Query | Declared? |
|---|---|---|
| `signals` | `isPublished == true` ordered by `timestamp desc` | yes |
| `signalFollows` | `userId == me` ordered by `followedAt desc` | yes |
| `academyVideos` | `isPublished == true` ordered by `createdAt desc` | **no** |

The third is missing from the shared project. iOS runs the identical query, so
its academy tab is affected the same way. Add to `firestore.indexes.json` in the
iOS/admin repo and `firebase deploy --only firestore:indexes`:

```json
{
  "collectionGroup": "academyVideos",
  "queryScope": "COLLECTION",
  "fields": [
    { "fieldPath": "isPublished", "order": "ASCENDING" },
    { "fieldPath": "createdAt",   "order": "DESCENDING" }
  ]
}
```

Note that the academy also requires `isPublished: true` to actually be written on
each lesson. A document without the field is filtered out, not defaulted in.

---

## Where the load-bearing pieces live

| Topic | File |
|---|---|
| Every collection and field name | [`core/firebase/FirestorePaths.kt`](app/src/main/java/com/talkswithtanha/twt/core/firebase/FirestorePaths.kt) |
| Access model, code normalisation | [`core/model/AccessCode.kt`](app/src/main/java/com/talkswithtanha/twt/core/model/AccessCode.kt), [`core/data/AccessTokenRepository.kt`](app/src/main/java/com/talkswithtanha/twt/core/data/AccessTokenRepository.kt) |
| Session, auth, device enforcement | [`core/session/SessionRepository.kt`](app/src/main/java/com/talkswithtanha/twt/core/session/SessionRepository.kt) |
| Follows, stats, alerting | [`core/signals/FollowedSignalTracker.kt`](app/src/main/java/com/talkswithtanha/twt/core/signals/FollowedSignalTracker.kt) |
| Design tokens | [`core/designsystem/`](app/src/main/java/com/talkswithtanha/twt/core/designsystem/) |

`FirestorePaths.kt` is a deliberate mirror of `FirestorePaths.swift`. Keep them
greppable against each other — a typo there does not fail to compile, it silently
reads nothing, and that is the single most expensive mistake available here.

---

## Things that are true and look wrong

**`membershipType: "premium"` does not mean "paid".** It means "has redeemed an
access code". There is no free tier — `"free"` means *signed up, not yet let in*.
The name is kept because iOS, the admin panel and the security rules all read it.

**Access is never `membershipType` alone.** Always `User.hasAppAccess`, which also
applies the expiry. Reading the raw field means a lapsed member keeps access
forever, because nothing rewrites it when the date passes.

**No price, plan or purchase appears anywhere in this binary.** Access is
invite-only by code. That is what lets the app ship without Apple's IAP, and the
same reasoning applies to Google Play Billing.

**A `users` update is judged as a whole.** The rule uses `hasOnly`, so one
disallowed key rejects the entire write. `UserField.SELF_WRITABLE` lists what is
permitted, and the two write paths assert against it rather than discovering the
refusal at runtime.

**The redemption expiry is copied verbatim, never recomputed.** The rule compares
it to the token's own `expiresAt` to the second. Rebuilding the same instant from
millis produces a value that differs by the round trip and the write is rejected.

**Money is an integer in minor units.** Never a float. `0.1 + 0.2` is not `0.3`,
and these values multiply amounts.

---

## Bugs deliberately not rebuilt

The build brief lists bugs iOS has already shipped. Each is handled here:

- **Claim the device session *before* attaching the profile listener.** The other
  order makes the listener fire with the *previous* device's `currentSessionId`,
  see a mismatch, and sign this device out moments after it signed in.
- **A failed write is not a takeover.** Single-device enforcement fails open: only
  a snapshot that actually arrived carrying a different session id signs anyone
  out. Never a network error.
- **Never announce a status change you have not seen change.** The last known
  status is persisted per followed signal, so a cold start records without
  announcing.
- **Never derive a member's result from the signal's.** Two people who took the
  same call did not get the same fill. The member types their own outcome.

Two more were found while porting:

- **Re-following a dropped signal.** iOS uses one `setData(merge:)` that always
  writes `followedAt: serverTimestamp()`. On a first follow that is a create and
  it is fine; on a re-follow it is an update that moves `followedAt`, which the
  update rule does not allow — so the whole write is rejected and re-following
  silently fails. Android runs a transaction and picks the right shape.
- **Support threads invisible in the admin queue.** The `chatRooms/{id}` document
  uses `kind`, `memberId`, `memberName`, `memberEmail` and `needsReply` — not the
  `type`/`user*` names an Android-first reading suggests. The admin panel's queue
  filters `kind == 'support'`, so a room document without it is a conversation
  Tanha never sees.

---

## Not built

- **The five-step email flow.** Steps 2 and 4 do not work on iOS either: Firebase
  emails a *link*, not a six-digit code, and making it a code needs a mail
  provider plus a server route minting a custom token. Passkeys are a pitch
  screen with nothing behind them. Email/password and Google are wired instead.
- **Push delivery.** `TwtMessagingService` handles the payload; nothing in the
  project *sends* one — the admin panel is a browser client and there are no
  Cloud Functions. The in-app path is complete on its own and the push path only
  has to deliver the same payload. Send data messages, not `notification` blocks:
  a `notification` block is swallowed by the system while the app is backgrounded
  and never reaches code that could check whether this member follows the trade.
- **Photo upload.** No Storage path exists yet, same as iOS.
- **Deals.** The USD/PKR desk quotes the live rate and hands off to WhatsApp. The
  `deals` collection and its rules exist for the admin panel's side; wiring the
  member's side is the next piece of work.
- **A Glance widget** for the followed signal. The dependency is in place. This
  would be a genuine improvement over iOS rather than a compromise.

## Sign in with Apple

Deliberately absent. It exists on iOS because Apple requires it of any app
offering third-party sign-in; on Android it would mean a web redirect flow for a
provider none of these members use. Existing accounts that signed up with Apple
still read correctly — `LoginProvider.APPLE` is parsed, just never written.

---

## Build

```sh
./gradlew :app:assembleDebug      # apk
./gradlew :app:testDebugUnitTest  # 30 unit tests
./gradlew :app:installDebug       # to a running emulator or device
```

`compileSdk` is 37 because `hilt-navigation-compose:1.4.0` and the 2026.09
Compose BOM refuse to link against less. `targetSdk` stays at 36 — that is the
one that opts the app in to new runtime behaviour, and it is a separate decision.
