package com.daniel.assistant.voice

/** Arabic Vosk models known to work. The URL can also be customised in settings. */
data class VoskModelInfo(val id: String, val title: String, val sizeMb: Int, val url: String, val note: String)

object VoskModels {
    private const val BASE = "https://alphacephei.com/vosk/models/"

    val MGB2 = VoskModelInfo(
        id = "vosk-model-ar-mgb2-0.4",
        title = "عربي MGB2 (موصى به)",
        sizeMb = 318,
        url = BASE + "vosk-model-ar-mgb2-0.4.zip",
        note = "مدرب على برامج عربية (فصحى ولهجات) — أفضل توازن دقة/حجم للموبايل",
    )
    val TUNISIAN_SMALL = VoskModelInfo(
        id = "vosk-model-small-ar-tn-0.1-linto",
        title = "عربي صغير (لهجة تونسية)",
        sizeMb = 160,
        url = BASE + "vosk-model-small-ar-tn-0.1-linto.zip",
        note = "الأخف على البطارية: بيدعم الجرامر فبيسمع كلمة التنبيه بس، ومع محرك Google للأوامر بيبقى أفضل اختيار للبطارية",
    )
    val LINTO_BIG = VoskModelInfo(
        id = "vosk-model-ar-0.22-linto-1.1.0",
        title = "عربي كبير (Linto)",
        sizeMb = 1300,
        url = BASE + "vosk-model-ar-0.22-linto-1.1.0.zip",
        note = "دقيق جدًا لكنه كبير وتقيل على الموبايل",
    )

    val ALL = listOf(MGB2, TUNISIAN_SMALL, LINTO_BIG)
    val DEFAULT = MGB2
}
