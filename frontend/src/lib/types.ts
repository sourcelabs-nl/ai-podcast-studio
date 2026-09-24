export interface User {
  id: string;
  name: string;
}

export interface ModelReference {
  provider: string;
  model: string;
}

export interface AvailableModel {
  name: string;
  type: string;
}

export interface Podcast {
  id: string;
  userId: string;
  name: string;
  topic: string;
  language: string;
  llmModels?: Record<string, ModelReference>;
  ttsProvider: string;
  ttsVoices?: Record<string, string>;
  ttsSettings?: Record<string, string>;
  style: string;
  targetWords?: number;
  cron: string;
  timezone: string;
  customInstructions?: string;
  relevanceThreshold: number;
  requireReview: boolean;
  requirePublishApproval: boolean;
  maxLlmCostCents?: number;
  maxArticleAgeDays?: number;
  speakerNames?: Record<string, string>;
  fullBodyThreshold?: number;
  sponsor?: Record<string, string>;
  pronunciations?: Record<string, string>;
  composeSettings?: Record<string, string>;
  deepDiveEnabled?: boolean;
  subtopics?: Record<string, number>;
  rapidFireWeightThreshold?: number;
  rapidFireMaxItems?: number;
  lastGeneratedAt?: string;
}

export interface Episode {
  id: number;
  podcastId: string;
  generatedAt: string;
  scriptText: string;
  status: string;
  publishApproved: boolean;
  audioFilePath?: string;
  durationSeconds?: number;
  composeModel?: string;
  ttsModel?: string;
  llmCostCents?: number;
  ttsCostCents?: number;
  recap?: string;
  showNotes?: string;
  errorMessage?: string;
  pipelineStage?: string;
  researchCalls?: number;
  researchCostCents?: number;
  costs?: EpisodeCosts;
  /** The focus text of a focus episode; absent for a regular episode. */
  focus?: string | null;
  /** The latest feedback a reviewer submitted to recompose a focus episode. */
  reviewFeedback?: string | null;
  /** Why this episode matched a search. Absent when the request carried no search query. */
  matches?: EpisodeMatches;
}

export interface EpisodeMatches {
  topics: string[];
  articleTitles: string[];
  /** Every matching topic, including those beyond the labels in `topics`. */
  topicTotal: number;
  /** Every matching article, including those beyond the labels in `articleTitles`. */
  articleTotal: number;
  /** The hit came only from the script, recap, or show notes, not from a covered story. */
  scriptOnly: boolean;
  /** The spoken text around the keyword, when the episode's own text mentions it. */
  scriptContext?: string;
}

export interface LlmStageCost {
  model: string | null;
  calls: number;
  inputTokens: number;
  outputTokens: number;
  costCents: number;
  /**
   * Carried by the scoring row alone: how much of that same row the candidates that never
   * reached the script account for. A breakdown of the row, not a row of its own, so it is
   * never added to `totalCostCents`.
   */
  droppedCalls?: number;
  droppedCostCents?: number;
}

export interface TtsCost {
  model: string | null;
  calls: number;
  characters: number;
  costCents: number;
}

export interface ResearchCost {
  calls: number;
  costCents: number;
}

export interface EpisodeCosts {
  score: LlmStageCost;
  dedup: LlmStageCost;
  /** The dedup stage's already-covered gate, costed apart from the call it relieves. */
  dedupGate: LlmStageCost;
  compose: LlmStageCost;
  recap: LlmStageCost;
  tts: TtsCost;
  research: ResearchCost;
  totalCostCents: number;
}

export interface Source {
  id: string;
  podcastId: string;
  type: string;
  url: string;
  pollIntervalMinutes: number;
  enabled: boolean;
  label: string | null;
  createdAt: string;
  articleCount: number;
  relevantArticleCount: number;
  postCount: number;
  host: string | null;
  hostSourceCount: number;
  hostBreakerOpen: boolean;
}

export interface ArticleSource {
  id: string;
  type: string;
  url: string;
  label: string | null;
}

