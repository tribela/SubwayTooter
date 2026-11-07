package jp.juggler.subwaytooter.util

import android.content.Context
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.SavedAccount

fun Context.translationTargetLanguage(): String =
    PrefS.spTranslationLanguage.value.ifBlank {
        resources.configuration.locales[0].language
    }

fun Context.loadTranslationLanguageList(): List<Pair<String, String>> {
    val uiLocale = resources.configuration.locales[0]
    val uiLanguage = uiLocale.displayLanguage.ifBlank { uiLocale.language }
    return buildList {
        add("" to getString(R.string.use_ui_language, uiLanguage))
        addAll(
            loadLanguageList().filter {
                it.first != SavedAccount.LANG_WEB && it.first != SavedAccount.LANG_DEVICE
            }
        )
    }
}

internal fun normalizeTranslationLanguage(language: String?): String? =
    language?.substringBefore('-')?.lowercase()
        ?.takeIf { it.isNotBlank() && it != "und" }

internal fun isSameTranslationLanguage(source: String?, targetLanguage: String): Boolean {
    val sourceLanguage = normalizeTranslationLanguage(source) ?: return false
    return sourceLanguage == targetLanguage.substringBefore('-').lowercase()
}
