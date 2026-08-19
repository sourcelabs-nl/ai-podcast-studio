"use client";

import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, ExternalLink } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { EpisodeArticle } from "@/lib/types";

interface ArticlesTabProps {
  userId: string;
  podcastId: string;
  episodeId: number;
  onCountLoaded?: (count: number) => void;
}

function getSourceDisplayName(source: EpisodeArticle["source"]): string {
  if (source.label) return source.label;
  try {
    const url = new URL(source.url);
    return url.hostname.replace(/^www\./, "");
  } catch {
    return source.url;
  }
}

function relevanceColor(score: number | null): string {
  if (score === null) return "bg-muted text-muted-foreground";
  if (score >= 7) return "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200";
  if (score >= 4) return "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200";
  return "bg-muted text-muted-foreground";
}

function ArticleCard({ article }: { article: EpisodeArticle }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="rounded-lg border border-border bg-card px-4 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h4 className="truncate text-sm font-medium">{article.title}</h4>
            {article.relevanceScore !== null && (
              <span
                className={`inline-flex shrink-0 items-center rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${relevanceColor(article.relevanceScore)}`}
              >
                {article.relevanceScore}
              </span>
            )}
            {article.subtopic && (
              <span className="inline-flex shrink-0 items-center rounded-full border border-border bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                {article.subtopic}
              </span>
            )}
          </div>
          {article.author && (
            <p className="mt-0.5 text-xs text-muted-foreground">{article.author}</p>
          )}
        </div>
        <a
          href={article.url}
          target="_blank"
          rel="noopener noreferrer"
          className="shrink-0 text-muted-foreground hover:text-foreground"
        >
          <ExternalLink className="size-4" />
        </a>
      </div>
      {(article.summary || article.body) && (
        <div className="mt-2">
          <p
            className={`text-sm text-muted-foreground ${!expanded ? "line-clamp-2" : ""}`}
            onClick={() => setExpanded(!expanded)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") setExpanded(!expanded); }}
          >
            {article.summary || article.body}
          </p>
        </div>
      )}
    </div>
  );
}

export function ArticlesTab({ userId, podcastId, episodeId, onCountLoaded }: ArticlesTabProps) {
  const [articles, setArticles] = useState<EpisodeArticle[]>([]);
  const [loading, setLoading] = useState(true);
  // Groups start collapsed: an episode links dozens of articles across many sources, so the tab
  // opens as a scannable list of sources with counts and the reader expands the one they want.
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  useEffect(() => {
    setLoading(true);
    fetch(`/api/users/${userId}/podcasts/${podcastId}/episodes/${episodeId}/articles`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data: EpisodeArticle[]) => {
        setArticles(data);
        onCountLoaded?.(data.length);
      })
      .catch(() => setArticles([]))
      .finally(() => setLoading(false));
  }, [userId, podcastId, episodeId, onCountLoaded]);

  if (loading) {
    return <p className="text-muted-foreground">Loading articles...</p>;
  }

  if (articles.length === 0) {
    return <p className="text-muted-foreground">No articles linked to this episode.</p>;
  }

  const grouped = articles.reduce<Record<string, { displayName: string; articles: EpisodeArticle[] }>>((acc, article) => {
    const key = article.source.id;
    if (!acc[key]) {
      acc[key] = {
        displayName: getSourceDisplayName(article.source),
        articles: [],
      };
    }
    acc[key].articles.push(article);
    return acc;
  }, {});

  const sortedGroups = Object.entries(grouped).sort(
    ([, a], [, b]) => b.articles.length - a.articles.length
  );

  function toggleGroup(sourceId: string) {
    setExpandedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(sourceId)) {
        next.delete(sourceId);
      } else {
        next.add(sourceId);
      }
      return next;
    });
  }

  return (
    <div className="space-y-4">
      {sortedGroups.map(([sourceId, group]) => {
        const isExpanded = expandedGroups.has(sourceId);
        return (
          <div key={sourceId}>
            <button
              onClick={() => toggleGroup(sourceId)}
              className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm font-semibold hover:bg-muted"
            >
              {isExpanded ? (
                <ChevronDown className="size-4 shrink-0" />
              ) : (
                <ChevronRight className="size-4 shrink-0" />
              )}
              <span>{group.displayName}</span>
              <Badge variant="secondary" className="text-[10px] px-1.5 py-px">
                {group.articles.length}
              </Badge>
            </button>
            {isExpanded && (
              <div className="ml-6 mt-2 space-y-2">
                {group.articles.map((article) => (
                  <ArticleCard key={article.id} article={article} />
                ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
