// The preview stream runs for minutes and its value is the live progress, so the response must
// never be cached or collected before it is forwarded.
export const dynamic = "force-dynamic";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ userId: string; podcastId: string }> }
) {
  const { userId, podcastId } = await params;
  const backendBase = process.env.BACKEND_URL || "http://localhost:8085";
  const backendUrl = `${backendBase}/users/${userId}/podcasts/${podcastId}/preview`;

  const backendResponse = await fetch(backendUrl, {
    headers: { Accept: "text/event-stream" },
  });

  if (!backendResponse.ok) {
    return new Response(backendResponse.statusText, {
      status: backendResponse.status,
    });
  }

  return new Response(backendResponse.body, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
      // Tells any reverse proxy in front of the app to forward each event as it arrives instead
      // of buffering the stream, which would batch progress updates into one late burst.
      "X-Accel-Buffering": "no",
    },
  });
}
