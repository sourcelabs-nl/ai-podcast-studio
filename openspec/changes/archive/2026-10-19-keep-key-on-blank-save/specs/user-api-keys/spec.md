## ADDED Requirements

### Requirement: A blank key keeps the stored key

Saving a provider config with an API key that is empty or only whitespace SHALL keep the key
already stored for that user, category and provider, exactly as saving with no key does. The
other fields of the config SHALL still be updated.

#### Scenario: Saving with an empty key field
- **WHEN** a stored OpenRouter config is saved again with a blank key and a new base URL
- **THEN** the stored key is unchanged and the base URL is updated
