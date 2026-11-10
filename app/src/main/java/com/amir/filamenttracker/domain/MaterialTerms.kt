package com.amir.filamenttracker.domain

/**
 * A material has two kinds of classification words:
 * - the sub-type (variant): performance and fill, e.g. "+", "High Speed", "CF". Spools of one main
 *   material and color count together whatever their sub-type.
 * - the finish: how the print looks, e.g. "Matte", "Silk", "Translucent". A black matte spool cannot
 *   stand in for a black silk one, so the finish is part of the stock line (see [lineName]).
 *
 * Known words are stored in one canonical spelling and order, so "rapid" and "HF" are both
 * "High Speed", and "Translucent HF" and "HF Translucent" are the same material.
 */
private enum class TermKind { VARIANT, COMMON_FINISH, MORE_FINISH, NO_FINISH }

private class Term(val name: String, val kind: TermKind, aliases: List<String>) {
    /** Each alias as lowercase words, longest first so "glow in the dark" wins over "glow". */
    val words: List<List<String>> = (aliases + name).map { splitWords(it.lowercase()) }.distinct().sortedByDescending { it.size }
}

// Declaration order is the stored order of the words.
private val TERMS = listOf(
    Term("+", TermKind.VARIANT, listOf("plus")),
    Term("Tough", TermKind.VARIANT, listOf("tough+")),
    Term("High Speed", TermKind.VARIANT, listOf("hi speed", "highspeed", "hispeed", "hs", "hf", "high flow", "rapid")),
    Term("CF", TermKind.VARIANT, listOf("carbon fiber", "carbon fibre", "carbon")),
    Term("GF", TermKind.VARIANT, listOf("glass fiber", "glass fibre")),
    Term("Matte", TermKind.COMMON_FINISH, listOf("matt")),
    Term("Silk", TermKind.COMMON_FINISH, listOf("silk+", "silky")),
    Term("Translucent", TermKind.COMMON_FINISH, emptyList()),
    Term("Transparent", TermKind.COMMON_FINISH, listOf("clear")),
    Term("Metal", TermKind.COMMON_FINISH, listOf("metallic")),
    Term("Marble", TermKind.MORE_FINISH, emptyList()),
    Term("Wood", TermKind.MORE_FINISH, emptyList()),
    Term("Sparkle", TermKind.MORE_FINISH, listOf("glitter")),
    Term("Galaxy", TermKind.MORE_FINISH, emptyList()),
    Term("Glow", TermKind.MORE_FINISH, listOf("glow in the dark", "gitd", "luminous")),
    Term("Dual Color", TermKind.MORE_FINISH, listOf("dual colour", "dual", "bicolor", "two color")),
    Term("Tri Color", TermKind.MORE_FINISH, listOf("tri colour", "tricolor", "triple color", "tri")),
    Term("Gradient", TermKind.MORE_FINISH, listOf("rainbow")),
    // "PLA Basic" is plain PLA: the word is recognized only to be dropped.
    Term("Basic", TermKind.NO_FINISH, listOf("standard", "regular")),
)

private fun namesOf(kind: TermKind) = TERMS.filter { it.kind == kind }.map { it.name }

/** Chips for the sub-type field. */
val VARIANT_TERMS: List<String> = namesOf(TermKind.VARIANT)

/** Finish chips that are always shown. */
val COMMON_FINISH_TERMS: List<String> = namesOf(TermKind.COMMON_FINISH)

/** Finish chips behind "more". */
val MORE_FINISH_TERMS: List<String> = namesOf(TermKind.MORE_FINISH)

private fun splitWords(text: String): List<String> = text.trim().split(Regex("[\\s\\p{Z}-]+")).filter { it.isNotEmpty() }

/** A word of the input: a known [term], or an unknown word kept as typed. */
private class Piece(val term: Term?, val text: String)

private fun parse(text: String): List<Piece> {
    val words = splitWords(text)
    val lower = words.map { it.lowercase() }
    val pieces = mutableListOf<Piece>()
    var i = 0
    while (i < words.size) {
        val match = TERMS.flatMap { t -> t.words.map { t to it } }
            .filter { (_, alias) -> alias == lower.subList(i, minOf(lower.size, i + alias.size)) }
            .maxByOrNull { (_, alias) -> alias.size }
        if (match == null) {
            pieces += Piece(null, words[i])
            i++
        } else {
            pieces += Piece(match.first, match.first.name)
            i += match.second.size
        }
    }
    return pieces
}

/** "+" first so it attaches to the family ("PLA+ Matte"), then unknown words as typed, then known words in catalog order. */
private fun render(pieces: List<Piece>): String {
    val known = pieces.mapNotNull { it.term }.filter { it.kind != TermKind.NO_FINISH }.distinct()
    val unknown = pieces.filter { it.term == null }.map { it.text }.distinctBy { it.lowercase() }
    val plus = known.filter { it.name == "+" }.map { it.name }
    val rest = TERMS.filter { it in known && it.name != "+" }.map { it.name }
    return (plus + unknown + rest).joinToString(" ")
}

data class MaterialParts(val variant: String, val finish: String)

/**
 * Known words go to their own field in canonical spelling and order, wherever they were typed;
 * unknown words stay in the field they were typed in; "Basic" is dropped.
 */
fun normalizeMaterialParts(variant: String, finish: String): MaterialParts {
    val fromVariant = parse(variant)
    val fromFinish = parse(finish)
    val known = (fromVariant + fromFinish).filter { it.term != null }
    return MaterialParts(
        variant = render(fromVariant.filter { it.term == null } + known.filter { it.term!!.kind == TermKind.VARIANT }),
        finish = render(fromFinish.filter { it.term == null } + known.filter { it.term!!.kind.isFinish() }),
    )
}

private fun TermKind.isFinish() = this == TermKind.COMMON_FINISH || this == TermKind.MORE_FINISH

/** Whether [text] contains the known word [term], under any of its spellings. */
fun hasTerm(text: String, term: String): Boolean = parse(text).any { it.term?.name == term }

/** Whether every word of [text] is a known word, so its chips already cover it. */
fun isKnownTermsOnly(text: String): Boolean = parse(text).all { it.term != null }

/** Adds the known word [term] to [text], or removes it when it is already there. */
fun toggleTerm(text: String, term: String): String {
    val pieces = parse(text)
    val known = TERMS.first { it.name == term }
    return render(if (pieces.any { it.term == known }) pieces.filter { it.term != known } else pieces + Piece(known, term))
}

/** The stock line: spools of one line and color count together. */
fun lineName(family: String, finish: String): String = if (finish.isEmpty()) family else "$family $finish"
