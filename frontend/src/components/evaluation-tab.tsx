"use client";

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Panel } from "@/components/section";
import { cn } from "@/lib/utils";
import type {
  BackchannelCandidate,
  EpisodeMetricsResponse,
  EpisodeScore,
  EvaluationRun,
  HumorAnchor,
  PromiseAnchor,
  ScriptJudgeAnchors,
  ScriptMetrics,
} from "@/lib/types";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface EvaluationTabProps {
  userId: string;
  podcastId: string;
  episodeId: number;
  /** How many turns the script renders as, so an index out of range offers no jump. */
  turnCount: number;
  onJumpToTurn: (turn: number) => void;
}

const EMPTY_ANCHORS: ScriptJudgeAnchors = { promises: [], humorBeats: [], teaserTopics: [] };

/**
 * A score's colour band. Nothing in the bundle yet defends a pass/fail threshold, so the bands say
 * only how high the number is on its own [0, 1] scale, never whether the episode passed.
 */
function scoreBand(value: number): { bar: string; text: string } {
  if (value < 1 / 3) return { bar: "bg-red-500", text: "text-red-600 dark:text-red-400" };
  if (value < 2 / 3) return { bar: "bg-amber-500", text: "text-amber-600 dark:text-amber-400" };
  return { bar: "bg-emerald-500", text: "text-emerald-600 dark:text-emerald-400" };
}

/** A score rendered as a filled track, so a row's height is readable before its digits are. */
function ScoreBar({ value, className }: { value: number; className?: string }) {
  return (
    <div className={cn("h-2 w-full overflow-hidden rounded-full bg-muted", className)}>
      <div
        className={cn("h-full rounded-full", scoreBand(value).bar)}
        style={{ width: `${Math.round(Math.min(Math.max(value, 0), 1) * 100)}%` }}
      />
    </div>
  );
}

function formatScore(value: number): string {
  return value.toFixed(2);
}

