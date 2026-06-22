-- Number of TTS synthesis API calls made for an episode (one per text chunk; the audio is
-- stitched together afterwards). Surfaced in the per-stage cost breakdown. Nullable: legacy
-- episodes generated before this column existed show no call count.
ALTER TABLE episodes ADD COLUMN tts_calls INTEGER;
