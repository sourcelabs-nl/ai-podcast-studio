"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import cronstrue from "cronstrue";
import { CronExpressionParser } from "cron-parser";
import { Check, ChevronDown, ChevronRight, Clock, Headphones, Loader2, RefreshCw, Settings, Upload, Volume2, X } from "lucide-react";
import { useUser } from "@/lib/user-context";
import { useEventStream } from "@/lib/event-context";
import type { Podcast, Episode, EpisodePublication, PagedResponse } from "@/lib/types";
import { Paginator } from "@/components/paginator";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
import { PublishWizard, TARGETS } from "@/components/publish-wizard";
import { PublicationsTab } from "@/components/publications-tab";
import { SourcesTab } from "@/components/sources-tab";
import { useTabParam } from "@/hooks/use-tab-param";

const TABS = ["episodes", "publications", "sources"] as const;

const STATUSES = [
  "GENERATING",
  "GENERATING_AUDIO",
  "GENERATED",
  "PENDING_REVIEW",
  "APPROVED",
  "FAILED",
  "DISCARDED",
] as const;

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  GENERATED: "outline",
  PENDING_REVIEW: "default",
  APPROVED: "default",
  FAILED: "default",
  DISCARDED: "secondary",
};

type PendingAction = {
  title: string;
  description: string;
  actionLabel: string;
  destructive?: boolean;
  onConfirm: () => Promise<void>;
};

const DEFAULT_PAGE_SIZE = 20;

