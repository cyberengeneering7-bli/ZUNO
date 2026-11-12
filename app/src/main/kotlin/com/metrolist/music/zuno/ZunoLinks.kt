package com.metrolist.music.zuno

object ZunoLinks {
    const val LANDING_PAGE = "https://pairingzuno.netlify.app"

    fun listenInvite(code: String): String {
        val link =
            if (LANDING_PAGE.startsWith("https://")) {
                "${LANDING_PAGE.trimEnd('/')}/?code=$code"
            } else {
                "zuno://listen?code=$code"
            }
        return "Join my ZUNO room!\nRoom code: $code\n$link"
    }
}
