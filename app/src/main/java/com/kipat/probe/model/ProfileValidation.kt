package com.kipat.probe.model

/**
 * Validation for the controlled profile fields. Kipat only accepts plain text and a few
 * well-formed link types — never markup or scripts.
 */
object ProfileValidation {
    const val MAX_NAME = 48
    const val MAX_LINE = 72
    const val MAX_BIO = 160

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    private val HANDLE = Regex("^[A-Za-z0-9-]{1,39}$")
    private val MARKUP = Regex("[<>]|javascript:", RegexOption.IGNORE_CASE)

    fun maxLength(field: ProfileField): Int = when (field) {
        ProfileField.DISPLAY_NAME -> MAX_NAME
        ProfileField.BIO -> MAX_BIO
        else -> MAX_LINE
    }

    /** @return an error message, or null when the value is acceptable. */
    fun validate(field: ProfileField, raw: String): String? {
        val value = raw.trim()
        if (field == ProfileField.DISPLAY_NAME && value.isEmpty()) return "A display name is required"
        if (value.isEmpty()) return null
        if (value.length > maxLength(field)) return "Keep it under ${maxLength(field)} characters"
        if (MARKUP.containsMatchIn(value)) return "Plain text only"
        return when (field) {
            ProfileField.PUBLIC_EMAIL -> if (EMAIL.matches(value)) null else "Enter a valid email"
            ProfileField.GITHUB -> if (HANDLE.matches(value.removePrefix("@"))) null else "Use your GitHub username"
            ProfileField.WEBSITE -> if (value.startsWith("https://") && value.length > 10 && ' ' !in value) null else "Use a full https:// link"
            ProfileField.LINKEDIN -> if (value.contains("linkedin.com/") || HANDLE.matches(value)) null else "Use your LinkedIn URL or handle"
            else -> null
        }
    }

    /** Errors for every field the template publishes. Empty map means publishable. */
    fun validate(template: ProfileTemplateId, fields: Map<ProfileField, String>): Map<ProfileField, String> =
        template.fields.mapNotNull { f -> validate(f, fields[f].orEmpty())?.let { f to it } }.toMap()
}
