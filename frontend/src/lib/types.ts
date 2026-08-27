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
}

export interface UpcomingArticlesResponse {
  articles: EpisodeArticle[];
  articleCount: number;
  postCount: number;
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
