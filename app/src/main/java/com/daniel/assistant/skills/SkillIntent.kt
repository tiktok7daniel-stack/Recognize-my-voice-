package com.daniel.assistant.skills

import com.daniel.assistant.voice.ArabicNormalizer

/** A natural-language request understood without AI (or extracted by the AI). */
sealed interface SkillIntent {
    data class Message(val contact: String, val text: String, val app: ChatApp?) : SkillIntent
    data class Call(val contact: String) : SkillIntent
    data class Alarm(val hour: Int, val minute: Int, val label: String = "") : SkillIntent
    data class Timer(val seconds: Int) : SkillIntent
    data class Search(val query: String, val where: SearchTarget) : SkillIntent
    data class Navigate(val place: String) : SkillIntent
    data class Volume(val percent: Int) : SkillIntent
    data class Brightness(val percent: Int) : SkillIntent
    data class Toggle(val what: ToggleTarget, val on: Boolean) : SkillIntent
    /** AI-only: open an app by name. */
    data class OpenApp(val name: String) : SkillIntent
    /** AI-only: a question / chat — answer is spoken back. */
    data class Chat(val answer: String) : SkillIntent
    /** AI-only: a multi-step screen task for the agent loop. */
    data class Task(val goal: String) : SkillIntent
}

/** Lets the (local or online) AI turn any free sentence into one [SkillIntent]. */
object AiIntent {
    val PROMPT = """
You are the brain of an Android voice assistant. The user speaks Egyptian Arabic.
Turn the user's request into exactly ONE JSON object and nothing else:
{"intent":"message","app":"whatsapp|messenger|telegram|instagram|sms|","contact":"name","text":"message to send, in the user's words"}
{"intent":"call","contact":"name"}
{"intent":"alarm","hour":7,"minute":30}
{"intent":"timer","seconds":300}
{"intent":"search","query":"...","where":"google|youtube|maps"}
{"intent":"navigate","place":"..."}
{"intent":"volume","percent":50}
{"intent":"brightness","percent":50}
{"intent":"toggle","what":"wifi|bluetooth","on":true}
{"intent":"open_app","name":"app name"}
{"intent":"task","goal":"short description of a multi-step task on the phone screen"}
{"intent":"chat","answer":"short helpful answer in Egyptian Arabic"}
Rules: use "chat" for questions and conversation. Use "task" only when the request needs tapping
through an app and no other intent fits. Keep the message text exactly as the user said it.
""".trimIndent()

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(reply: String): SkillIntent? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { json.parseToJsonElement(reply.substring(start, end + 1)) as? kotlinx.serialization.json.JsonObject }
            .getOrNull() ?: return null
        fun str(k: String) = (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it != "null" }
        fun int(k: String) = str(k)?.toDoubleOrNull()?.toInt()
        return when (str("intent")?.lowercase()) {
            "message" -> {
                val c = str("contact"); val t = str("text")
                if (c.isNullOrBlank() || t.isNullOrBlank()) null else SkillIntent.Message(c, t, ChatApp.byId(str("app")))
            }
            "call" -> str("contact")?.takeIf { it.isNotBlank() }?.let { SkillIntent.Call(it) }
            "alarm" -> int("hour")?.let { h -> SkillIntent.Alarm(h.coerceIn(0, 23), (int("minute") ?: 0).coerceIn(0, 59)) }
            "timer" -> int("seconds")?.takeIf { it > 0 }?.let { SkillIntent.Timer(it) }
            "search" -> str("query")?.let { q ->
                SkillIntent.Search(q, when (str("where")?.lowercase()) {
                    "youtube" -> SearchTarget.YOUTUBE; "maps" -> SearchTarget.MAPS; else -> SearchTarget.GOOGLE
                })
            }
            "navigate" -> str("place")?.let { SkillIntent.Navigate(it) }
            "volume" -> int("percent")?.let { SkillIntent.Volume(it.coerceIn(0, 100)) }
            "brightness" -> int("percent")?.let { SkillIntent.Brightness(it.coerceIn(0, 100)) }
            "toggle" -> SkillIntent.Toggle(
                if (str("what")?.contains("blue") == true) ToggleTarget.BLUETOOTH else ToggleTarget.WIFI,
                str("on")?.toBooleanStrictOrNull() ?: true,
            )
            "open_app" -> str("name")?.let { SkillIntent.OpenApp(it) }
            "task" -> str("goal")?.let { SkillIntent.Task(it) }
            "chat" -> str("answer")?.let { SkillIntent.Chat(it) }
            else -> null
        }
    }
}

