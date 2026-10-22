## ADDED Requirements

### Requirement: Settings page warns when subtopic names are lost
The Compose tab of the podcast settings page SHALL show an inline warning under the subtopics editor when a subtopic name present in the saved podcast is renamed or removed in the form. The warning SHALL name the affected old names and SHALL explain that already-scored articles keep the subtopic they were classified with, count as "Other" (weight 1), and land in the rapid-fire segment or drop out of the episode until they are scored again. Changing a weight or adding a name SHALL NOT show the warning. The warning SHALL disappear when the form no longer differs from the saved names, and showing it SHALL NOT save or change anything.

#### Scenario: A renamed subtopic shows the warning
- **WHEN** the saved subtopics contain "Harness" and the form renames it to "HarnessX"
- **THEN** the warning is shown with "Affected: Harness"

#### Scenario: A removed subtopic shows the warning
- **WHEN** the saved subtopics contain "Harness" and the form removes it
- **THEN** the warning is shown with "Affected: Harness"

#### Scenario: A weight change shows no warning
- **WHEN** the form changes only the weight of a saved subtopic
- **THEN** no warning is shown

#### Scenario: An added subtopic shows no warning
- **WHEN** the form adds a subtopic name and keeps all saved names
- **THEN** no warning is shown

#### Scenario: Reloading clears the warning
- **WHEN** the page is reloaded after an unsaved rename
- **THEN** the form shows the saved names and no warning is shown
