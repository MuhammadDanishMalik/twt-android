package com.talkswithtanha.twt.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String
)

/**
 * Three pages, and deliberately no mention of a price, a plan or a purchase.
 *
 * Access is invite-only by code. That is what lets the app ship without Apple's
 * IAP, and the same reasoning applies to Google Play Billing — anything in this
 * binary that reads as "buy the app" invites the exact review conversation the
 * model exists to avoid.
 */
private val pages = listOf(
    OnboardingPage(
        Icons.Outlined.ShowChart,
        "Signals you can act on",
        "Entry, stop and targets, with Tanha's chart attached. Follow a trade and your phone tells you when it moves."
    ),
    OnboardingPage(
        Icons.Outlined.School,
        "Learn the reasoning",
        "Lessons that explain why a setup is a setup, not just what to press."
    ),
    OnboardingPage(
        Icons.Outlined.Forum,
        "Ask a real person",
        "A community room, and a private thread with Tanha when you need one."
    )
)

@Composable
fun OnboardingScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    TwtScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Spacing.xl)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val item = pages[page]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(Radius.card))
                            .background(TwtColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = TwtColors.Gold,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = TwtColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TwtColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = Spacing.xs)
                            .height(6.dp)
                            .width(if (selected) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) TwtColors.Gold else TwtColors.HairlineStrong)
                    )
                }
            }

            val isLast = pagerState.currentPage == pages.lastIndex

            TwtButton(
                text = if (isLast) "Create account" else "Next",
                onClick = {
                    if (isLast) onCreateAccount()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            )

            TextButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "I already have an account",
                    color = TwtColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
