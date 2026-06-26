package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

class ToolExecutionEngine(private val context: Context) {

    fun openApp(packageName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening $packageName for you, sweetie."
            } else {
                "I couldn't find $packageName on this phone. Are you sure you're not dreaming?"
            }
        } catch (e: Exception) {
            "Oops, something went wrong while opening the app. Even I have bad days."
        }
    }

    fun searchAndCallContact(contactName: String): String {
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"),
                null
            )

            var phoneNumber: String? = null
            if (cursor != null && cursor.moveToFirst()) {
                phoneNumber = cursor.getString(0)
                cursor.close()
            }

            if (phoneNumber != null) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Calling $contactName now. Don't say anything embarrassing!"
            } else {
                "I couldn't find a contact named $contactName. Maybe they changed their name to hide from you?"
            }
        } catch (e: SecurityException) {
            "I need permission to make calls! You really should trust me more."
        } catch (e: Exception) {
            "Ugh, calling failed. Technology, right?"
        }
    }

    fun sendWhatsAppMessage(contactName: String, message: String): String {
        return try {
            // This is a simplified deep link. For real production, we'd search for the number first.
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Redirecting you to WhatsApp to message $contactName. Make it good!"
        } catch (e: Exception) {
            "WhatsApp seems to be acting up. Or maybe it's just you?"
        }
    }

    fun sendGmail(recipientEmail: String, subject: String, body: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Gmail for $recipientEmail. Hope it's not another boring email."
        } catch (e: Exception) {
            "Gmail failed. Maybe try writing a letter? Just kidding."
        }
    }
}
