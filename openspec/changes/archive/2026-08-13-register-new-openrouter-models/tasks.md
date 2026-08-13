## 1. Verify pricing and slugs

- [x] 1.1 Read `anthropic/claude-sonnet-5` and `anthropic/claude-opus-5` pricing from the OpenRouter model catalog (`https://openrouter.ai/api/v1/models`) and convert per-token prices to USD per Mtok
- [x] 1.2 Confirm the DeepSeek "latest flash" slug: the catalog reports it as `~deepseek/deepseek-v4-flash-latest` (alias of `deepseek-v4-flash-0731`); the tilde-less form 404s
- [x] 1.3 Read the alias's listed pricing (0.08 / 0.252 USD per Mtok)

## 2. Register the models

- [x] 2.1 Add `anthropic/claude-sonnet-5` to `app.models.openrouter` in `application.yaml` (type: llm, input 2.00, output 10.00)
- [x] 2.2 Add `anthropic/claude-opus-5` to `app.models.openrouter` in `application.yaml` (type: llm, input 5.00, output 25.00)
- [x] 2.3 Add `~deepseek/deepseek-v4-flash-latest` to `app.models.openrouter` (type: llm, input 0.08, output 0.252) with a comment recording that it is an alias and its cost tracking is approximate
- [x] 2.4 Leave `app.llm.defaults` and the existing pinned entries unchanged

## 3. Verification

- [x] 3.1 Full `mvn test` suite green
- [x] 3.2 Restart the app (`./stop.sh && ./start.sh`) and confirm `GET /config/defaults` lists all three models under `availableModels.openrouter`, with the alias's leading `~` intact
