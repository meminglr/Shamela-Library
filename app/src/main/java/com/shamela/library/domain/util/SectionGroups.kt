package com.shamela.library.domain.util

import androidx.annotation.StringRes
import com.shamela.library.R
import com.shamela.library.domain.model.Category
import java.text.Collator
import java.util.Locale

/**
 * Subject groups for Shamela's sections, so the library reads like a table of contents instead
 * of one long list: all fiqh (the four madhhabs, general fiqh, usul, fatwas, qada) together, all
 * hadith together, and so on. Within a group sections are in Arabic alphabetical order.
 */
enum class SectionGroup(@StringRes val label: Int) {
    Quran(R.string.group_quran),
    Hadith(R.string.group_hadith),
    Aqidah(R.string.group_aqidah),
    Fiqh(R.string.group_fiqh),
    History(R.string.group_history),
    Language(R.string.group_language),
    Raqaiq(R.string.group_raqaiq),
    Scholars(R.string.group_scholars),
    General(R.string.group_general),
    Other(R.string.group_other);

    companion object {
        private const val AUTO_NUMBERED = " - مرقم آليا"

        private val byName: Map<String, SectionGroup> = buildMap {
            fun put(group: SectionGroup, vararg names: String) = names.forEach { put(it, group) }
            put(Quran, "التفاسير", "علوم القرآن")
            put(
                Hadith, "متون الحديث", "شروح الحديث", "الأجزاء الحديثية", "علوم الحديث",
                "العلل والسؤالات", "كتب التخريج والزوائد", "مخطوطات حديثية",
            )
            put(Aqidah, "العقيدة")
            put(
                Fiqh, "فقه حنفي", "فقه مالكي", "فقه شافعي", "فقه حنبلي", "فقه عام",
                "أصول الفقه والقواعد الفقهية", "الفتاوى", "السياسة الشرعية والقضاء",
            )
            put(
                History, "التاريخ", "التراجم والطبقات", "السيرة والشمائل", "الأنساب",
                "البلدان والجغرافيا والرحلات",
            )
            put(Language, "النحو والصرف", "كتب اللغة", "الغريب والمعاجم ولغة الفقه", "الأدب والبلاغة")
            put(Raqaiq, "الرقاق والآداب والأذكار", "الدعوة وأحوال المسلمين")
            put(Scholars, "كتب ابن تيمية", "كتب ابن القيم", "كتب الألباني", "كتب ابن أبي الدنيا")
            put(
                General, "كتب إسلامية عامة", "بحوث ومسائل", "علوم أخرى", "الجوامع والمجلات ونحوها",
                "فهارس الكتب والأدلة", "محاضرات مفرغة",
            )
            // The 40 sections of shamela.ws (the Şamile source); shared names are listed above.
            put(Quran, "التفسير", "علوم القرآن وأصول التفسير", "التجويد والقراءات")
            put(Hadith, "كتب السنة", "التخريج والأطراف", "العلل والسؤلات الحديثية")
            put(Aqidah, "الفرق والردود")
            put(
                Fiqh, "أصول الفقه", "علوم الفقه والقواعد الفقهية", "الفقه الحنفي", "الفقه المالكي",
                "الفقه الشافعي", "الفقه الحنبلي", "الفقه العام", "مسائل فقهية", "الفرائض والوصايا",
            )
            put(History, "السيرة النبوية", "البلدان والرحلات")
            put(Language, "الغريب والمعاجم", "الأدب", "العروض والقوافي", "الشعر ودواوينه", "البلاغة")
            put(Raqaiq, "الرقائق والآداب والأذكار")
            put(General, "المنطق", "الجوامع", "الطب", "كتب عامة")
        }

        /** The group of a section; auto-numbered editions go with their original. */
        fun of(sectionName: String): SectionGroup = byName[SectionTaxonomy.canonicalOf(sectionName)] ?: Other

        private val collator: Collator = Collator.getInstance(Locale("ar")).apply { strength = Collator.PRIMARY }

        /** Alphabetical key: without the article "ال", as Arabic indexes are ordered. */
        private fun sortKey(name: String): String {
            val base = name.removeSuffix(AUTO_NUMBERED)
            val stripped = if (base.startsWith("ال") && base.length > 3) base.substring(2) else base
            // The auto-numbered edition right after its original.
            return if (name.endsWith(AUTO_NUMBERED)) "$stripped￿" else stripped
        }

        /** Non-empty groups in reading order, each with its sections in alphabetical order. */
        fun group(categories: Collection<Category>): List<Pair<SectionGroup, List<Category>>> =
            categories.groupBy { of(it.name) }
                .toSortedMap(compareBy { it.ordinal })
                .map { (group, items) ->
                    group to items.sortedWith { a, b ->
                        val primary = collator.compare(sortKey(a.name), sortKey(b.name))
                        if (primary != 0) primary else sortKey(a.name).compareTo(sortKey(b.name))
                    }
                }
    }
}