function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`;
}

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString();
}

/**
 * The judge's raw answer is stored as a JSON string. A row whose anchors do not parse still has
 * usable scores, so a parse failure costs the anchors and nothing else.
 */
function parseAnchors(anchorsJson: string): ScriptJudgeAnchors {
  try {
    const parsed = JSON.parse(anchorsJson) as Partial<ScriptJudgeAnchors>;
    return {
      promises: parsed.promises ?? [],
      humorBeats: parsed.humorBeats ?? [],
      teaserTopics: parsed.teaserTopics ?? [],
    };
  } catch {
    return EMPTY_ANCHORS;
  }
}

type Endpoint = "scores" | "metrics" | "runs";

interface LoadResult<T> {
  data: T | null;
  /** A request that failed, as opposed to one that returned nothing. */
  failed: boolean;
}

async function loadJson<T>(url: string): Promise<LoadResult<T>> {
  try {
    const res = await fetch(url);
    // A 404 is a real answer here: the episode or podcast is not the caller's, and the tab has
    // nothing to show for it. Any other non-OK status is a failure worth surfacing.
    if (res.status === 404) return { data: null, failed: false };
    if (!res.ok) return { data: null, failed: true };
    return { data: (await res.json()) as T, failed: false };
  } catch {
    return { data: null, failed: true };
  }
}

function Empty({ children }: { children: ReactNode }) {
  return <p className="text-sm text-muted-foreground italic">{children}</p>;
}

/**
 * A section that could not be loaded says so. Rendering an empty state instead would make a broken
 * endpoint look like an episode that was simply never scored.
 */
function LoadFailed({ what }: { what: string }) {
  return <p className="text-sm text-destructive">Could not load {what}.</p>;
}

/** A turn index rendered as a jump control, or as plain text when the script has no such turn. */
function TurnLink({
  turn,
  turnCount,
  onJumpToTurn,
}: {
  turn: number;
  turnCount: number;
  onJumpToTurn: (turn: number) => void;
}) {
  if (turn < 0 || turn >= turnCount) {
    return <span className="tabular-nums text-muted-foreground">#{turn}</span>;
  }
  return (
    <button
      type="button"
      onClick={() => onJumpToTurn(turn)}
      className="tabular-nums text-primary underline underline-offset-2 hover:no-underline"
    >
      #{turn}
    </button>
  );
}

/**
 * One judgement, without a frame of its own: the caller decides whether it is the card's whole
 * content or one of several stacked inside it.
 */
function ScoreCard({
  score,
  turnCount,
  onJumpToTurn,
}: {
  score: EpisodeScore;
  turnCount: number;
  onJumpToTurn: (turn: number) => void;
}) {
  const anchors = parseAnchors(score.anchorsJson);

  return (
    <div className="space-y-3">
      <div className="space-y-2">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <div className="flex items-baseline gap-2">
            <span className={cn("text-4xl font-bold tabular-nums", scoreBand(score.overall).text)}>
              {formatScore(score.overall)}
            </span>
            <span className="text-sm text-muted-foreground">overall</span>
          </div>
          <span className="text-xs text-muted-foreground">
            {score.judgeModel} &middot; scorer v{score.scorerVersion} &middot; {formatTimestamp(score.scoredAt)}
          </span>
        </div>
        <ScoreBar value={score.overall} className="h-3" />
      </div>

      <p className="text-xs text-muted-foreground">
        The three components are weighted equally as a placeholder: nothing yet says one device
        matters more than another. Read the components when deciding what to change, and the overall
        only to rank episodes against each other.
      </p>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-32">Component</TableHead>
            <TableHead className="w-40">Score</TableHead>
            <TableHead>Counts</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell className="font-medium">Cliffhangers</TableCell>
            <TableCell>
              <div className="flex items-center gap-2">
                <ScoreBar value={score.cliffhangerScore} />
                <span className={cn("tabular-nums font-medium", scoreBand(score.cliffhangerScore).text)}>
                  {formatScore(score.cliffhangerScore)}
                </span>
              </div>
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {score.promises} promises, {score.deferredPromises} deferred, {score.unpaidPromises} unpaid
              {score.medianDeferralTurns !== null && `, median deferral ${score.medianDeferralTurns} turns`}
            </TableCell>
          </TableRow>
          <TableRow>
            <TableCell className="font-medium">Humor</TableCell>
            <TableCell>
              <div className="flex items-center gap-2">
                <ScoreBar value={score.humorScore} />
                <span className={cn("tabular-nums font-medium", scoreBand(score.humorScore).text)}>
                  {formatScore(score.humorScore)}
                </span>
              </div>
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {score.humorBeats} beats, speaker balance {formatScore(score.humorSpeakerBalance)} (0.5 is even),
              reaction ratio {formatScore(score.humorReactionRatio)}
            </TableCell>
          </TableRow>
          <TableRow>
            <TableCell className="font-medium">Teasers</TableCell>
            <TableCell>
              <div className="flex items-center gap-2">
                <ScoreBar value={score.teaserScore} />
                <span className={cn("tabular-nums font-medium", scoreBand(score.teaserScore).text)}>
                  {formatScore(score.teaserScore)}
                </span>
              </div>
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {score.teaserTopics} distinct topics
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <AnchorTables anchors={anchors} turnCount={turnCount} onJumpToTurn={onJumpToTurn} />
    </div>
  );
}

function AnchorTables({
  anchors,
  turnCount,
  onJumpToTurn,
}: {
  anchors: ScriptJudgeAnchors;
  turnCount: number;
  onJumpToTurn: (turn: number) => void;
}) {
  const { promises, humorBeats, teaserTopics } = anchors;

  if (promises.length === 0 && humorBeats.length === 0 && teaserTopics.length === 0) {
    return <Empty>The judge returned no anchors for this score.</Empty>;
  }

  return (
    <div className="space-y-3">
      {promises.length > 0 && (
        <div>
          <h4 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">Promises</h4>
          <div className="flex flex-wrap gap-2 text-sm">
            {promises.map((promise: PromiseAnchor, i) => (
              <span key={i} className="rounded border border-border px-2 py-1">
                <TurnLink turn={promise.promiseTurn} turnCount={turnCount} onJumpToTurn={onJumpToTurn} />
                {promise.payoffTurn === null ? (
                  <span className="ml-1 text-muted-foreground">&rarr; unpaid</span>
                ) : (
                  <>
                    <span className="mx-1 text-muted-foreground">&rarr;</span>
                    <TurnLink turn={promise.payoffTurn} turnCount={turnCount} onJumpToTurn={onJumpToTurn} />
                  </>
                )}
              </span>
            ))}
          </div>
        </div>
      )}

      {humorBeats.length > 0 && (
        <div>
          <h4 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">Humor beats</h4>
          <div className="flex flex-wrap gap-2 text-sm">
            {humorBeats.map((beat: HumorAnchor, i) => (
              <span key={i} className="rounded border border-border px-2 py-1">
                <TurnLink turn={beat.turn} turnCount={turnCount} onJumpToTurn={onJumpToTurn} />
                <span className="ml-1 text-xs text-muted-foreground">
                  {beat.role}
                  {beat.reactsToPrevious ? ", reacts" : ", standalone"}
                </span>
              </span>
            ))}
          </div>
        </div>
      )}

      {teaserTopics.length > 0 && (
        <div>
          <h4 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">Teaser topics</h4>
          <p className="text-sm text-muted-foreground">{teaserTopics.join(", ")}</p>
        </div>
      )}
    </div>
  );
}

function ShapeSection({ metrics }: { metrics: ScriptMetrics }) {
  const laughTags = Object.entries(metrics.laughTagsByRole);

  return (
    <div className="space-y-2">
      <p className="text-sm text-muted-foreground">
        {metrics.turnCount} turns, {metrics.totalWords.toLocaleString()} words
      </p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Role</TableHead>
            <TableHead className="text-right">Turns</TableHead>
            <TableHead className="text-right">Words</TableHead>
            <TableHead className="text-right">Share</TableHead>
            <TableHead className="text-right">Median</TableHead>
            <TableHead className="text-right">Max</TableHead>
            <TableHead className="text-right">Over cap</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {metrics.roles.map((role) => {
            const lengths = metrics.turnLengthByRole[role.role];
            return (
              <TableRow key={role.role}>
                <TableCell className="font-medium">{role.role}</TableCell>
                <TableCell className="text-right tabular-nums">{role.turns}</TableCell>
                <TableCell className="text-right tabular-nums">{role.words.toLocaleString()}</TableCell>
                <TableCell className="text-right tabular-nums">{formatPercent(role.wordShare)}</TableCell>
                <TableCell className="text-right tabular-nums">{lengths?.medianWords ?? "—"}</TableCell>
                <TableCell className="text-right tabular-nums">{lengths?.maxWords ?? "—"}</TableCell>
                <TableCell className="text-right tabular-nums">{lengths?.turnsOverSentenceCap ?? "—"}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
      <p className="text-xs text-muted-foreground">
        &quot;Over cap&quot; counts turns longer than the compose prompt allows. Laugh tags:{" "}
        {laughTags.length === 0
          ? "none"
          : laughTags.map(([role, count]) => `${role} ${count}`).join(", ")}
        . A laugh tag is a proxy for humor distribution, never a humor count: a joke carrying no tag
        is invisible here.
      </p>
    </div>
  );
}

function OutliersSection({
  metrics,
  turnCount,
  onJumpToTurn,
}: {
  metrics: ScriptMetrics;
  turnCount: number;
  onJumpToTurn: (turn: number) => void;
}) {
  const { backchannelCandidates, sameSpeakerRuns } = metrics;

  return (
    <div className="space-y-3">
      <div>
        <h4 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
          Backchannel candidates
        </h4>
        {backchannelCandidates.length === 0 ? (
          <Empty>No turns matched the backchannel shape.</Empty>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-16">Turn</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead className="text-right">Words</TableHead>
                  <TableHead>Text</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {backchannelCandidates.map((candidate: BackchannelCandidate) => (
                  <TableRow key={candidate.turnIndex}>
                    <TableCell>
                      <TurnLink
                        turn={candidate.turnIndex}
                        turnCount={turnCount}
                        onJumpToTurn={onJumpToTurn}
                      />
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{candidate.role}</TableCell>
                    <TableCell className="text-right tabular-nums">{candidate.words}</TableCell>
                    <TableCell className="text-sm">{candidate.text}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <p className="mt-1 text-xs text-muted-foreground">
              A turn shaped like a token of listening. A backchannel is a wanted device, so this is a
              shape match to read, not a defect list.
            </p>
          </>
        )}
      </div>

      <div>
        <h4 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
          Same-speaker runs
        </h4>
        {sameSpeakerRuns.length === 0 ? (
          <Empty>No speaker held consecutive turns.</Empty>
        ) : (
          <div className="flex flex-wrap gap-2 text-sm">
            {sameSpeakerRuns.map((run, i) => (
              <span key={i} className="rounded border border-border px-2 py-1">
                <span className="mr-1 text-xs text-muted-foreground">{run.role}</span>
                {run.turnIndexes.map((turn, j) => (
                  <span key={turn}>
                    {j > 0 && <span className="text-muted-foreground">, </span>}
                    <TurnLink turn={turn} turnCount={turnCount} onJumpToTurn={onJumpToTurn} />
                  </span>
                ))}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function RunsSection({ runs }: { runs: EvaluationRun[] }) {
  return (
    <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Ran at</TableHead>
            <TableHead>Prompt</TableHead>
            <TableHead>Variety</TableHead>
            <TableHead>Model</TableHead>
            <TableHead className="text-right">Temp</TableHead>
            <TableHead>Cache</TableHead>
            <TableHead>Tools</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {runs.map((run) => (
            <TableRow key={run.id}>
              <TableCell className="text-xs">{formatTimestamp(run.ranAt)}</TableCell>
              <TableCell className="font-mono text-xs">{run.promptHash.slice(0, 8)}</TableCell>
              <TableCell className="text-xs text-muted-foreground">{run.varietySelection}</TableCell>
              <TableCell className="text-xs text-muted-foreground">{run.composeModel}</TableCell>
              <TableCell className="text-right tabular-nums">{run.temperature}</TableCell>
              <TableCell className="text-xs text-muted-foreground">
                {run.cacheBypassed ? "bypassed" : "used"}
                {run.cacheHit ? ", hit" : ""}
              </TableCell>
              <TableCell className="text-xs text-muted-foreground">{run.toolsFiredJson}</TableCell>
            </TableRow>
          ))}
      </TableBody>
    </Table>
  );
}

export function EvaluationTab({
  userId,
  podcastId,
  episodeId,
  turnCount,
  onJumpToTurn,
}: EvaluationTabProps) {
  const [scores, setScores] = useState<EpisodeScore[]>([]);
  const [metrics, setMetrics] = useState<ScriptMetrics | null>(null);
  const [runs, setRuns] = useState<EvaluationRun[]>([]);
  const [failed, setFailed] = useState<Record<Endpoint, boolean>>({
    scores: false,
    metrics: false,
    runs: false,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const base = `/api/users/${userId}/podcasts/${podcastId}/episodes/${episodeId}`;
    setLoading(true);
    // Clear the previous episode's results rather than relying on the loading gate to hide them:
    // a failure flag belonging to another episode is the kind of thing that surfaces later, when
    // someone lets sections render before every request has settled.
    setScores([]);
    setMetrics(null);
    setRuns([]);
    setFailed({ scores: false, metrics: false, runs: false });
    Promise.all([
      loadJson<EpisodeScore[]>(`${base}/scores`),
      loadJson<EpisodeMetricsResponse>(`${base}/metrics`),
      loadJson<EvaluationRun[]>(`${base}/evaluation-runs`),
    ])
      .then(([scoreResult, metricsResult, runsResult]) => {
        setScores(scoreResult.data ?? []);
        setMetrics(metricsResult.data?.metrics ?? null);
        setRuns(runsResult.data ?? []);
        setFailed({
          scores: scoreResult.failed,
          metrics: metricsResult.failed,
          runs: runsResult.failed,
        });
      })
      .finally(() => setLoading(false));
  }, [userId, podcastId, episodeId]);

  if (loading) {
    return <p className="text-muted-foreground">Loading evaluation...</p>;
  }

  // Newest first, so the row shown expanded is the most recent judgement. Rows from different
  // scorers describe different quantities and are never combined into one figure.
  const sortedScores = [...scores].sort((a, b) => b.scoredAt.localeCompare(a.scoredAt));
  const [newestScore, ...olderScores] = sortedScores;

  return (
    <div className="space-y-6">
      <Panel title="Attention score">
        {failed.scores ? (
          <LoadFailed what="the attention score" />
        ) : newestScore === undefined ? (
          <Empty>
            This episode has not been scored. Scoring runs after generation and is best-effort, so a
            failed or older episode can have no score.
          </Empty>
        ) : (
          <div className="space-y-3">
            <ScoreCard score={newestScore} turnCount={turnCount} onJumpToTurn={onJumpToTurn} />
            {olderScores.length > 0 && (
              <details>
                <summary className="cursor-pointer text-sm font-medium">
                  {olderScores.length} earlier score{olderScores.length === 1 ? "" : "s"} from other
                  scorers
                </summary>
                <div className="mt-2 space-y-3">
                  {olderScores.map((score) => (
                    <Panel key={score.id}>
                      <ScoreCard
                        score={score}
                        turnCount={turnCount}
                        onJumpToTurn={onJumpToTurn}
                      />
                    </Panel>
                  ))}
                </div>
              </details>
            )}
          </div>
        )}
      </Panel>

      <Panel title="Script shape">
        {failed.metrics ? (
          <LoadFailed what="the script metrics" />
        ) : metrics === null ? (
          <Empty>No script to measure for this episode.</Empty>
        ) : (
          <ShapeSection metrics={metrics} />
        )}
      </Panel>

      <Panel title="Outliers">
        {failed.metrics ? (
          <LoadFailed what="the script metrics" />
        ) : metrics === null ? (
          <Empty>No script to measure for this episode.</Empty>
        ) : (
          <OutliersSection metrics={metrics} turnCount={turnCount} onJumpToTurn={onJumpToTurn} />
        )}
      </Panel>

      <Panel title="Run conditions">
        {failed.runs ? (
          <LoadFailed what="the run conditions" />
        ) : runs.length === 0 ? (
          <Empty>
            No cache-bypassing run was recorded for this episode. Run conditions are stored only for
            a run that bypassed the LLM cache, which is what an ablation does.
          </Empty>
        ) : (
          <RunsSection runs={runs} />
        )}
      </Panel>
    </div>
  );
}
