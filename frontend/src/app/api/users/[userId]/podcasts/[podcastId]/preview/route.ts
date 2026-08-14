import { proxyEventStream } from "@/lib/sse-proxy";

// The preview stream runs for minutes and its value is the live progress, so it must never be
// cached or collected before it is forwarded.
export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ userId: string; podcastId: string }> }
) {
  const { userId, podcastId } = await params;
  return proxyEventStream(`/users/${userId}/podcasts/${podcastId}/preview`);
}
