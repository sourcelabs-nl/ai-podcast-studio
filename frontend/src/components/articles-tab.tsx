"use client";

import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { ArticleCard, getSourceDisplayName } from "@/components/article-card";
import type { EpisodeArticle } from "@/lib/types";

interface ArticlesTabProps {
  userId: string;
  podcastId: string;
  episodeId: number;
  onCountLoaded?: (count: number) => void;
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
                  <ArticleCard key={article.id} article={article} userId={userId} podcastId={podcastId} />
                ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
