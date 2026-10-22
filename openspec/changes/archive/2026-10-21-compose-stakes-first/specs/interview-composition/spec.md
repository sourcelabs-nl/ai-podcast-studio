## MODIFIED Requirements

### Requirement: The interview prompt asks for backchannels
The interview prompt SHALL ask for at most 2 backchannels per episode: a turn of a few words from the interviewer while the expert is mid-explanation, carrying no question, no new information and no topic change, after which the expert resumes their thought in a turn of their own.

The prompt SHALL distinguish a backchannel from an interruption, on the grounds that an interruption takes the floor and a backchannel hands it straight back.

#### Scenario: The rule reaches the model
- **WHEN** the interview prompt is built
- **THEN** it asks for at most 2 backchannels and states that they carry no question and no new information

#### Scenario: The turn length cap is unchanged
- **WHEN** the interview prompt is built
- **THEN** the expert's per-turn sentence cap is stated as before

## ADDED Requirements

### Requirement: The interviewer is a curious, skeptical colleague
The interview prompt SHALL cast the interviewer as an audience surrogate in the role of a curious, skeptical colleague who asks what each story means for the listener, pushes back on claims, and reacts.

#### Scenario: The interviewer role reaches the model
- **WHEN** the interview prompt is built
- **THEN** it describes the interviewer as a curious, skeptical colleague who asks what each story means for the listener
