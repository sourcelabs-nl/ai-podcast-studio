## Why

Short-form articles are threads, and the dashboard does not say so. An aggregated article is built from several posts joined into one body, but both views that list articles — the episode Articles tab and the upcoming-articles page — render it as a single card titled after the parent post. Nothing distinguishes an article that is one tweet from one that is a fourteen-post conversation, and the individual posts cannot be read at all.

That gap widened when the X sources moved to a combined Narro feed and aggregation began grouping by author. In episode 191, `X via Narro` is the largest source group with 16 articles covering 2 to 14 posts each, and several authors contribute more than one thread:

| author | threads | posts per thread |
|---|---|---|
| Anthony Morris | 1 | 14 |
| Mario Zechner | 2 | 10, 2 |
| Amjad Masad | 2 | 7, 3 |
| Ivan Fioravanti | 3 | 5, 5, 4 |

So the reader sees 16 near-identical cards and cannot tell which represents a substantial conversation. The posts are in the article body, joined by `\n\n---\n\n`, but the UI would have to reverse-engineer the aggregator's separator to split them, which would break silently the day that format changed.

## What Changes

- `EpisodeArticleResponse` gains `postCount`, the number of posts the article was aggregated from. Both the episode articles endpoint and the upcoming-articles endpoint return it. An article that is not an aggregate reports 1.
- A new endpoint returns the posts behind one article: `GET /users/{userId}/podcasts/{podcastId}/articles/{articleId}/posts`, ordered oldest first, each with its title, body, url and `publishedAt`. It is podcast-scoped and rejects an article belonging to another podcast's sources, so the posts follow the same ownership rules as every other read.
- The posts are fetched on expansion rather than embedded in the list response. The article body already carries the same text, so embedding them would roughly double a list payload that is dominated by bodies, and the upcoming view can list far more articles than an episode does.
- A shared `ArticleCard` component replaces the two near-identical copies in `articles-tab.tsx` and the upcoming page, which differed only in whether they rendered the subtopic badge. The card shows a post-count badge for a multi-post article and expands to list the posts, each with its timestamp. A single-post article is unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `episode-articles-api`: the article response carries `postCount`, and a new endpoint serves the posts behind an article.
- `upcoming-articles-api`: the article response carries `postCount`.
- `episode-detail-page`: the article card shows thread size and expands to the underlying posts.

## Impact

- Backend: `EpisodeArticleResponse` (new field), new `ArticlePostResponse`; `EpisodeArticleRepositoryCustom` (post-count subquery); a post-count lookup for the upcoming mapper; a posts-by-article query; a new controller endpoint and its service method.
- Frontend: new shared `components/article-card.tsx`; `articles-tab.tsx` and the upcoming page use it; `EpisodeArticle` type gains `postCount`, new `ArticlePost` type.
- No schema change: `post_articles` already links posts to articles.
