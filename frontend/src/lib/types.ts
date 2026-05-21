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
  lastGeneratedAt?: string;
}

export interface Episode {
  id: number;
  podcastId: string;
  generatedAt: string;
  scriptText: string;
  status: string;
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
