package com.talkswithtanha.twt.core.firebase

/**
 * Every collection and field name this app reads or writes, in one place.
 *
 * This is a deliberate mirror of `Twt/Core/Firebase/FirestorePaths.swift` in the
 * iOS project, and it exists for the same reason: Android, iOS and the Next.js
 * admin panel all talk to the *same* Firestore database. A typo'd string literal
 * in a query does not fail to compile — it silently returns nothing, and the two
 * apps quietly stop being able to see each other's data.
 *
 * So the names are spelled out here rather than derived from `@PropertyName`
 * annotations or a serializer, because the point is that a human can hold this
 * file next to `FirestorePaths.swift` and `twt-admin/lib/types.ts` and check
 * them by eye.
 *
 * **Do not rename anything here to something that reads better.** Several of
 * these names are wrong-ish on purpose and the reasons are documented at each
 * one — `membershipType` in particular does not mean what it says.
 */
object FirestorePaths {

    object Collection {
        const val USERS = "users"
        const val SIGNALS = "signals"
        const val DEALS = "deals"
        const val MESSAGES = "messages"
        const val CHAT_ROOMS = "chatRooms"
        const val ACADEMY_VIDEOS = "academyVideos"
        const val ANNOUNCEMENTS = "announcements"

        /**
         * Single-document collection holding values Tanha sets by hand — the
         * exchange rate lives here, not in a random walk on the client.
         */
        const val CONFIG = "config"

        /**
         * One document per access code, keyed by the code itself. Nobody may
         * *list* this collection — see firestore.rules. Knowing the code is the
         * only way to read the code, which is what stops somebody paging through
         * it looking for an unclaimed one.
         */
        const val ACCESS_TOKENS = "accessTokens"

        /**
         * One document per member per signal they took, addressed
         * `{uid}_{signalId}`. Flat rather than nested under either parent because
         * it is queried from both directions — the member's journal filters by
         * [SignalFollowField.USER_ID], and the notification fan-out filters by
         * [SignalFollowField.SIGNAL_ID] and [SignalFollowField.IS_ACTIVE].
         */
        const val SIGNAL_FOLLOWS = "signalFollows"
    }

    object Document {
        const val EXCHANGE_RATE = "exchangeRate"

        /**
         * Where a member is sent when they need a person: the access page, the
         * WhatsApp number, the support mailbox and the two legal links. Edited
         * from the admin panel so a changed number does not need a new build.
         */
        const val SUPPORT = "support"

        /** Holds the receiving accounts for the exchange desk. */
        const val MARKETPLACE = "marketplace"
    }

    /**
     * The community is one shared room. Support is one room *per member* — "chat
     * with a real person" is a conversation between one member and Tanha, not
     * something the room can see.
     */
    object ChatRoom {
        const val COMMUNITY = "community"

        /**
         * A second room rather than a permission flag on the first, because the
         * security rules gate by room id and Firestore cannot hide a subset of a
         * collection's documents. It is gated on *reading*, not just posting —
         * which is what closes it again the moment somebody's code lapses.
         */
        const val PREMIUM = "premium"

        const val SUPPORT_PREFIX = "support_"

        /**
         * The private room between [uid] and Tanha. The uid is in the room id so
         * the security rules can authorise a read without a document lookup.
         */
        fun support(uid: String): String = SUPPORT_PREFIX + uid
    }

    object SignalField {
        const val PAIR = "pair"
        const val ASSET = "asset"
        const val TYPE = "type"
        const val ENTRY_PRICE = "entryPrice"
        const val ENTRY_PRICE_VALUE = "entryPriceValue"
        const val TAKE_PROFITS = "takeProfits"
        const val STOP_LOSS = "stopLoss"
        const val STOP_LOSS_VALUE = "stopLossValue"
        const val STATUS = "status"
        const val TIMESTAMP = "timestamp"
        const val TIMEFRAME = "timeframe"
        const val RISK_REWARD = "riskReward"
        const val PIPS_GAINED = "pipsGained"
        const val NOTES = "notes"
        const val IS_PUBLISHED = "isPublished"
        const val TRADE_STYLE = "tradeStyle"
        const val CHART_IMAGE_URL = "chartImageURL"

        /**
         * Extra images the team attaches — the setup on another timeframe, a
         * close-up of the entry, a screenshot of the fill. Separate from
         * [CHART_IMAGE_URL], which is the one chart Tanha draws on.
         */
        const val SCREENSHOTS = "screenshots"
        const val IS_EDITED = "isEdited"

