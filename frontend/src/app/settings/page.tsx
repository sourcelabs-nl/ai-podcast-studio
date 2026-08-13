"use client";

import { Suspense, useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { Archive, Database, Lock, Pencil, Plus, Save, TestTube, Trash2 } from "lucide-react";
import cronstrue from "cronstrue";
import { CronExpressionParser } from "cron-parser";
import { toast } from "sonner";
import { useUser } from "@/lib/user-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ProviderConfigDialog } from "@/components/provider-config-dialog";
import { useTabParam } from "@/hooks/use-tab-param";
import { cn } from "@/lib/utils";

interface ProviderConfig {
  category: string;
  provider: string;
  baseUrl: string;
}

type FtpTransferMode = "PASSIVE" | "ACTIVE";

interface FtpForm {
  host: string;
  port: number;
  username: string;
  password: string;
  useTls: boolean;
  transferMode: FtpTransferMode;
}

interface SoundCloudForm {
  clientId: string;
  clientSecret: string;
  callbackUri: string;
}

interface BackupSettings {
  enabled: boolean;
  cron: string;
  retentionCount: number;
}

interface BackupInfo {
  name: string;
  sizeBytes: number;
  createdAt: string;
}

const TABS = ["profile", "api-keys", "publishing", "backups"] as const;

const defaultFtp: FtpForm = { host: "", port: 21, username: "", password: "", useTls: true, transferMode: "PASSIVE" };
const defaultSoundCloud: SoundCloudForm = { clientId: "", clientSecret: "", callbackUri: "" };

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const inputClass =
  "h-9 w-full rounded-md border border-input bg-background px-3 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50";

export default function SettingsPage() {
  return (
    <Suspense fallback={<p className="text-muted-foreground">Loading...</p>}>
      <SettingsContent />
    </Suspense>
  );
}

function SettingsContent() {
  const { selectedUser, refreshSelectedUser, loading: userLoading } = useUser();
  const [currentTab, setTab] = useTabParam("profile", TABS);

  // Profile state
  const [name, setName] = useState("");
  const [savingName, setSavingName] = useState(false);

  // API Keys state
  const [configs, setConfigs] = useState<ProviderConfig[]>([]);
  const [loadingConfigs, setLoadingConfigs] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editConfig, setEditConfig] = useState<ProviderConfig | null>(null);

  // Publishing state
  const [ftp, setFtp] = useState<FtpForm>(defaultFtp);
  const [soundCloud, setSoundCloud] = useState<SoundCloudForm>(defaultSoundCloud);
  const [ftpHasExisting, setFtpHasExisting] = useState(false);
  const [scHasExisting, setScHasExisting] = useState(false);
  const [savingFtp, setSavingFtp] = useState(false);
  const [savingSc, setSavingSc] = useState(false);
  const [testingFtp, setTestingFtp] = useState(false);
  const [testingSc, setTestingSc] = useState(false);
  const [pubTab, setPubTab] = useState<"ftp" | "soundcloud">("ftp");

  // Backups state (system-level, not user-scoped)
  const [backup, setBackup] = useState<BackupSettings>({ enabled: true, cron: "0 0 2 * * *", retentionCount: 7 });
  const [backupLoading, setBackupLoading] = useState(true);
  const [savingBackup, setSavingBackup] = useState(false);
  const [runningBackup, setRunningBackup] = useState(false);
  const [backups, setBackups] = useState<BackupInfo[]>([]);

  useEffect(() => {
    if (selectedUser) setName(selectedUser.name);
  }, [selectedUser]);

  const fetchConfigs = useCallback(() => {
    if (!selectedUser) return;
    setLoadingConfigs(true);
    fetch(`/api/users/${selectedUser.id}/api-keys`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data: ProviderConfig[]) => {
        setConfigs(data);
        const publishing = data.filter((c) => c.category === "PUBLISHING");
        const ftpConfig = publishing.find((c) => c.provider === "ftp");
        const scConfig = publishing.find((c) => c.provider === "soundcloud");
        setFtpHasExisting(!!ftpConfig);
        setScHasExisting(!!scConfig);
        if (ftpConfig?.baseUrl) {
          try {
            const parsed = JSON.parse(ftpConfig.baseUrl);
            setFtp((prev) => ({
              ...prev,
              host: parsed.host ?? "",
              port: parsed.port ?? 21,
              username: parsed.username ?? "",
              useTls: parsed.useTls ?? true,
              transferMode: parsed.transferMode === "ACTIVE" ? "ACTIVE" : "PASSIVE",
            }));
          } catch { /* ignore parse errors */ }
        }
        if (scConfig?.baseUrl) {
          try {
            const parsed = JSON.parse(scConfig.baseUrl);
            setSoundCloud((prev) => ({ ...prev, clientId: parsed.clientId ?? "", callbackUri: parsed.callbackUri ?? "" }));
          } catch { /* ignore parse errors */ }
        }
      })
      .catch(() => setConfigs([]))
      .finally(() => setLoadingConfigs(false));
  }, [selectedUser]);

  useEffect(() => {
    fetchConfigs();
  }, [fetchConfigs]);

  const fetchBackups = useCallback(() => {
    setBackupLoading(true);
    Promise.all([
      fetch(`/api/admin/backup/settings`).then((r) => (r.ok ? r.json() : null)),
      fetch(`/api/admin/backup`).then((r) => (r.ok ? r.json() : [])),
    ])
      .then(([settings, list]: [BackupSettings | null, BackupInfo[]]) => {
        if (settings) setBackup({ enabled: settings.enabled, cron: settings.cron, retentionCount: settings.retentionCount });
        setBackups(list ?? []);
      })
      .catch(() => { /* ignore */ })
      .finally(() => setBackupLoading(false));
  }, []);

  useEffect(() => {
    fetchBackups();
  }, [fetchBackups]);

  async function handleSaveBackup() {
    setSavingBackup(true);
    try {
      const res = await fetch(`/api/admin/backup/settings`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(backup),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error || "Failed to save backup settings");
      }
      toast.success("Backup settings saved.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save backup settings.");
    } finally {
      setSavingBackup(false);
    }
  }

  async function handleRunBackup() {
    setRunningBackup(true);
    try {
      const res = await fetch(`/api/admin/backup`, { method: "POST" });
      if (!res.ok) throw new Error("Backup failed");
      toast.success("Backup created.");
      fetchBackups();
    } catch {
      toast.error("Backup failed.");
    } finally {
      setRunningBackup(false);
    }
  }

  // Profile handlers
  async function handleSaveName() {
    if (!selectedUser || !name.trim()) return;
    setSavingName(true);
    try {
      const res = await fetch(`/api/users/${selectedUser.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: name.trim() }),
      });
      if (!res.ok) throw new Error("Failed to update name");
      const updated = await res.json();
      refreshSelectedUser(updated);
      toast.success("Name updated.");
    } catch {
      toast.error("Failed to update name.");
    } finally {
      setSavingName(false);
    }
  }

  // API Keys handlers
  async function handleRemove(config: ProviderConfig) {
    if (!selectedUser) return;
    await fetch(`/api/users/${selectedUser.id}/api-keys/${config.category}/${config.provider}`, {
      method: "DELETE",
    });
    fetchConfigs();
  }

  function handleEdit(config: ProviderConfig) {
    setEditConfig(config);
    setDialogOpen(true);
  }

  function handleAdd() {
    setEditConfig(null);
    setDialogOpen(true);
  }

  function handleDialogSaved() {
    setDialogOpen(false);
    setEditConfig(null);
    fetchConfigs();
  }

  // Publishing handlers
  async function handleSaveFtp() {
    if (!selectedUser) return;
    setSavingFtp(true);
    try {
      const { password, ...ftpMeta } = ftp;
      const res = await fetch(`/api/users/${selectedUser.id}/api-keys/PUBLISHING`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          provider: "ftp",
          ...(password ? { apiKey: JSON.stringify(ftp) } : {}),
          baseUrl: JSON.stringify(ftpMeta),
        }),
      });
      if (!res.ok) throw new Error("Failed to save FTP credentials");
      toast.success("FTP credentials saved.");
      setFtpHasExisting(true);
    } catch {
      toast.error("Failed to save FTP credentials.");
    } finally {
      setSavingFtp(false);
    }
  }

  async function handleSaveSoundCloud() {
    if (!selectedUser) return;
    setSavingSc(true);
    try {
      const { clientSecret, ...scMeta } = soundCloud;
      const res = await fetch(`/api/users/${selectedUser.id}/api-keys/PUBLISHING`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          provider: "soundcloud",
          ...(clientSecret ? { apiKey: JSON.stringify(soundCloud) } : {}),
          baseUrl: JSON.stringify(scMeta),
        }),
      });
      if (!res.ok) throw new Error("Failed to save SoundCloud credentials");
      toast.success("SoundCloud credentials saved.");
      setScHasExisting(true);
    } catch {
      toast.error("Failed to save SoundCloud credentials.");
    } finally {
      setSavingSc(false);
    }
  }

  async function handleTestFtp() {
    if (!selectedUser) return;
    setTestingFtp(true);
    try {
      const res = await fetch(`/api/users/${selectedUser.id}/publishing/test/ftp`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(ftp),
      });
      if (!res.ok) {
        const body = await res.text();
        throw new Error(body || "Connection test failed");
      }
      // A failed connection comes back as HTTP 200 with success: false, so the body decides.
      const result: { success: boolean; message?: string } = await res.json();
      if (!result.success) {
        throw new Error(result.message || "Connection test failed");
      }
      toast.success(result.message || "FTP connection successful.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Connection test failed.");
    } finally {
      setTestingFtp(false);
    }
  }

  async function handleTestSoundCloud() {
    if (!selectedUser) return;
    setTestingSc(true);
    try {
      const res = await fetch(`/api/users/${selectedUser.id}/publishing/test/soundcloud`, {
        method: "POST",
      });
      if (!res.ok) {
        const body = await res.text();
        throw new Error(body || "Connection test failed");
      }
      toast.success("SoundCloud connection successful.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Connection test failed.");
    } finally {
      setTestingSc(false);
    }
  }

  if (userLoading) {
    return <p className="text-muted-foreground">Loading...</p>;
  }

  if (!selectedUser) {
    return <p className="text-muted-foreground">No user selected.</p>;
  }

  // Derive a human-readable cron description + next run for the Backups tab
  let cronHuman = "";
  let cronValid = true;
  let cronNextRun = "";
  try {
    cronHuman = cronstrue.toString(backup.cron);
    cronNextRun = CronExpressionParser.parse(backup.cron, { tz: "UTC" }).next().toDate().toUTCString();
  } catch {
    cronValid = false;
  }

  return (
    <div>
      <div className="mb-4">
        <Link href="/podcasts" className="text-sm text-muted-foreground hover:underline">
          &larr; Back to podcasts
        </Link>
      </div>
      <h2 className="mb-6 text-2xl font-bold">Settings</h2>

      <Tabs value={currentTab} onValueChange={(v) => setTab(v as typeof TABS[number])}>
        <TabsList>
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="api-keys">API Keys</TabsTrigger>
          <TabsTrigger value="publishing">Publishing</TabsTrigger>
          <TabsTrigger value="backups">Backups</TabsTrigger>
        </TabsList>

        <TabsContent value="profile">
          <Card>
            <CardHeader>
              <CardTitle>Profile</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                <Label htmlFor="user-name">Name</Label>
                <Input
                  id="user-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>
            </CardContent>
          </Card>
          <div className="mt-6">
            <Button onClick={handleSaveName} disabled={savingName}>
              <Save className="mr-2 size-4" />
              {savingName ? "Saving..." : "Save"}
            </Button>
          </div>
        </TabsContent>

        <TabsContent value="api-keys">
          <Card>
            <CardHeader>
              <CardTitle>API Keys</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="mb-4 flex items-center gap-2 rounded-md border border-border bg-muted/50 px-3 py-2 text-sm text-muted-foreground">
                <Lock className="size-4 shrink-0" />
                All API keys are stored encrypted. Decryption requires the application master key.
              </div>

              {loadingConfigs ? (
                <p className="text-muted-foreground text-sm">Loading...</p>
              ) : configs.filter((c) => c.category !== "PUBLISHING").length === 0 ? (
                <p className="text-muted-foreground text-sm">No API keys configured yet.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Provider</TableHead>
                      <TableHead>Category</TableHead>
                      <TableHead>Base URL</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {configs.filter((c) => c.category !== "PUBLISHING").map((config) => (
                      <TableRow key={`${config.category}-${config.provider}`}>
                        <TableCell className="font-medium">{config.provider}</TableCell>
                        <TableCell>
                          <Badge variant={config.category === "LLM" ? "default" : "outline"}>
                            {config.category}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground">{config.baseUrl}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex items-center justify-end gap-2">
                            <Button variant="ghost" size="sm" title="Edit provider" onClick={() => handleEdit(config)}>
                              <Pencil className="size-4" />
                            </Button>
                            <Button variant="ghost" size="sm" title="Remove provider" onClick={() => handleRemove(config)}>
                              <Trash2 className="size-4" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
              <Button size="icon-lg" title="Add provider" onClick={handleAdd} className="mt-4">
                <Plus className="size-4" />
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="publishing">
          <Card>
            <CardContent className="pt-6">
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

              {pubTab === "ftp" && (
                <div className="mt-4 space-y-4">
                  {ftpHasExisting && (
                    <p className="text-sm text-muted-foreground">
                      Existing FTP credentials are stored. Fill in the form below to update them.
                    </p>
                  )}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="ftp-host">Host</Label>
                      <input
                        id="ftp-host"
                        className={inputClass}
                        value={ftp.host}
                        onChange={(e) => setFtp({ ...ftp, host: e.target.value })}
                        placeholder="ftp.example.com"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="ftp-port">Port</Label>
                      <input
                        id="ftp-port"
                        type="number"
                        className={inputClass}
                        value={ftp.port}
                        onChange={(e) => setFtp({ ...ftp, port: parseInt(e.target.value) || 21 })}
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="ftp-username">Username</Label>
                      <input
                        id="ftp-username"
                        className={inputClass}
                        value={ftp.username}
                        onChange={(e) => setFtp({ ...ftp, username: e.target.value })}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="ftp-password">Password</Label>
                      <input
                        id="ftp-password"
                        type="password"
                        className={inputClass}
                        value={ftp.password}
                        placeholder={ftpHasExisting ? "••••••••" : ""}
                        onChange={(e) => setFtp({ ...ftp, password: e.target.value })}
                      />
                      {ftpHasExisting && !ftp.password && (
                        <p className="text-xs text-muted-foreground">Leave empty to keep existing password</p>
                      )}
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="ftp-transfer-mode">Transfer Mode</Label>
                      <Select
                        value={ftp.transferMode}
                        onValueChange={(value) => setFtp({ ...ftp, transferMode: value as FtpTransferMode })}
                      >
                        <SelectTrigger id="ftp-transfer-mode" className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="PASSIVE">Passive</SelectItem>
                          <SelectItem value="ACTIVE">Active</SelectItem>
                        </SelectContent>
                      </Select>
                      <p className="text-xs text-muted-foreground">
                        Passive works on most networks. Active only helps when the network blocks outbound data
                        connections but allows the server to connect back.
                      </p>
                    </div>
                    <div className="flex items-center gap-2 pt-8">
                      <Switch
                        id="ftp-tls"
                        checked={ftp.useTls}
                        onCheckedChange={(checked) => setFtp({ ...ftp, useTls: checked })}
                      />
                      <Label htmlFor="ftp-tls">Use TLS</Label>
                    </div>
                  </div>
                </div>
              )}

              {pubTab === "soundcloud" && (
                <div className="mt-4 space-y-4">
                  {scHasExisting && (
                    <p className="text-sm text-muted-foreground">
                      Existing SoundCloud credentials are stored. Fill in the form below to update them.
                    </p>
                  )}
                  <div className="space-y-2">
                    <Label htmlFor="sc-client-id">Client ID</Label>
                    <input
                      id="sc-client-id"
                      className={inputClass}
                      value={soundCloud.clientId}
                      onChange={(e) => setSoundCloud({ ...soundCloud, clientId: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="sc-client-secret">Client Secret</Label>
                    <input
                      id="sc-client-secret"
                      type="password"
                      className={inputClass}
                      value={soundCloud.clientSecret}
                      placeholder={scHasExisting ? "••••••••" : ""}
                      onChange={(e) => setSoundCloud({ ...soundCloud, clientSecret: e.target.value })}
                    />
                    {scHasExisting && !soundCloud.clientSecret && (
                      <p className="text-xs text-muted-foreground">Leave empty to keep existing secret</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="sc-callback-uri">Callback URI</Label>
                    <input
                      id="sc-callback-uri"
                      className={inputClass}
                      value={soundCloud.callbackUri}
                      onChange={(e) => setSoundCloud({ ...soundCloud, callbackUri: e.target.value })}
                      placeholder="https://example.com/callback"
                    />
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
          <div className="mt-6 flex items-center gap-2">
            {pubTab === "ftp" && (
              <>
                <Button onClick={handleTestFtp} disabled={testingFtp || !ftp.host}>
                  <TestTube className="mr-2 size-4" />
                  {testingFtp ? "Testing..." : "Test Connection"}
                </Button>
                <Button onClick={handleSaveFtp} disabled={savingFtp || !ftp.host}>
                  <Save className="mr-2 size-4" />
                  {savingFtp ? "Saving..." : "Save"}
                </Button>
              </>
            )}
            {pubTab === "soundcloud" && (
              <>
                <Button onClick={handleTestSoundCloud} disabled={testingSc}>
                  <TestTube className="mr-2 size-4" />
                  {testingSc ? "Testing..." : "Test Connection"}
                </Button>
                <Button onClick={handleSaveSoundCloud} disabled={savingSc || !soundCloud.clientId}>
                  <Save className="mr-2 size-4" />
                  {savingSc ? "Saving..." : "Save"}
                </Button>
              </>
            )}
          </div>
        </TabsContent>

        <TabsContent value="backups">
          <Card>
            <CardHeader>
              <CardTitle>Database Backups</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="flex items-center gap-2 rounded-md border border-border bg-muted/50 px-3 py-2 text-sm text-muted-foreground">
                <Database className="size-4 shrink-0" />
                Scheduled, compressed snapshots of the database are written to the data folder. Changes to the schedule take effect immediately.
              </div>

              <div className="flex items-center gap-2">
                <Switch
                  id="backup-enabled"
                  checked={backup.enabled}
                  onCheckedChange={(checked) => setBackup({ ...backup, enabled: checked })}
                />
                <Label htmlFor="backup-enabled">Enable scheduled backups</Label>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="backup-cron">Cron schedule (UTC)</Label>
                  <Input
                    id="backup-cron"
                    value={backup.cron}
                    onChange={(e) => setBackup({ ...backup, cron: e.target.value })}
                    placeholder="0 0 2 * * *"
                  />
                  {cronValid ? (
                    <p className="text-xs text-muted-foreground">{cronHuman}. Next run: {cronNextRun}</p>
                  ) : (
                    <p className="text-xs text-destructive">Invalid cron expression</p>
                  )}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="backup-retention">Keep last N backups</Label>
                  <Input
                    id="backup-retention"
                    type="number"
                    min={1}
                    value={backup.retentionCount}
                    onChange={(e) => setBackup({ ...backup, retentionCount: parseInt(e.target.value) || 1 })}
                  />
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Button onClick={handleSaveBackup} disabled={savingBackup || !cronValid || backup.retentionCount < 1}>
                  <Save className="mr-2 size-4" />
                  {savingBackup ? "Saving..." : "Save"}
                </Button>
                <Button variant="outline" onClick={handleRunBackup} disabled={runningBackup}>
                  <Archive className="mr-2 size-4" />
                  {runningBackup ? "Backing up..." : "Back up now"}
                </Button>
              </div>

              <div>
                <h3 className="mb-2 text-sm font-medium">Existing backups</h3>
                {backupLoading ? (
                  <p className="text-sm text-muted-foreground">Loading...</p>
                ) : backups.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No backups yet.</p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>File</TableHead>
                        <TableHead className="text-right">Size</TableHead>
                        <TableHead>Created</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {backups.map((b) => (
                        <TableRow key={b.name}>
                          <TableCell className="font-mono text-xs">{b.name}</TableCell>
                          <TableCell className="text-right text-sm">{formatBytes(b.sizeBytes)}</TableCell>
                          <TableCell className="text-sm text-muted-foreground">{new Date(b.createdAt).toLocaleString()}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <ProviderConfigDialog
        open={dialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            setDialogOpen(false);
            setEditConfig(null);
          }
        }}
        userId={selectedUser.id}
        editConfig={editConfig}
        existingConfigs={configs}
        onSaved={handleDialogSaved}
      />
    </div>
  );
}
