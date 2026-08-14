/**
 * Streaming proxy for the backend's Server-Sent Events endpoints.
 *
 * The dashboard proxies the backend rather than exposing it, so every event stream passes through
 * Next. Two things here are load bearing. Chunks are pumped through a ReadableStream by hand rather
 * than by handing the upstream body to the Response: that keeps the response a stream Next forwards
 * as it arrives instead of a body it may collect first. And `no-transform` tells any layer in the
 * path to leave the bytes alone, because compressing a stream of small events withholds them until
 * a compression buffer fills, which for a pipeline that reports progress for minutes means the
 * progress arrives only once it no longer matters.
 */
const BACKEND_BASE = process.env.BACKEND_URL || "http://localhost:8085";

type EventStreamRequest = {
  /** Body to forward, for the streams that are started by a POST. */
  body?: string;
};

export async function proxyEventStream(
  backendPath: string,
  { body }: EventStreamRequest = {}
): Promise<Response> {
  const upstream = await fetch(`${BACKEND_BASE}${backendPath}`, {
    method: body === undefined ? "GET" : "POST",
    headers: body === undefined
      ? { Accept: "text/event-stream" }
      : { Accept: "text/event-stream", "Content-Type": "application/json" },
    body,
    cache: "no-store",
  });

  if (!upstream.ok || !upstream.body) {
    return new Response(upstream.statusText, { status: upstream.status });
  }

  const upstreamBody = upstream.body;
  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const reader = upstreamBody.getReader();
      try {
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          controller.enqueue(value);
        }
        controller.close();
      } catch (error) {
        controller.error(error);
      } finally {
        reader.releaseLock();
      }
    },
    // The client navigated away or aborted; stop pulling from the backend.
    cancel(reason) {
      void upstreamBody.cancel(reason);
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    },
  });
}
