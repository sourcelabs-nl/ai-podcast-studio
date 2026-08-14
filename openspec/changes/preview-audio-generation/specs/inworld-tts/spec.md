## MODIFIED Requirements

### Requirement: Inworld TTS script guidelines
The `InworldTtsProvider` SHALL return style-aware script guidelines via `scriptGuidelines(style, pronunciations)`. The guidelines SHALL instruct the LLM to use Inworld-specific markup:
- Non-verbal tags: `[sigh]`, `[laugh]`, `[breathe]`, `[cough]`, `[clear throat]`, `[yawn]` — spelled exactly as Inworld documents them, with a space rather than an underscore in `[clear throat]`
- Emphasis: `*word*` (single asterisks) for stressed words, or CAPS for a whole word or a single syllable (`AbsoLUTEly`)
- Pacing: ellipsis (`...`) for trailing pauses, exclamation marks for excitement
- Pauses: SSML break tags such as `<break time="1s" />` for a deliberate beat between segments, at most 20 per request and at most 10 seconds each, and not immediately before a paragraph break where the pause already exists
- Steering: at most one short free-form English delivery instruction in square brackets (for example `[warm and conversational with an easy pace]`) at the start of a speaker turn or segment, with `[reset]` to return to neutral delivery
- Acronyms: expand on first use, then use the short form — spoken as a word when pronounceable and spelled out letter by letter when not, because Inworld's normalization does not cover domain acronyms
- IPA phonemes: `/phoneme/` for precise pronunciation of proper nouns

The guidelines SHALL additionally include:
- Text normalization: write all numbers, dates, currencies, and symbols in fully spoken form
- Anti-markdown: never use markdown formatting; never use `**double asterisks**` as the TTS engine reads asterisk characters aloud
- Contractions: use natural contractions throughout for spoken naturalness
- Punctuation: always end sentences with proper punctuation for correct pacing

The steering guidance SHALL be emitted for every style and model. The post-processor is responsible for stripping steering instructions on models that do not support them, so the guidelines do not need a model parameter.

For `CASUAL`, `DEEP_DIVE`, `DIALOGUE` and `INTERVIEW` styles, guidelines SHALL additionally encourage natural filler words (`uh`, `um`, `well`, `you know`), because disfluencies are what make synthesised conversational speech sound human. A deep dive is long-form and exploratory, and reads as conversational rather than formal. For `EXECUTIVE_SUMMARY` and `NEWS_BRIEFING` styles, guidelines SHALL instruct to avoid filler words and minimize non-verbal tags.

When `pronunciations` is non-empty, the guidelines SHALL append a "Pronunciation Guide" section listing each term and its IPA phoneme. The guidelines SHALL instruct the LLM to REPLACE the word with its IPA phoneme notation on every occurrence (not write both the word and the phoneme), and to ONLY use IPA for the listed terms (never invent IPA for unlisted words). When `pronunciations` is empty, no pronunciation section SHALL be appended.

#### Scenario: Casual style guidelines include filler words
- **WHEN** `scriptGuidelines(PodcastStyle.CASUAL, emptyMap())` is called
- **THEN** the returned text includes instructions to use filler words naturally

#### Scenario: Interview style guidelines include filler words
- **WHEN** `scriptGuidelines(PodcastStyle.INTERVIEW, emptyMap())` is called
- **THEN** the returned text includes instructions to use filler words naturally and does not instruct to avoid them

#### Scenario: Deep dive style guidelines include filler words
- **WHEN** `scriptGuidelines(PodcastStyle.DEEP_DIVE, emptyMap())` is called
- **THEN** the returned text includes instructions to use filler words naturally and does not instruct to avoid them

#### Scenario: Executive summary guidelines suppress filler words
- **WHEN** `scriptGuidelines(PodcastStyle.EXECUTIVE_SUMMARY, emptyMap())` is called
- **THEN** the returned text instructs to avoid filler words and minimize non-verbal tags
