# Capability: Source Labels

## Purpose

A human-readable display name per source, used wherever a source is shown instead of its raw URL.

## Requirements

### Requirement: Source label field
The `sources` table SHALL have a nullable `label` column (TEXT) that stores a human-readable display name for the source.

#### Scenario: Source with label
- **WHEN** a source has a `label` value set
- **THEN** the label is returned in API responses that include source data

#### Scenario: Source without label
- **WHEN** a source has a NULL `label` value
- **THEN** the API returns `label` as null and consumers derive a display name from the source URL
