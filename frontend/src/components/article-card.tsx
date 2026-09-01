"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, ExternalLink, MessagesSquare } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { ArticlePost, EpisodeArticle } from "@/lib/types";

interface ArticleCardProps {
  article: EpisodeArticle;
  userId: string;
  podcastId: string;
}

export function relevanceColor(score: number | null): string {
  if (score === null) return "bg-muted text-muted-foreground";
  if (score >= 7) return "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200";
  if (score >= 4) return "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200";
  return "bg-muted text-muted-foreground";
}

export function getSourceDisplayName(source: EpisodeArticle["source"]): string {
  if (source.label) return source.label;
  try {
    const url = new URL(source.url);
    return url.hostname.replace(/^www\./, "");
  } catch {
    return source.url;
  }
}

function formatPostTime(publishedAt: string | null): string {
  if (!publishedAt) return "";
  const date = new Date(publishedAt);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * One article in the episode Articles tab and the upcoming-articles page.
 *
 * Short-form sources are aggregated into one article per author thread, so a card can stand for a
 * single tweet or a fourteen-post conversation. When it holds more than one post the card says so
 * and expands to the posts themselves, which are fetched on demand: the article body already
 * carries the same text, so shipping them with every list response would roughly double a payload
 * that is mostly bodies.
 */
export function ArticleCard({ article, userId, podcastId }: ArticleCardProps) {
  const [expanded, setExpanded] = useState(false);
  const [threadOpen, setThreadOpen] = useState(false);
  const [posts, setPosts] = useState<ArticlePost[] | null>(null);
  const [loadingPosts, setLoadingPosts] = useState(false);

  const isThread = article.postCount > 1;

  function toggleThread() {
    const opening = !threadOpen;
    setThreadOpen(opening);
    if (opening && posts === null && !loadingPosts) {
      setLoadingPosts(true);
      fetch(`/api/users/${userId}/podcasts/${podcastId}/articles/${article.id}/posts`)
        .then((res) => (res.ok ? res.json() : []))
        .then((data: ArticlePost[]) => setPosts(data))
        .catch(() => setPosts([]))
        .finally(() => setLoadingPosts(false));
    }
  }

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
      {isThread && (
        <div className="mt-2">
          <button
            onClick={toggleThread}
            className="flex items-center gap-1.5 rounded-md py-0.5 text-xs text-muted-foreground hover:text-foreground"
          >
            {threadOpen ? (
              <ChevronDown className="size-3.5 shrink-0" />
            ) : (
              <ChevronRight className="size-3.5 shrink-0" />
            )}
            <MessagesSquare className="size-3.5 shrink-0" />
            <Badge variant="secondary" className="text-[10px] px-1.5 py-px">
              {article.postCount} posts
            </Badge>
          </button>
          {threadOpen && (
            <div className="mt-2 space-y-2 border-l border-border pl-3">
              {loadingPosts && <p className="text-xs text-muted-foreground">Loading posts...</p>}
              {!loadingPosts && posts?.length === 0 && (
                <p className="text-xs text-muted-foreground">No posts found for this article.</p>
              )}
              {posts?.map((post) => (
                <div key={post.id} className="text-xs">
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <span>{formatPostTime(post.publishedAt)}</span>
                    <a
                      href={post.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="hover:text-foreground"
                    >
                      <ExternalLink className="size-3" />
                    </a>
                  </div>
                  <p className="mt-0.5 whitespace-pre-wrap text-foreground">{post.body}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
