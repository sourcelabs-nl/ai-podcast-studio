## ADDED Requirements

### Requirement: The interview prompt asks for backchannels
The interview prompt SHALL ask for 2 to 3 backchannels per episode: a turn of a few words from the interviewer while the expert is mid-explanation, carrying no question, no new information and no topic change, after which the expert resumes their thought in a turn of their own.

The prompt SHALL distinguish a backchannel from an interruption, on the grounds that an interruption takes the floor and a backchannel hands it straight back.

#### Scenario: The rule reaches the model
- **WHEN** the interview prompt is built
- **THEN** it asks for backchannels and states that they carry no question and no new information

#### Scenario: The turn length cap is unchanged
- **WHEN** the interview prompt is built
- **THEN** the expert's per-turn sentence cap is stated as before

### Requirement: Alternation admits the backchannel and nothing else
The interview prompt SHALL require speaker tags to alternate, with the backchannel as the single stated exception: after a backchannel turn the speaker who was interrupted MAY resume in a turn of their own.

The prompt SHALL continue to forbid two consecutive turns by one speaker that continue the same point or that evade the per-turn length cap.

The prompt SHALL NOT claim that consecutive same-speaker turns break the TTS pipeline, since they do not: each turn's role is read from its own opening tag and a voice is resolved per turn.

#### Scenario: The exception is named and bounded
- **WHEN** the interview prompt is built
- **THEN** it names the backchannel as the one exception and still forbids a same-speaker pair used to continue a point

#### Scenario: Consecutive same-speaker turns are synthesised
- **WHEN** a script contains an expert turn, a backchannel, and a further expert turn
- **THEN** each turn is voiced by the role's own voice and the audio is produced without error
