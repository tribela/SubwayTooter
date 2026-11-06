package jp.juggler.subwaytooter.api.entity

import android.text.Spannable

class TootTranslation(
    val decoded_content: Spannable,
    val decoded_spoiler_text: Spannable?,
    val language: String?,
    val detected_source_language: String?,
    val provider: String?,
)
