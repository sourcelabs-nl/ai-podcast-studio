"use client";

import { useEffect, useState } from "react";
import type { Source } from "@/lib/types";
import { Check, ChevronDown, Download, Pencil, Plus, Trash2 } from "lucide-react";
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
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

const SOURCE_TYPES = ["rss", "website", "twitter", "youtube"] as const;

interface SourcesTabProps {
  userId: string;
  podcastId: string;
}

interface SourceFormData {
  type: string;
  url: string;
  label: string;
  pollIntervalMinutes: number;
  enabled: boolean;
}

const defaultFormData: SourceFormData = {
  type: "rss",
  url: "",
  label: "",
  pollIntervalMinutes: 30,
  enabled: true,
};

export function SourcesTab({ userId, podcastId }: SourcesTabProps) {
  const [sources, setSources] = useState<Source[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingSource, setEditingSource] = useState<Source | null>(null);
  const [formData, setFormData] = useState<SourceFormData>(defaultFormData);
  const [deleteSource, setDeleteSource] = useState<Source | null>(null);
  const [saving, setSaving] = useState(false);
  // Retired sources are disabled rather than deleted, so they pile up and would otherwise dominate
  // the table. Open on the ones that actually run; the filter is applied by the backend.
  const [enabledFilter, setEnabledFilter] = useState<"all" | "enabled" | "disabled">("enabled");

  function fetchSources() {
    const qs = enabledFilter === "all" ? "" : `?enabled=${enabledFilter === "enabled"}`;
    fetch(`/api/users/${userId}/podcasts/${podcastId}/sources${qs}`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => setSources(data))
      .catch(() => setSources([]))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    fetchSources();
  }, [userId, podcastId, enabledFilter]);

  function openAddDialog() {
    setEditingSource(null);
    setFormData(defaultFormData);
    setDialogOpen(true);
  }

  function openEditDialog(source: Source) {
    setEditingSource(source);
    setFormData({
      type: source.type,
      url: source.url,
      label: source.label ?? "",
      pollIntervalMinutes: source.pollIntervalMinutes,
      enabled: source.enabled,
    });
    setDialogOpen(true);
  }

  async function handleSubmit() {
    setSaving(true);
    const body = {
      type: formData.type,
      url: formData.url,
      label: formData.label || null,
      pollIntervalMinutes: formData.pollIntervalMinutes,
      enabled: formData.enabled,
    };

    try {
      if (editingSource) {
        await fetch(
          `/api/users/${userId}/podcasts/${podcastId}/sources/${editingSource.id}`,
          { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }
        );
      } else {
        await fetch(
          `/api/users/${userId}/podcasts/${podcastId}/sources`,
          { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }
        );
      }
      setDialogOpen(false);
      fetchSources();
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteSource) return;
    await fetch(
      `/api/users/${userId}/podcasts/${podcastId}/sources/${deleteSource.id}`,
      { method: "DELETE" }
    );
    setDeleteSource(null);
    fetchSources();
  }

  function downloadSourcesMarkdown() {
    const typeGroups: Record<string, Source[]> = {};
    for (const source of sources) {
      const type = source.type;
      if (!typeGroups[type]) typeGroups[type] = [];
      typeGroups[type].push(source);
    }

    let md = "# Sources\n";
    for (const [type, group] of Object.entries(typeGroups)) {
      md += `\n## ${type.charAt(0).toUpperCase() + type.slice(1)}\n\n`;
      for (const s of group) {
        const label = s.label
          ? `[${s.label}](${s.url})`
          : s.url;
        const disabled = s.enabled ? "" : " (disabled)";
        md += `- ${label} - ${s.pollIntervalMinutes}m${disabled}\n`;
      }
    }

    const blob = new Blob([md], { type: "text/markdown" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "sources.md";
    a.click();
    URL.revokeObjectURL(url);
  }

  if (loading) {
    return <p className="text-muted-foreground">Loading sources...</p>;
  }

  return (
    <>
      <div className="flex justify-end gap-2 mb-4">
        <Button size="icon-lg" title="Download sources as markdown" onClick={downloadSourcesMarkdown}>
          <Download className="size-4" />
        </Button>
        <Button size="icon-lg" title="Add source" onClick={openAddDialog}>
          <Plus className="size-4" />
        </Button>
      </div>

      {sources.length === 0 ? (
        <p className="text-muted-foreground">
          {enabledFilter === "all" ? "No sources configured." : `No ${enabledFilter} sources.`}
        </p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-0">Label</TableHead>
              <TableHead className="w-0">Type</TableHead>
              <TableHead className="w-0">Interval</TableHead>
              <TableHead className="w-0">
                <DropdownMenu>
                  <DropdownMenuTrigger className="flex items-center gap-1 hover:text-foreground transition-colors">
                    {enabledFilter === "disabled" ? "Disabled" : "Enabled"}
                    {enabledFilter !== "all" && <span className="text-primary">&nbsp;only</span>}
                    <ChevronDown className="size-3.5" />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start">
                    {(["all", "enabled", "disabled"] as const).map((option) => (
                      <DropdownMenuCheckboxItem
                        key={option}
                        checked={enabledFilter === option}
                        onCheckedChange={() => setEnabledFilter(option)}
                      >
                        {option === "all" ? "All sources" : option}
                      </DropdownMenuCheckboxItem>
                    ))}
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableHead>
              <TableHead className="w-0">Articles</TableHead>
              <TableHead className="w-0">Posts</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sources.map((source) => (
              <TableRow key={source.id} className="cursor-pointer" onClick={() => openEditDialog(source)}>
                <TableCell className="text-sm whitespace-nowrap">
                  {source.label ? (
                    <>
                      <span
                        className="cursor-pointer hover:underline"
                        onClick={() => openEditDialog(source)}
                      >
                        {source.label}
                      </span>
                      <a
                        href={source.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="ml-2 text-primary underline text-xs"
                        onClick={(e) => e.stopPropagation()}
                      >
                        {source.url}
                      </a>
                    </>
                  ) : (
                    <a
                      href={source.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-primary underline"
                      onClick={(e) => e.stopPropagation()}
                    >
                      {source.url}
                    </a>
                  )}
                </TableCell>
                <TableCell>
                  <Badge variant="outline" className="text-[11px] px-1.5 py-px">
                    {source.type}
                  </Badge>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {source.pollIntervalMinutes}m
                </TableCell>
                <TableCell>
                  {!source.enabled ? (
                    <span className="text-sm text-muted-foreground">—</span>
                  ) : source.hostBreakerOpen ? (
                    // Enabled, but not actually being polled: the whole host looks down.
                    <Badge
                      variant="destructive"
                      className="text-[11px] px-1.5 py-px"
                      title={`${source.host} is not responding across all ${source.hostSourceCount} of its sources — polling is paused until it recovers`}
                    >
                      host down
                    </Badge>
                  ) : (
                    <Check className="size-4 text-primary" />
                  )}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                  {source.articleCount > 0
                    ? `${source.articleCount} (${Math.round((source.relevantArticleCount / source.articleCount) * 100)}% relevant)`
                    : "0"}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {source.postCount}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-2" onClick={(e) => e.stopPropagation()}>
                    <Button
                      size="icon-lg"
                      title="Edit source"
                      onClick={() => openEditDialog(source)}
                    >
                      <Pencil className="size-4" />
                    </Button>
                    <Button
                      size="icon-lg"
                      variant="destructive"
                      title="Delete source"
                      onClick={() => setDeleteSource(source)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={dialogOpen} onOpenChange={(open) => { if (!open) setDialogOpen(false); }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingSource ? "Edit Source" : "Add Source"}</DialogTitle>
            <DialogDescription>
              {editingSource
                ? "Update the source configuration."
                : "Add a new content source for this podcast."}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="source-type">Type</Label>
              <Select value={formData.type} onValueChange={(v) => setFormData({ ...formData, type: v })}>
                <SelectTrigger id="source-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SOURCE_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>{t}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="source-url">URL</Label>
              <Input
                id="source-url"
                value={formData.url}
                onChange={(e) => setFormData({ ...formData, url: e.target.value })}
                placeholder="https://example.com/feed.xml"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="source-label">Label (optional)</Label>
              <Input
                id="source-label"
                value={formData.label}
                onChange={(e) => setFormData({ ...formData, label: e.target.value })}
                placeholder="My RSS Feed"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="source-interval">Poll interval (minutes)</Label>
              <Input
                id="source-interval"
                type="number"
                min={1}
                value={formData.pollIntervalMinutes}
                onChange={(e) => setFormData({ ...formData, pollIntervalMinutes: parseInt(e.target.value) || 30 })}
              />
            </div>
            <div className="flex items-center gap-2">
              <Switch
                id="source-enabled"
                checked={formData.enabled}
                onCheckedChange={(checked) => setFormData({ ...formData, enabled: checked })}
              />
              <Label htmlFor="source-enabled">Enabled</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleSubmit} disabled={saving || !formData.url}>
              {saving ? "Saving..." : editingSource ? "Update" : "Create"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={!!deleteSource} onOpenChange={(open) => { if (!open) setDeleteSource(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Source</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete the source &quot;{deleteSource?.label ?? deleteSource?.url}&quot; and all its associated articles. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete}>Delete</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
