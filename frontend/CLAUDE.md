# Frontend (Next.js Dashboard)

The frontend uses Next.js (App Router), shadcn/ui, and Tailwind CSS v4.

- **Theme:** Orange primary color using oklch variables following official shadcn theming docs. All CSS variables go in `globals.css` under `:root` / `.dark` using oklch format, mapped via `@theme inline`.
- **Buttons:** All action buttons use the `default` variant (orange) — never `outline` or `secondary` for action buttons. Use consistent `size="sm"` across the app. Every button must have an icon (from lucide-react) alongside its label. Only use `outline` variant for cancel/close buttons in dialogs, and `destructive` for destructive actions (e.g., Discard). Use `ghost` variant only for inline icon-only buttons (e.g., delete row in key-value editors).
- **Badges:** Status badges use `default` (orange) for most statuses, except: `outline` (white) for GENERATED, `secondary` (grey) for DISCARDED. The "Published" badge uses `default` (orange) to visually stand out.
- **Header layout:** Both podcast detail and episode detail pages follow the same header order: title + inline badges on the first line, date/schedule in `text-sm` italic on the second line, description/topic in `text-sm` on the third line. The header icon in the app bar is a Podcast icon from lucide-react next to "AI Podcast Studio".
- **Episode list columns:** #, Date, Day, Status (with Published badge), Script Model (`text-xs`), TTS Model (`text-xs`), Cost (right-aligned, formatted as dollars), Actions (action buttons + View button with `outline` variant).
- **Script rendering:** Episode scripts render in chat-bubble style using `text-sm` for body text. Monologue styles use paragraph bubbles; dialogue/interview styles use alternating left/right chat bubbles with speaker labels.
- **Add/action buttons:** "Add" buttons (e.g., add row, add provider) always go below the content they add to, never in the card header. Use `size="icon-lg"` with a `+` icon. This applies to key-value editors, API key tables, and any list-like content.
- **Dialog width:** Script viewer dialog uses near-full viewport width (`w-[90vw] !max-w-7xl`). The `!important` is needed to override shadcn's default `sm:max-w-lg`.
- **Nested tabs:** Do not nest Radix `Tabs` components (shadcn Tabs). Inner tabs conflict with outer tabs context. Use state-based tab switching with styled buttons for sub-tabs inside a card.
- **API proxy:** `next.config.ts` rewrites `/api/**` to `http://localhost:8085/**`. Update the port if the backend port changes. The dashboard always proxies the backend rather than calling it directly, so the backend never has to be exposed. The rewrite is fine for ordinary request/response calls but must not be used for streams: see Server-Sent Events below.

- **Running:** `npm run dev` (use `/Users/soudmaijer/.nvm/versions/node/v22.16.0/bin/npm` to avoid the stale npm v2 at `~/node_modules/.bin/npm`). Prefer `./start.sh` and `./stop.sh` from the project root, which manage the backend and the frontend together.
- **Identifying the running dev server:** `npm run dev` spawns `next dev`, which spawns the `next-server` process that actually binds the port, so a recorded PID is a wrapper and does not tell you whether the frontend is serving. Use `lsof -nP -iTCP:3005 -sTCP:LISTEN`. This matters more than it sounds: an orphaned `next-server` keeps serving the old code while a restart reports success and dies on `EADDRINUSE`, so a change that is merged and "restarted" may not be the change in the browser. `stop.sh` and `start.sh` resolve services by port for this reason.
- **Config changes need a restart:** `next.config.ts` is picked up by a dev server restart. A hot reload of page code does not re-read it.
- **Root symlinks:** `node_modules` and `tsconfig.json` in the project root are symlinks to their `frontend/` counterparts. These exist so that IDE language servers (TypeScript, ESLint) can resolve modules and provide diagnostics when files are opened from the project root, rather than only when opened from `frontend/`.

## Server-Sent Events

Three backend endpoints stream SSE: the preview pipeline, the preview audio synthesis, and the user event stream. Every one of them is proxied through a route handler under `frontend/src/app/api/`, and all of them share `frontend/src/lib/sse-proxy.ts`. Add new streams to that helper rather than writing a fourth proxy.

A stream that is buffered anywhere in the path is indistinguishable from a stream that is never sent: the events arrive in one burst when the connection closes, by which time the progress they describe is over. Four separate things caused exactly that, and all four have to stay right:

- **Never let a stream fall back to the `next.config` rewrite.** The rewrite collects the response, so events surface only at the end. A stream needs its own route handler; the user event stream had none and looked broken for exactly this reason.
- **Pump chunks through a `ReadableStream` by hand.** Passing the upstream `Response.body` straight into a new `Response` lets Next collect it instead of forwarding it. `proxyEventStream` reads the upstream reader and enqueues each chunk.
- **Do not compress a stream.** Gzip withholds bytes until its buffer fills, which for small progress events means nothing arrives until the end. `compress: false` in `next.config.ts` and `Cache-Control: no-cache, no-transform` on the response both exist for this.
- **Set `export const dynamic = "force-dynamic"` and `export const runtime = "nodejs"`** on every streaming route handler.

**Reading a stream in the browser:** an SSE event is an `event:` line followed by a `data:` line, and a network chunk boundary can fall between them. Declare the current event name *outside* the read loop. Resetting it per read silently drops every event that happened to be split, which produces a stream that mostly works and occasionally loses events, including the final `result`.

**Verifying a stream really streams:** compare arrival timing against the backend directly, and use a window long enough to span the backend's heartbeat interval.

```
curl -sN --max-time 40 -H "Accept: text/event-stream" http://localhost:3005/api/users/<id>/events \
  | while IFS= read -r l; do [ -n "$l" ] && echo "+$(date +%s) $l"; done
```

Events must appear spread through the window. If every line lands at the moment the connection closes, it is still buffered. A single short request proves nothing, because a sparse heartbeat can be missed by timing alone.
