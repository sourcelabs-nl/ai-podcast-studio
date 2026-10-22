/**
 * Names present in the saved subtopics but missing from the edited ones
 * (renamed or removed). Weight changes and added names are not reported.
 */
export function findLostSubtopicNames(
  saved: Record<string, number> | null | undefined,
  edited: Record<string, number> | null | undefined,
): string[] {
  const editedNames = new Set(Object.keys(edited ?? {}));
  return Object.keys(saved ?? {}).filter((name) => !editedNames.has(name));
}
