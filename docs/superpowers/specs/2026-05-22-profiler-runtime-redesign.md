# GaProfiler Runtime Redesign

**Date:** 2026-05-22

**Goal**

Redesign `GaProfiler` around a low-overhead runtime path for profiling hot Minecraft mod code, even if this requires a breaking API change.

## Context

The current profiler is convenient, but the hot path still pays for:

- `String` section lookup on every `pop()`
- shared writes into global concurrent structures on every sample
- a reset model that is unsafe for long-lived worker threads

For profiling `Generator Accelerator`, the main priority is not backward compatibility. The main priority is a predictable, zero-allocation runtime path with no shared contention during sampling.

## Requirements

### Functional

- Register sections before profiling them.
- Support nested profiling scopes.
- Support both explicit `push/pop` and `try-with-resources` scopes.
- Preserve report generation through `ProfileData.Snapshot`, `dump()`, and `load()`.
- Keep tooltip and section name metadata for reports.
- Support per-section sample limits.
- Support a bulk operation that applies one sample limit to all registered sections.
- Support choosing report display units for nanoseconds, milliseconds, or seconds.

### Runtime

- Remove `String` lookup from the hot path.
- Remove shared writes from the hot path.
- Avoid per-sample allocation.
- Keep sampling logic thread-safe for multiple producer threads.
- Make reset safe when threads outlive one profiling run.
- Keep time storage in raw nanoseconds regardless of report display unit.

## Recommended Approach

Use pre-registered section handles with integer ids and keep all per-sample aggregation inside thread-local storage. Merge thread-local data only when producing a snapshot or writing a dump.

This keeps the runtime path cheap:

1. `push(section)` stores `section.id` and `System.nanoTime()` into thread-local arrays.
2. `pop()` computes the duration and updates thread-local arrays for `min`, `max`, `total`, and `count`.
3. `snapshot()/dump()` walk registered thread states and merge the cold data into report snapshots.

## API Design

### New public API

- `Profiler.Section register(String name)`
- `Profiler.Section register(String name, String tooltip)`
- `Profiler.Section register(String name, String tooltip, long maxSamples)`
- `void push(Profiler.Section section)`
- `Profiler.ProfileScope scope(Profiler.Section section)`
- `void pop()`
- `void configureDefaultSampleLimit(long maxSamples)`
- `void applySampleLimitToAllSections(long maxSamples)`
- `void setDisplayUnit(Profiler.TimeUnit unit)`
- `Profiler.TimeUnit getDisplayUnit()`
- `void reset()`
- `Collection<ProfileData.Snapshot> getData()`
- `void dump(String path)`
- `Collection<ProfileData.Snapshot> load(String path)`

### Breaking changes

- Remove `push(String name)`.
- Remove `scope(String name)`.
- Remove `setTooltip(String name, String tooltip)` as a runtime configuration API.

Tooltip and section names become section metadata owned by the registered `Section`.

## Internal Design

### Section registry

- `Profiler.Section` is a lightweight immutable handle with:
  - `int id`
  - `String name`
  - `String tooltip`
  - `volatile long maxSamples`
- Registration is a cold-path operation and may use synchronization.
- Section ids are assigned sequentially and are stable for the lifetime of the process.
- New sections inherit the current default sample limit unless a specific limit is passed during registration.

### Thread-local state

Each thread keeps one `ThreadState` with:

- stack arrays for active section ids and start timestamps
- aggregation arrays indexed by section id:
  - `long[] minDurations`
  - `long[] maxDurations`
  - `long[] totalDurations`
  - `long[] counts`
- a compact list of touched section ids so snapshot merging does not scan the full capacity
- the last seen profiler generation

Array growth is allowed, but only when a new section id or stack depth exceeds capacity. No allocation should happen in steady-state sampling.

### Reset model

`reset()` increments a global generation counter and clears global registered thread state if needed for future merges. A thread detects a generation mismatch on next access and lazily clears its local stack and aggregates before accepting new samples.

This avoids the current issue where only the calling thread is fully reset.

### Snapshot merge

- `getData()` iterates over all registered thread states.
- For every touched section id, merge thread-local values into a temporary aggregated view.
- Convert the final merged view into `ProfileData.Snapshot`.

Shared aggregation exists only on the cold path.

### Sample limit behavior

- Limits are tracked per section.
- `maxSamples <= 0` means unlimited collection.
- The hot path checks the local sample count for the section before writing a new sample.
- If a thread has already recorded at least `maxSamples` samples for that section in the current generation, it skips the update.
- `applySampleLimitToAllSections()` updates all registered sections and also updates the default for future registrations.

This means the limit is deterministic per section and does not require global coordination on every sample.

### Time unit behavior

- All recorded durations remain in raw nanoseconds internally.
- `ProfileData.Snapshot` continues to expose raw nanosecond values.
- A profiler-level display unit controls how values are rendered in reports and dumps meant for humans.
- `HtmlReporter` reads the configured display unit and formats labels and numbers accordingly.

## Error Handling

- `push(null)` throws `NullPointerException`.
- `pop()` on an empty stack throws `IllegalStateException` to surface instrumentation bugs immediately.
- `register()` rejects duplicate section names with `IllegalArgumentException`.
- Sample limit configuration rejects negative values other than the chosen sentinel for unlimited mode.
- `load()` remains tolerant of malformed files and skips invalid input through existing error reporting style.

## Testing Strategy

### Core behavior

- registering a section and recording one sample
- nested `push/pop` updates the correct sections
- `scope(section)` closes correctly
- duplicate registration fails
- `pop()` without `push()` fails fast
- per-section sample limit stops collection after the configured threshold
- bulk sample limit updates existing and future sections

### Concurrency and reset

- multiple threads writing to the same section merge correctly
- `reset()` isolates old thread-local data from the next run

### Persistence

- `dump()` and `load()` round-trip snapshots
- display unit affects human-readable output formatting without changing raw stored nanoseconds

## Files Expected To Change

- `E:/JavaProjects/GaProfiler/src/main/java/net/sixik/ga_profiler/Profiler.java`
- `E:/JavaProjects/GaProfiler/src/main/java/net/sixik/ga_profiler/ProfileData.java`
- `E:/JavaProjects/GaProfiler/src/main/java/net/sixik/ga_profiler/Main.java`
- `E:/JavaProjects/GaProfiler/src/main/java/net/sixik/ga_profiler/HtmlReporter.java` only if API fallout requires demo adjustments
- new tests under `E:/JavaProjects/GaProfiler/src/test/java/net/sixik/ga_profiler/`

## Non-Goals

- No attempt to preserve source compatibility with the old string-based API.
- No async background exporter in this iteration.
- No event timeline or flamegraph output in this iteration.
