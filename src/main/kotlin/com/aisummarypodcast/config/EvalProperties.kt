package com.aisummarypodcast.config

/**
 * Nothing here affects how an episode is composed. It only decides whether, and how hard, an
 * already-produced script is looked at.
 */
data class EvalProperties(
    val judge: JudgeProperties = JudgeProperties()
)

/**
 * [norm] deliberately has no default. Where the line between a poor script and an acceptable one
 * sits is a question about the distribution of judged scores over the archive, and a
 * plausible-looking default would be indistinguishable in the output from a measured one while
 * rejecting episodes on no evidence. [JudgeMode.ENFORCE] therefore falls back to advising while
 * this is null.
 */
data class JudgeProperties(
    val mode: JudgeMode = JudgeMode.ADVISE,
    val norm: Double? = null
)

/**
 * The mode never changes what the judge is asked or what it returns, so a score written under
 * [ADVISE] and one written under [ENFORCE] are the same kind of score and stay comparable.
 */
enum class JudgeMode {
    /** No judge call and no score row, so the feature costs nothing when it is not wanted. */
    OFF,

    /** Judge, persist and report. The episode proceeds whatever the score says. */
    ADVISE,

    /**
     * Judge and act on the comparison against the configured norm. With no norm configured this
     * behaves exactly as [ADVISE] and records that it did so.
     */
    ENFORCE
}
