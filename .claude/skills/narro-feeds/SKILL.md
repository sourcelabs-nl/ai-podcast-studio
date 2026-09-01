---
name: narro-feeds
description: Use when working with the Narro X/social feeds that supply this podcast's X content — inspecting or restructuring feeds and profiles, adding or removing accounts, diagnosing why the Narro source stopped delivering, or wiring a Narro feed into the app as a source. Covers the plan limits, the working API calls, and the UI-automation dead ends.
user-invocable: false
---

# Narro feeds

Narro (narro.info) turns X/social accounts into RSS. It replaced the per-account Nitter and
zpravobot sources, which are all dead and disabled. Everything the podcast gets from X now comes
through Narro feeds.

## The one thing that costs money: profiles, not feeds

Feeds are free and unlimited. **Profiles are the paid unit, and the cap is account-wide.**

| plan | profiles |
|---|---|
| Free trial (14 days, no card) | 20 |
| Standard $8/mo | 100 |
| More $19/mo | 300 |
| Max $99/mo | 1,800 |

After the trial ends **the account pauses until a tier is picked** — the RSS feeds stop, and with
them all X content for the podcast. Check `/settings` (Billing) for the trial end date before
assuming a feed problem is technical.

**The cap counts assignments, not distinct profiles.** Narro's own pricing FAQ says "the same
handle in three of your feeds is still one", but the API does not behave that way: at 20/20,
assigning an already-followed profile to a second feed returns `400`. Verified with a real mouse
click, not just automation. So a profile lives in exactly one feed while at the cap, and
reorganising means **moving**, never copying. Do not promise "seat-neutral, just drag it around" —
that was wrong.

Removing from a feed does free a seat immediately (`19/20 — 1 unassigned`).

## API (what actually works)

Base `https://api.narro.info`. Auth is a Bearer token from `localStorage.getItem('auth_token')`
in a logged-in narro.info tab. Run these with `browser_evaluate` from that tab.

```js
const tok = localStorage.getItem('auth_token');
const H = { 'Authorization': 'Bearer ' + tok, 'Content-Type': 'application/json' };
```

| operation | call |
|---|---|
| list feeds (ids + names) | `GET /api/feeds` → `[{id, name}]` |
| list assignments | `GET /api/profiles` → `{profiles: [...]}` |
| remove from a feed | `DELETE /api/feeds/{feedId}/profiles/{socialProfileId}` → 200 |
| add to a feed | `POST /api/profiles` body `{url, feed_id}` → 201 |

`GET /api/profiles` returns **assignment** records, not accounts: each has `id` (assignment),
`social_profile_id`, `feed_id`, `over_limit`, and a nested `social_profile` with `username` and
`url`. Use `social_profile.url` for the add call rather than building a URL by hand.

Dead ends, all returning 404 — don't retry them:
`POST /api/feeds/{id}/profiles`, `PUT /api/feeds/{id}/profiles/{socialProfileId}`,
`PUT /api/profiles/{assignmentId}`. `POST /api/profiles` without a url answers
`{"error":"url or urls is required"}`; the `urls` plural hints at a bulk form that has not been
tried yet.

**Moving a profile: DELETE first, then POST.** In that order, so the count dips to 19 and never
tries to exceed the cap. The reverse order fails with 400 at the cap.

## Getting a logged-in tab

The Playwright MCP browser has its own profile and is **not** logged in to the user's session.
Login is a magic link by default, and the emailed link opens in the user's own browser, not this
one. Two ways in: the user logs in inside the Playwright window (there is a "Use password
instead" option), or the user pastes the `\/auth-callback?token_hash=…` URL and it is navigated
directly. That URL is single-use. Never fill in the user's email or password.

## Do not automate the Narro UI

The profile actions menu (Assign to Feeds) **cannot be opened programmatically**. Neither
`element.click()` nor a dispatched pointerdown/mousedown/pointerup/click sequence opens it; the
menu stays empty. Real `browser_click` calls work but toggle unpredictably and cost three calls
per profile. Worse, a scripted loop over the list silently resolved the same profile 20 times and
still reported success, because it never verified. Use the API above instead, and **verify by
re-reading `GET /api/profiles`** rather than trusting a script's own report.

Creating a feed in the UI is fine: `/feeds` → "+ Create Feed", name it, Create. Note that typing
`&amp;` into the name field lands literally; type a plain `&`.

## Wiring a feed into the app

A feed's RSS URL is simply **`https://rss.narro.info/{feedId}`** — the same id `GET /api/feeds`
returns. No need to hunt for a share link.

Add one source per feed with `aggregate: true`, and give it the same label as the feed so the
dashboard's source groups match Narro. `PUT` on a source is a full replace, so send every existing
field back and change only what you mean to.

Two protections make splitting one feed into several safe:

- **Cross-source dedup** (`SourcePoller.kt`): all sources of the podcast are passed as siblings, and
  a post whose body hash already exists under any of them is skipped. The same tweet arriving via
  two Narro feeds does not produce a duplicate article.
- **First-poll floor** (`rssFetchFloor`): a new source starts at its creation time, so there is no
  backfill flood — but also a small gap for anything published just before the switch.

**Never delete a source to retire it — disable it.** `SourceService.delete` removes the source's
posts *and* articles, and `episode_articles.article_id` is `ON DELETE CASCADE`, so deleting the old
X sources would have stripped the source attribution from 113 published episodes (3,872 articles,
1,789 links). Disabling keeps all of it and stops the polling.

## Feed layout in use

| feed | app source label | who |
|---|---|---|
| `X - Labs & companies` | same | org and analysis accounts: announcements, low volume |
| `X - Lab people` | same | individuals working at the labs |
| `X - Tooling & builders` | same | devtool founders and prolific dev voices; this is where the long reply threads come from |

`Daily Agentic AI Podcast` is the Home Feed and is now empty; its old source `X via Narro` is
disabled but retains its history. Roughly 15 accounts from the Nitter era are still missing and
need seats before they can be added — see the disabled `nitter.net` / `zpravobot.news` sources for
the full list of handles.

Judging what to keep: score per author, not per feed. Relevance is a podcast-level setting, so
splitting feeds changes overview, never weighting. Query `articles` joined on the Narro source and
group by the handle in `url` to get threads, average relevance and how many clear the threshold.
