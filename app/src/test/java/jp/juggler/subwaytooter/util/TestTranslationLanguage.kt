package jp.juggler.subwaytooter.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestTranslationLanguage {

    @Test
    fun unknownSourceLanguageIsNotTreatedAsSame() {
        assertFalse(isSameTranslationLanguage(null, "ko"))
        assertFalse(isSameTranslationLanguage("", "ko"))
        assertFalse(isSameTranslationLanguage("und", "ko"))
    }

    @Test
    fun knownSourceLanguageComparesByPrimarySubtag() {
        assertTrue(isSameTranslationLanguage("ko", "ko"))
        assertTrue(isSameTranslationLanguage("KO-KR", "ko"))
        assertFalse(isSameTranslationLanguage("en-US", "ko"))
        assertFalse(isSameTranslationLanguage("ja", "ko"))
    }
}
