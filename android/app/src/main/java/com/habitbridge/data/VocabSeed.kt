package com.habitbridge.data

/** Built-in starter deck (same 12 words as the web prototype). Own-authored text; extend via the premium deck later (PRD §8.2). */
object VocabSeed {
  val items: List<VocabEntity> = listOf(
    VocabEntity(1, "epiphany", "갑작스러운 깨달음", "She had an epiphany about her career."),
    VocabEntity(2, "serendipity", "뜻밖의 행운", "Meeting her was pure serendipity."),
    VocabEntity(3, "resilient", "회복력 있는", "Children are remarkably resilient."),
    VocabEntity(4, "meticulous", "꼼꼼한", "He kept meticulous records."),
    VocabEntity(5, "candid", "솔직한", "Give me your candid opinion."),
    VocabEntity(6, "ubiquitous", "어디에나 있는", "Smartphones are ubiquitous."),
    VocabEntity(7, "lucid", "명료한", "Her explanation was lucid."),
    VocabEntity(8, "tenacious", "끈질긴", "A tenacious negotiator."),
    VocabEntity(9, "ephemeral", "덧없는", "Fame is ephemeral."),
    VocabEntity(10, "pragmatic", "실용적인", "A pragmatic approach to the problem."),
    VocabEntity(11, "deliberate", "의도적인", "A deliberate choice, not a reflex."),
    VocabEntity(12, "restraint", "자제", "He showed great restraint."),
  )
}
