import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * The frame a section's content sits in.
 *
 * Shared across the episode tabs so the panels cannot drift apart when one of them changes. Empty
 * and failure states deliberately stay outside it: a box drawn around a single line of italic text
 * reads as a broken panel rather than as a panel.
 */
export function Panel({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn("rounded-lg border border-border p-4", className)}>{children}</div>;
}

/**
 * A titled block within a tab: the heading sits above the panel rather than inside it, so a tab
 * reads as a column of labelled sections rather than as boxes with captions.
 *
 * The content decides whether to sit in a [Panel], because a section that has nothing to show says
 * so in plain text instead.
 */
export function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-semibold">{title}</h3>
      {children}
    </div>
  );
}
