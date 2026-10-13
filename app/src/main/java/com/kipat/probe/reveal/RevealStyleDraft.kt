package com.kipat.probe.reveal

/**
 * The Reveal style picker's state: what is applied to the person's card and what they are
 * trying. Selecting only changes the draft; [apply] is the explicit commit, so a tap on a
 * thumbnail never changes how others see you.
 */
data class RevealStyleDraft(
    val appliedTemplate: RevealTemplateId,
    val appliedTheme: RevealTheme,
    val template: RevealTemplateId = appliedTemplate,
    val theme: RevealTheme = appliedTheme,
) {
    val hasChanges: Boolean get() = template != appliedTemplate || theme != appliedTheme

    fun select(t: RevealTemplateId) = copy(template = t)

    fun select(t: RevealTheme) = copy(theme = t)

    /** The draft becomes the applied style. */
    fun apply() = copy(appliedTemplate = template, appliedTheme = theme)
}
