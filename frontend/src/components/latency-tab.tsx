"use client";

import { useEffect, useState } from "react";
import type {
  EpisodeLlmCallsResponse,
  LlmCall,
  LlmCallLatencyResponse,
  StageLatency,
} from "@/lib/types";
import { Panel, Section } from "@/components/section";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

/**
 * Stage labels. `filter` is shown as "Scoring" because the cost table above this one already calls
 * that stage Scoring, and one page should not have two names for it. `eval` has no cost row, so it
 * appears only here.
 */
const STAGE_LABELS: Record<string, string> = {
  filter: "Scoring",
  dedup: "Dedup",
  "dedup-gate": "Dedup Gate",
  compose: "Compose",
  eval: "Eval",
};

function stageLabel(stage: string): string {
  return STAGE_LABELS[stage] ?? stage;
}

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

function formatTime(startedAt: string): string {
  const parsed = new Date(startedAt);
  if (Number.isNaN(parsed.getTime())) return startedAt;
  return parsed.toLocaleTimeString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function StageRow({ stage }: { stage: StageLatency }) {
  const label = stageLabel(stage.stage);

  // A stage that issued nothing is shown rather than dropped: an absent row reads as "nothing to
  // worry about here", which is exactly what no data does not tell you.
  if (stage.samples === 0) {
    return (
      <TableRow>
        <TableCell className="font-medium">{label}</TableCell>
        <TableCell className="text-right text-muted-foreground" colSpan={5}>
          no requests
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
 * A request's outcome, where anything other than a plain successful call is marked. A cache hit
 * performed no request and a failure reports the time until it failed, so neither duration next to
 * them should be read as a provider's latency.
 */
function RequestOutcome({ request }: { request: LlmCall }) {
  if (request.cacheHit) {
    return <span className="text-muted-foreground">cached</span>;
  }
  if (request.outcome !== "ok") {
    return <span className="text-destructive">{request.outcome}</span>;
  }
  return <span className="text-muted-foreground">ok</span>;
}

function RequestList({ requests }: { requests: LlmCall[] }) {
  return (
    <Table>
      <TableHeader className="bg-muted/50">
        <TableRow>
          <TableHead>Started</TableHead>
          <TableHead>Stage</TableHead>
          <TableHead>Model</TableHead>
          <TableHead className="text-right">Duration</TableHead>
          <TableHead className="text-right">Outcome</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {requests.map((request, index) => (
          <TableRow key={`${request.startedAt}-${index}`}>
            <TableCell className="tabular-nums text-muted-foreground">
              {formatTime(request.startedAt)}
            </TableCell>
            <TableCell className="font-medium">{stageLabel(request.stage)}</TableCell>
            <TableCell className="text-muted-foreground">{request.model}</TableCell>
            <TableCell className="text-right tabular-nums">
              {request.cacheHit ? "—" : formatMs(request.durationMs)}
            </TableCell>
            <TableCell className="text-right">
              <RequestOutcome request={request} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * This episode's LLM request latency, shown against the timeout each stage is configured with, with
 * the episode's individual requests beneath it.
 *
 * Fetches its own data rather than receiving it from the episode page: these figures come from the
 * request telemetry rather than the episode's own cost accounting, and a failure to load them must
 * not affect the rest of the page.
 *
 * An episode issues a handful of requests per stage, so p99 is the slowest of them rather than a
 * tail estimate. The list is what answers which request was slow; the percentiles summarize it.
 */
export function LatencyTab({ episodeId }: { episodeId: number }) {
  const [latency, setLatency] = useState<LlmCallLatencyResponse | null>(null);
  const [calls, setCalls] = useState<EpisodeLlmCallsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [latencyRes, callsRes] = await Promise.all([
          fetch(`/api/llm/calls/latency?episodeId=${episodeId}`),
          fetch(`/api/llm/calls/episodes/${episodeId}`),
        ]);
        if (cancelled) return;
        if (!latencyRes.ok || !callsRes.ok) {
          setFailed(true);
          return;
        }
        const latencyBody = (await latencyRes.json()) as LlmCallLatencyResponse;
        const callsBody = (await callsRes.json()) as EpisodeLlmCallsResponse;
        if (cancelled) return;
        // A 200 carrying an unexpected body would otherwise reach `.map` during render and throw
        // there, taking the whole page down with it: this block must fail alone.
        if (!Array.isArray(latencyBody?.stages) || !Array.isArray(callsBody?.requests)) {
          setFailed(true);
          return;
        }
        setLatency(latencyBody);
        setCalls(callsBody);
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
  }, [episodeId]);

  if (loading) {
    return <p className="text-sm text-muted-foreground italic">Loading latency…</p>;
  }

  if (failed) {
    return <p className="text-sm text-destructive">Could not load LLM request latency.</p>;
  }

  if (calls?.predatesAttribution) {
    return (
      <Section title="LLM request latency">
        <p className="text-sm text-muted-foreground">
          This episode was generated before requests recorded which episode they belonged to, so
          none of its requests can be shown. Episodes generated from now on will have them.
        </p>
      </Section>
    );
  }

  return (
    <div className="space-y-6">
      <Section title="LLM request latency">
        <p className="text-xs text-muted-foreground">
          Per-request timings for this episode, against each stage&apos;s configured timeout. The
          percentiles cover requests that answered and requests that timed out; cache hits and
          faster failures are left out, so a stage at its ceiling reads as one.
        </p>

        {latency && (
          <Panel>
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
          </Panel>
        )}
      </Section>

      <Section title="Requests">
        <p className="text-xs text-muted-foreground">
          Every request this episode issued, newest first, including cache hits and failures.
        </p>

        {calls && calls.requests.length > 0 ? (
          <Panel>
            <RequestList requests={calls.requests} />
          </Panel>
        ) : (
          <p className="text-sm text-muted-foreground italic">
            This episode issued no recorded requests.
          </p>
        )}
      </Section>
    </div>
  );
}
