package com.daniel.assistant.commands

import com.daniel.assistant.voice.ArabicNormalizer
import com.daniel.assistant.voice.Transliterator

/** A launchable installed app plus every name it can be called by. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val userAliases: List<String> = emptyList(),
) {
    val builtinAliases: List<String> by lazy { BuiltinAliases.forApp(packageName, label) }

    val spokenNames: List<String> by lazy {
        buildList {
            add(label)
            addAll(userAliases)
            addAll(builtinAliases)
            if (!ArabicNormalizer.isArabic(label)) {
                val t = Transliterator.toArabic(label)
                if (t.isNotBlank()) add(t)
            }
        }.distinct()
    }
}

/** Common Egyptian names for popular apps (matched by package or by label keyword). */
object BuiltinAliases {
    private val byPackage: Map<String, List<String>> = mapOf(
        "com.google.android.youtube" to listOf("يوتيوب", "اليوتيوب"),
        "com.whatsapp" to listOf("واتساب", "واتس", "الواتس", "واتس اب"),
        "com.whatsapp.w4b" to listOf("واتساب بيزنس", "واتس بيزنس"),
        "com.facebook.katana" to listOf("فيسبوك", "فيس", "الفيس", "فيس بوك"),
        "com.facebook.orca" to listOf("ماسنجر", "الماسنجر"),
        "com.instagram.android" to listOf("انستجرام", "انستا", "الانستا", "انستغرام"),
        "com.zhiliaoapp.musically" to listOf("تيك توك", "التيك توك", "تيكتوك"),
        "com.ss.android.ugc.trill" to listOf("تيك توك", "التيك توك"),
        "com.android.chrome" to listOf("كروم", "جوجل كروم", "المتصفح"),
        "com.google.android.gm" to listOf("جيميل", "الجيميل", "الايميل", "البريد"),
        "com.google.android.apps.maps" to listOf("خرائط", "الخرائط", "جوجل ماب", "ماب"),
        "com.anthropic.claude" to listOf("كلود", "كلاود", "claude"),
        "com.openai.chatgpt" to listOf("شات جي بي تي", "تشات جي بي تي", "شات"),
        "org.telegram.messenger" to listOf("تليجرام", "التليجرام", "تلجرام"),
        "com.snapchat.android" to listOf("سناب", "سناب شات", "السناب"),
        "com.spotify.music" to listOf("سبوتيفاي", "سبوتي فاي"),
        "com.netflix.mediaclient" to listOf("نتفليكس", "نيتفليكس"),
        "com.twitter.android" to listOf("تويتر", "اكس"),
        "com.google.android.googlequicksearchbox" to listOf("جوجل"),
        "com.android.vending" to listOf("بلاي ستور", "المتجر", "جوجل بلاي"),
        "com.google.android.apps.photos" to listOf("الصور", "صور جوجل", "جوجل فوتوز"),
        "com.google.android.calculator" to listOf("الحاسبة", "الآلة الحاسبة"),
        "com.google.android.deskclock" to listOf("الساعة", "المنبه"),
        "com.google.android.dialer" to listOf("التليفون", "الاتصال", "المكالمات"),
        "com.google.android.apps.messaging" to listOf("الرسائل", "المسدجات"),
        "com.google.android.calendar" to listOf("التقويم", "الكالندر"),
        "com.android.settings" to listOf("الإعدادات", "الاعدادات", "السيتنج"),
        "com.google.android.GoogleCamera" to listOf("الكاميرا", "كاميرا"),
        "com.talabat" to listOf("طلبات"),
        "com.ubercab" to listOf("اوبر"),
        "com.careem.acma" to listOf("كريم"),
        "com.linkedin.android" to listOf("لينكد ان", "لينكدان"),
        "com.microsoft.teams" to listOf("تيمز"),
        "us.zoom.videomeetings" to listOf("زووم"),
        "com.google.android.apps.docs" to listOf("درايف", "جوجل درايف"),
        "com.google.android.keep" to listOf("كيب", "الملاحظات"),
        "com.pinterest" to listOf("بنترست"),
        "com.discord" to listOf("ديسكورد"),
    )

    private val byKeyword: List<Pair<String, List<String>>> = listOf(
        "camera" to listOf("الكاميرا", "كاميرا"),
        "gallery" to listOf("المعرض", "الاستوديو", "الصور"),
        "photos" to listOf("الصور"),
        "settings" to listOf("الإعدادات", "الاعدادات"),
        "calculator" to listOf("الحاسبة"),
        "clock" to listOf("الساعة", "المنبه"),
        "phone" to listOf("التليفون", "الاتصال"),
        "contacts" to listOf("جهات الاتصال", "الأسماء"),
        "messages" to listOf("الرسائل"),
        "music" to listOf("الأغاني", "الموسيقى"),
        "files" to listOf("الملفات"),
        "calendar" to listOf("التقويم"),
        "notes" to listOf("الملاحظات"),
        "weather" to listOf("الطقس"),
        "recorder" to listOf("المسجل", "التسجيل"),
    )

    fun forApp(pkg: String, label: String): List<String> {
        val out = mutableListOf<String>()
        byPackage[pkg]?.let(out::addAll)
        val l = label.lowercase()
        for ((kw, names) in byKeyword) if (l.contains(kw)) out.addAll(names)
        if (pkg.contains("camera", ignoreCase = true)) out.add("الكاميرا")
        if (pkg.contains("gallery", ignoreCase = true)) out.addAll(listOf("المعرض", "الاستوديو"))
        return out.distinct()
    }
}
