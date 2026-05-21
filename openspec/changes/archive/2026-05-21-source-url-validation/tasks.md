## 1. Source URL Validation

- [x] 1.1 Add `validateUrl(type, url)` method to `SourceService` — performs test fetch based on source type, throws `IllegalArgumentException` with descriptive message on failure
- [x] 1.2 For RSS sources: fetch URL, check response is non-empty, parse as RSS/Atom XML, verify at least one item exists
- [x] 1.3 For website sources: fetch URL, check response is non-empty, verify extractable text content
- [x] 1.4 Skip validation for Twitter/Reddit/YouTube source types
- [x] 1.5 Call `validateUrl()` in `SourceService.create()` before persisting

## 2. Controller Error Handling

- [x] 2.1 Catch `IllegalArgumentException` from `SourceService.create()` in `SourceController` and return HTTP 422 with error message

## 3. Tests

- [x] 3.1 Write tests for `SourceService.validateUrl()` — valid RSS, empty RSS, invalid XML, unreachable URL, website valid/empty, Twitter skipped
- [x] 3.2 Write controller test for 422 response on validation failure
