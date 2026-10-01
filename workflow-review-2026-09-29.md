# Workflow review (29 september 2026)

Basis: 37 Claude Code-sessies van de afgelopen 30 dagen en 107 commits van de afgelopen 5 weken. De tellingen zijn keyword-matches, dus ze geven een indruk en geen exacte cijfers.

Al afgedekt door bestaande tooling:

- OpenSpec, via de openspec-* skills (208 calls).
- Knowledge, via kb-add, kb-ask en kb-tidy.
- Commits, via `/conventional-commits:cc`.

## Grootste kansen

1. **`episode-ops` skill met een script**
   - Ongeveer 404 handmatige `curl localhost:8080/users/$U/podcasts/$P/episodes/...` calls, vaak met `sleep` om te pollen.
   - De UUID's zijn zo'n 30 keer opnieuw geplakt.
   - Idee: een script met `list`, `status`, `generate`, `regenerate-audio`, `approve` en `wait`, met compacte output.
2. **`episode-debug` skill**
   - Voor vragen als "waarom is 226 gefailed" of "waarom is compose 20m?" gebeurt nu steeds hetzelfde handwerk: ongeveer 170 `sqlite3` queries, 270 greps in `app.log` en ffprobe.
   - Die directe DB-queries gaan in tegen de regel in CLAUDE.md.
   - Idee: één read-only triage die status, kosten per stage, latency, een logfragment en de audioduur ophaalt.
3. **Script voor herstarten en verifiëren (`verify.sh`)**
   - 92 aanroepen van stop.sh/start.sh en 278 `mvn` calls.
   - Idee: `mvn test` stil draaien (alleen de failures tonen), de app herstarten en wachten tot de health check slaagt.
4. **`openrouter-probe` skill**
   - Staat nu alleen als memory, zonder vaste werkwijze of script.
   - Idee: een payload testen tegen OpenRouter en de kosten per call tonen.
5. **Hook die waarschuwt bij grote tool-output**
   - Idee: een PostToolUse-hook die waarschuwt als Bash-output boven ongeveer 5k tokens uitkomt.

Aanbevolen startpunt: 1 en 3.

## Observaties over de werkwijze

- **Ongeveer 626 `python3` heredoc-edits** in plaats van de Edit-tool. Een regel in CLAUDE.md kan dat oplossen: "gebruik Edit voor bestaande bestanden".
- **Lange sessies waarin de draad zoekraakt.** Er waren 14 keer `/compact` en veel context-samenvattingen. Kortere sessies per taak helpen hiertegen.
- **Scope drift en een niet-gevraagd formaat** (een HTML-versie). Dat valt al onder de bestaande regels over vragen en plannen.
- **Productpunt:** "generate episode" zou achter een approval gate moeten. Dit kan als kleine OpenSpec change.