        object TakeProfit {
            const val LABEL = "label"
            const val PRICE = "price"
            const val IS_HIT = "isHit"
        }
    }

    object ExchangeRateField {
        /**
         * PKR per 1 USD, stored in **paisa** so it is an integer. Money is never
         * a float here — 0.1 + 0.2 is not 0.3, and this value multiplies amounts.
         */
        const val PKR_PER_USD_PAISA = "pkrPerUsdPaisa"
        const val SELL_PKR_PER_USD_PAISA = "sellPkrPerUsdPaisa"
        const val PREVIOUS_PKR_PER_USD_PAISA = "previousPkrPerUsdPaisa"
        const val HISTORY = "history"
        const val MIN_AMOUNT_PAISA = "minAmountPaisa"
        const val MAX_AMOUNT_PAISA = "maxAmountPaisa"
        const val IS_ACCEPTING_DEALS = "isAcceptingDeals"
        const val UPDATED_AT = "updatedAt"
        const val UPDATED_BY = "updatedBy"
    }

    /**
     * The document id is the normalised code — `TWT4H2K9XQP`, uppercase, no
     * separators. See `AccessCode` for why, and for what a member may type
     * instead.
     */
    object AccessTokenField {
        /**
         * The display form with dashes, kept for the admin panel to show and for
         * Tanha to paste into WhatsApp. The id is what is looked up.
         */
        const val CODE = "code"

        /** Free text: "Ali Raza — 3 months, paid 12 Aug". Never shown in the app. */
        const val LABEL = "label"

        /**
         * When access granted by this code ends. **Absent or null means it never
         * expires.** Set at creation, not at redemption — the security rules
         * compare it to the expiry the client writes onto the profile, and an
         * exact comparison is only possible against a fixed date.
         */
        const val EXPIRES_AT = "expiresAt"

        /**
         * Tanha's kill switch. Turning this off does not revoke access already
         * granted — clear the member's `membershipExpiresAt` for that.
         */
        const val IS_ACTIVE = "isActive"

        /**
         * The uid that redeemed it. Null until then. A code is single-use: once
         * this is set, only the same uid may present it again, which is what
         * makes a forwarded code worthless.
         */
        const val CLAIMED_BY = "claimedBy"
        const val CLAIMED_BY_EMAIL = "claimedByEmail"
        const val CLAIMED_AT = "claimedAt"
        const val CREATED_AT = "createdAt"
        const val CREATED_BY = "createdBy"
        const val UPDATED_AT = "updatedAt"

        /**
         * The uid the code was generated *for*, if Tanha picked a member when
         * creating it. Advisory only — the rules do not check it, because people
         * sign up with a different address than they messaged from.
         */
        const val ISSUED_FOR_USER_ID = "issuedForUserId"
        const val ISSUED_FOR_EMAIL = "issuedForEmail"
    }

    object SignalFollowField {
        const val USER_ID = "userId"
        const val SIGNAL_ID = "signalId"

        /**
         * Copied from the signal at follow time, not read live — a journal that
         * rewrites itself when Tanha edits the call is not a journal. The
         * security rules refuse to let these four move after creation.
         */
        const val PAIR = "pair"
        const val TYPE = "type"
        const val ENTRY_PRICE = "entryPrice"
        const val STOP_LOSS = "stopLoss"

        const val FOLLOWED_AT = "followedAt"

        /**
         * False once dropped. The document is never deleted: it is the member's
         * record, and it is what the fan-out checks before sending anything.
         */
        const val IS_ACTIVE = "isActive"
        const val UNFOLLOWED_AT = "unfollowedAt"

        /**
         * `open` · `win` · `loss` · `breakeven`. The member's own result, which
         * is not the same thing as the signal's status.
         */
        const val OUTCOME = "outcome"
        const val RESULT_PIPS = "resultPips"

        /** Integer, in the currency's minor unit. Never a float. */
        const val RESULT_AMOUNT_MINOR = "resultAmountMinor"
        const val RESULT_CURRENCY = "resultCurrency"
        const val NOTE = "note"
        const val RECORDED_AT = "recordedAt"
    }

    /**
     * The `deals` collection, mirrored from `DealField` in the iOS project and
     * `twt-admin/lib/deals-repo.ts`.
     */
    object DealField {
        const val USER_ID = "userId"
        const val USER_NAME = "userName"
        const val REFERENCE = "reference"
        const val SIDE = "side"

