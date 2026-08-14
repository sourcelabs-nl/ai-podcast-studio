import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The dashboard proxies two Server-Sent Events streams from the backend: the preview pipeline
  // progress and the user event stream. Gzip holds bytes back until its buffer fills, which for a
  // stream of small events means nothing reaches the browser until the run is over, so progress
  // that arrives live over the wire is invisible in the UI. Browsers always ask for gzip, so the
  // only reliable fix is to stop compressing. The payloads are small and this app is served
  // locally, so the bandwidth cost is irrelevant next to losing live progress entirely.
  compress: false,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8085/:path*",
      },
    ];
  },
};

export default nextConfig;
