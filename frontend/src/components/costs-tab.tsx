"use client";

import { Panel } from "@/components/section";
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

/**
 * One stage as the table renders it. [input] and [output] are formatted here rather than in the
 * cell, because the columns hold whatever a stage is billed for: tokens for an LLM stage,
 * characters for TTS, and nothing at all for a stage billed per call.
 */
interface StageRow {
  key: string;
  label: string;
  note?: string | null;
  model: string | null;
  calls: number;
  input: string;
  output: string;
  costCents: number;
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

  // Shown inside the Scoring row rather than as a row of its own: this money is already part of
  // the scoring total, and a separate row reads as something to add to it. Hidden at zero, which
  // is what an episode generated before its candidates were recorded reports.
  const droppedCalls = costs.score.droppedCalls ?? 0;

  // Sorted by spend, dearest first: the row worth looking at is the one the episode paid most for,
  // and the pipeline's own order says nothing a reader of this table needs.
  const rows: StageRow[] = [
    {
      key: "score",
      label: "Scoring",
      note: droppedCalls > 0
        ? `${droppedCalls.toLocaleString()} dropped, ${formatCents(costs.score.droppedCostCents ?? 0)}`
        : null,
      model: costs.score.model,
      calls: costs.score.calls,
      input: formatInt(costs.score.inputTokens),
      output: formatInt(costs.score.outputTokens),
      costCents: costs.score.costCents,
    },
    {
      key: "dedup",
      label: "Dedup",
      model: costs.dedup.model,
      calls: costs.dedup.calls,
      input: formatInt(costs.dedup.inputTokens),
      output: formatInt(costs.dedup.outputTokens),
      costCents: costs.dedup.costCents,
    },
    {
      key: "dedup-gate",
      label: "Dedup Gate",
      model: costs.dedupGate?.model ?? null,
      calls: costs.dedupGate?.calls ?? 0,
      input: formatInt(costs.dedupGate?.inputTokens ?? 0),
      output: formatInt(costs.dedupGate?.outputTokens ?? 0),
      costCents: costs.dedupGate?.costCents ?? 0,
    },
    {
      key: "compose",
      label: "Compose",
      model: costs.compose.model,
      calls: costs.compose.calls,
      input: formatInt(costs.compose.inputTokens),
      output: formatInt(costs.compose.outputTokens),
      costCents: costs.compose.costCents,
    },
    {
      key: "recap",
      label: "Recap",
      model: costs.recap.model,
      calls: costs.recap.calls,
      input: formatInt(costs.recap.inputTokens),
      output: formatInt(costs.recap.outputTokens),
      costCents: costs.recap.costCents,
    },
    {
      key: "tts",
      label: "TTS",
      model: costs.tts.model,
      calls: costs.tts.calls,
      // TTS is billed per character, which is what it sends: the input column carries it.
      input: `${formatInt(costs.tts.characters)} chars`,
      output: "—",
      costCents: costs.tts.costCents,
    },
    {
      key: "research",
      label: "Research",
      model: "Tavily",
      calls: costs.research.calls,
      input: "—",
      output: "—",
      costCents: costs.research.costCents,
    },
  ].sort((a, b) => b.costCents - a.costCents);

  return (
    <Panel className="space-y-3">
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
            {rows.map((row) => (
              <TableRow key={row.key}>
                <TableCell className="font-medium">
                  {row.label}
                  {row.note && (
                    <span className="block text-xs font-normal text-muted-foreground">{row.note}</span>
                  )}
                </TableCell>
                <TableCell className="text-xs text-muted-foreground">{row.model ?? "—"}</TableCell>
                <TableCell className="text-right">{formatInt(row.calls)}</TableCell>
                <TableCell className="text-right">{row.input}</TableCell>
                <TableCell className="text-right">{row.output}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCents(row.costCents)}</TableCell>
              </TableRow>
            ))}
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
    </Panel>
  );
}
