<!-- Implemented before this change was written; every task below is already done. -->

## 1. Registry entry

- [x] 1.1 Verify GLM-5.3's price and provider list on the OpenRouter model and pricing pages rather than from memory
- [x] 1.2 Add `z-ai/glm-5.3` (type llm, input 1.40, output 4.40 USD per Mtok) to `app.models.openrouter` in `application.yaml`, with a comment recording that Z.ai is the only provider so its list price is the routed price

## 2. Verification

- [x] 2.1 Restart the application and confirm `GET /config/defaults` lists `z-ai/glm-5.3` under `availableModels.openrouter`
- [x] 2.2 Confirm `z-ai/glm-5.2` remains registered and the configured `compose` default is unchanged
