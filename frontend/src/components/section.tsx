import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * The card a block of content sits in, with its heading inside it.
 *
 * Shared across the tabs so the panels cannot drift apart when one of them changes. A tab that is
 * one block of content passes no [title]: the tab trigger already names it, and a heading repeating
 * it is noise.
 */
export function Panel({
  title,
  className,
  children,
}: {
  title?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn("rounded-lg border border-border p-4", className)}>
      {title && <h3 className="mb-3 text-sm font-semibold">{title}</h3>}
      {children}
    </div>
  );
}