enum class ChatApp(val id: String, val title: String, val packages: List<String>) {
    WHATSAPP("whatsapp", "واتساب", listOf("com.whatsapp", "com.whatsapp.w4b")),
    MESSENGER("messenger", "ماسنجر", listOf("com.facebook.orca", "com.facebook.mlite")),
    TELEGRAM("telegram", "تليجرام", listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")),
    INSTAGRAM("instagram", "انستجرام", listOf("com.instagram.android")),
    SMS("sms", "رسالة SMS", emptyList());

    companion object {
        fun byId(id: String?): ChatApp? = entries.firstOrNull { it.id == id?.lowercase() }
    }
}

enum class SearchTarget { GOOGLE, YOUTUBE, MAPS }
enum class ToggleTarget(val title: String) { WIFI("الواي فاي"), BLUETOOTH("البلوتوث") }

/**
 * Offline parser for common Egyptian requests:
 * "كلم محمد على الماسنجر وقوله ...", "ابعت لأحمد على الواتس انا جاي", "اتصل بماما",
 * "صحيني الساعة ٧ ونص", "تايمر ٥ دقايق", "دور على أغاني عمرو دياب في يوتيوب",
 * "وديني مدينة نصر", "خلي الصوت ٥٠", "شغل الواي فاي".
 * Message text keeps the user's original spelling.
 */
object IntentParser {

    private const val A = "[اأإآ]"
    private const val Y = "[يى]"
    private const val H = "[هة]"

    // verbs that start a message / call
    private val msgVerb = "(?:كلم|كلملي|ابعت|ابعتلي|${A}رسل|راسل|قول|قوللي|قل|بلغ)"
    private val tellWord = "(?:و\\s*)?(?:قوله|قولها|قولهم|قول\\s+له|قول\\s+لها|قل\\s+له|قله|${A}كتبله|${A}كتب\\s+له|${A}كتبلها|${A}بعتله|${A}بعت\\s+له|${A}بعتلها|بلغه|وقوله|بيقول|بتقول|${A}ن(?:ه|ها|ي)?(?=\\s))(?=\\s|$)"
    private val onWord = "(?:عل$Y|ع|عن\\s+طريق|ف$Y|ف)"
    private val appWord = "(?:ال)?(?:واتساب|واتس\\s*${A}ب|واتس|ماسنجر|مسنجر|مسينجر|فيسبوك|فيس|تليجرام|تلجرام|تيليجرام|${A}نستجرام|${A}نستا|رسايل|رساي?ل|رسال$H|sms|${A}س\\s*${A}م\\s*${A}س)"

    private val numberWords = mapOf(
        "واحد" to 1, "واحده" to 1, "اتنين" to 2, "اثنين" to 2, "تلاته" to 3, "ثلاثه" to 3, "اربعه" to 4, "اربع" to 4,
        "خمسه" to 5, "خمس" to 5, "سته" to 6, "ست" to 6, "سبعه" to 7, "سبع" to 7, "تمانيه" to 8, "ثمانيه" to 8, "تمن" to 8,
        "تسعه" to 9, "تسع" to 9, "عشره" to 10, "عشر" to 10, "حداشر" to 11, "اتناشر" to 12, "اطناشر" to 12,
        "ربع" to 15, "نص" to 30, "خمستاشر" to 15, "عشرين" to 20, "تلاتين" to 30, "اربعين" to 40, "خمسين" to 50,
        "ستين" to 60, "سبعين" to 70, "تمانين" to 80, "تسعين" to 90, "مية" to 100, "ميه" to 100,
    )