        /** Whole dollars, as a number — what the admin panel reads. */
        const val AMOUNT_USD = "amountUSD"
        /** The same amount in cents. Written alongside so nothing downstream
         *  has to trust a float for money. */
        const val AMOUNT_USD_CENTS = "amountUsdCents"

        const val LOCKED_RATE = "lockedRate"
        const val LOCKED_RATE_PAISA = "lockedRatePaisa"

        const val SELLER_ACCOUNT = "sellerAccount"
        const val RECEIVING_ACCOUNT = "receivingAccount"
        const val PAYMENT_REFERENCE = "paymentReference"
        const val STATUS = "status"
        const val EVENTS = "events"
        const val CREATED_AT = "createdAt"
    }

    /**
     * `config/marketplace` — the accounts a member pays into.
     *
     * Read live rather than compiled in. The iOS app carries Tanha's account
     * numbers as literals in `MarketplaceSeller.tanha`, and they are
     * placeholders — `03001234567` and a dummy IBAN. An app that shows a
     * made-up account number on the screen where somebody is about to transfer
     * money is worse than one that shows none, so this reads what staff have
     * actually configured and says so when there is nothing.
     */
    object MarketplaceConfigField {
        const val ACCOUNTS = "accounts"
        const val METHOD = "method"
        const val ACCOUNT_TITLE = "accountTitle"
        const val ACCOUNT_NUMBER = "accountNumber"

        /**
         * The seller's shopfront: the name, handle and record shown above the
         * rate.
         *
         * Every one of these is optional and the screen omits whatever is
         * missing. They are claims about a person a member is about to send
         * money to, so they are Tanha's to state in the admin panel — an app
         * that ships "4.9 ★" as a constant is inventing a reputation.
         */
        const val SELLER_NAME = "sellerName"
        const val SELLER_HANDLE = "sellerHandle"
        const val IS_VERIFIED = "isVerified"
        const val DEALS_COMPLETED = "dealsCompleted"
        const val RATING = "rating"
        /** Typical minutes from approved payment to released funds. */
        const val RELEASE_MINUTES = "releaseMinutes"
    }

    object SupportConfigField {
        /** Digits only, country code first. No `+`, no spaces. */
        const val WHATSAPP_NUMBER = "whatsAppNumber"
        const val SUPPORT_EMAIL = "supportEmail"

        /**
         * The public "how to get an access code" page. `https` only — the app
         * refuses every other scheme, see `SupportConfigRepository`.
         */
        const val ACCESS_PAGE_URL = "accessPageURL"
        const val TERMS_URL = "termsURL"
        const val PRIVACY_URL = "privacyURL"
        const val UPDATED_AT = "updatedAt"
        const val UPDATED_BY = "updatedBy"
    }

    object MessageField {
        const val TEXT = "text"
        const val SENDER_ID = "senderId"

        /**
         * Denormalised onto the message on purpose. The security rules keep the
         * `users` collection private, so a member cannot look up who wrote
         * something — the name has to travel with the message.
         */
        const val SENDER_NAME = "senderName"
        const val TIMESTAMP = "timestamp"
        const val REACTIONS = "reactions"
        const val IS_EDITED = "isEdited"
        const val IS_PINNED = "isPinned"
        const val REPLY_TO = "replyTo"
        const val NOTE_ATTACHMENT = "noteAttachment"
        const val SEEN_BY = "seenBy"

        object Reply {
            const val SENDER_NAME = "senderName"
            const val TEXT = "text"
        }
    }

    /**
     * Fields on the `chatRooms/{roomId}` document itself. A member writes their
     * own support room document — name, email and a preview of the last message
     * — so the thread appears in Tanha's queue. Firestore subcollections survive
     * without a parent document, so without this the messages exist and the
     * conversation is invisible.
     */
    object ChatRoomField {
        /**
         * `"support"` for a private thread. The admin panel's queue is
         * `where(kind == 'support').orderBy(lastMessageAt desc)`, so a room
         * document without this field is a conversation Tanha never sees —
         * mirrored from `SupportRoomField` in the iOS project, where these names
         * are also mirrored into `twt-admin/lib/support-repo.ts`.
         */
        const val KIND = "kind"
        const val KIND_SUPPORT = "support"

        const val MEMBER_ID = "memberId"
        const val MEMBER_NAME = "memberName"
        const val MEMBER_EMAIL = "memberEmail"
        const val LAST_MESSAGE = "lastMessage"
        const val LAST_MESSAGE_AT = "lastMessageAt"
        const val UPDATED_AT = "updatedAt"

