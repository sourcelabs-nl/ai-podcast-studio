import { proxyEventStream } from "@/lib/sse-proxy";

// The user event stream drives episode notifications and stays open indefinitely, so it must never
// be cached or collected before it is forwarded. Without an explicit route handler this path falls
// back to the next.config rewrite, which buffers the stream.
export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ userId: string }> }
) {
  const { userId } = await params;
  return proxyEventStream(`/users/${userId}/events`);
}
