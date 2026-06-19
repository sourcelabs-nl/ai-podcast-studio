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

interface OldestTrack {
  id: number;
  title: string | null;
  createdAt: string | null;
  duration: number | null;
}

// After deleting the oldest track, SoundCloud needs time to free the quota before a re-upload
// succeeds. Retry the publish on a spaced schedule (ms before each attempt) rather than immediately.
const RETRY_DELAYS_MS = [8000, 15000, 30000];

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

type PublishOutcome =
  | { kind: "ok"; data: EpisodePublication }
  | { kind: "quota"; message: string; oldestTrack: OldestTrack | null }
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
  const [oldestTrack, setOldestTrack] = useState<OldestTrack | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [retryAttempt, setRetryAttempt] = useState(0);
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
    setOldestTrack(null);
    setDeleting(false);
    setRetrying(false);
    setRetryAttempt(0);
  }

  function handleClose(isOpen: boolean) {
    if (!isOpen) {
      if (result) onPublished();
      reset();
    }
    onOpenChange(isOpen);
  }

  // Single publish POST, classified into an outcome. No state mutation — callers decide what to do
  // so this is reusable for both the initial publish and the post-deletion auto-retry loop.
  async function doPublishRequest(): Promise<PublishOutcome> {
    const label = TARGETS.find((t) => t.value === target)?.label ?? target;
    try {
      const res = await fetch(
        `/api/users/${userId}/podcasts/${podcastId}/episodes/${episode.id}/publish/${target}`,
        { method: "POST" }
      );
      if (res.ok) return { kind: "ok", data: await res.json() };
      const body = await res.json().catch(() => ({}));
      if (res.status === 401) return { kind: "auth", message: body.error || "Authorization expired" };
      if (res.status === 413) return { kind: "quota", message: body.error || "Upload quota exceeded", oldestTrack: body.oldestTrack ?? null };
      if (res.status === 409) return { kind: "conflict", message: `This episode has already been published to ${label}.` };
      return { kind: "error", message: body.error || "Publishing failed" };
    } catch {
      return { kind: "error", message: "Network error — could not reach the server." };
    }
  }

  function applyOutcome(outcome: PublishOutcome) {
    if (outcome.kind === "ok") {
      setResult(outcome.data);
      setError(null);
    } else if (outcome.kind === "auth") {
      setError(outcome.message);
      setIsOAuthExpired(true);
    } else if (outcome.kind === "quota") {
      setError(outcome.message);
      setOldestTrack(outcome.oldestTrack);
    } else {
      setError(outcome.message);
    }
    setStep("result");
  }

  async function handlePublish() {
    setPublishing(true);
    setError(null);
    setOldestTrack(null);
    const outcome = await doPublishRequest();
    applyOutcome(outcome);
    setPublishing(false);
  }

  // Confirm-once-then-auto: delete the oldest track, then wait for SoundCloud to free the quota and
  // retry publishing automatically on a spaced schedule (re-uploading immediately fails because the
  // deletion has not propagated yet).
  async function handleDeleteOldestAndRetry() {
    if (!oldestTrack) return;
    setDeleting(true);
    setError(null);
    let deleted = false;
    try {
      const res = await fetch(
        `/api/users/${userId}/oauth/soundcloud/tracks/${oldestTrack.id}`,
        { method: "DELETE" }
      );
      deleted = res.ok;
      if (!res.ok) {
        const body = await res.json().catch(() => ({ error: "Failed to delete" }));
        setError(body.error || "Failed to delete track from SoundCloud");
      }
    } catch {
      setError("Network error — could not reach the server.");
    } finally {
      setDeleting(false);
    }
    if (!deleted) return;

    setOldestTrack(null);
    setRetrying(true);
    let outcome: PublishOutcome | null = null;
    for (let i = 0; i < RETRY_DELAYS_MS.length; i++) {
      setRetryAttempt(i + 1);
      await sleep(RETRY_DELAYS_MS[i]);
      outcome = await doPublishRequest();
      if (outcome.kind !== "quota") break; // success or a different failure — stop retrying
    }
    setRetrying(false);
    setRetryAttempt(0);

    if (outcome?.kind === "quota") {
      // Still over quota after all retries: deletion is slow to propagate, or one track was not
      // enough. Surface the quota state again so the user can wait or remove another track.
      setError("SoundCloud is still freeing up space. Wait a moment and try publishing again, or remove another track.");
      setOldestTrack(outcome.oldestTrack);
      setStep("result");
    } else if (outcome) {
      applyOutcome(outcome);
    }
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
                {retrying ? "Publishing…" : error ? "Publication Failed" : "Published Successfully"}
              </DialogTitle>
            </DialogHeader>
            <div className="py-2">
              {retrying ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>
                    Freeing space on SoundCloud and republishing… (attempt {retryAttempt} of {RETRY_DELAYS_MS.length})
                  </span>
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
                  {oldestTrack && (
                    <div className="rounded-md border border-border bg-muted/50 p-3 text-sm">
                      <p className="mb-2">
                        Remove the oldest track from SoundCloud to free up space and republish automatically?
                      </p>
                      <p className="mb-2 text-muted-foreground">
                        &ldquo;{oldestTrack.title}&rdquo;
                      </p>
                      <Button
                        size="sm"
                        variant="destructive"
                        onClick={handleDeleteOldestAndRetry}
                        disabled={deleting}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        {deleting ? "Removing..." : "Remove & republish"}
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
              <Button onClick={() => handleClose(false)} disabled={retrying}>Done</Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