export default function EpisodesPage() {
  const params = useParams<{ podcastId: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const { selectedUser, loading: userLoading } = useUser();
  const [podcast, setPodcast] = useState<Podcast | null>(null);
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [episodesTotal, setEpisodesTotal] = useState(0);
  const [episodesTotalPages, setEpisodesTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [publishEpisode, setPublishEpisode] = useState<Episode | null>(null);
  const [publishedEpisodeIds, setPublishedEpisodeIds] = useState<Set<number>>(new Set());
  const [fullyPublishedEpisodeIds, setFullyPublishedEpisodeIds] = useState<Set<number>>(new Set());
  const [refreshKey, setRefreshKey] = useState(0);
  const [upcomingCount, setUpcomingCount] = useState<number>(0);
  const [upcomingPostCount, setUpcomingPostCount] = useState<number>(0);
  const [countdown, setCountdown] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [currentTab, setTab] = useTabParam("episodes", TABS);

  // URL-synced pagination + multi-select status filter
  const page = Math.max(0, parseInt(searchParams.get("page") ?? "0", 10) || 0);
  const pageSize = (() => {
    const raw = parseInt(searchParams.get("pageSize") ?? `${DEFAULT_PAGE_SIZE}`, 10);
    return raw > 0 && raw <= 200 ? raw : DEFAULT_PAGE_SIZE;
  })();
  const statusFilter = useMemo(() => new Set(searchParams.getAll("status")), [searchParams]);

  const updateQuery = useCallback((patch: Record<string, string | string[] | null>) => {
    const next = new URLSearchParams(searchParams.toString());
    for (const [k, v] of Object.entries(patch)) {
      next.delete(k);
      if (v === null) continue;
      if (Array.isArray(v)) v.forEach((x) => next.append(k, x));
      else next.set(k, v);
    }
    router.replace(`?${next.toString()}`, { scroll: false });
  }, [router, searchParams]);

  const toggleStatus = (status: string) => {
    const next = new Set(statusFilter);
    if (next.has(status)) next.delete(status); else next.add(status);
    updateQuery({ status: Array.from(next), page: "0" });
  };
  const clearStatuses = () => updateQuery({ status: null, page: "0" });

  const publishedDates = useMemo(() => {
    const dates = new Set<string>();
    for (const ep of episodes) {
      if (publishedEpisodeIds.has(ep.id)) {
        dates.add(new Date(ep.generatedAt).toLocaleDateString());
      }
    }
    return dates;
  }, [episodes, publishedEpisodeIds]);

  const fetchPublications = useCallback(
    (episodeList: Episode[]) => {
      if (!selectedUser || episodeList.length === 0) {
        setPublishedEpisodeIds(new Set());
        setFullyPublishedEpisodeIds(new Set());
        return;
      }
      Promise.all(
        episodeList.map((ep) =>
          fetch(
            `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/episodes/${ep.id}/publications`
          )
            .then((res) => (res.ok ? res.json() : []))
            .catch(() => [] as EpisodePublication[])
        )
      ).then((results) => {
        const published = new Set<number>();
        const fullyPublished = new Set<number>();
        const targetValues = TARGETS.map((t) => t.value);
        results.forEach((pubs: EpisodePublication[], i) => {
          const publishedTargets = new Set(
            pubs.filter((p) => p.status === "PUBLISHED").map((p) => p.target)
          );
          if (publishedTargets.size > 0) {
            published.add(episodeList[i].id);
          }
          if (targetValues.every((t) => publishedTargets.has(t))) {
            fullyPublished.add(episodeList[i].id);
          }
        });
        setPublishedEpisodeIds(published);
        setFullyPublishedEpisodeIds(fullyPublished);
      });
    },
    [selectedUser, params.podcastId]
  );

  const episodesQueryString = useMemo(() => {
    const qs = new URLSearchParams();
    qs.set("page", String(page));
    qs.set("pageSize", String(pageSize));
    statusFilter.forEach((s) => qs.append("status", s));
    return qs.toString();
  }, [page, pageSize, statusFilter]);

  const fetchEpisodes = useCallback(() => {
    if (!selectedUser) return;
    fetch(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}/episodes?${episodesQueryString}`)
      .then((res) => res.json())
      .then((data: PagedResponse<Episode>) => {
        setEpisodes(data.items);
        setEpisodesTotal(data.total);
        setEpisodesTotalPages(data.totalPages);
        fetchPublications(data.items);
      })
      .catch(() => setEpisodes([]));
  }, [selectedUser, params.podcastId, episodesQueryString, fetchPublications]);

  useEventStream(params.podcastId, useCallback((event: string, data: { data: Record<string, unknown> }) => {
    if (event === "episode.generating" || event === "episode.stage" || event === "pipeline.progress") {
      fetchEpisodes();
    } else {
      fetchEpisodes();
    }
  }, [fetchEpisodes]));

  useEffect(() => {
    if (!selectedUser) return;
    setLoading(true);
    Promise.all([
      fetch(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}`).then(
        (res) => res.json()
      ),
      fetch(
        `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/episodes?${episodesQueryString}`
      ).then((res) => res.json()),
      fetch(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}/upcoming-articles`)
        .then((res) => (res.ok ? res.json() : { articles: [], postCount: 0 }))
        .catch(() => ({ articles: [], postCount: 0 })),
    ])
      .then(([podcastData, episodeData, upcomingData]: [Podcast, PagedResponse<Episode>, { articleCount?: number; postCount?: number }]) => {
        setPodcast(podcastData);
        setEpisodes(episodeData.items);
        setEpisodesTotal(episodeData.total);
        setEpisodesTotalPages(episodeData.totalPages);
        setUpcomingCount(upcomingData.articleCount ?? 0);
        setUpcomingPostCount(upcomingData.postCount ?? 0);
        fetchPublications(episodeData.items);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [selectedUser, params.podcastId, episodesQueryString, fetchPublications]);

  async function doAction(episodeId: number, action: string) {
    if (!selectedUser) return;
    await fetch(
      `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/episodes/${episodeId}/${action}`,
      { method: "POST" }
    );
    await new Promise((r) => setTimeout(r, 500));
    fetchEpisodes();
  }

  function handlePublished() {
    fetchEpisodes();
    setRefreshKey((k) => k + 1);
  }

  const cronDescription = useMemo(() => {
    if (!podcast?.cron) return null;
    try {
      return cronstrue.toString(podcast.cron);
    } catch {
      return podcast.cron;
    }
  }, [podcast?.cron]);

  useEffect(() => {
    if (!podcast?.cron) {
      setCountdown(null);
      return;
    }
    function computeCountdown() {
      try {
        const expr = CronExpressionParser.parse(podcast!.cron, { tz: podcast!.timezone || 'UTC' });
        const next = expr.next().toDate();
        const diff = next.getTime() - Date.now();
        if (diff <= 0) { setCountdown(null); return; }
        const h = Math.floor(diff / 3_600_000);
        const m = Math.floor((diff % 3_600_000) / 60_000);
        const s = Math.floor((diff % 60_000) / 1_000);
        setCountdown(
          h > 0
            ? `${h}h ${String(m).padStart(2, "0")}m ${String(s).padStart(2, "0")}s`
            : m > 0
              ? `${m}m ${String(s).padStart(2, "0")}s`
              : `${s}s`
        );
      } catch {
        setCountdown(null);
      }
    }
    computeCountdown();
    const id = setInterval(computeCountdown, 1_000);
    return () => clearInterval(id);
  }, [podcast?.cron, podcast?.timezone]);

  if (userLoading || loading) {
    return <p className="text-muted-foreground">Loading...</p>;
  }

  if (!selectedUser || !podcast) {
    return <p className="text-muted-foreground">Podcast not found.</p>;
  }

  return (
    <div>
      <div className="mb-4">
        <Link href="/podcasts" className="text-sm text-muted-foreground hover:underline">
          &larr; Back to podcasts
        </Link>
      </div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-2xl font-bold">{podcast.name}</h2>
            <Badge>{podcast.style}</Badge>
          </div>
          <p className="text-sm text-muted-foreground">
            {podcast.topic}
            {podcast.cron && cronDescription && (
              <span> &middot; {cronDescription.toLowerCase()}{podcast.timezone && podcast.timezone !== "UTC" ? ` (${podcast.timezone})` : ""}</span>
            )}
          </p>
        </div>
        <Link href={`/podcasts/${params.podcastId}/settings`}>
          <Button size="icon-lg" title="Settings">
            <Settings className="size-4" />
          </Button>
        </Link>
      </div>

      <Link
        href={`/podcasts/${params.podcastId}/upcoming`}
        className="mb-6 flex items-center justify-between rounded-lg border border-border bg-muted/50 px-4 py-3 transition-colors hover:bg-muted"
      >
        <span className="text-sm font-medium">
          Next Episode {upcomingCount > 0
            ? <>&middot; {upcomingCount} article{upcomingCount !== 1 ? "s" : ""}{upcomingPostCount > upcomingCount ? ` / ${upcomingPostCount} posts` : ""} ready</>
            : <>&middot; no articles yet</>}
        </span>
        <div className="flex items-center gap-2">
          {countdown && (
            <span className="flex items-center gap-1 text-xs text-muted-foreground tabular-nums">
              <Clock className="size-3" />
              {countdown}
            </span>
          )}
          <ChevronRight className="size-4 text-muted-foreground" />
        </div>
      </Link>

      <Tabs value={currentTab} onValueChange={(v) => setTab(v as typeof TABS[number])}>
        <TabsList>
          <TabsTrigger value="episodes">Episodes</TabsTrigger>
          <TabsTrigger value="publications">Publications</TabsTrigger>
          <TabsTrigger value="sources">Sources</TabsTrigger>
        </TabsList>

        <TabsContent value="episodes">
          {episodes.length === 0 ? (
            <p className="text-muted-foreground">No episodes found.</p>
          ) : (
            <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">#</TableHead>
                  <TableHead className="w-24">Date</TableHead>
                  <TableHead className="w-12">Day</TableHead>
                  <TableHead className="w-32">
                    <DropdownMenu>
                      <DropdownMenuTrigger className="flex items-center gap-1 hover:text-foreground transition-colors">
                        Status{statusFilter.size > 0 && <span className="text-primary">({statusFilter.size})</span>}
                        <ChevronDown className="size-3.5" />
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="start">
                        <DropdownMenuCheckboxItem
                          checked={statusFilter.size === 0}
                          onCheckedChange={clearStatuses}
                        >
                          All statuses
                        </DropdownMenuCheckboxItem>
                        {STATUSES.map((status) => (
                          <DropdownMenuCheckboxItem
                            key={status}
                            checked={statusFilter.has(status)}
                            onCheckedChange={() => toggleStatus(status)}
                          >
                            {status.replace("_", " ").toLowerCase()}
                          </DropdownMenuCheckboxItem>
                        ))}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableHead>
                  <TableHead className="w-0">Script Model</TableHead>
                  <TableHead className="w-0">TTS Model</TableHead>
                  <TableHead>Cost</TableHead>
                  <TableHead className="text-right w-0 whitespace-nowrap">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {episodes.map((episode) => (
                  <TableRow
                    key={episode.id}
                    className={`cursor-pointer ${episode.status === "GENERATING" || episode.status === "GENERATING_AUDIO" ? "bg-primary/5" : ""}`}
                    onClick={() => router.push(`/podcasts/${params.podcastId}/episodes/${episode.id}`)}
                  >
                    <TableCell className="text-sm font-medium">{episode.status === "GENERATING" ? "—" : episode.id}</TableCell>
                    <TableCell className="text-sm">
                      {new Date(episode.generatedAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {new Date(episode.generatedAt).toLocaleDateString(undefined, { weekday: "short" })}
                    </TableCell>
                    <TableCell>
                      {episode.status === "GENERATING" || episode.status === "GENERATING_AUDIO" ? (
                        <span className="flex items-center gap-2 text-sm font-medium text-primary">
                          <Loader2 className="size-3.5 animate-spin" />
                          {episode.status === "GENERATING_AUDIO" ? "Generating audio..." : (
                            <>
                              {episode.pipelineStage === "aggregating" && "Aggregating..."}
                              {episode.pipelineStage === "scoring" && "Scoring..."}
                              {episode.pipelineStage === "deduplicating" && "Deduplicating..."}
                              {episode.pipelineStage === "composing" && "Composing..."}
                              {episode.pipelineStage === "tts" && "Generating audio..."}
                              {!episode.pipelineStage && "Starting..."}
                            </>
                          )}
                        </span>
                      ) : (
                        <div className="flex items-center gap-1.5">
                          <Badge variant={STATUS_VARIANT[episode.status] ?? "secondary"} className="text-[11px] px-1.5 py-px">
                            {episode.status.replace("_", " ")}
                          </Badge>
                          {publishedEpisodeIds.has(episode.id) && (
                            <Badge variant="default" className="text-[11px] px-1.5 py-px">
                              Published
                            </Badge>
                          )}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                      {episode.status === "GENERATING" || episode.status === "GENERATING_AUDIO" ? "—" : (episode.composeModel ?? "—")}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                      {episode.status === "GENERATING" || episode.status === "GENERATING_AUDIO" ? "—" : (episode.ttsModel ?? "—")}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {episode.status === "GENERATING" || episode.status === "GENERATING_AUDIO" ? "—" : (
                        ((episode.llmCostCents ?? 0) + (episode.ttsCostCents ?? 0)) > 0
                          ? `$${(((episode.llmCostCents ?? 0) + (episode.ttsCostCents ?? 0)) / 100).toFixed(2)}`
                          : "—"
                      )}
                    </TableCell>
                    <TableCell className="text-right h-12">
                      {episode.status === "GENERATING" || episode.status === "GENERATING_AUDIO" ? null : (
                      <div className="flex items-center justify-end gap-2" onClick={(e) => e.stopPropagation()}>
                        {episode.status === "PENDING_REVIEW" && (
                          <>
                            <Button
                              size="icon-lg"
                              title="Approve episode"
                              onClick={() => setPendingAction({
                                title: "Approve episode?",
                                description: "This will approve the script and start audio generation.",
                                actionLabel: "Approve",
                                onConfirm: () => doAction(episode.id, "approve"),
                              })}
                            >
                              <Check className="size-4" />
                            </Button>
                            {!publishedDates.has(new Date(episode.generatedAt).toLocaleDateString()) && (
                            <Button
                              size="icon-lg"
                              title="Regenerate episode"
                              onClick={() => setPendingAction({
                                title: "Regenerate episode?",
                                description: "This will re-compose the script from the same articles using the current podcast settings. A new episode will be created.",
                                actionLabel: "Regenerate",
                                onConfirm: () => doAction(episode.id, "regenerate"),
                              })}
                            >
                              <RefreshCw className="size-4" />
                            </Button>
                            )}
                            <Button
                              size="icon-lg"
                              variant="destructive"
                              title="Discard episode"
                              onClick={() => setPendingAction({
                                title: "Discard episode?",
                                description: "The episode will be discarded and articles will be reset for reprocessing.",
                                actionLabel: "Discard",
                                destructive: true,
                                onConfirm: () => doAction(episode.id, "discard"),
                              })}
                            >
                              <X className="size-4" />
                            </Button>
                          </>
                        )}
                        {episode.status === "GENERATED" && (
                          <>
                          {episode.audioFilePath && (
                          <Button
                            size="icon-lg"
                            title="Listen to episode"
                            onClick={() => window.open(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}/episodes/${episode.id}/audio`, "_blank")}
                          >
                            <Volume2 className="size-4" />
                          </Button>
                          )}
                          <Button
                            size="icon-lg"
                            title="Regenerate audio"
                            onClick={() => setPendingAction({
                              title: "Regenerate audio?",
                              description: "This will re-run TTS on the existing script. The previous audio file will be replaced.",
                              actionLabel: "Regenerate audio",
                              onConfirm: () => doAction(episode.id, "regenerate-audio"),
                            })}
                          >
                            <Headphones className="size-4" />
                          </Button>
                          {!fullyPublishedEpisodeIds.has(episode.id) && (
                          <Button
                            size="icon-lg"
                            title="Publish episode"
                            onClick={() => setPublishEpisode(episode)}
                          >
                            <Upload className="size-4" />
                          </Button>
                          )}
                          {!publishedEpisodeIds.has(episode.id) && (
                          <Button
                            size="icon-lg"
                            variant="destructive"
                            title="Discard episode"
                            onClick={() => setPendingAction({
                              title: "Discard episode?",
                              description: "The episode will be discarded.",
                              actionLabel: "Discard",
                              destructive: true,
                              onConfirm: () => doAction(episode.id, "discard"),
                            })}
                          >
                            <X className="size-4" />
                          </Button>
                          )}
                          </>
                        )}
                        {episode.status === "FAILED" && (
                          <Button
                            size="icon-lg"
                            title="Retry audio generation"
                            onClick={() => setPendingAction({
                              title: "Retry audio generation?",
                              description: "This will attempt to generate audio for the existing script.",
                              actionLabel: "Retry",
                              onConfirm: () => doAction(episode.id, "approve"),
                            })}
                          >
                            <Check className="size-4" />
                          </Button>
                        )}
                        {(episode.status === "DISCARDED" || episode.status === "FAILED") && !publishedDates.has(new Date(episode.generatedAt).toLocaleDateString()) && (
                          <Button
                            size="icon-lg"
                            title="Regenerate episode"
                            onClick={() => setPendingAction({
                              title: "Regenerate episode?",
                              description: "This will re-compose the script from the same articles using the current podcast settings. A new episode will be created.",
                              actionLabel: "Regenerate",
                              onConfirm: () => doAction(episode.id, "regenerate"),
                            })}
                          >
                            <RefreshCw className="size-4" />
                          </Button>
                        )}
                        {episode.status === "FAILED" && (
                          <Button
                            size="icon-lg"
                            variant="destructive"
                            title="Discard episode"
                            onClick={() => setPendingAction({
                              title: "Discard episode?",
                              description: "The episode will be discarded.",
                              actionLabel: "Discard",
                              destructive: true,
                              onConfirm: () => doAction(episode.id, "discard"),
                            })}
                          >
                            <X className="size-4" />
                          </Button>
                        )}
                        <Button
                          size="icon-lg"
                          title="View details"
                          onClick={() => router.push(`/podcasts/${params.podcastId}/episodes/${episode.id}`)}
                        >
                          <ChevronRight className="size-4" />
                        </Button>
                      </div>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Paginator
              page={page}
              pageSize={pageSize}
              total={episodesTotal}
              totalPages={episodesTotalPages}
              onPageChange={(p) => updateQuery({ page: String(p) })}
              onPageSizeChange={(s) => updateQuery({ pageSize: String(s), page: "0" })}
            />
            </>
          )}
        </TabsContent>

        <TabsContent value="publications">
          <div className="mt-4">
            <PublicationsTab
              userId={selectedUser.id}
              podcastId={params.podcastId}
              refreshKey={refreshKey}
              onRepublished={handlePublished}
            />
          </div>
        </TabsContent>

        <TabsContent value="sources">
          <div className="mt-4">
            <SourcesTab userId={selectedUser.id} podcastId={params.podcastId} />
          </div>
        </TabsContent>
      </Tabs>

      {publishEpisode && (
        <PublishWizard
          open={!!publishEpisode}
          onOpenChange={(open) => {
            if (!open) setPublishEpisode(null);
          }}
          episode={publishEpisode}
          podcastName={podcast.name}
          userId={selectedUser.id}
          podcastId={params.podcastId}
          onPublished={handlePublished}
        />
      )}

      <AlertDialog open={pendingAction !== null} onOpenChange={(open) => { if (!open) setPendingAction(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{pendingAction?.title}</AlertDialogTitle>
            <AlertDialogDescription>{pendingAction?.description}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className={pendingAction?.destructive ? "bg-destructive text-destructive-foreground hover:bg-destructive/90" : ""}
              onClick={async () => {
                const action = pendingAction;
                setPendingAction(null);
                await action?.onConfirm();
              }}
            >
              {pendingAction?.actionLabel}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
