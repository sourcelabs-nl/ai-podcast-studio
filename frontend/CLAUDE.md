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
- **API proxy:** `next.config.ts` rewrites `/api/**` to `http://localhost:8085/**`. Update the port if the backend port changes.
- **Running:** `npm run dev` (use `/Users/soudmaijer/.nvm/versions/node/v22.16.0/bin/npm` to avoid the stale npm v2 at `~/node_modules/.bin/npm`).
- **Root symlinks:** `node_modules` and `tsconfig.json` in the project root are symlinks to their `frontend/` counterparts. These exist so that IDE language servers (TypeScript, ESLint) can resolve modules and provide diagnostics when files are opened from the project root, rather than only when opened from `frontend/`.
