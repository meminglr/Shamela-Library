package com.shamela.library.domain.util

/**
 * The one list of sections the app shows. Every catalog brings its own section names: Kütüphane 1
 * has each section twice (the edition and its " - مرقم آليا" auto-numbered edition), shamela.ws has
 * 40 sections of its own ("الفقه الشافعي" next to "فقه شافعي"), and downloaded folders may carry any
 * of them. All of those names are mapped here to one canonical section, so e.g. every Shafi'i fiqh
 * book is in "فقه شافعي".
 *
 * Only what is *shown* changes: a book's stored category and its folder stay what the source calls
 * it (the download link and the book id both derive from it), see [sourcesOf].
 */
object SectionTaxonomy {
    const val AUTO_NUMBERED = " - مرقم آليا"

    /** Source section name -> canonical name, for names that differ after [stripVariant]. */
    private val aliases: List<Pair<String, String>> = listOf(
        // shamela.ws's 40 sections -> the canonical (Kütüphane 1) sections.
        "الفرق والردود" to "العقيدة",
        "التفسير" to "التفاسير",
        "علوم القرآن وأصول التفسير" to "علوم القرآن",
        "التجويد والقراءات" to "علوم القرآن",
        "كتب السنة" to "متون الحديث",
        "التخريج والأطراف" to "كتب التخريج والزوائد",
        "العلل والسؤلات الحديثية" to "العلل والسؤالات",
        "أصول الفقه" to "أصول الفقه والقواعد الفقهية",
        "علوم الفقه والقواعد الفقهية" to "أصول الفقه والقواعد الفقهية",
        "المنطق" to "علوم أخرى",
        "الفقه الحنفي" to "فقه حنفي",
        "الفقه المالكي" to "فقه مالكي",
        "الفقه الشافعي" to "فقه شافعي",
        "الفقه الحنبلي" to "فقه حنبلي",
        "الفقه العام" to "فقه عام",
        "مسائل فقهية" to "فقه عام",
        "الفرائض والوصايا" to "فقه عام",
        "الرقائق والآداب والأذكار" to "الرقاق والآداب والأذكار",
        "السيرة النبوية" to "السيرة والشمائل",
        "البلدان والرحلات" to "البلدان والجغرافيا والرحلات",
        "الغريب والمعاجم" to "الغريب والمعاجم ولغة الفقه",
        "الأدب" to "الأدب والبلاغة",
        "العروض والقوافي" to "الأدب والبلاغة",
        "الشعر ودواوينه" to "الأدب والبلاغة",
        "البلاغة" to "الأدب والبلاغة",
        "الجوامع" to "الجوامع والمجلات ونحوها",
        "الطب" to "علوم أخرى",
        "كتب عامة" to "كتب إسلامية عامة",
        // Spelling variants seen in folders.
        "فقه الشافعية" to "فقه شافعي",
        "فقه الحنابلة" to "فقه حنبلي",
        "فقه الأحناف" to "فقه حنفي",
        "فقه المالكية" to "فقه مالكي",
    )

    /** Section names that are canonical as they are (listed so [sourcesOf] and the docs know them). */
    private val AUTO_NUMBERED_REGEX = Regex("""\s*[-–—]?\s*\(?\s*مرقم\s*آلي[اًا]\s*\)?\s*$""")

    private val byKey: Map<String, String> = aliases.associate { (source, canonical) -> key(source) to canonical }

    /** Name without the auto-numbered suffix and with whitespace tidied. */
    fun stripVariant(name: String): String =
        name.replace(AUTO_NUMBERED_REGEX, "").trim().replace(Regex("\\s+"), " ")

    /** Lookup key: no diacritics/tatweel, alef and ya forms unified. */
    private fun key(name: String): String = stripVariant(name)
        .replace(Regex("[\\u064B-\\u065F\\u0640]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        .replace('ى', 'ي')

    /** The canonical section a source section (any catalog, any folder) is shown under. */
    fun canonicalOf(name: String): String {
        val stripped = stripVariant(name)
        return byKey[key(stripped)] ?: stripped
    }

    /** True when [name] is an auto-numbered edition's section name. */
    fun isAutoNumbered(name: String): Boolean = AUTO_NUMBERED_REGEX.containsMatchIn(name)

    /**
     * Every known source name (folder) shown as [canonical]: itself, its auto-numbered edition, and
     * the aliases merged into it with theirs. Folders not listed here but mapping to it by
     * [canonicalOf] are matched by the callers that walk the disk.
     */
    fun sourcesOf(canonical: String): List<String> {
        val names = LinkedHashSet<String>()
        names += canonical
        aliases.filter { it.second == canonical }.forEach { names += it.first }
        return names.flatMap { listOf(it, it + AUTO_NUMBERED) }.distinct()
    }

    /** The alias table, for tests and documentation. */
    fun aliasTable(): List<Pair<String, String>> = aliases
}
