# Code review backlog (2 oktober 2026)

Bevindingen uit de `/code-review all` van 1 en 2 oktober 2026 die bewust niet in de batches zijn opgepakt. Elke ronde van de review las meer bestanden en vond nieuwe punten, dus dit is een werklijst en geen nulmeting. De reviewer las ongeveer 190 van de 260 bestanden; de rest is alleen met patroonscans bekeken.

Reeds opgelost in de commits `c2add9f` t/m de laatste opvolgronde (batch 6): onderbroken unpublish, `CancellationException`, HTML-escaping, cache van correctie-retries, scheduler via `SourceService`, status-checks en validatie uit de controllers, OAuth-flow, queries naar de database, getypte API-fouten, afronding van een episode na audio, pipeline-status, aggregator-race, ownership-resolver, composer-skelet, thread-veilige SSE, het verlies van de nieuwe track-id bij een mislukte permalink-claim, `ScriptJudge` op `Dispatchers.IO`, de scope van `AutoPublishListener`, de overige handgebouwde 400's.

## Verdient een eigen OpenSpec change

- **`EpisodeService` is een god service** (ongeveer 1050 regels, 18 dependencies). Opsplitsen naar levenscyclus: generatie, review, publicatie, onderhoud. De vier kopieën van het opslaan van stage-totalen (`saveDedupResults`, `saveFocusSelection`, `saveComposeResult`, `generateAndStoreRecap`) en de dubbele logica in `createEpisodeFromPipelineResult` en `finalizeEpisode` (gate, TTS, markeren als verwerkt, `lastGeneratedAt`, event) horen daarbij. De KDoc van `Episode.kt` ("alleen `finalizeEpisode` schrijft deze totalen") klopt niet meer.
- **`PipelineRunner` heeft nog een te brede try** (A9). Een fout in de stappen tussen audio en afronden zet het episode nog steeds op FAILED. De retry is nu goedkoop, maar de status blijft misleidend.
- **`deletePodcastCascade`** verwijdert mp3-bestanden binnen een `@Transactional` methode. Een rollback laat rijen achter die naar verwijderde bestanden wijzen. Verwijder de bestanden na de commit.
- **`generateAndStoreRecap`** geeft bij falen de episode van vóór het opslaan terug; een aanroeper die dat opslaat draait de recap terug.
- **`PodcastService.update` valideert de samengevoegde podcast.** Een oude opgeslagen waarde kan daardoor een niet-gerelateerde update blokkeren. Valideer alleen de gewijzigde velden, of leg vast dat dit zo bedoeld is.
- **`LlmPipeline`**: drie kopieën van de `when (podcast.style)`-dispatch (compose, recompose, preview) en drie van het aggregeren van niet-gekoppelde posts. `preview()` mist de kostenpoort, de distinct/cap en de recente focus-afhandeling van de echte run. Gebruikt repositories rechtstreeks.
- **`CachingChatModel` herkent een correctie-retry aan de vorm van de prompt** (laatste bericht is USER na een ASSISTANT-bericht). Laat de advisor dat expliciet markeren, zodat een toekomstige multi-turn aanroeper geen cache-rijen overschrijft. `find` en `save` zijn niet atomair.
- **Blanco completions** worden door de drie composers niet apart afgewezen (`TopicDedupFilter` doet dat wel). De tag-validatie vangt het meestal op.
- **`ScoreStage`/`TopicDedupFilter`**: `DedupCluster.status` is een String en wordt op twee plekken verschillend vergeleken (`ignoreCase` en niet).

## Kleiner werk

