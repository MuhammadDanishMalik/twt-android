package com.talkswithtanha.twt.features.setup

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.images.CloudinaryUploader
import com.talkswithtanha.twt.core.images.UploadResult
import com.talkswithtanha.twt.core.model.BrokerStatus
import com.talkswithtanha.twt.core.model.Brokers
import com.talkswithtanha.twt.core.model.Experience
import com.talkswithtanha.twt.core.model.IbStatus
import com.talkswithtanha.twt.core.model.TradingDuration
import com.talkswithtanha.twt.core.model.TradingProfile
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** One question per screen, in the order they are asked. */
enum class SetupStep { ABOUT, EXPERIENCE, DURATION, BROKER, IB }

data class ProfileSetupUiState(
    val step: SetupStep = SetupStep.ABOUT,
    val fullName: String = "",
    val country: String = "",
    val photoUrl: String? = null,
    val isUploadingPhoto: Boolean = false,
    val experience: Experience? = null,
    val duration: TradingDuration? = null,
    val brokerStatus: BrokerStatus? = null,
    /** A name from [Brokers.known], or [Brokers.OTHER]. */
    val broker: String? = null,
    val brokerOther: String = "",
    val ibStatus: IbStatus? = null,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    /** The IB question only makes sense for somebody who has an account. */
    val steps: List<SetupStep>
        get() = if (brokerStatus?.hasAccount == false) SetupStep.entries - SetupStep.IB
        else SetupStep.entries

    val stepIndex: Int get() = steps.indexOf(step).coerceAtLeast(0)
    val isLastStep: Boolean get() = step == steps.last()

    val canContinue: Boolean
        get() = !isSaving && !isUploadingPhoto && when (step) {
            SetupStep.ABOUT -> fullName.isNotBlank() && country.length == 2
            SetupStep.EXPERIENCE -> experience != null
            SetupStep.DURATION -> duration != null
            SetupStep.BROKER -> when {
                brokerStatus == null -> false
                !brokerStatus.hasAccount -> true
                broker == null -> false
                broker == Brokers.OTHER -> brokerOther.isNotBlank()
                else -> true
            }
            SetupStep.IB -> ibStatus != null
        }

    val initial: String get() = fullName.trim().take(1).uppercase().ifBlank { "T" }
}

/**
 * Getting to know a new member: who they are, how long they have traded, and
 * where. Asked once, after the email is confirmed and before the access code.
 *
 * Nothing is written until the last step, and then in a single update — so a
 * member who closes the app halfway comes back to the start rather than to a
 * half-saved profile the gate would already consider finished. The one
 * exception is the photo, which is saved the moment it is uploaded, exactly as
 * on the edit-profile screen.
 */
@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val session: SessionRepository,
    private val uploader: CloudinaryUploader
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    init {
        val user = session.currentUser.value
        _state.update {
            it.copy(
                fullName = user?.fullName.orEmpty(),
                // Sign-up guessed this from the phone's region; the member
                // confirms or changes it here.
                country = user?.country?.takeIf { c -> c.length == 2 }
                    ?: Locale.getDefault().country.takeIf { c -> c.length == 2 }.orEmpty(),
                photoUrl = user?.profilePhoto
            )
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(fullName = value.take(60), error = null) }
    fun onCountryChange(code: String) = _state.update { it.copy(country = code.uppercase(), error = null) }

    fun onExperience(value: Experience) = pick { it.copy(experience = value) }
    fun onDuration(value: TradingDuration) = pick { it.copy(duration = value) }
    fun onIb(value: IbStatus) = pick { it.copy(ibStatus = value) }

    fun onBrokerStatus(value: BrokerStatus) = _state.update {
        it.copy(
            brokerStatus = value,
            // Clearing the broker when they say they have none, so a name they
            // tapped before changing their mind is not saved against "Not yet".
            broker = if (value.hasAccount) it.broker else null,
            ibStatus = if (value.hasAccount) it.ibStatus else null,
            error = null
        )
    }

    fun onBroker(name: String) = _state.update { it.copy(broker = name, error = null) }
    fun onBrokerOther(value: String) = _state.update { it.copy(brokerOther = value.take(60)) }

    /**
     * A single-answer question moves on by itself once answered — tapping an
     * option and then a Continue button is one tap more than the question needs.
     * The pause is long enough to see the selection land and read the
     * sticker it stamps.
     */
    private fun pick(change: (ProfileSetupUiState) -> ProfileSetupUiState) {
        _state.update { change(it).copy(error = null) }
        // Never on the last step: that tap would save the whole profile, and
        // finishing should be something the member chooses to do.
        if (_state.value.isLastStep) return
        val stepAtPick = _state.value.step
        viewModelScope.launch {
            delay(750)
            if (_state.value.step == stepAtPick) next()
        }
    }

    fun next() {
        val current = _state.value
        if (!current.canContinue) return
        if (current.isLastStep) {
            save()
            return
        }
        _state.update { it.copy(step = it.steps[it.stepIndex + 1]) }
    }

    /** False when there is no earlier step, so the screen knows to let back through. */
    fun back(): Boolean {
        val current = _state.value
        if (current.stepIndex == 0) return false
        _state.update { it.copy(step = it.steps[it.stepIndex - 1], error = null) }
        return true
    }

    fun onPhotoCropped(bitmap: Bitmap) {
        _state.update { it.copy(isUploadingPhoto = true, error = null) }
        viewModelScope.launch {
            when (val result = uploader.uploadAvatar(bitmap)) {
                is UploadResult.Success -> {
                    runCatching { session.updateProfile(profilePhoto = result.url) }
                    _state.update { it.copy(isUploadingPhoto = false, photoUrl = result.url) }
                }
                is UploadResult.Failed -> _state.update {
                    it.copy(isUploadingPhoto = false, error = result.message)
                }
            }
        }
    }

    fun onPhotoRemoved() {
        _state.update { it.copy(photoUrl = null) }
        viewModelScope.launch { runCatching { session.updateProfile(profilePhoto = "") } }
    }

    private fun save() {
        val s = _state.value
        _state.update { it.copy(isSaving = true, error = null) }
        val profile = TradingProfile(
            brokerStatus = s.brokerStatus,
            broker = when {
                s.brokerStatus?.hasAccount != true -> null
                s.broker == Brokers.OTHER -> s.brokerOther.trim()
                else -> s.broker
            },
            ibStatus = s.ibStatus.takeIf { s.brokerStatus?.hasAccount == true },
            experience = s.experience,
            tradingDuration = s.duration
        )
        viewModelScope.launch {
            runCatching { session.completeProfileSetup(s.fullName, s.country, profile) }
                // Success needs nothing here: profileSetupAt arrives through the
                // profile listener and the gate moves on to the access code.
                .onFailure {
                    _state.update {
                        it.copy(isSaving = false, error = "Could not save. Check your connection and try again.")
                    }
                }
        }
    }

    fun signOut() = session.signOut()
}
