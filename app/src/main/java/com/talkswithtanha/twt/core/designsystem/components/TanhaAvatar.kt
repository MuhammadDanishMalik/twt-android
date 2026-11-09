package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.data.SupportConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TanhaPhotoViewModel @Inject constructor(config: SupportConfigRepository) : ViewModel() {
    val photo: StateFlow<String?> = config.observeConfig().map { it.tanhaPhotoUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Tanha's face wherever he speaks — feed posts, his replies — from the one
 * photo set on `config/support` (`tanhaPhoto`), the same one the chat header
 * uses. The TWT mark until a photo is set.
 */
@Composable
fun TanhaAvatar(size: Dp, modifier: Modifier = Modifier, viewModel: TanhaPhotoViewModel = hiltViewModel()) {
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    Box(modifier.size(size).clip(CircleShape).background(Color(0xFF0B0B0B)), contentAlignment = Alignment.Center) {
        val url = photo
        if (url != null) {
            coil3.compose.AsyncImage(
                model = com.talkswithtanha.twt.core.images.Cloudinary.transformed(url, "c_fill,g_face,w_160,h_160,f_auto,q_auto") ?: url,
                contentDescription = "Tanha",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Image(painterResource(R.drawable.twt_logo), contentDescription = "Tanha", modifier = Modifier.size(size * 0.9f))
        }
    }
}
