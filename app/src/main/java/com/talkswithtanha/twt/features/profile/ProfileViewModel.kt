package com.talkswithtanha.twt.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.SupportConfigRepository
import com.talkswithtanha.twt.core.model.SupportConfig
import android.graphics.Bitmap
import com.talkswithtanha.twt.core.images.CloudinaryUploader
import com.talkswithtanha.twt.core.images.UploadResult
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.session.SessionRepository
import com.talkswithtanha.twt.core.signals.FollowedSignalTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val session: SessionRepository,
    supportConfig: SupportConfigRepository,
    tracker: FollowedSignalTracker
) : ViewModel() {

    val user: StateFlow<User?> = session.currentUser

    /** Shown as "2 open" beside My Signals, the way iOS does. */
    val openFollowCount: StateFlow<Int> = tracker.openFollows
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val support: StateFlow<SupportConfig> = supportConfig.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupportConfig())

    fun signOut() = session.signOut()

    fun deleteAccount(onDone: () -> Unit) = viewModelScope.launch {
        runCatching { session.deleteAccount() }.onSuccess { onDone() }
    }
}

data class EditProfileUiState(
    val fullName: String = "",
    val phone: String = "",
    val bio: String = "",
    val country: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loadedFor: String? = null,
    val photoUrl: String? = null,
    val isUploadingPhoto: Boolean = false
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val session: SessionRepository,
    private val uploader: CloudinaryUploader
) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.currentUser.collect { user ->
                // Seeded once per account rather than on every emission. The
                // profile listener fires whenever anything on the document
                // changes, and re-seeding would wipe whatever the member was
                // halfway through typing.
                if (user == null) return@collect
                // The photo is not a text field — it is written the moment it
                // is chosen — so it tracks every emission, while the typed
                // fields are seeded once so a save does not wipe live typing.
                _state.update { it.copy(photoUrl = user.profilePhoto) }
                if (_state.value.loadedFor == user.id) return@collect
                _state.update {
                    it.copy(
                        fullName = user.fullName,
                        phone = user.phone.orEmpty(),
                        bio = user.bio.orEmpty(),
                        country = user.country.orEmpty(),
                        loadedFor = user.id
                    )
                }
            }
        }
    }

    fun onFullNameChange(value: String) = _state.update { it.copy(fullName = value, saved = false) }
    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, saved = false) }
    fun onBioChange(value: String) = _state.update { it.copy(bio = value, saved = false) }
    fun onCountryChange(value: String) =
        _state.update { it.copy(country = value.uppercase().take(2), saved = false) }

    /**
     * Uploads the square the member framed, then points their profile at it.
     *
     * Written straight away rather than waiting for Save. A photo is not a
     * half-finished edit the way a part-typed bio is — the member has already
     * confirmed it on the crop screen, and leaving it unsaved until they find
     * a button is how people end up thinking it did not work.
     */
    fun onPhotoCropped(bitmap: Bitmap) {
        if (_state.value.isUploadingPhoto) return
        _state.update { it.copy(isUploadingPhoto = true, error = null) }

        viewModelScope.launch {
            when (val result = uploader.uploadAvatar(bitmap)) {
                is UploadResult.Success -> {
                    runCatching { session.updateProfile(profilePhoto = result.url) }
                        .onFailure {
                            _state.update { s ->
                                s.copy(error = "Photo uploaded, but saving it failed.")
                            }
                        }
                    _state.update { it.copy(isUploadingPhoto = false) }
                }
                is UploadResult.Failed -> _state.update {
                    it.copy(isUploadingPhoto = false, error = result.message)
                }
            }
        }
    }

    /**
     * Clears the photo.
     *
     * The asset is left in Cloudinary. Deleting it needs the API secret, which
     * deliberately does not exist in this app; clearing the field is what the
     * member can actually authorise, and it is what they asked for.
     */
    fun onPhotoRemoved() {
        viewModelScope.launch {
            runCatching { session.updateProfile(profilePhoto = "") }
        }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.fullName.isBlank()) return

        _state.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                session.updateProfile(
                    fullName = current.fullName.trim(),
                    phone = current.phone.trim(),
                    country = current.country.takeIf { it.length == 2 },
                    // Empty string rather than null, so clearing a bio actually
                    // clears it. Null means "not editing this field".
                    bio = current.bio.trim()
                )
                // No local mutation. The profile listener delivers the change,
                // which keeps one source of truth instead of two that disagree
                // if the write is rejected.
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = "Could not save. Check your connection and try again."
                    )
                }
            }
        }
    }
}
