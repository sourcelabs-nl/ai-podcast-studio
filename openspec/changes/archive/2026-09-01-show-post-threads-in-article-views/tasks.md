## 1. Expose thread size on the article responses

- [x] 1.1 Add `postCount: Int` to `EpisodeArticleResponse`
- [x] 1.2 Add a post-count subquery to `findArticlesWithSourcesByEpisodeId`
- [x] 1.3 Add a post-count-by-article-ids lookup for the upcoming mapper
- [x] 1.4 Populate `postCount` in `UpcomingContent.toResponse`, defaulting to 1 where an article has no post links

## 2. Serve the posts behind an article

- [x] 2.1 Add `ArticlePostResponse(id, title, body, url, publishedAt)`
- [x] 2.2 Add a posts-by-article-id query ordered by `publishedAt` ascending
- [x] 2.3 Add a service method that resolves the article, verifies its source belongs to the podcast, and maps the posts
- [x] 2.4 Add `GET /{podcastId}/articles/{articleId}/posts` to the controller, 404 on unknown or cross-podcast article

## 3. Shared article card with thread expansion

- [x] 3.1 Extract `components/article-card.tsx` from the two near-identical copies, keeping the subtopic badge
- [x] 3.2 Add `postCount` to the `EpisodeArticle` type and an `ArticlePost` type
- [x] 3.3 Show a post-count badge when `postCount > 1`
- [x] 3.4 Expand to fetch and list the posts oldest first, each with its timestamp
- [x] 3.5 Use the shared card in `articles-tab.tsx` and in the upcoming page

## 4. Tests

- [x] 4.1 Repository: the episode query reports the aggregated post count
- [x] 4.2 Repository: posts are returned oldest first for an article
- [x] 4.3 Service/controller: a cross-podcast article returns 404
- [x] 4.4 Mapper: upcoming articles carry their post counts, defaulting to 1
- [x] 4.5 Run `mvn test` and confirm the whole suite passes (1268 tests, 0 failures)
- [x] 4.6 Run `npx tsc --noEmit` in `frontend/` (never `npm run build` while the dev server runs)

## 5. Verify in the running app

- [x] 5.1 Check episode 191's Articles tab shows the Narro threads with their post counts (11 badges: 14, 10, 8, 7, 5, 5, 4, 3, 3, 2, 2; other sources show none)
- [x] 5.2 Expand a large thread and confirm the posts list oldest first (14 posts, each with its timestamp and x.com link)
