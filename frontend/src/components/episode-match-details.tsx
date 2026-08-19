import { Hash, Newspaper } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { highlightTerms } from "@/lib/highlight";
import type { EpisodeMatches } from "@/lib/types";

/**
 * Explains why an episode came back from a search, in at most two compact lines.
 *
 * Each line opens with plain language naming what was found and where, because a bare row of chips
 * leaves the reader to infer what they are looking at. Topics are the high-signal labels so they get
 * the chips; article titles are long and an episode routinely matches a dozen, so they collapse to a
 * count with the titles on hover. Every chip truncates, which needs `min-w-0` on the label inside the
 * badge's flex box, otherwise the badge grows to fit and pushes the table wider than the page.
 */
export function EpisodeMatchDetails({
  matches,
  terms,
  query,
}: {
  matches: EpisodeMatches;
  terms: string[];
  query: string;
}) {
  const extraTopics = matches.topicTotal - matches.topics.length;
  const hasLabels = matches.topicTotal > 0 || matches.articleTotal > 0;
  const quoted = `"${query.trim()}"`;

  return (
    <div className="flex flex-col gap-1.5 min-w-0">
      {hasLabels && (
        <div className="flex items-center gap-1.5 min-w-0">
          <span className="text-xs text-muted-foreground whitespace-nowrap">Found {quoted} in</span>
          {matches.topics.map((topic) => (
            <Badge
              key={topic}
              variant="secondary"
              className="font-normal max-w-[22rem] min-w-0"
              title={topic}
            >
              <Hash className="size-3 shrink-0" />
              <span className="truncate min-w-0">{highlightTerms(topic, terms)}</span>
            </Badge>
          ))}
          {extraTopics > 0 && (
            <span className="text-xs text-muted-foreground whitespace-nowrap">
              +{extraTopics} more {extraTopics === 1 ? "topic" : "topics"}
            </span>
          )}
          {matches.articleTotal > 0 && (
            <Badge
              variant="outline"
              className="font-normal whitespace-nowrap"
              title={matches.articleTitles.join("\n")}
            >
              <Newspaper className="size-3 shrink-0" />
              {matches.articleTotal} {matches.articleTotal === 1 ? "article" : "articles"}
            </Badge>
          )}
        </div>
      )}
      {matches.scriptContext && (
        <div className="flex items-baseline gap-1.5 min-w-0 text-xs text-muted-foreground">
          <span className="whitespace-nowrap">
            {hasLabels ? "and spoken in the episode:" : `Found ${quoted} only in the script:`}
          </span>
          <span className="truncate min-w-0 italic" title={matches.scriptContext}>
            {highlightTerms(matches.scriptContext, terms)}
          </span>
        </div>
      )}
      {!hasLabels && !matches.scriptContext && (
        <span className="text-xs text-muted-foreground">Found {quoted} in this episode</span>
      )}
    </div>
  );
}
