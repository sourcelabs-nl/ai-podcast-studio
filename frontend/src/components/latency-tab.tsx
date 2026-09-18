"use client";

import { useEffect, useState } from "react";
import type { LlmCallLatencyResponse, StageLatency } from "@/lib/types";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

const WINDOW_DAYS = 7;

/**
 * Stage labels. `filter` is shown as "Scoring" because the cost table above this one already calls
 * that stage Scoring, and one page should not have two names for it. `eval` has no cost row, so it
 * appears only here.
 */
const STAGE_LABELS: Record<string, string> = {
  filter: "Scoring",
  dedup: "Dedup",
  compose: "Compose",
  eval: "Eval",
};

/**
 * Durations span three orders of magnitude here: a scoring p50 is hundreds of milliseconds while a
 * compose timeout is twenty minutes. One unit would make one end of that unreadable.
 */
function formatMs(ms: number | null): string {
  if (ms === null) return "—";
  if (ms < 1000) return `${ms} ms`;
  const seconds = ms / 1000;
  if (seconds < 90) return `${seconds.toFixed(1)}s`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return `${minutes}m ${rest}s`;
}

function StageRow({ stage }: { stage: StageLatency }) {
  const label = STAGE_LABELS[stage.stage] ?? stage.stage;

  // A stage that issued nothing is shown rather than dropped: an absent row reads as "nothing to
  // worry about here", which is exactly what no data does not tell you.
  if (stage.samples === 0) {
    return (
      <TableRow>
        <TableCell className="font-medium">{label}</TableCell>
        <TableCell className="text-right text-muted-foreground" colSpan={5}>
          no requests in this window
        </TableCell>
        <TableCell className="text-right tabular-nums text-muted-foreground">
          {formatMs(stage.timeoutMs)}
        </TableCell>
      </TableRow>
    );
  }

  return (
    <TableRow>
      <TableCell className="font-medium">{label}</TableCell>
      <TableCell className="text-right tabular-nums">{stage.samples.toLocaleString()}</TableCell>
      <TableCell className="text-right tabular-nums">{formatMs(stage.p50Ms)}</TableCell>
      <TableCell className="text-right tabular-nums">{formatMs(stage.p90Ms)}</TableCell>
      <TableCell className="text-right tabular-nums">{formatMs(stage.p95Ms)}</TableCell>
      <TableCell className="text-right tabular-nums">{formatMs(stage.p99Ms)}</TableCell>
      <TableCell className="text-right tabular-nums text-muted-foreground">
        {formatMs(stage.timeoutMs)}
      </TableCell>
    </TableRow>
  );
}

/**
 * Per-request LLM latency, shown against the timeout each stage is configured with.
 *
 * Fetches its own data rather than receiving it from the episode page: these figures cover a window
 * across all episodes and carry no episode attribution, so they must not travel in the episode's
 * data path, and a failure to load them must not affect the rest of the page.
 *
 * It sits on an episode's page while describing a window, so the heading says so. A tab of its own
 * makes that easier to hold onto than a block under the episode's cost table would.
 */
export function LatencyTab() {
  const [latency, setLatency] = useState<LlmCallLatencyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const res = await fetch(`/api/llm/calls/latency?days=${WINDOW_DAYS}`);
        if (cancelled) return;
        if (!res.ok) {
          setFailed(true);
          return;
        }
        const body = (await res.json()) as LlmCallLatencyResponse;
        if (cancelled) return;
        // A 200 carrying an unexpected body would otherwise reach `stages.map` during render and
        // throw there, taking the whole Costs tab down with it: this block must fail alone.
        if (!Array.isArray(body?.stages)) {
          setFailed(true);
          return;
        }
        setLatency(body);
      } catch {
        if (!cancelled) setFailed(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="space-y-2">
      <div>
        <h3 className="text-sm font-semibold">LLM request latency</h3>
        <p className="text-xs text-muted-foreground">
          Per-request timings across all episodes over the last {WINDOW_DAYS} days, not this episode.
          Cache hits and failed requests are excluded.
        </p>
      </div>

      {loading && <p className="text-sm text-muted-foreground italic">Loading latency…</p>}

      {!loading && failed && (
        <p className="text-sm text-destructive">Could not load LLM request latency.</p>
      )}

      {!loading && !failed && latency && (
        <Table>
          <TableHeader className="bg-muted/50">
            <TableRow>
              <TableHead>Stage</TableHead>
              <TableHead className="text-right">Requests</TableHead>
              <TableHead className="text-right">p50</TableHead>
              <TableHead className="text-right">p90</TableHead>
              <TableHead className="text-right">p95</TableHead>
              <TableHead className="text-right">p99</TableHead>
              <TableHead className="text-right">Timeout</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {latency.stages.map((stage) => (
              <StageRow key={stage.stage} stage={stage} />
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
