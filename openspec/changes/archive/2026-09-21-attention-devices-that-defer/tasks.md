<!-- Implemented before this change was written; every task below is already done. -->

## 1. Cliffhangers

- [x] 1.1 Rewrite the STRATEGIC CLIFFHANGERS bullet: 1-2 hooks, explicitly parked, at least 3 intervening topics, payoff refers back
- [x] 1.2 Name the failure mode in the bullet, so a tease resolved by the next turn is excluded rather than merely undescribed

## 2. Shared humor

- [x] 2.1 Add a `multiSpeaker` parameter to `buildHumorBlock` and document why the flavour menu alone produces one-sided humor
- [x] 2.2 Require one beat from a speaker other than the opener, and one beat that reacts to the other speaker's previous line
- [x] 2.3 Pass `multiSpeaker = true` from `InterviewComposer` and `DialogueComposer`, and `false` explicitly from `BriefingComposer`

## 3. Teaser

- [x] 3.1 Require at least 3 distinct topics from different parts of the episode, and exclude three angles on the opening story
- [x] 3.2 Raise the teaser budget from 25 to 40 words, since 3 topics do not fit in 25

## 4. Tests

- [x] 4.1 `InterviewComposerTest`: the teaser demands several topics from across the episode
- [x] 4.2 `InterviewComposerTest`: cliffhangers must defer their payoff
- [x] 4.3 `InterviewComposerTest`: humor is required from more than one speaker
- [x] 4.4 Update the two existing cases that pinned "2-3 forward hooks" and "under 25 words"
- [x] 4.5 Run `mvn test` and confirm the full suite passes (1420 tests, 0 failures)

## 5. Deploy

- [x] 5.1 Restart the application so the next composition uses the new prompt
