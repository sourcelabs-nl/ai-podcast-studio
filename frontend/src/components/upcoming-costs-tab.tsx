"use client";

import { Panel } from "@/components/section";
import type { LlmStageCost } from "@/lib/types";
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
  return `$${(cents / 100).toFixed(4)}`;
}

function formatInt(n: number): string {
  if (n === 0) return "—";
  return n.toLocaleString();
}

/**
 * What has already been spent scoring the articles standing for the next episode.
 *
 * Only the scoring stage has run at this point. The stages that run during generation are left
 * out rather than listed at zero: a stage shown at zero reads as one that cost nothing, not as one
 * that has not happened yet.
 */
export function UpcomingCostsTab({ scoring }: { scoring: LlmStageCost | undefined }) {
  if (!scoring || scoring.calls === 0) {
    return (
      <p className="text-sm text-muted-foreground italic">
        Nothing has been scored for the next episode yet.
      </p>
    );
  }

  return (
    <Panel title="Spent so far" className="space-y-3">
        <Table>
          <TableHeader>
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
              <TableCell className="text-xs text-muted-foreground">{scoring.model ?? "—"}</TableCell>
              <TableCell className="text-right">{formatInt(scoring.calls)}</TableCell>
              <TableCell className="text-right">{formatInt(scoring.inputTokens)}</TableCell>
              <TableCell className="text-right">{formatInt(scoring.outputTokens)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatCents(scoring.costCents)}</TableCell>
            </TableRow>
          </TableBody>
          <TableFooter>
            <TableRow>
              <TableCell colSpan={5} className="font-medium">Total</TableCell>
              <TableCell className="text-right font-semibold tabular-nums">
                {formatCents(scoring.costCents)}
              </TableCell>
            </TableRow>
          </TableFooter>
        </Table>
        <p className="text-xs text-muted-foreground italic">
          These articles have already been scored and charged. Generating the episode adds the dedup,
          compose, recap and audio stages.
        </p>
    </Panel>
  );
}
