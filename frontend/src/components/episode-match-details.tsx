import { FileText, Hash, Newspaper } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { EpisodeMatches } from "@/lib/types";

/**
 * Explains why an episode came back from a search: the covered topics and articles containing the
 * query, plus the spoken line when the script mentions it. A script-only hit is called out rather
 * than shown as a topic, because a passing mention in dialogue is much weaker evidence than a
 * story the episode was built around.
 */
export function EpisodeMatchDetails({ matches }: { matches: EpisodeMatches }) {
  const hasLabels = matches.topics.length > 0 || matches.articleTitles.length > 0;

  return (
    <div className="flex flex-col gap-1">
      {hasLabels && (
        <div className="flex flex-wrap items-center gap-1.5">
          {matches.topics.map((topic) => (
            <Badge key={topic} variant="secondary" className="font-normal max-w-md truncate" title={topic}>
              <Hash className="size-3 shrink-0" />
              {topic}
            </Badge>
          ))}
          {matches.articleTitles.map((title) => (
            <Badge key={title} variant="outline" className="font-normal max-w-md truncate" title={title}>
              <Newspaper className="size-3 shrink-0" />
              {title}
            </Badge>
          ))}
          {matches.hasMore && <span className="text-xs text-muted-foreground">and more</span>}
        </div>
      )}
      {matches.scriptContext && (
        <span className="flex items-start gap-1.5 text-xs text-muted-foreground">
          <FileText className="size-3 mt-0.5 shrink-0" />
          <span>
            {matches.scriptOnly && <span className="mr-1">in the script only:</span>}
            <span className="italic">{matches.scriptContext}</span>
          </span>
        </span>
      )}
      {!hasLabels && !matches.scriptContext && (
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <FileText className="size-3" />
          mentioned in the script only
        </span>
      )}
    </div>
  );
}