- Eén `FtpPathResolver` voor `remotePath` en `publicUrl`: de afleiding staat drie keer in `FtpPublisher` en één keer in `StaticFeedExporter`. `StaticFeedExporter` hardcodeert `"ftp"` terwijl `FtpPublisher.TARGET_NAME` bestaat. `FtpPublisher.unpublish` is een stille no-op. `FtpPublisher.unpublish` en `deleteRemoteFile` delen één try: controleer of beide idempotent zijn.
- `UserProviderConfigController.list` leidt de base-URL af in de controller (derde kopie naast twee in `UserProviderConfigService`).
- `EpisodeScoringController` geeft `@Table`-entiteiten terug; maak DTO's.
- `SourceController.list` orkestreert vier service-aanroepen; `PodcastPublicationTargetController` serialiseert config-JSON in de controller; `PublishingTestController.resolveStoredFtpPassword` parst opgeslagen credentials en slikt de fout.
- `SourceService.update` roept `validateUrl` niet aan, `create` wel. `validateRssUrl` leest `e.message`.
- `TwitterFetcher.fetchWithUserId` slikt alle fouten en geeft een leeg resultaat, zodat rate limits en auth-fouten nooit geclassificeerd of teruggeschroefd worden. `XClient` bouwt URL's met een niet-gecodeerde `username`.
- `UserService` omzeilt `UserProviderConfigService` met `providerConfigRepository`; `UserProviderConfigService.resolveConfig` heeft een dubbel mapping-blok.
- `FeedGenerator.generate` is ongeveer 130 regels. `escapeHtml` staat in `FeedGenerator` en `EpisodeSourcesGenerator`; gebruik `HtmlUtils`. `CachingChatModel.sha256` dupliceert `util/HashUtils.sha256`.
- `PodcastMappers.buildCosts` (ongeveer 120 regels) is kostenlogica in een mapper; `USD_TO_CENTS` staat ook in `CostEstimator`.
- `EpisodeScoringService`, `TopicDedupFilter` en andere services geven data- en resultaatklassen mee in hun bestand (A7); verplaats naar `*Types.kt`.
- Event- en stagenamen zijn stringliteralen verspreid over de code (`"episode.generated"`, `"episode.stage"`, `"tts"`). Constanten of een enum voorkomen dat het volgende event stil dood gaat, zoals `pipeline.progress` deed.
- Stringly typed velden in `store/` (`LlmCall.stage`, `Episode.pipelineStage`, `EpisodePublication.target`, ...) en `fromValue` die drie keer bestaat.
- `ArticleRepository`: `findRelevantUnprocessedBySourceIds` en `findUnprocessedSince` blijven `@Query` (bewust: `is_processed`, `NULLS LAST`).
- `PublishingService.unpublish` downcast `(publisher as FtpPublisher)` na een string-vergelijking op `target`.
- `SourceAggregator`-testdekking: er is nu een echte SQLite-test voor `insertIgnore`; overweeg er één voor de volledige `aggregateAndPersist` in een transactie.
- `EventStreamController` controleert niet of de gebruiker bestaat. `WebConfig` hardcodeert de CORS-origin `http://localhost:3005`.
- Mappen die `Map<String, Any>` teruggeven (`UpcomingContent.toResponse`, `regenerateAllShowNotes`) horen een benoemde klasse te zijn.
- `TtsPipeline.kt:70` en enkele andere plekken gebruiken `!!`; gebruik `requireNotNull` met een boodschap.
- Migraties: `idx_episode_candidates_episode` is overbodig naast de UNIQUE-constraint; V30 en V36 maken beide `idx_episode_articles_episode`.

## Documentatie die niet meer klopt

- `openspec/specs/pipeline-progress/spec.md` zegt dat het systeem `pipeline.progress` SSE-events uitstuurt; de code publiceert `episode.stage`. De frontend luistert nog naar beide.
- De `spring-ai` skill noemt `createForCompose`, maar `ChatClientFactory` heeft alleen `createForModel`.
- `.claude/rules/controllers.md` en `kotlin-main.md` kunnen `PodcastAccess` en de typed API-excepties noemen.

## Uit de afsluitende review (2 oktober, na batch 5c)

Naast wat hierboven staat:

