"use client";

import { useState, useEffect } from "react";
import type { Episode, EpisodePublication } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { KeyRound, Loader2, Trash2 } from "lucide-react";

export const TARGETS = [
  { value: "soundcloud", label: "SoundCloud" },
  { value: "ftp", label: "FTP" },
] as const;

type Step = "select" | "confirm" | "result";

interface QuotaTrack {
  id: number;
  title: string | null;
  createdAt: string | null;
  durationSeconds: number;
}

interface QuotaPlan {
  tracksToDelete: QuotaTrack[];
  secondsToFree: number;
}

type PublishOutcome =
  | { kind: "ok"; data: EpisodePublication }
  | { kind: "quota"; message: string; plan: QuotaPlan }
  | { kind: "auth"; message: string }
  | { kind: "conflict"; message: string }
  | { kind: "error"; message: string };

interface PublishWizardProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  episode: Episode;
  podcastName: string;
  userId: string;
  podcastId: string;
  onPublished: () => void;
}

function parseQuotaPlan(body: { secondsToFree?: number; tracksToDelete?: QuotaTrack[] }): QuotaPlan {
  return {
    tracksToDelete: body.tracksToDelete ?? [],
    secondsToFree: body.secondsToFree ?? 0,
  };
}

function formatMinutes(seconds: number): string {
  const mins = Math.round(seconds / 60);
  return mins <= 1 ? "about a minute" : `about ${mins} minutes`;
}

