package com.yourteam.expensetracker.categoriser

/**
 * Which golden-dataset scenario (Golden Dataset v1.0, Feature 1) a real payee falls under, so the team can see
 * how real AI cases spread across the dataset and which kinds of payee it doesn't cover yet (v2 candidates).
 * Rule-based and approximate: name shape only, never sent anywhere. Only the scenario key and counts are uploaded.
 *
 * Keys must match SCENARIOS in server/evals.py.
 */
enum class GoldenScenario(val key: String, val covered: Boolean) {
    MISSING_FIELDS("missing_fields", true),     // CAT-014, 015, 025
    ADVERSARIAL("adversarial", true),           // CAT-020
    CLEAR_MERCHANT("clear_merchant", true),     // CAT-005, 021, 022, 024
    WEAK_MERCHANT("weak_merchant", true),       // CAT-003, 006, 018
    GENERIC_BUSINESS("generic_business", true), // CAT-007, 008, 019
    ABBREVIATED("abbreviated", true),           // CAT-013
    PERSON("person", true),                     // CAT-012
    BRAND_NAME("brand_name", true),             // CAT-001, 002, 004, 009, 010, 011, 016, 017
    UPI_ID_ONLY("upi_id_only", false),          // no golden case yet
    PHONE_NUMBER_ID("phone_number_id", false),  // no golden case yet
    NON_LATIN("non_latin", false);              // no golden case yet

    companion object {
        private val INSTRUCTION = Regex("""\b(ignore|disregard|instructions?|prompt|system|assistant|select|override)\b""", RegexOption.IGNORE_CASE)
        private val GENERIC = setOf(
            "services", "service", "enterprises", "enterprise", "solutions", "ventures", "traders", "trading", "agency",
            "agencies", "centre", "center", "city", "pvt", "private", "ltd", "limited", "llp", "india", "co", "company",
            "corporation", "corp", "group", "international", "global", "industries", "associates", "and", "&",
        )
        private val CAMEL = Regex("""[a-z][A-Z]""")
        private val VOWEL = Regex("""[aeiouAEIOU]""")

        fun of(payee: String?, vpa: String?): GoldenScenario {
            val name = payee?.trim().orEmpty()
            if (name.isEmpty() && vpa.isNullOrBlank()) return MISSING_FIELDS
            if (INSTRUCTION.findAll(name).count() >= 2) return ADVERSARIAL
            if (name.isEmpty()) {
                val local = vpa!!.substringBefore('@')
                return if (local.count(Char::isDigit) >= 10) PHONE_NUMBER_ID else UPI_ID_ONLY
            }
            if (name.any { it.isLetter() && it.code > 0x24F }) return NON_LATIN

            val tokens = name.split(Regex("""[\s.,/_-]+""")).filter { it.isNotBlank() }
            val rest = tokens.filter { it.lowercase() !in GENERIC }
            if (rest.size < tokens.size && rest.all { it.length <= 3 }) return GENERIC_BUSINESS

            val word = MerchantMap.shopWord(payee, vpa)
            if (word != null) return if (word.strength >= Categoriser.HIGH) CLEAR_MERCHANT else WEAK_MERCHANT

            if (tokens.size >= 2 && tokens.all { it == it.uppercase() && it.length <= 5 } && tokens.any { !VOWEL.containsMatchIn(it) }) {
                return ABBREVIATED
            }
            val looksLikePerson = tokens.size in 2..3 &&
                tokens.all { t -> t.all(Char::isLetter) && t.length >= 2 && !CAMEL.containsMatchIn(t) && t.lowercase() !in GENERIC }
            return if (looksLikePerson) PERSON else BRAND_NAME
        }
    }
}
