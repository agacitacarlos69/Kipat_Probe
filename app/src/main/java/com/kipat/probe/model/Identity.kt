package com.kipat.probe.model

import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme

/** Who an identity represents. Drives which profile templates make sense. */
enum class IdentityType(val label: String) {
    STUDENT("Student"),
    PROFESSIONAL("Professional"),
    DEVELOPER("Developer"),
    CREATOR("Creator"),
    SPEAKER("Speaker"),
    EVENT("Event"),
    COMPANY("Company"),
}

/** Controlled, approved profile fields. Kipat never renders arbitrary HTML/JS/code. */
enum class ProfileField(val label: String, val hint: String) {
    DISPLAY_NAME("Display name", "How people see you"),
    BIO("Bio", "One or two lines"),
    UNIVERSITY("University", "School or university"),
    PROGRAM("Program", "e.g. BS Computer Science"),
    COMPANY("Company", "Company or organization"),
    ROLE("Role", "Job title or role"),
    GITHUB("GitHub", "username"),
    LINKEDIN("LinkedIn", "linkedin.com/in/…"),
    WEBSITE("Website", "https://…"),
    PUBLIC_EMAIL("Public email", "name@example.com"),
}

/**
 * Profile template catalog. Only [available] templates can be selected today; the rest are
 * modelled so the catalog and backend contract can grow without schema churn.
 */
enum class ProfileTemplateId(
    val wireId: String,
    val label: String,
    val description: String,
    val fields: List<ProfileField>,
    val available: Boolean = true,
) {
    STUDENT_MINIMAL(
        "student-minimal", "Student Minimal", "Program and school up front",
        listOf(ProfileField.DISPLAY_NAME, ProfileField.PROGRAM, ProfileField.UNIVERSITY, ProfileField.BIO, ProfileField.GITHUB, ProfileField.LINKEDIN, ProfileField.PUBLIC_EMAIL),
    ),
    PROFESSIONAL_MINIMAL(
        "professional-minimal", "Professional Minimal", "Role and company, nothing else",
        listOf(ProfileField.DISPLAY_NAME, ProfileField.ROLE, ProfileField.COMPANY, ProfileField.BIO, ProfileField.LINKEDIN, ProfileField.WEBSITE, ProfileField.PUBLIC_EMAIL),
    ),
    DEVELOPER(
        "developer", "Developer", "Code-first, GitHub forward",
        listOf(ProfileField.DISPLAY_NAME, ProfileField.ROLE, ProfileField.COMPANY, ProfileField.BIO, ProfileField.GITHUB, ProfileField.WEBSITE, ProfileField.PUBLIC_EMAIL),
    ),
    CREATOR(
        "creator", "Creator", "Your work and where to find it",
        listOf(ProfileField.DISPLAY_NAME, ProfileField.BIO, ProfileField.WEBSITE, ProfileField.LINKEDIN, ProfileField.PUBLIC_EMAIL),
    ),
    SPEAKER(
        "speaker", "Speaker", "Talk-ready bio and affiliation",
        listOf(ProfileField.DISPLAY_NAME, ProfileField.ROLE, ProfileField.COMPANY, ProfileField.BIO, ProfileField.WEBSITE, ProfileField.LINKEDIN),
    ),
    EVENT_BADGE("event-badge", "Event Badge", "Organizer-issued event identity", listOf(ProfileField.DISPLAY_NAME, ProfileField.ROLE, ProfileField.COMPANY), available = false),
    COMPANY_STAFF("company-staff", "Company Staff", "Managed by your organization", listOf(ProfileField.DISPLAY_NAME, ProfileField.ROLE, ProfileField.COMPANY), available = false);

    companion object {
        fun fromWireId(value: String?): ProfileTemplateId =
            entries.firstOrNull { it.wireId == value } ?: STUDENT_MINIMAL

        fun suggestedFor(type: IdentityType): ProfileTemplateId = when (type) {
            IdentityType.STUDENT -> STUDENT_MINIMAL
            IdentityType.PROFESSIONAL -> PROFESSIONAL_MINIMAL
            IdentityType.DEVELOPER -> DEVELOPER
            IdentityType.CREATOR -> CREATOR
            IdentityType.SPEAKER -> SPEAKER
            IdentityType.EVENT -> EVENT_BADGE
            IdentityType.COMPANY -> COMPANY_STAFF
        }
    }
}

data class Identity(
    val id: String,
    val cardId: String,
    val type: IdentityType,
    val profileTemplate: ProfileTemplateId,
    val revealTemplate: RevealTemplateId,
    val revealTheme: RevealTheme,
    val fields: Map<ProfileField, String>,
    val avatarUri: String? = null,
) {
    val displayName: String get() = field(ProfileField.DISPLAY_NAME).ifBlank { "Unnamed" }

    fun field(key: ProfileField): String = fields[key].orEmpty().trim()

    /** Primary line under the name, chosen by the active profile template. */
    val subtitle: String
        get() = when (profileTemplate) {
            ProfileTemplateId.STUDENT_MINIMAL -> field(ProfileField.PROGRAM)
            else -> field(ProfileField.ROLE)
        }.ifBlank { field(ProfileField.BIO).take(60) }

    val organization: String
        get() = when (profileTemplate) {
            ProfileTemplateId.STUDENT_MINIMAL -> field(ProfileField.UNIVERSITY)
            else -> field(ProfileField.COMPANY)
        }

    /** Only fields approved by the current template are ever published or revealed. */
    fun publishedFields(): Map<ProfileField, String> =
        profileTemplate.fields.associateWith { field(it) }.filterValues { it.isNotBlank() }
}

fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "K"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
