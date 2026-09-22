"use client";

import { Fragment, useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { Panel } from "@/components/section";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ArticleCard, getSourceDisplayName } from "@/components/article-card";
import type { EpisodeArticle } from "@/lib/types";

interface ArticlesTabProps {
  userId: string;
  podcastId: string;
  episodeId: number;
  onCountLoaded?: (count: number) => void;
}

/** One source with the articles this episode drew from it. */
interface SourceGroup {
  sourceId: string;
  displayName: string;
  type: string;
  articles: EpisodeArticle[];
  /** The best relevance any of its articles scored, so a source can be read against the others. */
  topRelevance: number | null;
}

function groupBySource(articles: EpisodeArticle[]): SourceGroup[] {
  const groups = new Map<string, SourceGroup>();
  for (const article of articles) {
    const key = article.source.id;
    let group = groups.get(key);
    if (!group) {
      group = {
        sourceId: key,
        displayName: getSourceDisplayName(article.source),
        type: article.source.type,
        articles: [],
        topRelevance: null,
      };
      groups.set(key, group);
    }
    group.articles.push(article);
    if (article.relevanceScore !== null) {
      group.topRelevance = Math.max(group.topRelevance ?? 0, article.relevanceScore);
    }
  }
  return [...groups.values()].sort((a, b) => b.articles.length - a.articles.length);
}

export function ArticlesTab({ userId, podcastId, episodeId, onCountLoaded }: ArticlesTabProps) {
  const [articles, setArticles] = useState<EpisodeArticle[]>([]);
  const [loading, setLoading] = useState(true);
  // Rows start collapsed: an episode links dozens of articles across many sources, so the tab
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

  const groups = groupBySource(articles);

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
    <Panel>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-8" />
            <TableHead>Source</TableHead>
            <TableHead className="w-24">Type</TableHead>
            <TableHead className="w-24 text-right">Articles</TableHead>
            <TableHead className="w-28 text-right">Top score</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {groups.map((group) => {
            const isExpanded = expandedGroups.has(group.sourceId);
            return (
              <Fragment key={group.sourceId}>
                <TableRow
                  // The whole row toggles, so the chevron marks the state rather than being the
                  // only thing that can be hit.
                  className={`cursor-pointer ${isExpanded ? "border-b-0" : ""}`}
                  onClick={() => toggleGroup(group.sourceId)}
                >
                  <TableCell className="text-muted-foreground">
                    {isExpanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                  </TableCell>
                  <TableCell className="font-medium">{group.displayName}</TableCell>
                  <TableCell className="text-xs text-muted-foreground">{group.type}</TableCell>
                  <TableCell className="text-right">
                    <Badge variant="secondary" className="text-[10px] px-1.5 py-px">
                      {group.articles.length}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right tabular-nums text-sm">
                    {group.topRelevance ?? "—"}
                  </TableCell>
                </TableRow>
                {isExpanded && (
                  <TableRow className="hover:bg-transparent">
                    <TableCell />
                    {/* max-w-0 keeps the cell from growing to fit its content, which is what lets
                        the cards inside wrap instead of widening the whole table. */}
                    <TableCell colSpan={4} className="max-w-0 overflow-hidden pt-1.5 pb-3">
                      <div className="space-y-2">
                        {group.articles.map((article) => (
                          <ArticleCard
                            key={article.id}
                            article={article}
                            userId={userId}
                            podcastId={podcastId}
                          />
                        ))}
                      </div>
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            );
          })}
        </TableBody>
      </Table>
    </Panel>
  );
}