export interface EpisodeArticle {
  id: number;
  title: string;
  url: string;
  author: string | null;
  publishedAt: string | null;
  relevanceScore: number | null;
  summary: string | null;
  body: string | null;
  subtopic: string | null;
  source: ArticleSource;
  /** Posts this article was aggregated from; 1 when it is not an aggregate. */
  postCount: number;
}

/** One post behind an aggregated article, shown when a thread card is expanded. */
export interface ArticlePost {
  id: number;
  title: string;
  body: string;
  url: string;
  publishedAt: string | null;
}

export interface UpcomingArticlesResponse {
  articles: EpisodeArticle[];
  articleCount: number;
  postCount: number;
  /** What has already been spent scoring the articles standing for the next episode. */
  scoring?: LlmStageCost;
}

export interface PodcastDefaults {
  llmModels: Record<string, ModelReference>;
  availableModels: Record<string, AvailableModel[]>;
  maxLlmCostCents: number;
  targetWords: number;
  fullBodyThreshold: number;
  maxArticleAgeDays: number;
}

export interface PreviewResponse {
  scriptText: string;
  style: string;
  articleIds: number[];
}

export interface PreviewAudioEstimate {
  characters: number;
  costCents: number | null;
}

export interface EpisodePublication {
  id: number;
  episodeId: number;
  target: string;
  status: string;
  externalId: string | null;
  externalUrl: string | null;
  errorMessage: string | null;
  publishedAt: string | null;
  createdAt: string;
}

/**
 * Standard envelope returned by paginated list endpoints (`page` is 0-indexed).
 * Mirrors backend `PagedResponse<T>`.
 */
export interface PagedResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

/** Lightweight episode reference embedded in podcast-level publication rows. */
export interface PublicationEpisodeRef {
  id: number;
  generatedAt: string;
  status: string;
}

export interface PodcastPublicationRow {
  publication: EpisodePublication;
  episode: PublicationEpisodeRef;
}

/**
 * A judged attention score. Mirrors backend `EpisodeScore`.
 *
 * `scorerVersion` and `judgeModel` are part of the row's identity: rows produced by different
 * scorers describe different quantities and are reported separately rather than averaged.
 */
export interface EpisodeScore {
  id: number;
  episodeId: number;
  scorerVersion: number;
  judgeModel: string;
  scoredAt: string;
  overall: number;
  cliffhangerScore: number;
  humorScore: number;
  teaserScore: number;
  promises: number;
  deferredPromises: number;
  unpaidPromises: number;
  medianDeferralTurns: number | null;
  humorBeats: number;
  humorSpeakerBalance: number;
  humorReactionRatio: number;
  teaserTopics: number;
  anchorsJson: string;
  inputTokens: number;
  outputTokens: number;
  costCents: number | null;
}

/**
 * The judge's raw answer, parsed out of `EpisodeScore.anchorsJson`. Mirrors backend
 * `ScriptJudgeAnchors`. Turn numbers are 0-based indices into the script's turn sequence.
 */
export interface ScriptJudgeAnchors {
  promises: PromiseAnchor[];
  humorBeats: HumorAnchor[];
  teaserTopics: string[];
}

/** A null `payoffTurn` is a finding, not a gap: the promise was never paid off. */
export interface PromiseAnchor {
  promiseTurn: number;
  payoffTurn: number | null;
}

export interface HumorAnchor {
  turn: number;
  role: string;
  reactsToPrevious: boolean;
}

export interface RoleMetrics {
  role: string;
  turns: number;
  words: number;
  wordShare: number;
}

/** `turnsOverSentenceCap` counts turns longer than the compose prompt allows. */
export interface TurnLengthMetrics {
  medianWords: number;
  maxWords: number;
  turnsOverSentenceCap: number;
}

/** A turn shaped like a token of listening. A shape match, not a defect. */
export interface BackchannelCandidate {
  turnIndex: number;
  role: string;
  words: number;
  text: string;
}

export interface SameSpeakerRun {
  role: string;
  turnIndexes: number[];
}