export function PublishWizard({
  open,
  onOpenChange,
  episode,
  podcastName,
  userId,
  podcastId,
  onPublished,
}: PublishWizardProps) {
  const [step, setStep] = useState<Step>("select");
  const [target, setTarget] = useState<string>(TARGETS[0].value);
  const [publishing, setPublishing] = useState(false);
  const [result, setResult] = useState<EpisodePublication | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isOAuthExpired, setIsOAuthExpired] = useState(false);
  const [quotaPlan, setQuotaPlan] = useState<QuotaPlan | null>(null);
  const [freeing, setFreeing] = useState(false);
  const [publications, setPublications] = useState<EpisodePublication[]>([]);

  useEffect(() => {
    if (!open) return;
    fetch(`/api/users/${userId}/podcasts/${podcastId}/episodes/${episode.id}/publications`)
      .then((res) => (res.ok ? res.json() : []))
      .then(setPublications)
      .catch(() => setPublications([]));
  }, [open, userId, podcastId, episode.id]);

  function reset() {
    setStep("select");
    setTarget(TARGETS[0].value);
    setPublishing(false);
    setResult(null);
    setError(null);
    setIsOAuthExpired(false);
    setQuotaPlan(null);
    setFreeing(false);
  }

  function handleClose(isOpen: boolean) {
    if (!isOpen) {
      if (result) onPublished();
      reset();
    }
    onOpenChange(isOpen);
  }

  // Single POST, classified into an outcome. The server owns all quota arithmetic and track
  // selection; the client only forwards the user's consent. `path` lets us reuse this for both
  // the initial publish and the consent-driven free-and-publish call.
  async function postPublish(path: string, body?: unknown): Promise<PublishOutcome> {
    const label = TARGETS.find((t) => t.value === target)?.label ?? target;
    try {
      const res = await fetch(
        `/api/users/${userId}/podcasts/${podcastId}/episodes/${episode.id}/${path}`,
        body === undefined
          ? { method: "POST" }
          : { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }
      );
      if (res.ok) return { kind: "ok", data: await res.json() };
      const resBody = await res.json().catch(() => ({}));
      if (res.status === 401) return { kind: "auth", message: resBody.error || "Authorization expired" };
      if (res.status === 413) return { kind: "quota", message: resBody.error || "Upload quota full", plan: parseQuotaPlan(resBody) };
      if (res.status === 409) return { kind: "conflict", message: `This episode has already been published to ${label}.` };
      return { kind: "error", message: resBody.error || "Publishing failed" };
    } catch {
      return { kind: "error", message: "Network error — could not reach the server." };
    }
  }

  function applyOutcome(outcome: PublishOutcome) {
    if (outcome.kind === "ok") {
      setResult(outcome.data);
      setError(null);
      setQuotaPlan(null);
    } else if (outcome.kind === "auth") {
      setError(outcome.message);
      setIsOAuthExpired(true);
    } else if (outcome.kind === "quota") {
      setError(outcome.message);
      setQuotaPlan(outcome.plan);
    } else {
      setError(outcome.message);
    }
    setStep("result");
  }

  async function handlePublish() {
    setPublishing(true);
    setError(null);
    setQuotaPlan(null);
    const outcome = await postPublish(`publish/${target}`);
    applyOutcome(outcome);
    setPublishing(false);
  }

  // Consent given: ask the server to delete the planned tracks and publish in one operation. The
  // server re-checks the quota, so if more space is still needed it returns a fresh plan.
  async function handleFreeAndPublish() {
    if (!quotaPlan) return;
    setFreeing(true);
    setError(null);
    const trackIds = quotaPlan.tracksToDelete.map((t) => t.id);
    const outcome = await postPublish(`publish/${target}/free-and-publish`, { trackIds });
    applyOutcome(outcome);
    setFreeing(false);
  }

  const targetLabel = TARGETS.find((t) => t.value === target)?.label ?? target;

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        {step === "select" && (
          <>
            <DialogHeader>
              <DialogTitle>Publish Episode #{episode.id}</DialogTitle>
              <DialogDescription>Select a provider to publish to.</DialogDescription>
            </DialogHeader>
            <div className="flex flex-col gap-2 py-2">
              {TARGETS.map((t) => {
                const pub = publications.find((p) => p.target === t.value);
                return (
                  <button
                    key={t.value}
                    onClick={() => setTarget(t.value)}
                    className={`flex items-center justify-between rounded-md border p-3 text-left transition-colors ${
                      target === t.value
                        ? "border-primary bg-primary/5"
                        : "border-border hover:bg-accent"
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{t.label}</span>
                      {pub?.status === "PUBLISHED" && (
                        <Badge variant="outline" className="text-[11px] px-1.5 py-px">Published</Badge>
                      )}
                      {pub?.status === "FAILED" && (
                        <Badge variant="destructive" className="text-[11px] px-1.5 py-px">Failed</Badge>
                      )}
                      {pub?.status === "UNPUBLISHED" && (
                        <Badge variant="secondary" className="text-[11px] px-1.5 py-px">Unpublished</Badge>
                      )}
                    </div>
                    {target === t.value && (
                      <Badge>Selected</Badge>
                    )}
                  </button>
                );
              })}
            </div>
            <DialogFooter>
              <Button onClick={() => setStep("confirm")}>Next</Button>
            </DialogFooter>
          </>
        )}

        {step === "confirm" && (
          <>
            <DialogHeader>
              <DialogTitle>Confirm Publication</DialogTitle>
              <DialogDescription>
                Review the details before publishing.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-2 py-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Podcast</span>
                <span className="font-medium">{podcastName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Episode</span>
                <span className="font-medium">#{episode.id}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Date</span>
                <span>{new Date(episode.generatedAt).toLocaleDateString()}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Target</span>
                <Badge>{targetLabel}</Badge>
              </div>
              {episode.recap && (
                <div className="pt-2">
                  <span className="text-muted-foreground">Summary</span>
                  <p className="mt-1 text-foreground">{episode.recap}</p>
                </div>
              )}
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setStep("select")}>
                Back
              </Button>
              <Button onClick={handlePublish} disabled={publishing}>
                {publishing ? "Publishing..." : "Publish"}
              </Button>
            </DialogFooter>
          </>
        )}

        {step === "result" && (
          <>
            <DialogHeader>
              <DialogTitle>
                {freeing ? "Publishing…" : error ? "Publication Failed" : "Published Successfully"}
              </DialogTitle>
            </DialogHeader>
            <div className="py-2">
              {freeing ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>Freeing space on SoundCloud and publishing…</span>
                </div>
              ) : error ? (
                <div className="space-y-3">
                  <p className="text-sm text-destructive">{error}</p>
                  {isOAuthExpired && (
                    <Button
                      size="sm"
                      onClick={async () => {
                        try {
                          const res = await fetch(`/api/users/${userId}/oauth/soundcloud/authorize`);
                          if (res.ok) {
                            const data = await res.json();
                            window.open(data.authorizationUrl, "_blank");
                          }
                        } catch {
                          // ignore network errors
                        }
                      }}
                    >
                      <KeyRound className="mr-2 h-4 w-4" />
                      Re-authorize SoundCloud
                    </Button>
                  )}
                  {quotaPlan && quotaPlan.tracksToDelete.length > 0 && (
                    <div className="rounded-md border border-border bg-muted/50 p-3 text-sm">
                      <p className="mb-2">
                        To free {formatMinutes(quotaPlan.secondsToFree)}, the following{" "}
                        {quotaPlan.tracksToDelete.length === 1 ? "track" : `${quotaPlan.tracksToDelete.length} tracks`}{" "}
                        will be permanently removed from SoundCloud, then this episode is published:
                      </p>
                      <ul className="mb-3 list-disc space-y-0.5 pl-5 text-muted-foreground">
                        {quotaPlan.tracksToDelete.map((t) => (
                          <li key={t.id}>&ldquo;{t.title ?? `Track ${t.id}`}&rdquo;</li>
                        ))}
                      </ul>
                      <Button
                        size="sm"
                        variant="destructive"
                        onClick={handleFreeAndPublish}
                        disabled={freeing}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        Remove &amp; publish
                      </Button>
                    </div>
                  )}
                </div>
              ) : result ? (
                <div className="space-y-2 text-sm">
                  <p>Episode #{episode.id} has been published to {targetLabel}.</p>
                  {result.externalUrl && (
                    <div className="flex items-center gap-2">
                      <a
                        href={result.externalUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-primary underline"
                      >
                        Link
                      </a>
                      {result.target === "soundcloud" && (() => {
                        const match = result.externalUrl?.match(/^https:\/\/soundcloud\.com\/([^/]+)\//);
                        if (!match) return null;
                        return (
                          <>
                            <span className="text-muted-foreground">|</span>
                            <a
                              href={`https://soundcloud.com/${match[1]}/sets`}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-primary underline"
                            >
                              Playlist
                            </a>
                          </>
                        );
                      })()}
                    </div>
                  )}
                </div>
              ) : null}
            </div>
            <DialogFooter>
              <Button onClick={() => handleClose(false)} disabled={freeing}>Done</Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
