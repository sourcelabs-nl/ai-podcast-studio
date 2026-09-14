## ADDED Requirements

### Requirement: The bundle is a plain OKF v0.2 directory
Project knowledge SHALL live at `knowledge/`, tracked in git, as markdown files with YAML frontmatter conforming to Open Knowledge Format v0.2. The bundle root SHALL contain `index.md` declaring `okf_version: "0.2"`.

The bundle SHALL be readable without any tool: no loader, no database, no build step, and no code in the application that reads it.

#### Scenario: A reader opens the bundle with no tooling
- **WHEN** someone opens `knowledge/index.md` in a text editor
- **THEN** the sections and their purpose are legible without running anything

#### Scenario: The application does not depend on the bundle
- **WHEN** `knowledge/` is absent
- **THEN** the application builds, starts and generates an episode unchanged

### Requirement: Three layers with one writer each
The raw layer (the episode archive and its scripts, probe output, score rows, reference transcripts) SHALL NOT be modified by knowledge maintenance; entries cite it.

The bundle SHALL be written and maintained by the agent as part of the work that produces knowledge, rather than as a separate documentation exercise.

`CLAUDE.md` SHALL carry the conventions and operations as its configuration, and SHALL point at `knowledge/index.md` rather than restating the bundle's contents, so per-session load stays bounded.

#### Scenario: Raw sources are cited, not rewritten
- **WHEN** an entry draws on an episode script or a probe result
- **THEN** it references that source and leaves it unchanged

#### Scenario: A session knows the bundle exists
- **WHEN** a session starts and reads `CLAUDE.md`
- **THEN** it learns the bundle's location, its conventions and the three operations

### Requirement: The raw layer names its stores and is cited, not copied
The raw layer SHALL be understood to include the episode archive and its scripts, probe output, score rows, reference transcripts, the git history, the OpenSpec archive, and past session transcripts.

Knowledge that one of those stores already records durably SHALL NOT be reproduced in the bundle. Git records what changed and when; the OpenSpec archive records why a change was made. An entry SHALL cite the commit or archived change rather than summarising it, and SHALL confine itself to what neither can state: what was measured, and what was tried and rejected.

#### Scenario: A rationale already held by an archived change
- **WHEN** the reason for a change is recorded in the OpenSpec archive
- **THEN** the entry links to that change rather than restating its reasoning

#### Scenario: Knowledge that exists only in a transcript
- **WHEN** a measurement survives only in a session transcript
- **THEN** it is written into the bundle, since the transcript is not versioned and will not survive

### Requirement: Material from a transcript is rewritten, never pasted
Content taken from a session transcript SHALL be reformulated as a finished entry. Raw conversation, shell output, file paths from unrelated work and third-party material SHALL NOT be copied into the bundle, which is tracked in git and therefore permanent.

A claim drawn from a transcript SHALL be checked against later sessions before it is recorded, since a conclusion reached in one session may have been overturned in a later one.

#### Scenario: A superseded conclusion
- **WHEN** a transcript states a conclusion that a later session corrected
- **THEN** the correction is what is recorded, not the original claim

#### Scenario: Incidental content in a transcript
- **WHEN** a transcript passage carries shell output or unrelated project material
- **THEN** only the reformulated finding enters the bundle

### Requirement: Recording happens within the task that produced the knowledge
When a measurement is made, a prompt rule is removed or materially rewritten, or an experiment produces a result, the bundle SHALL be updated before that task is reported complete: the entry itself, any index that lists it, and `log.md`.

A result showing no difference SHALL be recorded on the same terms as one showing a difference.

#### Scenario: A measurement against a third-party API
- **WHEN** probes establish a behaviour of the TTS engine
- **THEN** a finding entry, its index and the log are updated as part of that task

#### Scenario: An ablation finds nothing
- **WHEN** removing a prompt rule produces no measurable change
- **THEN** that negative result is recorded as an experiment entry

### Requirement: An answer worth keeping is filed back
When a question is answered from the bundle and the answer has standing value, it SHALL be written back as an entry rather than left only in the conversation.

