package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.images.Cloudinary

/**
 * A member's face, or the letter that stands in for it.
 *
 * Extracted because five screens were drawing this circle independently and
 * three of them had been taught to show a photograph while two had not — so a
 * member who set an avatar saw it in Settings and their initial on the home
 * screen, which reads as the app not knowing who they are.
 *
 * The photo is fetched through Cloudinary at the size it will actually be drawn
 * — a 38dp circle has no use for a 1024px square, and on a phone connection the
 * difference is the whole reason the avatar appears at all.
 */
@Composable
fun MemberAvatar(
    photoUrl: String?,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackInitial: String = "T"
) {
    val initial = name?.trim()?.takeIf { it.isNotEmpty() }
        ?.take(1)?.uppercase()
        ?: fallbackInitial

    // Requested at twice the drawn size, so it stays sharp on a 2x and 3x
    // screen without asking for the full upload.
    val rendered = Cloudinary.avatar(
        photoUrl?.takeIf { it.isNotBlank() },
        size = (size.value * 2).toInt()
    )

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(IosColors.AvatarGradient)),
        contentAlignment = Alignment.Center
    ) {
        if (rendered != null) {
            AsyncImage(
                model = rendered,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                // Proportional to the circle, so one component serves a 22dp
                // chat bubble and a 76dp profile header without a size table.
                fontSize = (size.value * 0.40f).sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