    private fun clean(s: String): String {
        val digits = s.map { c ->
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            }
        }.joinToString("")
        return ArabicNormalizer.stripDiacritics(digits).replace('ـ', ' ').replace(Regex("\\s+"), " ").trim()
    }

    private fun num(token: String?): Int? {
        if (token.isNullOrBlank()) return null
        val t = ArabicNormalizer.normalize(token)
        return t.toIntOrNull() ?: numberWords[t]
    }

    fun appFrom(word: String?): ChatApp? {
        val w = ArabicNormalizer.normalize(word ?: return null).removePrefix("ال")
        return when {
            w.startsWith("واتس") -> ChatApp.WHATSAPP
            w.contains("سنجر") || w.startsWith("فيس") -> ChatApp.MESSENGER
            w.contains("ليجرام") || w.contains("لجرام") -> ChatApp.TELEGRAM
            w.startsWith("انست") -> ChatApp.INSTAGRAM
            w.startsWith("رسا") || w == "sms" || w.replace(" ", "") == "اسماس" -> ChatApp.SMS
            else -> null
        }
    }

    private fun stripWake(text: String, wakeWord: String): String {
        val w = clean(wakeWord)
        return if (w.isNotBlank() && ArabicNormalizer.normalize(text).startsWith(ArabicNormalizer.normalize(w))) {
            text.split(' ').drop(w.split(' ').size).joinToString(" ")
        } else text
    }

    private fun cleanName(n: String): String =
        n.trim().removePrefix("ل ").let { if (it.startsWith("ل") && it.length > 3 && !it.startsWith("لي")) it.removePrefix("ل") else it }
            .removePrefix("ب").let { if (it.length < 2) n.trim() else it }.trim()

