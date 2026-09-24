"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { AudioLines, ChevronDown, ChevronRight, ExternalLink, Loader2, Sparkles, Volume2, X } from "lucide-react";
import { CronExpressionParser } from "cron-parser";
import { useUser } from "@/lib/user-context";
import { ArticleCard, getSourceDisplayName } from "@/components/article-card";
import type { EpisodeArticle, LlmStageCost, Podcast, PreviewAudioEstimate, PreviewResponse, UpcomingArticlesResponse } from "@/lib/types";
import { UpcomingCostsTab } from "@/components/upcoming-costs-tab";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { ScriptContent } from "@/components/script-viewer";
import { useTabParam } from "@/hooks/use-tab-param";

const WORDS_PER_MINUTE = 150;
const TABS = ["articles", "script", "costs"] as const;
const EPISODE_KINDS = [
  { value: "regular", label: "Regular" },
  { value: "focus", label: "Focus" },
] as const;

function formatCents(costCents: number | null): string {
  if (costCents === null) return "an unknown amount";
  return `$${(costCents / 100).toFixed(2)}`;
}

export default function UpcomingPage() {
  const params = useParams<{ podcastId: string }>();
  const { selectedUser, loading: userLoading } = useUser();
  const router = useRouter();
  const [podcast, setPodcast] = useState<Podcast | null>(null);
  const [articles, setArticles] = useState<EpisodeArticle[]>([]);
  const [articleCount, setArticleCount] = useState(0);
  const [postCount, setPostCount] = useState(0);
  const [scoring, setScoring] = useState<LlmStageCost | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewStage, setPreviewStage] = useState<string | null>(null);
  const [generateLoading, setGenerateLoading] = useState(false);
  const [episodeKind, setEpisodeKind] = useState<"regular" | "focus">("regular");
  const [focus, setFocus] = useState("");
  const [confirmGenerate, setConfirmGenerate] = useState(false);
  const [sampleLoading, setSampleLoading] = useState(false);
  const [fullAudioLoading, setFullAudioLoading] = useState(false);
  const [fullAudioProgress, setFullAudioProgress] = useState<string | null>(null);
  const [audioEstimate, setAudioEstimate] = useState<PreviewAudioEstimate | null>(null);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // A sample plays from a blob URL, which stays allocated until it is explicitly revoked.
  const blobUrlRef = useRef<string | null>(null);
  const [currentTab, setTab] = useTabParam("articles", TABS);

  useEffect(() => {
    if (!selectedUser) return;
    setLoading(true);
    Promise.all([
      fetch(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}`).then((res) => res.json()),
      fetch(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}/upcoming-articles`)
        .then((res) => (res.ok ? res.json() : { articles: [], articleCount: 0, postCount: 0 }))
        .catch(() => ({ articles: [], articleCount: 0, postCount: 0 })),
    ])
      .then(([podcastData, upcomingData]: [Podcast, UpcomingArticlesResponse]) => {
        setPodcast(podcastData);
        setArticles(upcomingData.articles);
        setArticleCount(upcomingData.articleCount);
        setPostCount(upcomingData.postCount);
        setScoring(upcomingData.scoring);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [selectedUser, params.podcastId]);

  useEffect(() => () => {
    if (blobUrlRef.current) URL.revokeObjectURL(blobUrlRef.current);
  }, []);

  async function handlePreview() {
    if (!selectedUser) return;
    setPreviewLoading(true);
    setPreviewStage(null);
    setError(null);
    try {
      const res = await fetch(
        `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/preview`,
        { headers: { Accept: "text/event-stream" } }
      );
      if (!res.ok) {
        setError("Failed to generate preview");
        setPreviewLoading(false);
        return;
      }
      const reader = res.body?.getReader();
      if (!reader) {
        setError("Failed to read preview stream");
        setPreviewLoading(false);
        return;
      }
      const decoder = new TextDecoder();
      let buffer = "";
      // Kept across reads: an SSE event arrives as "event: <name>" followed by "data: <json>",
      // and a chunk boundary can fall between the two lines. Resetting per read would drop the
      // name and with it every event that happened to be split.
      let eventName = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";
        for (const line of lines) {
          if (line.startsWith("event:")) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith("data:")) {
            const dataStr = line.slice(5).trim();
            if (!dataStr) continue;
            try {
              const data = JSON.parse(dataStr);
              if (eventName === "progress") {
                const { stage, articleCount, postCount, scoredCount } = data;
                if (stage === "aggregating") {
                  setPreviewStage(`Aggregating ${postCount ?? ""} posts...`);
                } else if (stage === "scoring") {
                  // Scoring re-emits per article, so show progress rather than a static count.
                  setPreviewStage(
                    scoredCount != null && articleCount != null
                      ? `Scoring articles... ${scoredCount}/${articleCount}`
                      : `Scoring ${articleCount ?? ""} articles...`
                  );
                } else if (stage === "deduplicating") {
                  setPreviewStage(`Deduplicating ${articleCount ?? ""} articles...`);
                } else if (stage === "composing") {
                  setPreviewStage(`Composing script from ${articleCount ?? ""} articles...`);
                }
              } else if (eventName === "result") {
                if (data.scriptText) {
                  setPreview(data);
                } else {
                  setError(data.message || "No content available for preview");
                }
                setPreviewLoading(false);
                setPreviewStage(null);
              } else if (eventName === "error") {
                setError(data.message || "Preview generation failed");
                setPreviewLoading(false);
                setPreviewStage(null);
              }
            } catch {
              // ignore non-JSON data lines
            }
            eventName = "";
          }
        }
      }
      setPreviewLoading(false);
      setPreviewStage(null);
    } catch {
      setError("Failed to generate preview");
      setPreviewLoading(false);
      setPreviewStage(null);
    }
  }

  function showAudio(url: string, isBlob: boolean) {
    if (blobUrlRef.current) URL.revokeObjectURL(blobUrlRef.current);
    blobUrlRef.current = isBlob ? url : null;
    setAudioUrl(url);
  }

  const previewAudioBase = selectedUser
    ? `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/preview/audio`
    : null;

  async function handlePlaySample() {
    if (!previewAudioBase || !preview) return;
    setSampleLoading(true);
    setError(null);
    try {
      const res = await fetch(`${previewAudioBase}/sample`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ scriptText: preview.scriptText }),
      });
      if (!res.ok) {
        setError("Failed to generate the audio sample");
        return;
      }
      showAudio(URL.createObjectURL(await res.blob()), true);
    } catch {
      setError("Failed to generate the audio sample");
    } finally {
      setSampleLoading(false);
    }
  }

  async function handleEstimateFullAudio() {
    if (!previewAudioBase || !preview) return;
    setError(null);
    try {
      const res = await fetch(`${previewAudioBase}/estimate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ scriptText: preview.scriptText }),
      });
      if (!res.ok) {
        setError("Failed to estimate the audio cost");
        return;
      }
      setAudioEstimate(await res.json());
    } catch {
      setError("Failed to estimate the audio cost");
    }
  }

  async function handleGenerateFullAudio() {
    if (!previewAudioBase || !preview) return;
    setFullAudioLoading(true);
    setFullAudioProgress(null);
    setError(null);
    try {
      const res = await fetch(previewAudioBase, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ scriptText: preview.scriptText }),
      });
      if (!res.ok) {
        setError("Failed to start audio generation");
        setFullAudioLoading(false);
        return;
      }
      const reader = res.body?.getReader();
      if (!reader) {
        setError("Failed to read the audio stream");
        setFullAudioLoading(false);
        return;
      }
      const decoder = new TextDecoder();
      let buffer = "";
      // Kept across reads: an SSE event arrives as "event: <name>" followed by "data: <json>",
      // and a chunk boundary can fall between the two lines. Resetting per read would drop the
      // name and with it every event that happened to be split.
      let eventName = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";
        for (const line of lines) {
          if (line.startsWith("event:")) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith("data:")) {
            const dataStr = line.slice(5).trim();
            if (!dataStr) continue;
            try {
              const data = JSON.parse(dataStr);
              if (eventName === "progress" && data.stage === "synthesizing") {
                setFullAudioProgress(`Synthesizing ${data.chunk}/${data.total}`);
              } else if (eventName === "result") {
                showAudio(`${previewAudioBase}/${data.audioId}`, false);
                setFullAudioLoading(false);
                setFullAudioProgress(null);
              } else if (eventName === "error") {
                setError(data.message || "Audio generation failed");
                setFullAudioLoading(false);
                setFullAudioProgress(null);
              }
            } catch {
              // ignore non-JSON data lines
            }
            eventName = "";
          }
        }
      }
      setFullAudioLoading(false);
      setFullAudioProgress(null);
    } catch {
      setError("Failed to generate the audio");
      setFullAudioLoading(false);
      setFullAudioProgress(null);
    }
  }

  async function handleGenerate() {
    if (!selectedUser) return;
    setGenerateLoading(true);
    setError(null);
    try {
      // Without a focus the request is sent exactly as before, with no body.
      const trimmedFocus = episodeKind === "focus" ? focus.trim() : "";
      const res = await fetch(
        `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/generate`,
        trimmedFocus
          ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ focus: trimmedFocus }) }
          : { method: "POST" }
      );
      const data = await res.json();
      if (data.episodeId) {
        router.push(`/podcasts/${params.podcastId}/episodes/${data.episodeId}`);
      } else {
        setError(data.message || data);
      }
    } catch {
      setError("Failed to generate episode");
    } finally {
      setGenerateLoading(false);
    }
  }

  const grouped = articles.reduce<Record<string, { displayName: string; articles: EpisodeArticle[] }>>((acc, article) => {
    const key = article.source.id;
    if (!acc[key]) {
      acc[key] = { displayName: getSourceDisplayName(article.source), articles: [] };
    }
    acc[key].articles.push(article);
    return acc;
  }, {});

  const sortedGroups = Object.entries(grouped).sort(
    ([, a], [, b]) => b.articles.length - a.articles.length
  );

  const sourceCount = sortedGroups.length;

  const nextGenerationDate = useMemo(() => {
    if (!podcast?.cron) return null;
    try {
      const expr = CronExpressionParser.parse(podcast.cron, { tz: 'UTC' });
      return expr.next().toDate();
    } catch {
      return null;
    }
  }, [podcast?.cron]);

  const { wordCount, estimatedMinutes } = useMemo(() => {
    if (!preview) return { wordCount: 0, estimatedMinutes: 0 };
    const plainText = preview.scriptText.replace(/<\/?[^>]+>/g, " ");
    const words = plainText.split(/\s+/).filter(Boolean).length;
    return { wordCount: words, estimatedMinutes: Math.round(words / WORDS_PER_MINUTE) };
  }, [preview]);

  function toggleGroup(sourceId: string) {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(sourceId)) next.delete(sourceId);
      else next.add(sourceId);
      return next;
    });
  }

  if (userLoading || loading) {
    return <p className="text-muted-foreground">Loading...</p>;
  }

  if (!selectedUser || !podcast) {
    return <p className="text-muted-foreground">Podcast not found.</p>;
  }

  return (
    <div>
      <div className="mb-4">
        <Link href={`/podcasts/${params.podcastId}`} className="text-sm text-muted-foreground hover:underline">
          &larr; Back to Episodes
        </Link>
      </div>

      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">Upcoming Episode</h2>
          <p className="text-sm text-muted-foreground">
            {articleCount > 0 && postCount > articleCount ? (
              <>{articleCount} article{articleCount !== 1 ? "s" : ""} consisting of {postCount} post{postCount !== 1 ? "s" : ""}</>
            ) : postCount > 0 && articleCount === 0 ? (
              <>{postCount} post{postCount !== 1 ? "s" : ""}</>
            ) : (
              <>{articles.length} article{articles.length !== 1 ? "s" : ""}</>
            )}
            {" "}from {sourceCount} source{sourceCount !== 1 ? "s" : ""}
            {nextGenerationDate && (
              <> &middot; Will be generated {nextGenerationDate.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" })} at {nextGenerationDate.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}</>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            onClick={() => setConfirmGenerate(true)}
            disabled={previewLoading || generateLoading || articles.length === 0}
          >
            {generateLoading ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
            Generate Episode
          </Button>
        </div>
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <Tabs value={currentTab} onValueChange={(v) => setTab(v as typeof TABS[number])}>
        <TabsList>
          <TabsTrigger value="articles">
            Articles ({articles.length})
          </TabsTrigger>
          <TabsTrigger value="script">
            Script {preview && `(${wordCount.toLocaleString()} words)`}
          </TabsTrigger>
          <TabsTrigger value="costs">
            Costs
          </TabsTrigger>
        </TabsList>

        <TabsContent value="articles">
          <div className="mt-4">
            {articles.length === 0 ? (
              <p className="text-muted-foreground">No content has been collected yet.</p>
            ) : (
              <div className="space-y-4">
                {sortedGroups.map(([sourceId, group]) => {
                  const isCollapsed = collapsedGroups.has(sourceId);
                  return (
                    <div key={sourceId}>
                      <button
                        onClick={() => toggleGroup(sourceId)}
                        className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm font-semibold hover:bg-muted"
                      >
                        {isCollapsed ? (
                          <ChevronRight className="size-4 shrink-0" />
                        ) : (
                          <ChevronDown className="size-4 shrink-0" />
                        )}
                        <span>{group.displayName}</span>
                        <Badge variant="secondary" className="text-[10px] px-1.5 py-px">
                          {group.articles.length}
                        </Badge>
                      </button>
                      {!isCollapsed && (
                        <div className="ml-6 mt-2 space-y-2">
                          {group.articles.map((article) => (
                            <ArticleCard key={article.id} article={article} userId={selectedUser.id} podcastId={params.podcastId} />
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </TabsContent>

        <TabsContent value="script">
          <div className="mt-4">
            {preview ? (
              <div>
                <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm text-muted-foreground">
                    {preview.articleIds.length} article{preview.articleIds.length !== 1 ? "s" : ""} &middot; {wordCount.toLocaleString()} words &middot; ~{estimatedMinutes} min
                  </p>
                  <div className="flex items-center gap-2">
                    {fullAudioProgress && (
                      <span className="text-sm text-muted-foreground">{fullAudioProgress}</span>
                    )}
                    <Button size="sm" onClick={handlePlaySample} disabled={sampleLoading || fullAudioLoading}>
                      {sampleLoading ? <Loader2 className="size-4 animate-spin" /> : <Volume2 className="size-4" />}
                      Play Sample
                    </Button>
                    <Button size="sm" onClick={handleEstimateFullAudio} disabled={sampleLoading || fullAudioLoading}>
                      {fullAudioLoading ? <Loader2 className="size-4 animate-spin" /> : <AudioLines className="size-4" />}
                      Generate Full Audio
                    </Button>
                  </div>
                </div>
                {audioUrl && (
                  <audio key={audioUrl} controls autoPlay src={audioUrl} className="mb-4 w-full" />
                )}
                <ScriptContent
                  scriptText={preview.scriptText}
                  style={preview.style}
                  speakerNames={podcast.speakerNames}
                />
              </div>
            ) : (
              <div className="flex flex-col items-center gap-4 py-12">
                {previewLoading && previewStage ? (
                  <p className="text-sm text-muted-foreground">{previewStage}</p>
                ) : (
                  <p className="text-muted-foreground">No script preview generated yet.</p>
                )}
                <Button
                  variant="outline"
                  onClick={handlePreview}
                  disabled={previewLoading || articles.length === 0}
                >
                  {previewLoading && <Loader2 className="size-4 animate-spin" />}
                  Preview Script
                </Button>
              </div>
            )}
          </div>
        </TabsContent>

        <TabsContent value="costs">
          <div className="mt-4">
            <UpcomingCostsTab scoring={scoring} />
          </div>
        </TabsContent>
      </Tabs>

      <AlertDialog open={confirmGenerate} onOpenChange={setConfirmGenerate}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Generate an episode</AlertDialogTitle>
            <AlertDialogDescription>
              Choose what kind of episode to generate from the {articles.length} upcoming
              article{articles.length !== 1 ? "s" : ""}.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <div role="radiogroup" aria-label="Episode kind" className="grid grid-cols-2 gap-1 rounded-lg bg-muted p-1">
            {EPISODE_KINDS.map((kind) => (
              <button
                key={kind.value}
                type="button"
                role="radio"
                aria-checked={episodeKind === kind.value}
                onClick={() => setEpisodeKind(kind.value)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  episodeKind === kind.value
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {kind.label}
              </button>
            ))}
          </div>

          {episodeKind === "regular" ? (
            <div className="space-y-2 text-sm text-muted-foreground">
              <p>The next regular episode, generated now instead of at its scheduled time.</p>
              <ul className="list-disc space-y-1 pl-5">
                <li>
                  <span className="font-medium text-foreground">Articles:</span> the upcoming articles scoring{" "}
                  {podcast.relevanceThreshold} or higher against the podcast&rsquo;s topic, grouped by topic with
                  duplicates merged. They are marked as used, and the schedule moves on from this episode.
                </li>
                <li>
                  <span className="font-medium text-foreground">Research:</span>{" "}
                  {podcast.deepDiveEnabled
                    ? "deep-dive research is on, so web search runs up to 3 queries on the most newsworthy stories."
                    : "deep-dive research is off, so no web search runs."}{" "}
                  Past episodes on the same stories are looked up either way.
                </li>
                <li>
                  <span className="font-medium text-foreground">Intro and sign-off:</span> the podcast&rsquo;s usual
                  format.
                </li>
                <li>
                  {podcast.requireReview
                    ? "It stops for review before audio."
                    : "It goes straight on to audio, without a review step."}
                </li>
              </ul>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="space-y-2 text-sm text-muted-foreground">
                <p>A one-off special episode about a single subject, on top of the regular schedule.</p>
                <ul className="list-disc space-y-1 pl-5">
                  <li>
                    <span className="font-medium text-foreground">Articles:</span> every article in the current
                    window is scored against the focus instead of the podcast topic, including articles a regular
                    episode already used (pure retweets excluded). Only those scoring {podcast.relevanceThreshold} or
                    higher are kept; if none do, generation stops with an error.
                  </li>
                  <li>
                    <span className="font-medium text-foreground">Research:</span> web search always runs, with up
                    to 5 queries spread over the subject&rsquo;s angles (the announcement, reactions, numbers or
                    benchmarks, what it means for the field), plus a look at past episodes that covered it.
                  </li>
                  <li>
                    <span className="font-medium text-foreground">Intro and closing:</span> the intro announces it
                    as an extra, special episode on this subject, and the closing says the
                    regular episode follows as usual. The feed title reads &ldquo;Special: {focus.trim() || "<focus>"}&rdquo;.
                  </li>
                  <li>
                    It always stops for review before audio. It does not use up any articles or change the
                    schedule, and the next regular episode picks the subject up as a follow-up.
                  </li>
                </ul>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="focus">Focus</Label>
                <Input
                  id="focus"
                  autoFocus
                  placeholder="e.g. Claude Opus 5.5 release"
                  value={focus}
                  onChange={(e) => setFocus(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && focus.trim()) {
                      setConfirmGenerate(false);
                      handleGenerate();
                    }
                  }}
                />
              </div>
            </div>
          )}

          <AlertDialogFooter>
            <AlertDialogCancel size="sm">
              <X className="size-4" />
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              size="sm"
              disabled={episodeKind === "focus" && !focus.trim()}
              onClick={() => {
                setConfirmGenerate(false);
                handleGenerate();
              }}
            >
              <Sparkles className="size-4" />
              {episodeKind === "focus" ? "Generate Focus Episode" : "Generate Regular Episode"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={audioEstimate !== null} onOpenChange={(open) => { if (!open) setAudioEstimate(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Generate full audio?</AlertDialogTitle>
            <AlertDialogDescription>
              This synthesises the whole script: {audioEstimate?.characters.toLocaleString()} characters,
              costing about {formatCents(audioEstimate?.costCents ?? null)}. It takes a few minutes and
              creates no episode.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel size="sm">
              <X className="size-4" />
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              size="sm"
              onClick={() => {
                setAudioEstimate(null);
                handleGenerateFullAudio();
              }}
            >
              <AudioLines className="size-4" />
              Generate Full Audio
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
