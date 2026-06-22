"use client";

import type { EpisodeCosts } from "@/lib/types";
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

function formatCents(cents: number): string {
  if (cents === 0) return "—";
  // Cents are integer; convert to dollars with 4 decimals so sub-cent stage totals
  // stay visible (e.g. 1¢ → $0.0100). Larger values still read naturally.
  return `$${(cents / 100).toFixed(4)}`;
}

function formatInt(n: number): string {
  if (n === 0) return "—";
  return n.toLocaleString();
}

export function CostsTab({ costs }: { costs: EpisodeCosts | undefined }) {
  if (!costs) {
    return (
      <p className="text-sm text-muted-foreground italic">
        Cost breakdown is not available for this episode.
      </p>
    );
  }

  // Legacy episodes (pre-V57) have backfilled score tokens but no dedup/compose/recap
  // data — those stages stay at 0 tokens despite the episode being fully generated.
  // Use TTS characters as the "fully generated" signal.
  const fullyGenerated = costs.tts.characters > 0;
  const dedupComposeRecapMissing =
    costs.dedup.inputTokens === 0 &&
    costs.compose.inputTokens === 0 &&
    costs.recap.inputTokens === 0;
  const showLegacyNotice = fullyGenerated && dedupComposeRecapMissing;

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader className="bg-muted/50">
          <TableRow>
            <TableHead>Stage</TableHead>
            <TableHead>Model</TableHead>
            <TableHead className="text-right">Calls</TableHead>
            <TableHead className="text-right">Input</TableHead>
            <TableHead className="text-right">Output</TableHead>
            <TableHead className="text-right">Cost</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell className="font-medium">Scoring</TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {costs.score.model ?? "—"}
            </TableCell>
            <TableCell className="text-right">{formatInt(costs.score.calls)}</TableCell>
            <TableCell className="text-right">{formatInt(costs.score.inputTokens)}</TableCell>
            <TableCell className="text-right">{formatInt(costs.score.outputTokens)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatCents(costs.score.costCents)}</TableCell>
          </TableRow>
          <TableRow>
            <TableCell className="font-medium">Dedup</TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {costs.dedup.model ?? "—"}
            </TableCell>
            <TableCell className="text-right">{formatInt(costs.dedup.calls)}</TableCell>
            <TableCell className="text-right">{formatInt(costs.dedup.inputTokens)}</TableCell>
            <TableCell className="text-right">{formatInt(costs.dedup.outputTokens)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatCents(costs.dedup.costCents)}</TableCell>
          </TableRow>
          <TableRow>
            <TableCell className="font-medium">Compose</TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {costs.compose.model ?? "—"}
            </TableCell>
            <TableCell className="text-right">{formatInt(costs.compose.calls)}</TableCell>
            <TableCell className="text-right">{formatInt(costs.compose.inputTokens)}</TableCell>
            <TableCell className="text-right">{formatInt(costs.compose.outputTokens)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatCents(costs.compose.costCents)}</TableCell>
          </TableRow>
          <TableRow>
            <TableCell className="font-medium">Recap</TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {costs.recap.model ?? "—"}
            </TableCell>
            <TableCell className="text-right">{formatInt(costs.recap.calls)}</TableCell>
            <TableCell className="text-right">{formatInt(costs.recap.inputTokens)}</TableCell>
            <TableCell className="text-right">{formatInt(costs.recap.outputTokens)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatCents(costs.recap.costCents)}</TableCell>
          </TableRow>
          <TableRow>
            <TableCell className="font-medium">TTS</TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {costs.tts.model ?? "—"}
            </TableCell>
            <TableCell className="text-right">{formatInt(costs.tts.calls)}</TableCell>
            <TableCell className="text-right" colSpan={2}>
              {formatInt(costs.tts.characters)} chars
            </TableCell>
            <TableCell className="text-right tabular-nums">{formatCents(costs.tts.costCents)}</TableCell>
          </TableRow>
          <TableRow>
            <TableCell className="font-medium">Research</TableCell>
            <TableCell className="text-xs text-muted-foreground">Tavily</TableCell>
            <TableCell className="text-right">{formatInt(costs.research.calls)}</TableCell>
            <TableCell className="text-right">—</TableCell>
            <TableCell className="text-right">—</TableCell>
            <TableCell className="text-right tabular-nums">{formatCents(costs.research.costCents)}</TableCell>
          </TableRow>
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell colSpan={5} className="font-medium">Total</TableCell>
            <TableCell className="text-right font-semibold tabular-nums">
              {formatCents(costs.totalCostCents)}
            </TableCell>
          </TableRow>
        </TableFooter>
      </Table>
      {showLegacyNotice && (
        <p className="text-xs text-muted-foreground italic">
          Detailed per-stage breakdown is not available for episodes generated
          before this feature shipped. Stage totals show as zero; only TTS and
          research costs are accurate for legacy episodes.
        </p>
      )}
    </div>
  );
}
