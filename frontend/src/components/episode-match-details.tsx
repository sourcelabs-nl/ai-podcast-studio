import { Hash, Newspaper, Quote } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { EpisodeMatches } from "@/lib/types";

/**
 * Explains why an episode came back from a search, in one line per episode.
 *
 * Topics are the high-signal labels, so they get the chips. Article titles are long and there can be
 * many, so they collapse to a count with the titles on hover: rendering them inline was what made
 * the result list unreadable. Every chip truncates, which needs `min-w-0` on the label inside the
 * badge's flex box, otherwise the badge grows to fit and pushes the table wider than the page.
 */
export function EpisodeMatchDetails({ matches }: { matches: EpisodeMatches }) {
  const extraTopics = matches.topicTotal - matches.topics.length;

  return (
    <div className="flex flex-col gap-1 min-w-0">
      {(matches.topicTotal > 0 || matches.articleTotal > 0) && (
        <div className="flex items-center gap-1.5 min-w-0">
          {matches.topics.map((topic) => (
            <Badge
              key={topic}
              variant="secondary"
              className="font-normal max-w-[22rem] min-w-0"
              title={topic}
            >
              <Hash className="size-3 shrink-0" />
              <span className="truncate min-w-0">{topic}</span>
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
        <div className="flex items-start gap-1.5 min-w-0 text-xs text-muted-foreground">
          <Quote className="size-3 mt-0.5 shrink-0" />
          <span className="truncate min-w-0 italic" title={matches.scriptContext}>
            {matches.scriptContext}
          </span>
        </div>
      )}
      {matches.scriptOnly && !matches.scriptContext && (
        <span className="text-xs text-muted-foreground">mentioned in the script only</span>
      )}
    </div>
  );
}