- **`SoundCloudPublisher` verwijdert tracks op een te losse titel.** Het vrijmaken van quota verwijdert elke track waarvan de titel met `podcast.name` begint. Op een gedeeld account verwijdert een podcast "Tech" dus tracks van "Tech Weekly". Match op het exacte prefix dat de klasse zelf genereert (`"${podcast.name} - "`), of op track-id's uit de publicatie-rijen. Dit is de enige bevinding uit de review die ik als destructief beschouw.
- **`LlmPipeline.preview` omzeilt de kostenpoort** (`maxLlmCostCents`) en geeft geen `episodeId` mee, doordat het aggregeren, scoren en eligibility opnieuw implementeert in plaats van `aggregateScoreAndFilter` en `dedup` te hergebruiken. De compose-dispatch en de context-opbouw staan drie keer in dit bestand.
- **Events binnen een transactie.** `createGeneratingEpisode`, `discardOnly` en `discardAndResetArticles` publiceren in `@Transactional`; listeners kunnen een toestand melden die daarna terugrolt. Publiceer na de commit.
- **Stale snapshot (SB1).** `recordRun`, `updatePipelineStage`, `markRecomposing`, `clearPipelineStage`, `updateScript` en de twee approve-methoden laden een `Episode` en slaan een volledige `copy` op. `updatePipelineStage` vuurt uit progress-callbacks terwijl andere schrijvers de rij raken. Gebruik gerichte `@Modifying` updates, of valideer de status op `fresh`.
- **`regenerateAllShowNotes`** heeft een dode catch (`generateSourcesFile` slikt zelf alle fouten), zodat `generatedSources++` mislukkingen meetelt. De logtekst zegt `sources.md` terwijl het bestand `sources.html` is.
- **Schedulers.** `BriefingGenerationScheduler` leest `lastPollRoundCompletedAt` van `SourcePollingScheduler` en roept `pollPodcastSourcesNow` aan; catch-up polling en "is deze podcast aan de beurt" (cron-berekening) horen in services. `SourcePollingScheduler.pollSources` en `pollPodcastSourcesNow` kopiëren hetzelfde `supervisorScope`-mechanisme.
- **Coroutine-scopes.** Tien klassen maken hun eigen `CoroutineScope(Dispatchers.IO + SupervisorJob())`; overweeg één gedeelde scope-bean.
- **`PreviewAudioController`** bezit nog een eigen scope en orkestratie voor het volledige-audio-stream; verplaats naar een component naast `PreviewStreamer` en deel één `SseJobStreamer`.
- **`EpisodeController.get`** bouwt de kostencontext uit meerdere services en bezit `stageCostFnFromModels`. Eén service-aanroep die het kostenbeeld teruggeeft.
- **`PublishingService.listByPodcast`** geeft web-DTO's terug (`PodcastPublicationRow`) en negeert de sort van de `Pageable`; `PublishingService` injecteert `EpisodeRepository` rechtstreeks. `liveTargets` filtert in het geheugen.
- **`deletePodcastCascade`**: de foreign keys in V1 en V6 hebben geen `ON DELETE CASCADE`, dus het cascade-verwijderen gebeurt met de hand.
- **Dode code**: `TtsPipeline.generate` (alleen de test roept het aan), `SoundCloudClient.getPlaylist`, `PostArticleRepository.findByArticleId`, `EpisodeRepository.findByPodcastIdAndStatus` (alleen tests), `EpisodeService.findRawArticlesForEpisode` met `findRawArticlesByEpisodeId`.
- **`AudioGenerationService`**: `generateAudioAsync` en `regenerateAudioAsync` hebben identieke bodies.
- **`EpisodeController.retry`** bouwt een gebruikersbericht uit `resumePoint.name.lowercase().replace(...)`.
- **Tests**: tien testbestanden gebruiken `runBlocking` in plaats van `runTest` (`PublishingServiceTest`, `FtpPublisherTest`, `SoundCloudPublisherTest`, `TaggedScriptComposerTest`, `LlmPipelineTest`, `PreviewAudioServiceTest`, `BriefingGenerationSchedulerTest`, de drie TTS-provider-tests).
- **`ScriptJudge`**: geen `maxTokens`-cap en een handgebouwde `BeanOutputConverter`-lus in plaats van `validateSchema()`.
