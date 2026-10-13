package com.kipat.probe.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.model.Identity
import com.kipat.probe.model.IdentityType
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.ProfileTemplateId
import com.kipat.probe.model.ProfileValidation
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds an editable draft of the identity; nothing is visible to others until published. */
class ProfileViewModel : ViewModel() {
    private val _draft = MutableStateFlow(KipatRepository.identity.value)
    val draft: StateFlow<Identity> = _draft.asStateFlow()

    val isDirty: Boolean get() = _draft.value != KipatRepository.identity.value

    fun errors(): Map<ProfileField, String> = ProfileValidation.validate(_draft.value.profileTemplate, _draft.value.fields)

    fun setType(type: IdentityType) = _draft.update {
        val suggested = ProfileTemplateId.suggestedFor(type)
        it.copy(type = type, profileTemplate = if (suggested.available) suggested else it.profileTemplate)
    }

    fun setTemplate(t: ProfileTemplateId) {
        if (t.available) _draft.update { it.copy(profileTemplate = t) }
    }

    fun setReveal(t: RevealTemplateId) = _draft.update { it.copy(revealTemplate = t) }

    fun setTheme(t: RevealTheme) = _draft.update { it.copy(revealTheme = t) }

    fun setField(field: ProfileField, value: String) = _draft.update {
        it.copy(fields = it.fields + (field to value.take(ProfileValidation.maxLength(field))))
    }

    /** @return false when validation blocks publishing. */
    fun publish(): Boolean {
        if (errors().isNotEmpty()) return false
        val trimmed = _draft.value.let { d -> d.copy(fields = d.fields.mapValues { it.value.trim() }) }
        _draft.value = trimmed
        KipatRepository.publishIdentity(trimmed)
        return true
    }

    fun discard() {
        _draft.value = KipatRepository.identity.value
    }
}
