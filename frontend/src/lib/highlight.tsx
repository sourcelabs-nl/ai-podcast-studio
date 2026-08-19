import { Fragment, type ReactNode } from "react";

function escapeRegExp(term: string): string {
  return term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * Wraps every whole-word occurrence of any term in bold.
 *
 * The word boundary mirrors the search query's `[^a-z]` rule: a letter ends a word but a digit does
 * not, so "qwen" highlights inside "Qwen3.8" while "java" leaves "JavaScript" alone. Matching the
 * query's own rule matters, because bolding a fragment the search did not match on would point at
 * the wrong evidence.
 */
export function highlightTerms(text: string, terms: string[]): ReactNode {
  const usable = terms.filter((term) => term.length > 0).map(escapeRegExp);
  if (usable.length === 0) return text;

  const pattern = new RegExp(`(?<![a-z])(${usable.join("|")})(?![a-z])`, "gi");
  // split() on a capturing group alternates plain text and captured matches.
  return text.split(pattern).map((part, index) =>
    index % 2 === 1 ? (
      <strong key={index} className="font-semibold text-foreground">{part}</strong>
    ) : (
      <Fragment key={index}>{part}</Fragment>
    )
  );
}
