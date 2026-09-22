## ADDED Requirements

### Requirement: Speaker-tag validation ignores the topic-order block

Speaker-tag validation of a multi-speaker script SHALL strip the topic-order metadata block (`|||TOPIC_ORDER||| ... |||END_TOPIC_ORDER|||`) before it cleans up and checks the script, so the block is never reported as discarded untagged text.

#### Scenario: Script ending with the topic-order block
- **WHEN** a correctly tagged dialogue script is followed by a topic-order block
- **THEN** validation passes and no "Discarded ... untagged text after the script" warning is logged
