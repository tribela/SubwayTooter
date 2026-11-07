package jp.juggler.subwaytooter.util

import android.content.Context
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.auth.authRepo
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.log.LogCategory
import jp.juggler.util.network.toPostRequestBuilder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AccountCache {
    private val log = LogCategory("AccountCache")

    private val loadMutex = Mutex()

    suspend fun load(context: Context, src: SavedAccount?): TootAccount? {
        src ?: return null
        return withContext(AppDispatchers.IO) {
            loadMutex.withLock {
                try {
                    // キャッシュの可否はHTTPヘッダに任せる
                    val newAccount = context.runApiTask2(
                        src,
                        progressStyle = ApiTask.PROGRESS_NONE,
                    ) { client ->
                        val json = if (src.isMisskey) {
                            val result = client.request(
                                "/api/i",
                                src.putMisskeyApiToken().toPostRequestBuilder()
                            ) ?: return@runApiTask2 null
                            result.error?.let { error(it) }
                            result.jsonObject
                        } else {
                            // 承認待ち状態のチェック
                            authRepo.checkConfirmed(src, client)
                            val result = client.request(
                                "/api/v1/accounts/verify_credentials"
                            ) ?: return@runApiTask2 null
                            result.error?.let { error(it) }
                            result.jsonObject
                        }
                        TootParser(this, src).account(json) ?: error("parse error.")
                    }
                    return@withLock newAccount
                } catch (ex: Throwable) {
                    log.e(ex, "failed.")
                    return@withLock null
                }
            }
        }
    }
}
