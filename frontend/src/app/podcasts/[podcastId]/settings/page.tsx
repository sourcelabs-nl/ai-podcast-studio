"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useUser } from "@/lib/user-context";
import type { Podcast, PodcastDefaults, ModelReference, AvailableModel } from "@/lib/types";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Switch } from "@/components/ui/switch";
import { Upload, Trash2, ImageIcon, Save, Settings2, Wifi, WifiOff, TestTube, AlertTriangle } from "lucide-react";
import { useTabParam } from "@/hooks/use-tab-param";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { KeyValueEditor } from "@/components/key-value-editor";
import { SubtopicsEditor } from "@/components/subtopics-editor";
import { cn } from "@/lib/utils";

const STYLES = [
  "news-briefing",
  "casual",
  "deep-dive",
  "executive-summary",
  "dialogue",
  "interview",
];

const TTS_PROVIDERS = ["openai", "elevenlabs", "inworld"];

const TABS = ["general", "llm", "tts", "compose", "research", "publishing", "danger"] as const;

interface PublicationTarget {
  target: string;
  config: Record<string, string>;
  enabled: boolean;
  autoPublish: boolean;
}

interface ProviderConfig {
  category: string;
  provider: string;
}

function FieldLabel({ children }: { children: React.ReactNode }) {
  return (
    <label className="text-sm font-medium leading-none">{children}</label>
  );
}

function FieldGroup({ label, description, children }: { label: string; description?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <FieldLabel>{label}</FieldLabel>
      {description && <p className="text-xs text-muted-foreground">{description}</p>}
      {children}
    </div>
  );
}

const inputClass =
  "h-9 w-full rounded-md border border-input bg-background px-3 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50";

const textareaClass =
  "w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 min-h-[80px]";

