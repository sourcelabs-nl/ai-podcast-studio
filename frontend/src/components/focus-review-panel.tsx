"use client";

import { useCallback, useEffect, useState } from "react";
import { ExternalLink, Loader2, RefreshCw } from "lucide-react";
import type { Episode, ResearchSource } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Panel } from "@/components/section";

/**
 * The review context of a focus episode: what it is about, the research sources its script drew
 * on, and a feedback box that recomposes the script of this same episode against the same articles.
 * The selected articles live on the Articles tab and the estimated length in the page header.
 */
export function FocusReviewPanel({
  userId,
  podcastId,
  episode,
  onRecomposeStarted,
}: {
  userId: string;
  podcastId: string;
  episode: Episode;
  onRecomposeStarted: () => void;
}) {
  const [sources, setSources] = useState<ResearchSource[]>([]);
  const [feedback, setFeedback] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const base = `/api/users/${userId}/podcasts/${podcastId}/episodes/${episode.id}`;
  const recomposing = episode.status === "PENDING_REVIEW" && !!episode.pipelineStage;

  const fetchSources = useCallback(() => {
    fetch(`${base}/research-sources`)
      .then((res) => (res.ok ? res.json() : []))
      .then(setSources)
      .catch(() => setSources([]));
  }, [base]);

  // The script and its sources change together, so both are reread whenever the episode is.
  useEffect(() => {
    fetchSources();
  }, [fetchSources, episode.scriptText]);

  async function submitFeedback() {
    setSubmitting(true);
    setError(null);
    try {
      const res = await fetch(`${base}/regenerate-script`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ feedback: feedback.trim() }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setError(data.error || "Failed to regenerate the script");
        return;
      }
      setFeedback("");
      onRecomposeStarted();
    } catch {
      setError("Failed to regenerate the script");
    } finally {
      setSubmitting(false);
    }
  }

  const byQuery = sources.reduce<Record<string, ResearchSource[]>>((acc, source) => {
    (acc[source.query] ??= []).push(source);
    return acc;
  }, {});

  return (
    <Panel title={`Focus: ${episode.focus}`} className="mb-6">
      <div className="space-y-4 text-sm">
        <div>
          <p className="mb-1 font-medium">Research sources</p>
          {sources.length === 0 ? (
            <p className="text-muted-foreground">No research sources were recorded.</p>
          ) : (
            <div className="space-y-2">
              {Object.entries(byQuery).map(([query, hits]) => (
                <div key={query}>
                  <p className="text-xs italic text-muted-foreground">&ldquo;{query}&rdquo;</p>
                  <ul className="ml-4 list-disc">
                    {hits.map((hit) => (
                      <li key={`${query}-${hit.url}`}>
                        <a href={hit.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-primary hover:underline">
                          {hit.title}
                          <ExternalLink className="size-3" />
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>

        {episode.status === "PENDING_REVIEW" && (
          <div>
            <p className="mb-1 font-medium">Feedback</p>
            {episode.reviewFeedback && (
              <p className="mb-2 text-xs text-muted-foreground">Last feedback: {episode.reviewFeedback}</p>
            )}
            <textarea
              value={feedback}
              onChange={(e) => setFeedback(e.target.value)}
              placeholder="What should change in the script? e.g. make it a bit shorter and focus more on benchmarks"
              rows={3}
              disabled={recomposing || submitting}
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
            />
            {error && <p className="mt-1 text-destructive">{error}</p>}
            <div className="mt-2 flex items-center gap-2">
              <Button size="sm" onClick={submitFeedback} disabled={recomposing || submitting || !feedback.trim()}>
                {recomposing || submitting ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
                Regenerate script
              </Button>
              {recomposing && <span className="text-muted-foreground">Recomposing ({episode.pipelineStage})...</span>}
            </div>
          </div>
        )}
      </div>
    </Panel>
  );
}
