"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Plus, Trash2 } from "lucide-react";

interface SubtopicRow {
  name: string;
  weight: string;
}

interface SubtopicsEditorProps {
  value: Record<string, number> | null | undefined;
  onChange: (value: Record<string, number>) => void;
}

function toRows(value: Record<string, number> | null | undefined): SubtopicRow[] {
  if (!value || Object.keys(value).length === 0) return [];
  return Object.entries(value).map(([name, weight]) => ({ name, weight: String(weight) }));
}

function toRecord(rows: SubtopicRow[]): Record<string, number> {
  const result: Record<string, number> = {};
  for (const row of rows) {
    const name = row.name.trim();
    if (name === "") continue;
    const w = parseInt(row.weight, 10);
    if (Number.isNaN(w) || w < 1 || w > 10) continue;
    result[name] = w;
  }
  return result;
}

export function SubtopicsEditor({ value, onChange }: SubtopicsEditorProps) {
  const [rows, setRows] = useState<SubtopicRow[]>(() => toRows(value));

  useEffect(() => {
    setRows(toRows(value));
  }, [value]);

  function updateRow(index: number, field: "name" | "weight", newValue: string) {
    const updated = [...rows];
    updated[index] = { ...updated[index], [field]: newValue };
    setRows(updated);
    onChange(toRecord(updated));
  }

  function addRow() {
    setRows([...rows, { name: "", weight: "5" }]);
  }

  function removeRow(index: number) {
    const updated = rows.filter((_, i) => i !== index);
    setRows(updated);
    onChange(toRecord(updated));
  }

  return (
    <div className="space-y-2">
      {rows.map((row, index) => {
        const w = parseInt(row.weight, 10);
        const invalid = row.weight !== "" && (Number.isNaN(w) || w < 1 || w > 10);
        return (
          <div key={index} className="flex items-center gap-2">
            <input
              type="text"
              value={row.name}
              onChange={(e) => updateRow(index, "name", e.target.value)}
              placeholder="Subtopic (e.g. LLM releases)"
              className="h-9 flex-1 rounded-md border border-input bg-background px-3 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
            />
            <input
              type="number"
              min={1}
              max={10}
              value={row.weight}
              onChange={(e) => updateRow(index, "weight", e.target.value)}
              placeholder="Weight (1-10)"
              className={`h-9 w-32 rounded-md border bg-background px-3 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 ${
                invalid ? "border-destructive focus-visible:border-destructive" : "border-input focus-visible:border-ring"
              }`}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={() => removeRow(index)}
            >
              <Trash2 className="size-4 text-muted-foreground" />
            </Button>
          </div>
        );
      })}
      <Button type="button" size="icon-lg" title="Add subtopic" onClick={addRow}>
        <Plus className="size-4" />
      </Button>
    </div>
  );
}
