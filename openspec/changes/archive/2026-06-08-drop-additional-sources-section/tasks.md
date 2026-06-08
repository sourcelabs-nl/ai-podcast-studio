## 1. Remove Additional Sources (retrofit: already implemented)

- [x] 1.1 Drop the "Additional Sources" rendering in `EpisodeSourcesGenerator`; list only discussed topics
- [x] 1.2 Update `EpisodeSourcesGeneratorTest` to assert non-discussed articles and the section are omitted
- [x] 1.3 Run `mvn test`
- [x] 1.4 Regenerate the sources file for episode 140 and republish; verify the section is gone
