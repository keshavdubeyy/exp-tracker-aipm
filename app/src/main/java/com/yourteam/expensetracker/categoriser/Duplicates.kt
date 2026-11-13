// Finds the same person, or the same category, under different names. Suggestions only:
// nothing is merged until the user says so. Words only, no money (AGENTS.md rule 1).
package com.yourteam.expensetracker.categoriser

/** A payee or sender name as it appears in payments, with its UPI IDs and how often it's used. */
data class NameUse(val name: String, val vpas: Set<String> = emptySet(), val uses: Int = 1)

enum class DuplicateKind { PERSON, CATEGORY }

/**
 * Names that look like one person (or one category). [suggested] is the name to keep;
 * [reason] says why we think so, in plain words.
 */
data class DuplicateGroup(
    val kind: DuplicateKind,
    val members: List<String>,
    val suggested: String,
    val reason: String,
) {
    /** Stable id: the same names in any order. Used to remember "Not the same". */
    val id: String get() = kind.name + ":" + members.map { it.trim().lowercase() }.sorted().joinToString("|")
}

object Duplicates {

    private val honorifics = setOf("mr", "mrs", "ms", "miss", "shri", "sri", "smt", "dr", "kumari")

    /** "Mr. ASHATA  Sharma." → ["ashata", "sharma"]. */
    fun tokens(name: String): List<String> =
        name.lowercase().replace(Regex("""[^a-z0-9 ]"""), " ").split(' ').filter { it.isNotEmpty() && it !in honorifics }

