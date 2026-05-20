"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Save } from "lucide-react";

const CATEGORIES = ["LLM", "TTS", "RESEARCH"] as const;

const PROVIDERS_BY_CATEGORY: Record<string, string[]> = {
  LLM: ["openrouter", "openai", "ollama"],
  TTS: ["openai", "elevenlabs", "inworld"],
  RESEARCH: ["tavily"],
};

const PROVIDER_DEFAULT_URLS: Record<string, string> = {
  openrouter: "https://openrouter.ai/api",
  openai: "https://api.openai.com",
  ollama: "http://localhost:11434/v1",
  elevenlabs: "https://api.elevenlabs.io",
  inworld: "https://api.inworld.ai",
  tavily: "https://api.tavily.com",
};

interface ProviderConfig {
  category: string;
  provider: string;
  baseUrl: string;
}

interface ProviderConfigDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  userId: string;
  editConfig: ProviderConfig | null;
  existingConfigs: ProviderConfig[];
  onSaved: () => void;
}

export function ProviderConfigDialog({
  open,
  onOpenChange,
  userId,
  editConfig,
  existingConfigs,
  onSaved,
}: ProviderConfigDialogProps) {
  const isEdit = !!editConfig;

  const [category, setCategory] = useState("");
  const [provider, setProvider] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      if (editConfig) {
        setCategory(editConfig.category);
        setProvider(editConfig.provider);
        setBaseUrl(editConfig.baseUrl);
      } else {
        setCategory("");
        setProvider("");
        setBaseUrl("");
      }
      setApiKey("");
      setError(null);
    }
  }, [open, editConfig]);

  useEffect(() => {
    if (!isEdit && provider) {
      setBaseUrl(PROVIDER_DEFAULT_URLS[provider] ?? "");
    }
  }, [provider, isEdit]);

  const availableProviders = category
    ? (PROVIDERS_BY_CATEGORY[category] ?? []).filter(
        (p) => isEdit || !existingConfigs.some((c) => c.category === category && c.provider === p)
      )
    : [];

  const isOllama = provider === "ollama";
  const canSubmit = category && provider && (isOllama || apiKey || isEdit) && baseUrl;

  async function handleSubmit() {
    if (!canSubmit) return;
    setSaving(true);
    setError(null);
    try {
      const body: Record<string, string | undefined> = {
        provider,
        baseUrl: baseUrl || undefined,
      };
      if (apiKey) {
        body.apiKey = apiKey;
      }
      const res = await fetch(`/api/users/${userId}/api-keys/${category}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new Error(data?.error || `Failed (${res.status})`);
      }
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit Provider" : "Add Provider"}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label>Category</Label>
            {isEdit ? (
              <Input value={category} disabled />
            ) : (
              <Select value={category} onValueChange={(v) => { setCategory(v); setProvider(""); }}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select category" />
                </SelectTrigger>
                <SelectContent>
                  {CATEGORIES.map((c) => (
                    <SelectItem key={c} value={c}>{c}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>

          <div className="space-y-2">
            <Label>Provider</Label>
            {isEdit ? (
              <Input value={provider} disabled />
            ) : (
              <Select value={provider} onValueChange={setProvider} disabled={!category}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder={category ? "Select provider" : "Select category first"} />
                </SelectTrigger>
                <SelectContent>
                  {availableProviders.map((p) => (
                    <SelectItem key={p} value={p}>{p}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>

          {provider && (
            <>
              <div className="space-y-2">
                <Label>API Key {isOllama && <span className="text-muted-foreground font-normal">(optional)</span>}</Label>
                <Input
                  type="password"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder={isEdit ? "Leave empty to keep current key" : isOllama ? "Not required" : "Enter API key"}
                />
              </div>

              <div className="space-y-2">
                <Label>Base URL</Label>
                <Input
                  value={baseUrl}
                  onChange={(e) => setBaseUrl(e.target.value)}
                />
              </div>
            </>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button size="sm" onClick={handleSubmit} disabled={!canSubmit || saving}>
            <Save className="size-4" />
            {saving ? "Saving..." : "Save"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
