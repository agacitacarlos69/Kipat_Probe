package com.kipat.probe.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast

/**
 * One-tap "Save contact": opens Android's own contact editor pre-filled (ACTION_INSERT). No
 * contacts permission and no .vcf download — the recipient reviews and saves in their contacts app.
 */
object ContactSaver {
    fun intent(name: String, jobTitle: String?, company: String?, cardId: String?): Intent =
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, name)
            jobTitle?.takeIf { it.isNotBlank() }?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
            company?.takeIf { it.isNotBlank() }?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
            cardId?.let { putExtra(ContactsContract.Intents.Insert.NOTES, "Kipat: https://kipat.app/c/$it") }
        }

    /** @return false when no contacts app can handle the request (shown to the user as a toast). */
    fun save(context: Context, name: String, jobTitle: String?, company: String?, cardId: String?): Boolean = try {
        context.startActivity(intent(name, jobTitle, company, cardId))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No contacts app found to save this contact", Toast.LENGTH_SHORT).show()
        false
    }
}
