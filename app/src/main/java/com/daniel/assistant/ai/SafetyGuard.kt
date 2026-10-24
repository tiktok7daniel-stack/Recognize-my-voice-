package com.daniel.assistant.ai

import com.daniel.assistant.voice.ArabicNormalizer

/** Safety rules for AI mode: blocked apps and actions that need confirmation. */
object SafetyGuard {

    private val BLOCKED_PACKAGES = setOf(
        "com.x8bit.bitwarden", "com.agilebits.onepassword", "com.lastpass.lpandroid",
        "com.dashlane", "keepass2android.keepass2android", "com.kunzisoft.keepass.free",
        "com.google.android.apps.walletnfcrel", "com.paypal.android.p2pmobile",
        "com.samsung.android.spay", "com.samsung.android.samsungpass",
        "com.azure.authenticator", "com.google.android.apps.authenticator2",
    )

    private val BLOCKED_SEGMENTS = listOf(
        "bank", "banque", "banca", "wallet", "vault", "password", "passwd", "keepass", "bitwarden",
        "onepassword", "lastpass", "instapay", "fawry", "vodafonecash", "paypal", "payoneer",
        "binance", "crypto", "authenticator", "cib", "nbe", "banquemisr",
    )

    private val SENSITIVE_WORDS = listOf(
        "send", "إرسال", "ارسال", "ارسل", "post", "نشر", "انشر", "share", "invia", "pubblica",
        "buy", "شراء", "اشتري", "purchase", "order", "اطلب", "checkout", "compra", "acquista",
        "pay", "ادفع", "دفع", "paga", "transfer", "تحويل", "حول",
        "delete", "حذف", "امسح", "مسح", "remove", "uninstall", "elimina", "cancella",
        "confirm", "تأكيد", "اكد", "conferma",
    )

    private val SETTINGS_PACKAGES = setOf("com.android.settings", "com.samsung.android.settings", "com.miui.securitycenter")

    fun isBlockedApp(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (pkg in BLOCKED_PACKAGES) return true
        val segments = pkg.lowercase().split('.')
        return segments.any { seg -> BLOCKED_SEGMENTS.any { w -> seg == w || seg.contains(w) && w.length >= 4 } }
    }

    fun isSettings(pkg: String?): Boolean = pkg != null && (pkg in SETTINGS_PACKAGES || pkg.endsWith(".settings"))

    fun looksSensitive(label: String?): Boolean {
        if (label.isNullOrBlank()) return false
        val n = ArabicNormalizer.normalize(label)
        return SENSITIVE_WORDS.any { w ->
            val nw = ArabicNormalizer.normalize(w)
            n == nw || n.split(' ').any { it == nw } || (nw.length >= 5 && n.contains(nw))
        }
    }
}