#### Scenario: A synthesis produced while answering
- **WHEN** answering a question yields a conclusion that would otherwise be re-derived
- **THEN** it becomes an entry with its own provenance

### Requirement: Lint is a recurring pass over the whole bundle
A lint pass SHALL check the bundle for entries contradicting each other, claims whose `stale_after` has passed, entries that nothing links to, concepts referenced but having no entry, and missing cross-references.

Lint SHALL be able to remove and merge entries, not only add them. An entry whose body has accumulated a history of its own edits SHALL be reported by lint and rewritten to state what is currently known.

A finding whose `stale_after` has passed SHALL be treated as a hypothesis to re-measure rather than as a current fact.

#### Scenario: An expired measurement
- **WHEN** lint finds a finding past its `stale_after`
- **THEN** it is re-measured or marked as unverified before it is acted on

#### Scenario: An entry carrying its own changelog
- **WHEN** an entry's body narrates how it was revised
- **THEN** lint reports it and the entry is rewritten to the current state

### Requirement: Provenance and confirmation are recorded separately
Every entry SHALL carry `generated` with the actor that produced its current content and when, using the same field for a person and for a model.

Confirmation SHALL be recorded in `verified` as a list of events with their actor and time, independent of `generated`, so an entry nobody has checked is distinguishable from one confirmed by a machine and from one confirmed by a person.

An entry SHALL NOT be treated as established merely because it exists in the bundle.

#### Scenario: An unchecked agent-written entry
- **WHEN** an entry has `generated` by a model and no `verified` events
- **THEN** it reads as unconfirmed rather than as settled knowledge

#### Scenario: A person confirms a claim
- **WHEN** a human checks an entry
- **THEN** a confirmation event naming that person is appended to `verified`

### Requirement: Status is lifecycle, not strength of evidence
`status` SHALL describe lifecycle only, following OKF's `draft`, `stable` and `deprecated`, and SHALL NOT be used to express how well a claim is established, which is `verified`'s role.

A deprecated entry MAY be well verified, which is the ordinary case for a rule removed on evidence.

#### Scenario: A removed rule that was measured
- **WHEN** a prompt rule is removed on the strength of a measurement
- **THEN** its entry is `deprecated` while retaining its confirmation events

### Requirement: Findings about third-party behaviour expire
A finding describing an external model or API SHALL record how it was measured, the model version it was measured against, and `stale_after` as an absolute instant.

#### Scenario: A finding without its method
- **WHEN** an entry states a measured number without saying how it was measured
- **THEN** the entry is incomplete and is rejected in review

### Requirement: Rationale entries link to code rather than quoting it
An entry explaining a prompt rule SHALL name the source file and identifier it describes and SHALL NOT reproduce the rule's text.

#### Scenario: A rule's wording changes
- **WHEN** the rule text is edited in the source file
- **THEN** the entry remains correct because it never held a copy

### Requirement: The log is chronological and greppable
`log.md` SHALL record what changed and when, newest first, with each entry beginning with a consistent prefix carrying the date and the operation, so recent activity can be read with a line-oriented search.

A directory MAY carry its own `log.md` for changes scoped to it.

#### Scenario: Reading recent activity
- **WHEN** someone greps the log's entry prefix and takes the last lines
- **THEN** the most recent operations are listed without reading the whole file

### Requirement: Entry types follow a project convention
Entries SHALL use the types `finding`, `rule-rationale`, `experiment` and `reference`. Consistent with OKF, a type outside that set SHALL be a judgement made in review rather than a validation failure, and a reader encountering an unknown type SHALL treat it as a generic entry.

#### Scenario: An entry needing a new type
- **WHEN** knowledge does not fit the four conventional types
- **THEN** a new type is chosen deliberately in review and added to the conventions

### Requirement: Nothing writes to the bundle programmatically
No automated process SHALL create or modify files under `knowledge/`. Scoring runs SHALL persist their results to the database; only a conclusion drawn from them SHALL enter the bundle.

#### Scenario: A scoring run completes
- **WHEN** an episode scoring run finishes
- **THEN** it writes score rows and touches no file under `knowledge/`
