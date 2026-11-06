package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.Host

// 開発用: ローカルのMastodonサーバにはHTTPで接続する
fun Host.isLocalDevHost(): Boolean {
    val s = ascii
    return s == "localhost" || s.startsWith("localhost:") ||
        s == "10.0.2.2" || s.startsWith("10.0.2.2:")
}

fun Host.schemeForLocalDev(): String = if (isLocalDevHost()) "http" else "https"
