package com.talkswithtanha.twt.core.images

/**
 * Cloudinary, for everything the app shows and everything a member uploads.
 *
 * ## Why there is no API secret here
 *
 * An APK is a zip file. Anything compiled into it — a string constant, a
 * BuildConfig field, a value in `local.properties` that gets baked in — can be
 * read by anyone who downloads the app, in about the time it takes to run
 * `unzip`. So the secret is not here, and must never be added: it would let a
 * stranger delete every asset in the account.
 *
 * The two values below are the ones Cloudinary designs to be public. The cloud
 * name is in every delivery URL the app renders, so it is already on the wire.
 * The upload preset is a named set of rules that lives in the Cloudinary console
 * — what folder uploads land in, how large they may be, which formats are
 * allowed. The app names the preset; Cloudinary enforces it. Tightening the
 * rules is a console change, not an app release.
 *
 * The preset must be created once, in Settings → Upload → Upload presets, as
 * **unsigned**, with:
 *
 *  - Folder: `twt/avatars`
 *  - Allowed formats: `jpg, png, webp, heic`
 *  - Max file size: 10 MB
 *  - Unique filename: on, Overwrite: off
 *
 * Moderation is worth turning on too. Unsigned means someone who unpacks the
 * app could upload into that folder, and the preset's restrictions are the only
 * thing standing in the way.
 */
object Cloudinary {

    /** Public by design — it appears in every delivery URL the app renders. */
    const val CLOUD_NAME = "zqncgxxg"

    /** The unsigned preset that governs member avatar uploads. */
    const val AVATAR_PRESET = "twt_avatars"

    const val UPLOAD_ENDPOINT = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"

    private const val DELIVERY_MARKER = "/image/upload/"

    /**
     * Injects transformations into a Cloudinary delivery URL.
     *
     * Anything that is not a Cloudinary URL is returned untouched, which is the
     * point: [com.talkswithtanha.twt.core.model.User.profilePhoto] also holds
     * Google sign-in photo URLs on accounts that were created that way, and on
     * iOS, and those must keep rendering.
     */
    fun transformed(url: String?, transformation: String): String? {
        if (url.isNullOrBlank()) return url
        val marker = url.indexOf(DELIVERY_MARKER)
        if (marker == -1 || !url.contains("res.cloudinary.com")) return url

        val cut = marker + DELIVERY_MARKER.length
        // A URL that already carries these transformations would otherwise
        // accumulate a second copy every time it passed through here.
        val tail = url.substring(cut)
        if (tail.startsWith("$transformation/")) return url
        return url.substring(0, cut) + transformation + "/" + tail
    }

    /**
     * A member's avatar at [size] device pixels, square.
     *
     * `c_fill` with `g_face` crops to the face when the image has one, which
     * matters because the member already chose their crop — this is the second
     * crop, the one that fits their choice into whatever circle the screen
     * happens to be drawing. `f_auto,q_auto` let Cloudinary pick the format and
     * quality per request, so a modern Android gets AVIF and an old one gets
     * JPEG without the app knowing the difference.
     */
    fun avatar(url: String?, size: Int): String? =
        transformed(url, "c_fill,g_face,w_$size,h_$size,f_auto,q_auto,dpr_2.0")

    /** A wide image — a chart or a screenshot — fitted to [width] device pixels. */
    fun wide(url: String?, width: Int): String? =
        transformed(url, "c_limit,w_$width,f_auto,q_auto")
}