        /**
         * True when the member has spoken last. Cleared by the admin panel when
         * Tanha replies — it is the whole reason his queue has an order.
         *
         * Set, not incremented: a member sending a second message before he has
         * read the first should not read as two unhandled threads. It is one
         * conversation waiting on him.
         */
        const val NEEDS_REPLY = "needsReply"
    }

    object AcademyVideoField {
        const val TITLE = "title"
        const val DESCRIPTION = "description"

        /**
         * The YouTube id, despite the name.
         *
         * The field is called `videoUrl` because it once held one; the admin
         * panel now writes a bare id into it and iOS reads it as such. Renaming
         * it would orphan every lesson already published, so the name stays
         * wrong and this comment exists instead. The thumbnail is derived from
         * it rather than stored, so there is no second field to keep in sync.
         */
        const val VIDEO_ID = "videoUrl"

        const val CATEGORY = "category"
        const val LEVEL = "level"

        /** Seconds. */
        const val DURATION = "duration"

        /** Points toward the learning score the academy screen shows. */
        const val XP_REWARD = "xpReward"

        const val IS_PUBLISHED = "isPublished"
        const val CREATED_AT = "createdAt"
    }

    /**
     * Spelled out rather than derived from a serializer so the admin panel's
     * TypeScript can be checked against this list by eye.
     */
    object UserField {
        const val UID = "uid"
        const val FULL_NAME = "fullName"
        const val EMAIL = "email"
        const val PROFILE_PHOTO = "profilePhoto"
        const val PHONE = "phone"

        /** Free text the member writes about themselves. Self-writable. */
        const val BIO = "bio"

        /** ISO 3166-1 alpha-2. Decides which price the member is quoted. */
        const val COUNTRY = "country"
        const val LOGIN_PROVIDER = "loginProvider"

        /**
         * **Does not mean "paid".** It means "has redeemed an access code".
         * There is no free tier — `free` means *signed up, not yet let in*. The
         * name is kept because iOS, the admin panel and the security rules all
         * read it and every existing document carries it.
         */
        const val MEMBERSHIP_TYPE = "membershipType"

        /** **null means never expires.** See `User.hasAppAccess`. */
        const val MEMBERSHIP_EXPIRES_AT = "membershipExpiresAt"

        /**
         * The normalised access code this member redeemed. Written by the app
         * during redemption and by nothing else; the rules will only accept it
         * alongside a matching, unclaimed token document.
         */
        const val ACCESS_CODE = "accessCode"
        const val ACCESS_GRANTED_AT = "accessGrantedAt"
        const val ROLE = "role"

        /** Can still read, cannot post. */
        const val IS_BLOCKED = "isBlocked"
        const val REFERRAL_CODE = "referralCode"

        /** One phone can hold two accounts, so this is written with `arrayUnion`. */
        const val FCM_TOKENS = "fcmTokens"

        // Single-device session enforcement.
        const val CURRENT_SESSION_ID = "currentSessionId"
        const val LAST_ACTIVE_DEVICE_ID = "lastActiveDeviceId"
        const val LAST_ACTIVE_DEVICE_MODEL = "lastActiveDeviceModel"
        const val LAST_ACTIVE_PLATFORM = "lastActivePlatform"
        const val LAST_LOGIN_AT = "lastLoginAt"

        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"

        /**
         * Exactly the keys a member is allowed to change on their own document.
         *
         * The security rule uses `hasOnly`, so an update is judged **as a
         * whole**: one key outside this set rejects the *entire* write, not just
         * the offending field. This has already caused two production bugs on
         * iOS — see the note on `UserRepository.claimDeviceSession`.
         *
         * Kept as a set so writes can be checked against it before they are sent
         * rather than after they are refused.
         */
        val SELF_WRITABLE: Set<String> = setOf(
            FULL_NAME, PHONE, BIO, PROFILE_PHOTO, COUNTRY, EMAIL, FCM_TOKENS,
            CURRENT_SESSION_ID, LAST_ACTIVE_DEVICE_ID, LAST_ACTIVE_DEVICE_MODEL,
            LAST_ACTIVE_PLATFORM, LAST_LOGIN_AT, UPDATED_AT
        )

        /**
         * The five keys the redemption rule allows to move together, and only
         * together, when a real access token backs the write.
         */
        val REDEMPTION_KEYS: Set<String> = setOf(
            ACCESS_CODE, ACCESS_GRANTED_AT, MEMBERSHIP_TYPE, MEMBERSHIP_EXPIRES_AT, UPDATED_AT
        )
    }
}
