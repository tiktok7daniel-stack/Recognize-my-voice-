package com.daniel.assistant.commands

/**
 * Built-in system commands with their spoken Egyptian-Arabic phrases.
 * Phrases are matched after [com.daniel.assistant.voice.ArabicNormalizer] normalization.
 */
enum class SysAction(
    val id: String,
    val title: String,
    val phrases: List<String>,
    val needsAccessibility: Boolean = true,
    val takesArg: Boolean = false,
    val minSdk: Int = 26,
) {
    BACK("back", "رجوع", listOf("رجوع", "ارجع", "ارجع ورا", "باك")),
    HOME("home", "الرئيسية", listOf("الرئيسية", "الشاشة الرئيسية", "هوم", "روح الرئيسية")),
    RECENTS("recents", "التطبيقات الأخيرة", listOf("التطبيقات الأخيرة", "آخر التطبيقات", "الأخيرة")),
    NOTIFICATIONS("notifications", "الإشعارات", listOf("الإشعارات", "افتح الإشعارات", "نزل الإشعارات")),
    QUICK_SETTINGS("quick_settings", "الإعدادات السريعة", listOf("الإعدادات السريعة", "افتح الإعدادات السريعة")),
    SCREENSHOT("screenshot", "سكرين شوت", listOf("سكرين شوت", "سكرينشوت", "اسكرين", "صور الشاشة"), minSdk = 28),
    LOCK("lock", "اقفل الشاشة", listOf("اقفل الشاشة", "قفل الشاشة", "اقفل التليفون"), minSdk = 28),
    SCROLL_DOWN("scroll_down", "انزل", listOf("انزل", "نزل", "انزل لتحت", "لتحت")),
    SCROLL_UP("scroll_up", "اطلع", listOf("اطلع", "طلع", "اطلع لفوق", "لفوق")),
    VOLUME_UP("volume_up", "علّي الصوت", listOf("علّي الصوت", "علي الصوت", "ارفع الصوت"), needsAccessibility = false),
    VOLUME_DOWN("volume_down", "وطّي الصوت", listOf("وطّي الصوت", "وطي الصوت", "اخفض الصوت", "قلل الصوت"), needsAccessibility = false),
    TORCH_ON("torch_on", "شغّل الكشاف", listOf("شغّل الكشاف", "شغل الكشاف", "ولع الكشاف", "افتح الكشاف", "شغل الفلاش"), needsAccessibility = false),
    TORCH_OFF("torch_off", "اطفي الكشاف", listOf("اطفي الكشاف", "طفي الكشاف", "اقفل الكشاف", "اطفي الفلاش"), needsAccessibility = false),
    WIFI("wifi", "واي فاي", listOf("واي فاي", "الواي فاي", "وايفاي", "النت"), needsAccessibility = false),
    BLUETOOTH("bluetooth", "بلوتوث", listOf("بلوتوث", "البلوتوث"), needsAccessibility = false),
    PLAY_PAUSE("play_pause", "شغّل / وقّف الأغنية", listOf("شغل الاغنية", "وقف الاغنية", "كمل الاغنية", "بلاي", "بوز"), needsAccessibility = false),
    NEXT_TRACK("next_track", "الأغنية اللي بعدها", listOf("اللي بعدها", "الاغنية اللي بعدها", "التالي"), needsAccessibility = false),
    PREV_TRACK("prev_track", "الأغنية اللي قبلها", listOf("اللي قبلها", "الاغنية اللي قبلها", "السابق"), needsAccessibility = false),
    POWER_MENU("power_menu", "قايمة الباور", listOf("قايمة الباور", "زرار الباور", "اطفي التليفون")),
    SPLIT_SCREEN("split_screen", "تقسيم الشاشة", listOf("قسم الشاشة", "تقسيم الشاشة")),
    SWIPE_LEFT("swipe_left", "اسحب شمال", listOf("اسحب شمال", "شمال")),
    SWIPE_RIGHT("swipe_right", "اسحب يمين", listOf("اسحب يمين", "يمين")),
    CLICK("click", "اضغط على", listOf("اضغط على", "دوس على", "اضغط"), takesArg = true),
    TYPE("type", "اكتب", listOf("اكتب", "اكتبلي"), takesArg = true);

    companion object {
        fun byId(id: String): SysAction? = entries.firstOrNull { it.id == id }
        val CANCEL_WORDS = listOf("وقف", "بطل", "الغي", "كفاية")
        val YES_WORDS = listOf("ايوه", "اه", "نعم", "تمام", "اكد", "موافق", "يلا")
        val NO_WORDS = listOf("لا", "لأ", "مش عايز", "الغي")
        val OPEN_PREFIXES = listOf("افتح لي", "افتحلي", "افتح", "شغل لي", "شغللي", "شغل", "روح على")
    }
}
