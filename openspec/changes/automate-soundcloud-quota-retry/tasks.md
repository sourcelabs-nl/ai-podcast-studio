> Retrofit: implemented first, then documented. All tasks reflect completed work.

## 1. Frontend: auto delete-and-retry

- [x] 1.1 Extract the publish POST into a reusable `doPublishRequest` returning a classified outcome (ok / quota / auth / conflict / error)
- [x] 1.2 On quota (413), capture the oldest track and show the "Remove the oldest track…?" confirmation (one-time consent for the destructive delete)
- [x] 1.3 On confirm, delete the oldest track, then auto-retry publishing on a spaced schedule (8s / 15s / 30s), showing a "republishing… (attempt N of 3)" progress state
- [x] 1.4 If still over quota after all retries, surface the quota state again so the user can wait or remove another track
- [x] 1.5 Disable the "Done" button while a retry is in progress

## 2. Verification

- [x] 2.1 Frontend typechecks (`tsc --noEmit`) clean
