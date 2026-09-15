package com.aisummarypodcast.config

/**
 * Evaluation of scripts that have already been produced. Nothing here affects how an episode is
 * composed; it only decides whether, and how hard, a finished script is looked at.
 */
data class EvalProperties(
    val judge: JudgeProperties = JudgeProperties()
)

/**
 * How the script judge behaves.
 *
 * [norm] deliberately has no default. It is the score below which a script counts as poor, and
 * where that line sits is a question about the distribution of judged scores over the archive, not
 * a question anyone can answer from first principles. A plausible-looking default would be
 * indistinguishable in the output from a measured one and would reject episodes on no evidence, so
 * [JudgeMode.ENFORCE] falls back to advising while this is null. See [JudgeMode].
 */
data class JudgeProperties(
    val mode: JudgeMode = JudgeMode.ADVISE,
    val norm: Double? = null
)

/**
 * What a run does with a judged score.
 *
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