/** Mirrors backend `ScriptMetricsResult`. */
export interface ScriptMetrics {
  turnCount: number;
  totalWords: number;
  roles: RoleMetrics[];
  turnLengthByRole: Record<string, TurnLengthMetrics>;
  /** Laugh tags per role: a proxy for humor distribution, never a humor count. */
  laughTagsByRole: Record<string, number>;
  backchannelCandidates: BackchannelCandidate[];
  sameSpeakerRuns: SameSpeakerRun[];
}

export interface EpisodeMetricsResponse {
  episodeId: number;
  generatedAt: string;
  durationSeconds: number | null;
  metrics: ScriptMetrics;
}

/**
 * The conditions one evaluation run was composed under. Mirrors backend `EvaluationRun`.
 * Written only for a run that bypassed the LLM cache, so most episodes have none.
 */
export interface EvaluationRun {
  id: number;
  episodeId: number;
  podcastId: string;
  ranAt: string;
  promptHash: string;
  varietySelection: string;
  composeModel: string;
  temperature: number;
  cacheBypassed: boolean;
  cacheHit: boolean;
  toolsFiredJson: string;
}

/**
 * Latency percentiles for one pipeline stage, in milliseconds. Mirrors backend
 * `StageLatencyResponse`.
 *
 * The percentiles are null when the stage issued no qualifying requests; `samples` says how many
 * requests they rest on, which for a single episode is usually a handful.
 */
export interface StageLatency {
  stage: string;
  samples: number;
  p50Ms: number | null;
  p90Ms: number | null;
  p95Ms: number | null;
  p99Ms: number | null;
  timeoutMs: number;
}

/**
 * Per-request LLM latency, either over a rolling window across all episodes or for one episode.
 * Mirrors backend `LlmCallLatencyResponse`.
 *
 * `since` is the start of the window, and is null for an episode: an episode is a bounded set of
 * requests rather than a period.
 */
export interface LlmCallLatencyResponse {
  since: string | null;
  stages: StageLatency[];
}

/** One upstream endpoint OpenRouter tried for a request. Mirrors backend `ProviderAttempt`. */
export interface ProviderAttempt {
  provider: string | null;
  status: number | null;
  latencyMs: number | null;
}

/**
 * OpenRouter's account of a request. Mirrors backend `GenerationStats`. `generationTimeMs` spans the
 * whole request and `firstContentMs` is the time until the first answer token, after reasoning.
 */
export interface GenerationStats {
  firstContentMs: number | null;
  generationTimeMs: number | null;
  nativeCompletionTokens: number | null;
  nativeReasoningTokens: number | null;
  finishReason: string | null;
  servedProvider: string | null;
  attempts: ProviderAttempt[];
}

/**
 * A request's time split into phases. Mirrors backend `RequestPhases`: startup until the provider
 * began responding (failed attempts included), reasoning until the first answer token, then writing.
 */
export interface RequestPhases {
  startupMs: number | null;
  reasoningMs: number | null;
  writingMs: number | null;
  tokensPerSecond: number | null;
}

/** One recorded LLM request of an episode. Mirrors backend `LlmCallResponse`. */
export interface LlmCall {
  startedAt: string;
  stage: string;
  model: string;
  durationMs: number;
  outcome: string;
  cacheHit: boolean;
  servedProvider?: string | null;
  reasoningTokens?: number | null;
  /** Null until fetched shortly after the request, and for non-OpenRouter, cached and failed requests. */
  generationStats?: GenerationStats | null;
  phases?: RequestPhases | null;
}

/**
 * One episode's individual LLM requests. Mirrors backend `EpisodeLlmCallsResponse`.
 *
 * `predatesAttribution` is true for an episode generated before requests recorded which episode
 * they belonged to. Such an episode has no requests and never will, which is a fact about the
 * records rather than about the episode, and an empty list alone cannot say which of the two it is.
 */
export interface EpisodeLlmCallsResponse {
  episodeId: number;
  predatesAttribution: boolean;
  requests: LlmCall[];
}

/** One recorded web-search result of a focus episode, with the query that found it. */
export interface ResearchSource {
  query: string;
  title: string;
  url: string;
}
