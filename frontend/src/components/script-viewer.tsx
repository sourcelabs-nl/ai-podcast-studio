"use client";

import { useEffect, useRef } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface ScriptContentProps {
  scriptText: string;
  style: string;
  speakerNames?: Record<string, string>;
  /**
   * A 0-based turn index to scroll to and highlight, addressed the same way the evaluation
   * endpoints address turns. See `parseMultiSpeakerScript`.
   */
  focusedTurn?: number | null;
  /**
   * Label each turn with its index. Only wanted where the reader cross-references turn numbers
   * against the evaluation tab; elsewhere the numbers are noise.
   */
  showTurnNumbers?: boolean;
}

interface ScriptViewerProps extends ScriptContentProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

interface SpeakerBlock {
  speaker: string;
  text: string;
}

const FIRST_SPEAKER_STYLE = { bg: "bg-muted border-border text-foreground", name: "text-muted-foreground" };
const SECOND_SPEAKER_STYLE = { bg: "bg-primary border-primary text-primary-foreground", name: "text-primary" };

// Tolerant of malformed tags the LLM occasionally emits (mismatched or missing closing tags):
// a turn's speaker comes from its opening tag, and the turn ends at the next tag token regardless
// of what that token says. Mirrors the backend DialogueScriptParser so turns are never dropped.
//
// The mirroring is load-bearing beyond parity of content: the block index here must equal the
// turn index the backend reports, because ScriptJudge numbers turns with
// DialogueScriptParser.parse(...).mapIndexed and ScriptMetrics numbers BackchannelCandidate the
// same way. The evaluation tab jumps to a turn by that number, so if the two parsers ever
// disagree the reviewer is sent to the wrong turn. Change one only with the other in hand.
function parseMultiSpeakerScript(scriptText: string): SpeakerBlock[] | null {
  const tagPattern = /<\/?(\w+)>/g;
  const blocks: SpeakerBlock[] = [];
  let openSpeaker: string | null = null;
  let lastEnd = 0;
  let match;

  while ((match = tagPattern.exec(scriptText)) !== null) {
    const preceding = scriptText.slice(lastEnd, match.index).trim();
    if (openSpeaker !== null && preceding) {
      blocks.push({ speaker: openSpeaker, text: preceding });
    }
    const isClosing = match[0].startsWith("</");
    openSpeaker = isClosing ? null : match[1];
    lastEnd = tagPattern.lastIndex;
  }

  const trailing = scriptText.slice(lastEnd).trim();
  if (openSpeaker !== null && trailing) {
    blocks.push({ speaker: openSpeaker, text: trailing });
  }

  return blocks.length > 0 ? blocks : null;
}

/**
 * How many turns a script renders as, so a caller holding turn indices from the evaluation
 * endpoints can tell whether a given index exists before offering to jump to it.
 */
export function countScriptTurns(scriptText: string, style: string): number {
  const isMultiSpeaker = style === "dialogue" || style === "interview";
  if (!isMultiSpeaker) return 0;
  return parseMultiSpeakerScript(scriptText)?.length ?? 0;
}

function MonologueScript({ scriptText }: { scriptText: string }) {
  const paragraphs = scriptText.split(/\n\n+/).filter(Boolean);
  return (
    <div className="space-y-3">
      {paragraphs.map((paragraph, i) => (
        <div
          key={i}
          className="rounded-2xl border border-border bg-muted px-4 py-3"
        >
          <p className="text-sm leading-relaxed">{paragraph}</p>
        </div>
      ))}
    </div>
  );
}

function MultiSpeakerScript({
  blocks,
  speakerNames,
  focusedTurn,
  showTurnNumbers,
}: {
  blocks: SpeakerBlock[];
  speakerNames?: Record<string, string>;
  focusedTurn?: number | null;
  showTurnNumbers?: boolean;
}) {
  const speakers = [...new Set(blocks.map((b) => b.speaker))];
  const isFirst = speakers[0];
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (focusedTurn === null || focusedTurn === undefined) return;
    const turn = containerRef.current?.querySelector(`[data-turn="${focusedTurn}"]`);
    turn?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [focusedTurn]);

  return (
    <div className="space-y-3" ref={containerRef}>
      {blocks.map((block, i) => {
        const displayName =
          speakerNames?.[block.speaker] ?? block.speaker.toUpperCase();
        const alignRight = block.speaker !== isFirst;
        const styles = alignRight ? SECOND_SPEAKER_STYLE : FIRST_SPEAKER_STYLE;
        const focused = i === focusedTurn;

        return (
          <div
            key={i}
            data-turn={i}
            className={`flex ${alignRight ? "justify-end" : "justify-start"}`}
          >
            <div className={`max-w-[75%] space-y-1`}>
              <span
                className={`text-xs font-semibold ${styles.name} ${
                  alignRight ? "block text-right" : ""
                }`}
              >
                {displayName}
                {showTurnNumbers && (
                  <span className="ml-2 font-normal text-muted-foreground">#{i}</span>
                )}
              </span>
              <div
                className={`rounded-2xl border px-4 py-3 ${styles.bg} ${
                  alignRight ? "rounded-tr-sm" : "rounded-tl-sm"
                } ${focused ? "ring-2 ring-primary ring-offset-2 ring-offset-background" : ""}`}
              >
                <p className="text-sm leading-relaxed">{block.text}</p>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function ScriptContent({
  scriptText,
  style,
  speakerNames,
  focusedTurn,
  showTurnNumbers,
}: ScriptContentProps) {
  const isMultiSpeaker = style === "dialogue" || style === "interview";
  const blocks = isMultiSpeaker ? parseMultiSpeakerScript(scriptText) : null;

  return blocks ? (
    <MultiSpeakerScript
      blocks={blocks}
      speakerNames={speakerNames}
      focusedTurn={focusedTurn}
      showTurnNumbers={showTurnNumbers}
    />
  ) : (
    <MonologueScript scriptText={scriptText} />
  );
}

export function ScriptViewer({
  open,
  onOpenChange,
  scriptText,
  style,
  speakerNames,
}: ScriptViewerProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[80vh] w-[95vw] !max-w-[1600px] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Episode Script</DialogTitle>
        </DialogHeader>
        <div className="mt-4">
          <ScriptContent scriptText={scriptText} style={style} speakerNames={speakerNames} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