export default function PodcastSettingsPage() {
  const params = useParams<{ podcastId: string }>();
  const router = useRouter();
  const { selectedUser, loading: userLoading } = useUser();
  const [podcast, setPodcast] = useState<Podcast | null>(null);
  const [form, setForm] = useState<Podcast | null>(null);
  const [loading, setLoading] = useState(true);
  const [defaults, setDefaults] = useState<PodcastDefaults | null>(null);
  const [saving, setSaving] = useState(false);
  const [currentTab, setTab] = useTabParam("general", TABS);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [imageUploading, setImageUploading] = useState(false);

  // Publishing targets state
  const [pubTargets, setPubTargets] = useState<PublicationTarget[]>([]);
  const [pubForm, setPubForm] = useState<Record<string, { config: Record<string, string>; enabled: boolean; autoPublish: boolean }>>({});
  const [configuredProviders, setConfiguredProviders] = useState<Set<string>>(new Set());
  const [pubTab, setPubTab] = useState<"ftp" | "soundcloud">("ftp");

  // Research state
  const [testingTavily, setTestingTavily] = useState(false);

  // Delete podcast state
  const [deleteConfirmText, setDeleteConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!selectedUser) return;
    setLoading(true);
    Promise.all([
      fetch(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}`)
        .then((res) => { if (!res.ok) throw new Error("Not found"); return res.json(); }),
      fetch("/api/config/defaults")
        .then((res) => (res.ok ? res.json() : null))
        .catch(() => null),
    ])
      .then(([podcastData, defaultsData]: [Podcast, PodcastDefaults | null]) => {
        setPodcast(podcastData);
        setForm(podcastData);
        setDefaults(defaultsData);
      })
      .catch(() => setPodcast(null))
      .finally(() => setLoading(false));
  }, [selectedUser, params.podcastId]);

  useEffect(() => {
    if (!selectedUser) return;
    fetch(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}/image`)
      .then((res) => {
        if (res.ok) {
          setImageUrl(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}/image?t=${Date.now()}`);
        } else {
          setImageUrl(null);
        }
      })
      .catch(() => setImageUrl(null));
  }, [selectedUser, params.podcastId]);

  // Load publication targets and check which providers have credentials configured
  const loadPubTargets = useCallback(() => {
    if (!selectedUser) return;
    Promise.all([
      fetch(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}/publication-targets`)
        .then((res) => (res.ok ? res.json() : []))
        .catch(() => []),
      fetch(`/api/users/${selectedUser.id}/api-keys`)
        .then((res) => (res.ok ? res.json() : []))
        .catch(() => []),
    ]).then(([targets, apiKeys]: [PublicationTarget[], ProviderConfig[]]) => {
      setPubTargets(targets);
      const formState: Record<string, { config: Record<string, string>; enabled: boolean; autoPublish: boolean }> = {};
      // Initialize defaults for ftp and soundcloud
      formState.ftp = { config: { remotePath: "", publicUrl: "" }, enabled: false, autoPublish: false };
      formState.soundcloud = { config: { playlistId: "" }, enabled: false, autoPublish: false };
      // Overlay with existing targets from API
      for (const t of targets) {
        formState[t.target] = { config: { ...formState[t.target]?.config, ...t.config }, enabled: t.enabled, autoPublish: t.autoPublish };
      }
      setPubForm(formState);
      const providers = new Set(
        apiKeys
          .filter((k) => k.category === "PUBLISHING")
          .map((k) => k.provider)
      );
      setConfiguredProviders(providers);
    });
  }, [selectedUser, params.podcastId]);

  useEffect(() => {
    loadPubTargets();
  }, [loadPubTargets]);

  function updatePubForm(target: string, field: string, value: string) {
    setPubForm((prev) => ({
      ...prev,
      [target]: {
        ...prev[target],
        config: { ...prev[target]?.config, [field]: value },
      },
    }));
  }

  function togglePubTarget(target: string, enabled: boolean) {
    setPubForm((prev) => ({
      ...prev,
      // Auto-publish only makes sense when the target is enabled; turning the target off clears it.
      [target]: { ...prev[target], enabled, autoPublish: enabled ? prev[target]?.autoPublish ?? false : false },
    }));
  }

  function toggleAutoPublish(target: string, autoPublish: boolean) {
    setPubForm((prev) => ({
      ...prev,
      [target]: { ...prev[target], autoPublish },
    }));
  }

  const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
  const MAX_IMAGE_SIZE = 1024 * 1024; // 1MB

  async function handleImageUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !selectedUser) return;
    // Reset the input so the same file can be re-selected
    e.target.value = "";

    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      toast.error("Only JPEG, PNG, and WebP images are accepted.");
      return;
    }
    if (file.size > MAX_IMAGE_SIZE) {
      toast.error("Image must be smaller than 1 MB.");
      return;
    }

    setImageUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      const res = await fetch(
        `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/image`,
        { method: "POST", body: formData }
      );
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error || `Upload failed (${res.status})`);
      }
      setImageUrl(`/api/users/${selectedUser.id}/podcasts/${params.podcastId}/image?t=${Date.now()}`);
      toast.success("Image uploaded.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setImageUploading(false);
    }
  }

  async function handleImageDelete() {
    if (!selectedUser) return;
    try {
      const res = await fetch(
        `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/image`,
        { method: "DELETE" }
      );
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error || `Delete failed (${res.status})`);
      }
      setImageUrl(null);
      toast.success("Image deleted.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Delete failed");
    }
  }

  async function handleSave() {
    if (!selectedUser || !form) return;
    setSaving(true);
    try {
      // Save podcast settings
      const res = await fetch(
        `/api/users/${selectedUser.id}/podcasts/${params.podcastId}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            name: form.name,
            topic: form.topic,
            language: form.language,
            llmModels: form.llmModels,
            ttsProvider: form.ttsProvider,
            ttsVoices: form.ttsVoices,
            ttsSettings: form.ttsSettings,
            style: form.style,
            targetWords: form.targetWords ?? null,
            cron: form.cron,
            timezone: form.timezone,
            customInstructions: form.customInstructions ?? "",
            relevanceThreshold: form.relevanceThreshold,
            requireReview: form.requireReview,
            requirePublishApproval: form.requirePublishApproval,
            maxLlmCostCents: form.maxLlmCostCents ?? null,
            maxArticleAgeDays: form.maxArticleAgeDays ?? null,
            speakerNames: form.speakerNames,
            fullBodyThreshold: form.fullBodyThreshold ?? null,
            sponsor: form.sponsor,
            pronunciations: form.pronunciations,
            composeSettings: form.composeSettings,
            deepDiveEnabled: form.deepDiveEnabled ?? false,
            subtopics: form.subtopics ?? {},
            rapidFireWeightThreshold: form.rapidFireWeightThreshold ?? 3,
            rapidFireMaxItems: form.rapidFireMaxItems ?? null,
          }),
        }
      );
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error || `Save failed (${res.status})`);
      }
      const updated: Podcast = await res.json();
      setPodcast(updated);
      setForm(updated);

      // Save publication targets
      const pubErrors: string[] = [];
      for (const [target, entry] of Object.entries(pubForm)) {
        try {
          const pubRes = await fetch(
            `/api/users/${selectedUser.id}/podcasts/${params.podcastId}/publication-targets/${target}`,
            {
              method: "PUT",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ config: entry.config, enabled: entry.enabled, autoPublish: entry.autoPublish }),
            }
          );
          if (!pubRes.ok) {
            const body = await pubRes.json().catch(() => null);
            pubErrors.push(`${target}: ${body?.error || pubRes.status}`);
          }
        } catch (e) {
          pubErrors.push(`${target}: ${e instanceof Error ? e.message : "failed"}`);
        }
      }

      if (pubErrors.length > 0) {
        toast.error(`Settings saved, but publishing targets failed: ${pubErrors.join(", ")}`);
      } else {
        toast.success("Settings saved.");
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  async function handleTestTavily() {
    if (!selectedUser) return;
    setTestingTavily(true);
    try {
      const res = await fetch(`/api/users/${selectedUser.id}/research/test/tavily`, { method: "POST" });
      const result = await res.json().catch(() => null);
      if (result?.success) {
        toast.success(result.message ?? "Tavily reachable.");
      } else {
        toast.error(result?.message ?? "Tavily test failed.");
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Tavily test failed.");
    } finally {
      setTestingTavily(false);
    }
  }

  async function handleDeletePodcast() {
    if (!selectedUser || !podcast) return;
    setDeleting(true);
    try {
      const res = await fetch(
        `/api/users/${selectedUser.id}/podcasts/${params.podcastId}`,
        { method: "DELETE" }
      );
      if (!res.ok) throw new Error(`Delete failed (${res.status})`);
      toast.success(`Deleted podcast "${podcast.name}".`);
      router.push("/podcasts");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to delete podcast");
      setDeleting(false);
    }
  }

  function update<K extends keyof Podcast>(key: K, value: Podcast[K]) {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));
  }

  function updateNumber(key: keyof Podcast, raw: string) {
    const parsed = raw === "" ? null : Number(raw);
    update(key, (parsed !== null && isNaN(parsed) ? null : parsed) as never);
  }

  if (userLoading || loading) {
    return <p className="text-muted-foreground">Loading...</p>;
  }

  if (!selectedUser || !podcast || !form) {
    return <p className="text-muted-foreground">Podcast not found.</p>;
  }

  return (
    <div>
      <div className="mb-4">
        <Link
          href={`/podcasts/${params.podcastId}`}
          className="text-sm text-muted-foreground hover:underline"
        >
          &larr; Back to podcast
        </Link>
      </div>
      <h2 className="mb-6 text-2xl font-bold">Settings: {podcast.name}</h2>

      <Tabs value={currentTab} onValueChange={(v) => setTab(v as typeof TABS[number])}>
        <TabsList>
          <TabsTrigger value="general">General</TabsTrigger>
          <TabsTrigger value="llm">LLM</TabsTrigger>
          <TabsTrigger value="tts">TTS</TabsTrigger>
          <TabsTrigger value="compose">Compose</TabsTrigger>
          <TabsTrigger value="research">Research</TabsTrigger>
          <TabsTrigger value="publishing">Publishing</TabsTrigger>
          <TabsTrigger value="danger">Danger</TabsTrigger>
        </TabsList>

        <TabsContent value="general">
          <Card>
            <CardHeader>
              <CardTitle>General Settings</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <FieldLabel>Podcast Image</FieldLabel>
                <div className="flex items-start gap-4">
                  {imageUrl ? (
                    <img
                      src={imageUrl}
                      alt="Podcast cover"
                      className="size-24 rounded-md border border-input object-cover"
                    />
                  ) : (
                    <div className="flex size-24 items-center justify-center rounded-md border border-dashed border-input">
                      <ImageIcon className="size-8 text-muted-foreground" />
                    </div>
                  )}
                  <div className="flex flex-col gap-2">
                    <div className="flex items-center gap-2">
                      <label>
                        <input
                          type="file"
                          accept="image/jpeg,image/png,image/webp"
                          onChange={handleImageUpload}
                          className="hidden"
                        />
                        <Button type="button" size="icon-lg" title={imageUploading ? "Uploading..." : "Upload image"} disabled={imageUploading} asChild>
                          <span>
                            <Upload className="size-4" />
                          </span>
                        </Button>
                      </label>
                      {imageUrl && (
                        <Button
                          type="button"
                          size="icon-lg"
                          variant="destructive"
                          title="Delete image"
                          onClick={handleImageDelete}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground">
                      JPEG, PNG, or WebP. Max 1 MB.
                    </p>
                  </div>
                </div>
              </div>
              <FieldGroup label="Name" description="Display name for the podcast, used in the feed and episode titles.">
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => update("name", e.target.value)}
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Topic" description="Subject area used by the LLM to filter and score articles for relevance.">
                <input
                  type="text"
                  value={form.topic}
                  onChange={(e) => update("topic", e.target.value)}
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Language" description="ISO language code (e.g. en, nl, de). Controls the script language and TTS pronunciation.">
                <input
                  type="text"
                  value={form.language}
                  onChange={(e) => update("language", e.target.value)}
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Style" description="Script format: news-briefing (anchor), casual (friendly chat), deep-dive (analysis), executive-summary (concise), dialogue (two hosts), interview (interviewer + expert).">
                <Select value={form.style} onValueChange={(v) => update("style", v)}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {STYLES.map((s) => (
                      <SelectItem key={s} value={s}>
                        {s}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FieldGroup>
              <FieldGroup label="Cron Schedule" description="When to auto-generate episodes. Standard cron format: second minute hour day month weekday (e.g. '0 0 6 * * *' = daily at 6:00).">
                <input
                  type="text"
                  value={form.cron}
                  onChange={(e) => update("cron", e.target.value)}
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Timezone" description="IANA timezone for the cron schedule (e.g. Europe/Amsterdam). Handles daylight saving automatically.">
                <input
                  type="text"
                  value={form.timezone}
                  onChange={(e) => update("timezone", e.target.value)}
                  list="timezone-suggestions"
                  className={inputClass}
                />
                <datalist id="timezone-suggestions">
                  <option value="UTC" />
                  <option value="Europe/Amsterdam" />
                  <option value="Europe/London" />
                  <option value="Europe/Berlin" />
                  <option value="Europe/Paris" />
                  <option value="America/New_York" />
                  <option value="America/Chicago" />
                  <option value="America/Denver" />
                  <option value="America/Los_Angeles" />
                  <option value="Asia/Tokyo" />
                  <option value="Asia/Shanghai" />
                  <option value="Australia/Sydney" />
                </datalist>
              </FieldGroup>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="requireReview"
                  checked={form.requireReview}
                  onChange={(e) => update("requireReview", e.target.checked)}
                  className="size-4 rounded border border-input"
                />
                <label htmlFor="requireReview" className="text-sm font-medium">
                  Require review before audio generation
                </label>
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="requirePublishApproval"
                  checked={form.requirePublishApproval ?? false}
                  onChange={(e) => update("requirePublishApproval", e.target.checked)}
                  className="size-4 rounded border border-input"
                />
                <label htmlFor="requirePublishApproval" className="text-sm font-medium">
                  Require approval before publishing
                </label>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="llm">
          <Card>
            <CardHeader>
              <CardTitle>LLM Settings</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <FieldGroup label="LLM Models" description="Override the LLM model per pipeline stage. Filter handles scoring/summarization. Dedup clusters and deduplicates articles against recent episodes. Compose handles script writing.">
                {(["filter", "dedup", "compose"] as const).map((stage) => {
                  const override = form.llmModels?.[stage];
                  const defaultRef = defaults?.llmModels?.[stage];
                  const llmProviders = defaults?.availableModels
                    ? Object.entries(defaults.availableModels)
                        .filter(([, models]) => models.some((m) => m.type === "llm"))
                        .map(([provider]) => provider)
                    : [];
                  const activeProvider = override?.provider ?? defaultRef?.provider ?? "";
                  const modelsForProvider = activeProvider && defaults?.availableModels?.[activeProvider]
                    ? defaults.availableModels[activeProvider].filter((m) => m.type === "llm")
                    : [];
                  const activeModel = override?.model ?? defaultRef?.model ?? "";

                  return (
                    <div key={stage} className="space-y-1">
                      <p className="text-xs font-medium capitalize">{stage}</p>
                      <div className="flex gap-2">
                        <Select
                          value={activeProvider}
                          onValueChange={(provider) => {
                            const currentModels = form.llmModels ?? {};
                            const firstModel = defaults?.availableModels?.[provider]
                              ?.find((m) => m.type === "llm")?.name ?? "";
                            update("llmModels", { ...currentModels, [stage]: { provider, model: firstModel } });
                          }}
                        >
                          <SelectTrigger className="w-[200px]">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {llmProviders.map((p) => (
                              <SelectItem key={p} value={p}>{p}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <Select
                          value={activeModel}
                          onValueChange={(model) => {
                            const currentModels = form.llmModels ?? {};
                            update("llmModels", { ...currentModels, [stage]: { provider: activeProvider, model } });
                          }}
                        >
                          <SelectTrigger className="flex-1">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {modelsForProvider.map((m) => (
                              <SelectItem key={m.name} value={m.name}>{m.name}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        {override && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              const currentModels = { ...form.llmModels };
                              delete currentModels[stage];
                              update("llmModels", Object.keys(currentModels).length > 0 ? currentModels : undefined);
                            }}
                          >
                            Reset
                          </Button>
                        )}
                      </div>
                      {defaultRef && !override && (
                        <p className="text-xs text-muted-foreground">
                          Using system default
                        </p>
                      )}
                    </div>
                  );
                })}
              </FieldGroup>
              <FieldGroup label="Relevance Threshold" description="Minimum relevance score (1-10) an article must receive to be included in the episode. Higher = stricter filtering, fewer articles. Default: 5.">
                <input
                  type="number"
                  value={form.relevanceThreshold ?? ""}
                  onChange={(e) => updateNumber("relevanceThreshold", e.target.value)}
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Max LLM Cost (cents)" description="Cost gate: if the estimated LLM cost for a pipeline run exceeds this value (in cents), the run is skipped. Leave empty for system default.">
                <input
                  type="number"
                  value={form.maxLlmCostCents ?? ""}
                  onChange={(e) => updateNumber("maxLlmCostCents", e.target.value)}
                  placeholder={defaults ? `${defaults.maxLlmCostCents} (system default)` : ""}
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Max Article Age (days)" description="Articles older than this many days are excluded from the pipeline. Prevents stale content from being included in new episodes.">
                <input
                  type="number"
                  value={form.maxArticleAgeDays ?? ""}
                  onChange={(e) => updateNumber("maxArticleAgeDays", e.target.value)}
                  placeholder={defaults ? `${defaults.maxArticleAgeDays} (system default)` : ""}
                  className={inputClass}
                />
              </FieldGroup>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="tts">
          <Card>
            <CardHeader>
              <CardTitle>TTS Settings</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <FieldGroup label="TTS Provider & Model" description="Text-to-speech engine and model variant.">
                <div className="flex gap-2">
                  <Select
                    value={form.ttsProvider}
                    onValueChange={(v) => update("ttsProvider", v)}
                  >
                    <SelectTrigger className="w-[200px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {TTS_PROVIDERS.map((p) => (
                        <SelectItem key={p} value={p}>
                          {p}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {(() => {
                    const ttsModels = defaults?.availableModels?.[form.ttsProvider]
                      ?.filter((m) => m.type === "tts") ?? [];
                    if (ttsModels.length === 0) return null;
                    const currentModel = form.ttsSettings?.model ?? ttsModels[0]?.name ?? "";
                    return (
                      <Select
                        value={currentModel}
                        onValueChange={(model) => {
                          const current = form.ttsSettings ?? {};
                          update("ttsSettings", { ...current, model });
                        }}
                      >
                        <SelectTrigger className="flex-1">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {ttsModels.map((m) => (
                            <SelectItem key={m.name} value={m.name}>{m.name}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    );
                  })()}
                </div>
              </FieldGroup>
              {(() => {
                const selectedModel = form.ttsSettings?.model ?? "";
                if (form.ttsProvider !== "inworld" || selectedModel !== "inworld-tts-2") return null;
                const current = form.ttsSettings?.deliveryMode ?? "";
                const value = current === "" ? "_unset" : current;
                return (
                  <FieldGroup label="Delivery Mode" description="Inworld TTS-2 only. STABLE for consistency, BALANCED (default) for general use, EXPRESSIVE for emotional range. When set, replaces the temperature parameter.">
                    <Select
                      value={value}
                      onValueChange={(v) => {
                        const next = { ...(form.ttsSettings ?? {}) };
                        if (v === "_unset") {
                          delete next.deliveryMode;
                        } else {
                          next.deliveryMode = v;
                        }
                        update("ttsSettings", next);
                      }}
                    >
                      <SelectTrigger className="w-[240px]">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="_unset">— (provider default)</SelectItem>
                        <SelectItem value="STABLE">STABLE</SelectItem>
                        <SelectItem value="BALANCED">BALANCED</SelectItem>
                        <SelectItem value="EXPRESSIVE">EXPRESSIVE</SelectItem>
                      </SelectContent>
                    </Select>
                  </FieldGroup>
                );
              })()}
              {form.ttsProvider === "inworld" && (
                <FieldGroup label="Enhanced Audio Quality" description="Inworld only. Applies denoising to the synthesized audio to reduce background noise and artifacts. Matches the 'Enhanced' toggle in the Inworld studio.">
                  <Switch
                    checked={form.ttsSettings?.enhanceGeneration === "true"}
                    onCheckedChange={(checked) => {
                      const next = { ...(form.ttsSettings ?? {}) };
                      if (checked) {
                        next.enhanceGeneration = "true";
                      } else {
                        delete next.enhanceGeneration;
                      }
                      update("ttsSettings", next);
                    }}
                  />
                </FieldGroup>
              )}
              <FieldGroup label="TTS Voices" description="Voice assignment per role. For monologue styles use key 'default'. For dialogue/interview use role keys (e.g. 'interviewer', 'expert', 'host', 'cohost'). Values are provider-specific voice IDs.">
                <KeyValueEditor
                  value={form.ttsVoices}
                  onChange={(v) => update("ttsVoices", v)}
                  keyPlaceholder="Role (e.g. default, host)"
                  valuePlaceholder="Voice ID"
                />
              </FieldGroup>
              <FieldGroup label="TTS Settings" description="Provider-specific settings. Common keys: 'model' (TTS model ID), 'speed' (speaking rate, e.g. 1.0), 'temperature' (expressiveness, 0.0-1.0).">
                <KeyValueEditor
                  value={form.ttsSettings}
                  onChange={(v) => update("ttsSettings", v)}
                  keyPlaceholder="Setting (e.g. speed)"
                  valuePlaceholder="Value"
                />
              </FieldGroup>
              <FieldGroup label="Speaker Names" description="Display names for speakers in dialogue/interview styles. Keys match TTS voice roles (e.g. 'interviewer', 'expert'). Used in the script for natural conversation.">
                <KeyValueEditor
                  value={form.speakerNames}
                  onChange={(v) => update("speakerNames", v)}
                  keyPlaceholder="Role (e.g. host, cohost)"
                  valuePlaceholder="Display name"
                />
              </FieldGroup>
              <FieldGroup label="Pronunciations" description="IPA pronunciation overrides for proper nouns. Keys: the word as written. Values: IPA notation (e.g. '/jɑrnoː/'). Applied on first occurrence in the script. Currently supported by Inworld TTS.">
                <KeyValueEditor
                  value={form.pronunciations}
                  onChange={(v) => update("pronunciations", v)}
                  keyPlaceholder="Word"
                  valuePlaceholder="IPA pronunciation"
                />
              </FieldGroup>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="compose">
          <Card>
            <CardHeader>
              <CardTitle>Compose Settings</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <FieldGroup label="Target Words" description="Approximate word count for the generated script. The LLM aims for this length but may vary based on content.">
                <input
                  type="number"
                  value={form.targetWords ?? ""}
                  onChange={(e) => updateNumber("targetWords", e.target.value)}
                  placeholder={defaults ? `${defaults.targetWords} (system default)` : ""}
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Full Body Threshold" description="When the number of articles is at or below this threshold, the full article body is sent to the composer instead of just the summary. Produces richer scripts for small batches.">
                <input
                  type="number"
                  value={form.fullBodyThreshold ?? ""}
                  onChange={(e) => updateNumber("fullBodyThreshold", e.target.value)}
                  placeholder={defaults ? `${defaults.fullBodyThreshold} (system default)` : ""}
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Subtopics" description="Optional subtopics within this podcast's topic, each with an importance weight (1-10). High-weight subtopics get full script segments with word budgets proportional to weight; low-weight subtopics (at or below the rapid-fire threshold) are rolled into a single labeled rapid-fire segment at the end of the script. Keep names short and non-overlapping. Leave empty to disable the feature.">
                <SubtopicsEditor
                  value={form.subtopics}
                  onChange={(v) => update("subtopics", v)}
                />
              </FieldGroup>
              <FieldGroup label="Rapid-fire Weight Threshold" description="Subtopics with weight at or below this value (plus unclassified articles in a synthetic 'Other' bucket of weight 1) are rolled into a single rapid-fire segment at the end of the script. 0 = nothing is rapid-fire; 10 = everything is rapid-fire. Default: 3.">
                <input
                  type="number"
                  min={0}
                  max={10}
                  value={form.rapidFireWeightThreshold ?? ""}
                  onChange={(e) => updateNumber("rapidFireWeightThreshold", e.target.value)}
                  placeholder="3"
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Rapid-fire Max Items" description="Maximum number of articles to cover in the rapid-fire segment. Articles past the cap are dropped from the episode entirely (ranked by subtopic weight, then relevance score). Keeps the closing segment readable instead of cramming 15+ items into a few sentences. Leave empty to use the system default (6).">
                <input
                  type="number"
                  min={0}
                  max={50}
                  value={form.rapidFireMaxItems ?? ""}
                  onChange={(e) => updateNumber("rapidFireMaxItems", e.target.value)}
                  placeholder="6 (system default)"
                  className={inputClass}
                />
              </FieldGroup>
              <FieldGroup label="Custom Instructions" description="Additional instructions appended to the LLM composition prompt. Use this for tone, structure, engagement techniques, or topic-specific guidance.">
                <textarea
                  value={form.customInstructions ?? ""}
                  onChange={(e) => update("customInstructions", e.target.value)}
                  className={`${textareaClass} min-h-[300px]`}
                />
              </FieldGroup>
              <FieldGroup label="Composer Settings" description="Script composer settings as key/value pairs. Common keys: 'temperature' (sampling variety for briefing/dialogue/interview composers, 0.0–2.0, default 0.95). Unknown keys are persisted as-is for future use.">
                <KeyValueEditor
                  value={form.composeSettings}
                  onChange={(v) => update("composeSettings", v)}
                  keyPlaceholder="Setting (e.g. temperature)"
                  valuePlaceholder="Value"
                />
              </FieldGroup>
              <FieldGroup label="Sponsor" description="Sponsor message injected into the script. Keys: 'name' (sponsor name) and 'message' (tagline). Both required for the sponsor mention to appear.">
                <KeyValueEditor
                  value={form.sponsor}
                  onChange={(v) => update("sponsor", v)}
                  keyPlaceholder="Field (e.g. name, message)"
                  valuePlaceholder="Value"
                />
              </FieldGroup>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="research">
          <Card>
            <CardHeader>
              <CardTitle>Deep-Dive Web Research</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="rounded-md border border-border bg-muted/50 p-4 text-sm text-muted-foreground space-y-2">
                <p>
                  When enabled, the script composer is given a <code>webSearch</code> tool backed by{" "}
                  <a href="https://tavily.com" target="_blank" rel="noreferrer" className="underline">Tavily</a>.
                  The LLM decides for itself when to call it — typically 1–2 times for the most newsworthy
                  story, to add outside context that isn&apos;t in the source articles.
                </p>
                <p>
                  Hard cap: <strong>3 search calls per episode</strong>. Each call costs roughly $0.008
                  (Tavily basic search) and a ~5¢ buffer is added to the per-episode cost gate when this
                  is on.
                </p>
                <p>
                  Requires a Tavily API key. Configure one under{" "}
                  <Link href="/settings?tab=api-keys" className="underline">Settings &rarr; API Keys</Link>{" "}
                  (category <strong>RESEARCH</strong>, provider <strong>tavily</strong>), or set the
                  <code> TAVILY_API_KEY</code> environment variable. If no key is configured, generation
                  still succeeds but the tool returns empty results and a single warning is logged.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="deepDiveEnabled"
                  checked={form.deepDiveEnabled ?? false}
                  onChange={(e) => update("deepDiveEnabled", e.target.checked)}
                  className="size-4 rounded border border-input"
                />
                <label htmlFor="deepDiveEnabled" className="text-sm font-medium">
                  Enable deep-dive web research
                </label>
              </div>
              <div className="space-y-2">
                <FieldLabel>Validate Tavily connection</FieldLabel>
                <p className="text-xs text-muted-foreground">
                  Runs a single test search against Tavily using the configured API key. Result is shown as a toast.
                </p>
                <Button onClick={handleTestTavily} disabled={testingTavily} size="sm">
                  <TestTube className="mr-2 size-4" />
                  {testingTavily ? "Testing..." : "Test Tavily"}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="publishing">
          <Card>
            <CardContent className="pt-6">
              <div className="mb-4 flex items-center justify-between rounded-md border border-border bg-muted/50 px-4 py-3">
                <p className="text-sm text-muted-foreground">
                  Publication credentials are managed in your user settings.
                </p>
                <Link href="/settings?tab=publishing">
                  <Button variant="outline" size="sm">
                    <Settings2 className="mr-2 size-4" />
                    Manage Credentials
                  </Button>
                </Link>
              </div>
              <div className="mb-4 inline-flex h-9 w-fit items-center justify-center rounded-lg bg-muted p-[3px] text-muted-foreground">
                <button
                  onClick={() => setPubTab("ftp")}
                  className={cn(
                    "inline-flex h-[calc(100%-1px)] items-center justify-center rounded-md px-3 text-sm font-medium transition-all",
                    pubTab === "ftp" ? "bg-background text-foreground shadow-sm" : "text-foreground/60 hover:text-foreground"
                  )}
                >
                  FTP
                </button>
                <button
                  onClick={() => setPubTab("soundcloud")}
                  className={cn(
                    "inline-flex h-[calc(100%-1px)] items-center justify-center rounded-md px-3 text-sm font-medium transition-all",
                    pubTab === "soundcloud" ? "bg-background text-foreground shadow-sm" : "text-foreground/60 hover:text-foreground"
                  )}
                >
                  SoundCloud
                </button>
              </div>

              {pubTab === "ftp" && (() => {
                const hasCreds = configuredProviders.has("ftp");
                const entry = pubForm.ftp ?? { config: { remotePath: "", publicUrl: "" }, enabled: false, autoPublish: false };
                return (
                  <div className={cn("mt-4", !hasCreds && "opacity-50 pointer-events-none")}>
                    <div className="mb-4 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        {hasCreds ? <Wifi className="size-4 text-green-600" /> : <WifiOff className="size-4 text-muted-foreground" />}
                        <span className="font-medium">FTP Target</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground">{entry.enabled ? "Enabled" : "Disabled"}</span>
                        <Switch checked={entry.enabled} onCheckedChange={(checked) => togglePubTarget("ftp", checked)} />
                      </div>
                    </div>
                    {!hasCreds && <p className="mb-4 text-sm text-muted-foreground">Configure credentials in Settings first.</p>}
                    <div className="mb-4 flex items-center justify-between rounded-md border p-3">
                      <div>
                        <span className="text-sm font-medium">Auto-publish on generate</span>
                        <p className="text-xs text-muted-foreground">Publish to FTP automatically when an episode is generated.</p>
                      </div>
                      <Switch checked={entry.autoPublish} disabled={!entry.enabled} onCheckedChange={(checked) => toggleAutoPublish("ftp", checked)} />
                    </div>
                    <div className="space-y-4">
                      <FieldGroup label="Remote Path">
                        <input type="text" value={entry.config.remotePath ?? ""} onChange={(e) => updatePubForm("ftp", "remotePath", e.target.value)} placeholder={`/${params.podcastId}/`} className={inputClass} />
                        <p className="text-xs text-muted-foreground">FTP directory for this podcast. Defaults to /{"{podcastId}"}/ if empty.</p>
                      </FieldGroup>
                      <FieldGroup label="Public URL">
                        <input type="text" value={entry.config.publicUrl ?? ""} onChange={(e) => updatePubForm("ftp", "publicUrl", e.target.value)} placeholder="https://example.com/shows/my-podcast" className={inputClass} />
                      </FieldGroup>
                    </div>
                  </div>
                );
              })()}

              {pubTab === "soundcloud" && (() => {
                const hasCreds = configuredProviders.has("soundcloud");
                const entry = pubForm.soundcloud ?? { config: { playlistId: "" }, enabled: false, autoPublish: false };
                return (
                  <div className={cn("mt-4", !hasCreds && "opacity-50 pointer-events-none")}>
                    <div className="mb-4 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        {hasCreds ? <Wifi className="size-4 text-green-600" /> : <WifiOff className="size-4 text-muted-foreground" />}
                        <span className="font-medium">SoundCloud Target</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground">{entry.enabled ? "Enabled" : "Disabled"}</span>
                        <Switch checked={entry.enabled} onCheckedChange={(checked) => togglePubTarget("soundcloud", checked)} />
                      </div>
                    </div>
                    {!hasCreds && <p className="mb-4 text-sm text-muted-foreground">Configure credentials in Settings first.</p>}
                    <div className="mb-4 flex items-center justify-between rounded-md border p-3">
                      <div>
                        <span className="text-sm font-medium">Auto-publish on generate</span>
                        <p className="text-xs text-muted-foreground">Publish to SoundCloud automatically when an episode is generated. Skipped if the upload quota is full.</p>
                      </div>
                      <Switch checked={entry.autoPublish} disabled={!entry.enabled} onCheckedChange={(checked) => toggleAutoPublish("soundcloud", checked)} />
                    </div>
                    <div className="space-y-4">
                      <FieldGroup label="Playlist ID">
                        <input type="text" value={entry.config.playlistId ?? ""} readOnly className={`${inputClass} bg-muted`} />
                        <p className="text-xs text-muted-foreground">Auto-managed during publish. Read-only.</p>
                      </FieldGroup>
                    </div>
                  </div>
                );
              })()}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="danger">
          <Card className="border-destructive/40">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-destructive">
                <AlertTriangle className="size-5" />
                Delete Podcast
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-muted-foreground">
                Permanently delete <strong>{podcast.name}</strong>, including all of its episodes, sources, and audio files. This action cannot be undone.
              </p>
              <FieldGroup label={`Type "${podcast.name}" to confirm`}>
                <input
                  type="text"
                  value={deleteConfirmText}
                  onChange={(e) => setDeleteConfirmText(e.target.value)}
                  placeholder={podcast.name}
                  className={inputClass}
                />
              </FieldGroup>
              <Button
                variant="destructive"
                disabled={deleteConfirmText !== podcast.name || deleting}
                onClick={handleDeletePodcast}
              >
                <Trash2 className="mr-2 size-4" />
                {deleting ? "Deleting..." : "Delete podcast"}
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

      </Tabs>

      {currentTab !== "danger" && (
        <div className="mt-6">
          <Button onClick={handleSave} disabled={saving}>
            <Save className="mr-2 size-4" />
            {saving ? "Saving..." : "Save"}
          </Button>
        </div>
      )}
    </div>
  );
}
