package com.talkswithtanha.twt.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.SupportConfigRepository
import com.talkswithtanha.twt.core.model.SupportConfig
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val session: SessionRepository,
    supportConfig: SupportConfigRepository
) : ViewModel() {

    val user: StateFlow<User?> = session.currentUser

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
    val loadedFor: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val session: SessionRepository
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
                if (user == null || _state.value.loadedFor == user.id) return@collect
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
