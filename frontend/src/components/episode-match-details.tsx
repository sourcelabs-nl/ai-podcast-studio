import { FileText, Hash, Newspaper } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { EpisodeMatches } from "@/lib/types";

/**
 * Explains why an episode came back from a search: the covered topics and article headlines
 * containing the query. A script-only hit is called out rather than shown as a topic, because a
 * passing mention in dialogue is much weaker evidence than a story the episode was built around.
 */
export function EpisodeMatchDetails({ matches }: { matches: EpisodeMatches }) {
  if (matches.scriptOnly) {
    return (
      <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <FileText className="size-3" />
        mentioned in the script only
      </span>
    );
  }

  return (
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
  );
}
