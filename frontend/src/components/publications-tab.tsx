"use client";

import { useEffect, useState } from "react";
import type { PagedResponse, PodcastPublicationRow } from "@/lib/types";
import { Cloud, RefreshCw, Server, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Paginator } from "@/components/paginator";

interface PublicationsTabProps {
  userId: string;
  podcastId: string;
  /** When set, scopes the listing to a single episode (used by the episode detail page). */
  episodeId?: number;
  /** When `episodeId` is set, supplies the episode's generated-at timestamp for the table rows. */
  episodeGeneratedAt?: string;
  refreshKey: number;
  onRepublished: () => void;
}

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  PUBLISHED: "default",
  PENDING: "default",
  FAILED: "default",
  UNPUBLISHED: "secondary",
};

const DEFAULT_PAGE_SIZE = 20;

export function PublicationsTab({
  userId,
  podcastId,
  episodeId,
  episodeGeneratedAt,
  refreshKey,
  onRepublished,
}: PublicationsTabProps) {
  const [rows, setRows] = useState<PodcastPublicationRow[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(true);
  const [confirmRow, setConfirmRow] = useState<PodcastPublicationRow | null>(null);
  const [confirmAction, setConfirmAction] = useState<"republish" | "unpublish">("republish");
  const [actionInProgress, setActionInProgress] = useState(false);

  useEffect(() => {
    setLoading(true);
    if (episodeId != null) {
      // Single-episode mode: existing per-episode endpoint returns a flat list (small).
      fetch(`/api/users/${userId}/podcasts/${podcastId}/episodes/${episodeId}/publications`)
        .then((res) => (res.ok ? res.json() : []))
        .then((pubs: { id: number; episodeId: number; target: string; status: string; externalId: string | null; externalUrl: string | null; errorMessage: string | null; publishedAt: string | null; createdAt: string }[]) => {
          const generatedAt = episodeGeneratedAt ?? "";
          setRows(pubs.map((p) => ({
            publication: p,
            episode: { id: episodeId, generatedAt, status: "" },
          })));
          setTotal(pubs.length);
          setTotalPages(1);
        })
        .catch(() => {
          setRows([]);
          setTotal(0);
          setTotalPages(0);
        })
        .finally(() => setLoading(false));
      return;
    }
    fetch(
      `/api/users/${userId}/podcasts/${podcastId}/publications?page=${page}&pageSize=${pageSize}`
    )
      .then((res) => (res.ok ? res.json() : { items: [], total: 0, totalPages: 0 }))
      .then((data: PagedResponse<PodcastPublicationRow>) => {
        setRows(data.items);
        setTotal(data.total);
        setTotalPages(data.totalPages);
      })
      .catch(() => {
        setRows([]);
        setTotal(0);
        setTotalPages(0);
      })
      .finally(() => setLoading(false));
  }, [userId, podcastId, episodeId, episodeGeneratedAt, page, pageSize, refreshKey]);

  async function handleRepublish() {
    if (!confirmRow) return;
    setActionInProgress(true);
    try {
      await fetch(
        `/api/users/${userId}/podcasts/${podcastId}/episodes/${confirmRow.publication.episodeId}/publish/${confirmRow.publication.target}`,
        { method: "POST" }
      );
      onRepublished();
    } finally {
      setActionInProgress(false);
      setConfirmRow(null);
    }
  }

  async function handleUnpublish() {
    if (!confirmRow) return;
    setActionInProgress(true);
    try {
      await fetch(
        `/api/users/${userId}/podcasts/${podcastId}/episodes/${confirmRow.publication.episodeId}/publications/${confirmRow.publication.target}`,
        { method: "DELETE" }
      );
      onRepublished();
    } finally {
      setActionInProgress(false);
      setConfirmRow(null);
    }
  }

  function openConfirm(row: PodcastPublicationRow, action: "republish" | "unpublish") {
    setConfirmRow(row);
    setConfirmAction(action);
  }

  if (loading) {
    return <p className="text-muted-foreground">Loading publications...</p>;
  }

  if (rows.length === 0) {
    return <p className="text-muted-foreground">No publications found.</p>;
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-12">#</TableHead>
            <TableHead className="w-24">Date</TableHead>
            <TableHead className="w-12">Day</TableHead>
            <TableHead className="w-24">Published</TableHead>
            <TableHead className="w-24">Status</TableHead>
            <TableHead className="w-32">Target</TableHead>
            <TableHead>URL</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => {
            const pub = row.publication;
            return (
              <TableRow key={pub.id}>
                <TableCell className="font-medium">{row.episode.id}</TableCell>
                <TableCell className="text-sm">
                  {new Date(row.episode.generatedAt).toLocaleDateString()}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {new Date(row.episode.generatedAt).toLocaleDateString(undefined, { weekday: "short" })}
                </TableCell>
                <TableCell className="text-sm">
                  {pub.publishedAt
                    ? new Date(pub.publishedAt).toLocaleDateString()
                    : "—"}
                </TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[pub.status] ?? "default"} className="text-[11px] px-1.5 py-px">{pub.status}</Badge>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-1.5">
                    {pub.target === "soundcloud" && <Cloud className="size-4 text-muted-foreground" />}
                    {pub.target === "ftp" && <Server className="size-4 text-muted-foreground" />}
                    <span>{pub.target === "soundcloud" ? "SoundCloud" : pub.target.toUpperCase()}</span>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    {pub.externalUrl ? (
                      <>
                        <a
                          href={pub.externalUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-primary underline"
                        >
                          Track
                        </a>
                        {pub.target === "soundcloud" && (() => {
                          const match = pub.externalUrl?.match(/^https:\/\/soundcloud\.com\/([^/]+)\//);
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
                        {pub.target === "ftp" && (() => {
                          const episodesIdx = pub.externalUrl?.lastIndexOf("/episodes/");
                          if (episodesIdx == null || episodesIdx < 0) return null;
                          const feedUrl = pub.externalUrl!.substring(0, episodesIdx + 1) + "feed.xml";
                          return (
                            <>
                              <span className="text-muted-foreground">|</span>
                              <a
                                href={feedUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-primary underline"
                              >
                                Feed
                              </a>
                            </>
                          );
                        })()}
                      </>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </div>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1">
                    <Button
                      size="icon-lg"
                      title="Republish"
                      onClick={() => openConfirm(row, "republish")}
                    >
                      <RefreshCw className="size-4" />
                    </Button>
                    {pub.status === "PUBLISHED" && (
                      <Button
                        size="icon-lg"
                        variant="destructive"
                        title="Unpublish"
                        onClick={() => openConfirm(row, "unpublish")}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      {episodeId == null && (
        <Paginator
          page={page}
          pageSize={pageSize}
          total={total}
          totalPages={totalPages}
          onPageChange={setPage}
          onPageSizeChange={(s) => { setPageSize(s); setPage(0); }}
        />
      )}

      <Dialog open={!!confirmRow} onOpenChange={(open) => { if (!open) setConfirmRow(null); }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{confirmAction === "republish" ? "Republish" : "Unpublish"} Episode</DialogTitle>
            <DialogDescription>
              Are you sure you want to {confirmAction} episode #{confirmRow?.episode.id} {confirmAction === "republish" ? "to" : "from"} {confirmRow?.publication.target === "soundcloud" ? "SoundCloud" : confirmRow?.publication.target}?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmRow(null)}>
              Cancel
            </Button>
            {confirmAction === "republish" ? (
              <Button onClick={handleRepublish} disabled={actionInProgress}>
                {actionInProgress ? "Republishing..." : "Republish"}
              </Button>
            ) : (
              <Button variant="destructive" onClick={handleUnpublish} disabled={actionInProgress}>
                {actionInProgress ? "Unpublishing..." : "Unpublish"}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