    /**
     * People:
     *  - the same UPI ID under different names;
     *  - one name is the other with initials ("Ashata S" / "Ashata Sharma") or in another order;
     *  - a short name that fits exactly one longer name ("Ashata" / "Ashata Sharma"). If it fits
     *    two ("Rahul" / "Rahul Gupta" / "Rahul Verma"), we don't guess.
     */
    fun people(names: List<NameUse>, rejected: Set<String> = emptySet()): List<DuplicateGroup> {
        val list = names.filter { tokens(it.name).isNotEmpty() && '@' !in it.name }.distinctBy { it.name }
        val parent = IntArray(list.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) x = parent[x]; return x }
        val why = mutableMapOf<Int, String>()
        fun join(a: Int, b: Int, reason: String) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) { parent[rb] = ra; why.putIfAbsent(ra, reason) }
        }
        val toks = list.map { tokens(it.name) }

        for (i in list.indices) for (j in i + 1 until list.size) {
            when {
                list[i].vpas.any { it in list[j].vpas } -> join(i, j, "Same UPI ID")
                toks[i].sorted() == toks[j].sorted() -> join(i, j, "Same name, written differently")
                initialsMatch(toks[i], toks[j]) -> join(i, j, "Same name with initials")
            }
        }
        // A one-word name that fits exactly one longer name.
        for (i in list.indices) {
            val t = toks[i]
            if (t.size != 1 || t[0].length < 3) continue
            // Counted per person found so far: "Ashata S" and "Ashata Sharma" are already one.
            val fits = list.indices.filter { j -> j != i && toks[j].size > 1 && t[0] in toks[j] }
            if (fits.isNotEmpty() && fits.map(::find).distinct().size == 1) join(i, fits[0], "Short form of the same name")
        }

        return list.indices.groupBy(::find).filterValues { it.size > 1 }.map { (root, idx) ->
            val members = idx.map { list[it] }
            DuplicateGroup(
                DuplicateKind.PERSON,
                members.sortedByDescending { it.uses }.map { it.name },
                suggested = bestName(members),
                reason = why[root] ?: "Looks like the same person",
            )
        }.filter { it.id !in rejected }
    }

    /** Same number of words; each pair equal, or one is an initial of the other ("S" / "Sharma"). */
    private fun initialsMatch(a: List<String>, b: List<String>): Boolean {
        if (a.size != b.size || a.size < 2 || a == b) return false
        var initials = 0
        for (k in a.indices) {
            val x = a[k]; val y = b[k]
            when {
                x == y -> {}
                x.length == 1 && y.startsWith(x) -> initials++
                y.length == 1 && x.startsWith(y) -> initials++
                else -> return false
            }
        }
        return initials > 0 && a.zip(b).any { (x, y) -> x == y && x.length >= 3 }
    }

    /** The fullest real name (most words, no initials), then the most used. */
    private fun bestName(members: List<NameUse>): String =
        members.sortedWith(
            compareByDescending<NameUse> { m -> tokens(m.name).count { it.length > 1 } }
                .thenBy { m -> tokens(m.name).count { it.length == 1 } }
                .thenByDescending { it.uses },
        ).first().name.let(::titleCase)

    private fun titleCase(s: String): String =
        if (s == s.uppercase()) s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) } else s

    /**
     * Categories: "P2P" / "Person-to-person" / "Person to Person", "Snack" / "Snacks",
     * "Bills and utilities" / "Bills & Utilities". Two built-in categories are never merged.
     * [uses] = how many payments use each name; the built-in or most used one is kept.
     */
    fun categories(names: List<String>, uses: Map<String, Int> = emptyMap(), builtIn: Set<String> = emptySet(), rejected: Set<String> = emptySet()): List<DuplicateGroup> {
        val list = names.distinct()
        val forms = list.map(::forms)
        val parent = IntArray(list.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) x = parent[x]; return x }
        for (i in list.indices) for (j in i + 1 until list.size) {
            if (list[i] in builtIn && list[j] in builtIn) continue
            if (forms[i].any { it in forms[j] }) { val a = find(i); val b = find(j); if (a != b) parent[b] = a }
        }
        return list.indices.groupBy(::find).filterValues { it.size > 1 }.mapNotNull { (_, idx) ->
            val members = idx.map { list[it] }
            if (members.count { it in builtIn } > 1) return@mapNotNull null
            val keep = members.firstOrNull { it in builtIn } ?: members.maxWith(compareBy<String> { uses[it] ?: 0 }.thenBy { it.length })
            DuplicateGroup(DuplicateKind.CATEGORY, members, keep, "Same meaning, different spelling")
        }.filter { it.id !in rejected }
    }

    /** Ways a category name can be written: squashed, singular, and as a short form ("p2p"). */
    private fun forms(name: String): Set<String> {
        val words = name.lowercase().replace("&", " and ").split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
        val squashed = words.joinToString("")
        val singular = words.joinToString("") { it.removeSuffix("s") }
        val noAnd = words.filter { it != "and" }.joinToString("")
        val short = words.joinToString("") { w -> when (w) { "to" -> "2"; "for" -> "4"; "and" -> "n"; else -> w.take(1) } }
        return setOfNotNull(squashed, singular, noAnd, short.takeIf { words.size >= 2 })
    }

    /** A group from the AI: same shape as the rule groups, keeping the fullest name. */
    fun personGroup(members: List<NameUse>, reason: String): DuplicateGroup =
        DuplicateGroup(DuplicateKind.PERSON, members.sortedByDescending { it.uses }.map { it.name }, bestName(members), reason)

    fun categoryGroup(members: List<String>, uses: Map<String, Int>, builtIn: Set<String>, reason: String): DuplicateGroup? {
        if (members.count { it in builtIn } > 1) return null
        val keep = members.firstOrNull { it in builtIn } ?: members.maxWith(compareBy<String> { uses[it] ?: 0 }.thenBy { it.length })
        return DuplicateGroup(DuplicateKind.CATEGORY, members, keep, reason)
    }

    /** Joins groups that share a name (rule groups + AI groups), keeping the first group's suggestion. */
    fun mergeOverlapping(groups: List<DuplicateGroup>): List<DuplicateGroup> {
        val out = mutableListOf<DuplicateGroup>()
        for (g in groups) {
            val i = out.indexOfFirst { o -> o.kind == g.kind && o.members.any { m -> g.members.any { it.equals(m, true) } } }
            if (i < 0) out += g
            else out[i] = out[i].copy(members = (out[i].members + g.members).distinctBy { it.lowercase() })
        }
        return out
    }
}