    fun parse(raw: String, wakeWord: String = ""): SkillIntent? {
        val text = stripWake(clean(raw), wakeWord)
        if (text.isBlank()) return null
        val ic = setOf(RegexOption.IGNORE_CASE)

        // 1) message with app: "كلم محمد على الماسنجر وقوله ..."
        Regex("^$msgVerb\\s+(.+?)\\s+$onWord\\s+($appWord)\\s+$tellWord\\s*(.+)$", ic).find(text)?.let { m ->
            return SkillIntent.Message(cleanName(m.groupValues[1]), m.groupValues[3].trim(), appFrom(m.groupValues[2]))
        }
        // 1b) "ابعت لمحمد على الواتس انا جاي" (no tell-word)
        Regex("^(?:${A}بعت|${A}بعتلي|${A}رسل|راسل)\\s+(.+?)\\s+$onWord\\s+($appWord)\\s+(?:رسال$H\\s+)?(.+)$", ic).find(text)?.let { m ->
            return SkillIntent.Message(cleanName(m.groupValues[1]), m.groupValues[3].trim(), appFrom(m.groupValues[2]))
        }
        // 2) message without app: "ابعت لمحمد وقوله ..." / "قول لمحمد إن ..."
        Regex("^$msgVerb\\s+(.+?)\\s+$tellWord\\s*(.+)$", ic).find(text)?.let { m ->
            return SkillIntent.Message(cleanName(m.groupValues[1]), m.groupValues[2].trim(), null)
        }
        // 3) call: "اتصل بمحمد" / "كلم ماما" / "رن على احمد"
        Regex("^(?:${A}تصل|${A}تصللي|كلم|كلملي|رن|رنلي)\\s+(?:ب|عل$Y|ع)?\\s*(.+)$", ic).find(text)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotBlank() && name.split(' ').size <= 4) return SkillIntent.Call(cleanName(name))
        }
        // 4) alarm: "صحيني الساعة ٧ ونص الصبح"
        Regex("^(?:صحيني|صحيني|نبهني|${A}عمل\\s+منب$H|ظبط\\s+منب$H|منب$H)\\s+(?:الساع$H|عل$Y|ع)?\\s*([\\p{L}\\d]+)(?:[:.](\\d{1,2}))?(?:\\s+(?:و|إلا|الا)\\s*([\\p{L}\\d]+))?(?:\\s+(?:دقيق$H|دقايق))?\\s*(.*)$", ic).find(text)?.let { m ->
            var h = num(m.groupValues[1]) ?: return@let
            var min = m.groupValues[2].toIntOrNull() ?: 0
            val extra = m.groupValues[3]
            if (extra.isNotBlank()) {
                val e = num(extra) ?: 0
                val minus = Regex("(?:إلا|الا)").containsMatchIn(m.value.substringAfter(m.groupValues[1]).take(6))
                if (minus) { h = (h + 23) % 24; min = 60 - e } else min = e
            }
            val rest = ArabicNormalizer.normalize(m.groupValues[4])
            if ((rest.contains("ليل") || rest.contains("مسا") || rest.contains("عصر") || rest.contains("بعد الضهر")) && h in 1..11) h += 12
            if (h in 0..23 && min in 0..59) return SkillIntent.Alarm(h, min)
        }
        // 5) timer: "تايمر ٥ دقايق" / "نبهني بعد ١٠ دقايق"
        Regex("^(?:تايمر|عداد|نبهني\\s+بعد|${A}عمل\\s+تايمر)\\s+([\\p{L}\\d]+)\\s+(دقيق$H|دقايق|دقيقتين|ثاني$H|ثواني|ساع$H|ساعات|ساعتين)", ic).find(text)?.let { m ->
            val n = num(m.groupValues[1]) ?: return@let
            val unit = ArabicNormalizer.normalize(m.groupValues[2])
            val sec = when {
                unit.startsWith("ساع") -> n * 3600
                unit.startsWith("ثان") || unit.startsWith("ثوان") -> n
                else -> n * 60
            }
            return SkillIntent.Timer(sec)
        }
        // 6) navigation: "وديني مدينة نصر"
        Regex("^(?:وديني|خدني|روحني|${A}زاي\\s+${A}روح|الطريق\\s+ل)\\s+(?:عل$Y|ل|ع)?\\s*(.+)$", ic).find(text)?.let { m ->
            return SkillIntent.Navigate(m.groupValues[1].trim())
        }
        // 7) search: "دور على X في يوتيوب" / "ابحث عن X"
        Regex("^(?:دور|${A}بحث|سيرش|شوفلي|هاتلي)\\s+(?:عل$Y|عن|ع)\\s+(.+?)(?:\\s+(?:ف$Y|عل$Y|ع)\\s+(يوتيوب|اليوتيوب|جوجل|الخرايط|خرايط|الخرائط|خرائط|جوجل ماب))?$", ic).find(text)?.let { m ->
            val where = ArabicNormalizer.normalize(m.groupValues[2])
            val target = when {
                where.contains("يوتيوب") -> SearchTarget.YOUTUBE
                where.contains("خرا") || where.contains("ماب") -> SearchTarget.MAPS
                else -> SearchTarget.GOOGLE
            }
            return SkillIntent.Search(m.groupValues[1].trim(), target)
        }
        // 8) volume / brightness level: "خلي الصوت ٥٠"
        Regex("^(?:خلي|خل$Y|ظبط|${A}عمل)\\s+(الصوت|الاضاء$H|الإضاء$H|السطوع|النور)\\s+(?:عل$Y|ع)?\\s*([\\p{L}\\d]+)", ic).find(text)?.let { m ->
            val p = num(m.groupValues[2])?.coerceIn(0, 100) ?: return@let
            return if (m.groupValues[1].contains("صوت")) SkillIntent.Volume(p) else SkillIntent.Brightness(p)
        }
        // 9) real toggles: "شغل الواي فاي" / "اقفل البلوتوث"
        Regex("^(شغل|ولع|${A}فتح|${A}قفل|${A}طفي|طفي)\\s+(?:ال)?(واي\\s*فاي|wifi|النت|بلوتوث|البلوتوث)$", ic).find(text)?.let { m ->
            val on = ArabicNormalizer.normalize(m.groupValues[1]).let { it.startsWith("شغل") || it.startsWith("ولع") || it.startsWith("افتح") }
            val what = if (m.groupValues[2].contains("بلوتوث")) ToggleTarget.BLUETOOTH else ToggleTarget.WIFI
            return SkillIntent.Toggle(what, on)
        }
        return null
    }
}
