import { proxyEventStream } from "@/lib/sse-proxy";

// Synthesising a full script runs for minutes and its value is the live per-chunk progress, so the
// response must never be cached or collected before it is forwarded.
export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function POST(
  request: Request,
  { params }: { params: Promise<{ userId: string; podcastId: string }> }
) {
  const { userId, podcastId } = await params;
  return proxyEventStream(`/users/${userId}/podcasts/${podcastId}/preview/audio`, {
    body: await request.text(),
  });
}
