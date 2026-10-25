package com.talkswithtanha.twt.features.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.designsystem.WebBackdrop
import com.talkswithtanha.twt.core.designsystem.WebButton
import com.talkswithtanha.twt.core.designsystem.WebColors
import com.talkswithtanha.twt.core.designsystem.WebType
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Free members are in the app now, as on the website: they can look around,
 * read the analysis and the results, use Markets, News and the academy. What
 * a code opens — live trades, live analysis, the feed, the rooms, the
 * exchange and the journal — shows a lock in its place that names what is
 * behind it and opens the code screen.
 *
 * Presentation only. The security rules are what keep a free account out of
 * those reads; this decides what a member sees instead of an error.
 */
@HiltViewModel
class ProAccessViewModel @Inject constructor(session: SessionRepository) : ViewModel() {
    /** Null until the profile has loaded, so nothing flashes locked for a Pro member. */
    val isPro: StateFlow<Boolean?> = session.currentUser
        .map { user -> user?.let { it.hasAppAccess || it.isStaff } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), session.currentUser.value?.let { it.hasAppAccess || it.isStaff })
}

/** Opens the access-code screen. Provided once by the nav host. */
val LocalOpenUnlock = compositionLocalOf<() -> Unit> { {} }

/** True for a Pro member or staff; null while the profile loads. */
@Composable
fun rememberIsPro(viewModel: ProAccessViewModel = hiltViewModel()): Boolean? {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    return isPro
}

/**
 * [content] for Pro members; for a free member, a full-screen lock that names
 * the [feature] and says what it is ([blurb]).
 */
@Composable
fun ProGate(feature: String, blurb: String, content: @Composable () -> Unit) {
    when (rememberIsPro()) {
        true -> content()
        false -> WebBackdrop {
            Box(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), contentAlignment = Alignment.Center) {
                ProLockCard(feature, blurb)
            }
        }
        null -> Unit
    }
}

/** The lock itself, for use inside a screen as well as in place of one. */
@Composable
fun ProLockCard(feature: String, blurb: String, modifier: Modifier = Modifier) {
    val unlock = LocalOpenUnlock.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WebColors.Ink2)
            .border(1.dp, WebColors.Line2, RoundedCornerShape(22.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(WebColors.BrandSoft),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Lock, contentDescription = null, tint = WebColors.Brand, modifier = Modifier.size(22.dp)) }
        Text(feature, style = WebType.h3, textAlign = TextAlign.Center)
        Text(blurb, style = WebType.small, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        WebButton(text = "Unlock with an access code", onClick = unlock)
        Text(
            "Free either way: open an account through our broker and Pro is included.",
            style = WebType.small.copy(color = WebColors.Text3),
            textAlign = TextAlign.Center
        )
    }
}
