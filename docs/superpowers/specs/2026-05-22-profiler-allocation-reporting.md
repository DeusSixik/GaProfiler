# GaProfiler Allocation Reporting Modes

**Date:** 2026-05-22

**Goal**

Extend `GaProfiler` so it can optionally collect per-section memory allocation metrics and show them in HTML reports as a separate user-facing mode, without regressing the low-overhead time-only profiling path.

## Context

The current profiler now has a good runtime foundation for section-based timing, but users of `Generator Accelerator` need reports that answer more than "which section is slow". They also need to see which sections create a lot of garbage and may increase GC pressure.

For this use case, retained heap per section is not required. The practical target is allocated bytes per section, because that is useful for Minecraft world generation workloads and can be measured from Java without native code.

The report must remain understandable to ordinary users, not just developers reading raw profiler internals.

## Requirements

### Functional

- Keep the existing execution-time profiling behavior.
- Add optional per-section allocation profiling based on allocated bytes, not retained heap.
- Make allocation profiling opt-in behind a dedicated flag so time-only profiling keeps the lowest possible overhead.
- Show reports in two clear user-facing modes:
  - `Code Execution Time`
  - `Code Memory Allocation`
- If allocation metrics are unavailable on the current JVM, keep reports working and mark the allocation mode as `Not Supported`.
- Design the report mode system so additional modes can be added later without rewriting the whole reporter.

### Runtime

- Do not require C++ or native agents for the first iteration.
- Use thread-scoped allocation counters so nested sections still work with the current thread-local runtime model.
- Avoid collecting allocation metrics when the feature flag is disabled.
- Keep the steady-state hot path as close as possible to the current timing-only path.

## Approaches Considered

### Option 1 - Extend the current profiler with optional allocation metrics

Add allocation capture next to execution timing inside the existing `Profiler`, `ThreadState`, and `Snapshot` flow.

**Pros**

- Reuses the current section registry and thread-local runtime.
- Keeps one snapshot model and one report pipeline.
- Simplest path for combined time and allocation comparisons between runs.

**Cons**

- Makes snapshot and report models richer.
- Adds some branching in the hot path when allocation mode is enabled.

### Option 2 - Separate allocation profiler

Create a second profiler that tracks allocation metrics independently and merge both data sources in HTML.

**Pros**

- Cleaner separation of concerns.

**Cons**

- More code and more synchronization points.
- Harder dump/load format.
- Easier for reports to drift when one dataset is missing or renamed.

### Option 3 - External-only memory profiling

Rely on JFR or other external profilers for allocation data and keep `GaProfiler` time-only.

**Pros**

- No runtime changes inside `GaProfiler`.

**Cons**

- Fails the goal of user-friendly HTML reports in the same tool.
- Higher setup burden for mod users.

## Recommended Approach

Use **Option 1**: extend the current profiler with optional allocation metrics and expose report rendering through an extensible report-mode abstraction.

This gives the best balance of low friction, acceptable runtime overhead when enabled, and clean report UX.

## Runtime Design

### Allocation data source

Use `com.sun.management.ThreadMXBean` and `getThreadAllocatedBytes(threadId)` when available.

This provides a practical measure of bytes allocated by the current thread during a section:

1. On `push(section)`, store the thread's allocated-byte counter if allocation profiling is both enabled and supported.
2. On `pop()`, read the current counter, compute the delta, and record it into allocation aggregates for that section.

If the JVM does not support thread allocation counters, mark allocation profiling as unsupported and keep the execution-time path active.

### Feature flags and support state

Add profiler-level state:

- `allocationProfilingEnabled`
- `allocationProfilingSupported`

Behavior:

- if disabled: do not read allocation counters and do not update allocation aggregates
- if enabled and supported: collect allocation deltas
- if enabled and unsupported: expose report mode as `Not Supported`

### Thread-local state

Extend `ThreadState` with allocation-specific arrays parallel to time arrays:

- stack array for `allocatedBytesAtPush`
- aggregate arrays indexed by `section.id`:
  - `minAllocatedBytes`
  - `maxAllocatedBytes`
  - `totalAllocatedBytes`
  - `allocationCounts`

Allocation counts may mirror execution counts when every closed section contributes an allocation sample. Keeping a distinct metric group is still preferred because future modes should not be forced to share the same semantics.

### Hot-path expectations

- Time-only mode stays the default and avoids all allocation-counter reads.
- Allocation mode adds one allocation-counter read at `push()` and one at `pop()`.
- No new per-sample object allocation is introduced.

## Snapshot Model

Replace the single hard-coded time metric shape with reusable metric groups.

### New data types

- `MetricStats`
  - `long min`
  - `long max`
  - `long total`
  - `long count`
  - `double getAvg()`

- `ProfileData.Snapshot`
  - section metadata (`name`, `tooltip`)
  - `MetricStats executionTime`
  - `MetricStats memoryAllocation`

This structure keeps the model extensible. Future modes can add their own `MetricStats` group without redesigning the rest of the report pipeline.

## Report Mode Design

Introduce a report-mode abstraction used by both `generate(...)` and `generateComparison(...)`.

### Initial modes

- `Code Execution Time`
- `Code Memory Allocation`

Each mode defines:

- display name
- support state resolver
- table column labels
- chart axis label
- chart value extractor
- value formatter
- comparison sort metric

This avoids hard-coding `if time else memory` logic all over `HtmlReporter`.

### User-facing behavior

- Reports expose a visible mode switcher between:
  - `Code Execution Time`
  - `Code Memory Allocation`
- `Code Memory Allocation` remains visible even when unsupported.
- In unsupported environments, memory tables and charts show `Not Supported` instead of zeroes.
- Values are formatted for humans:
  - bytes
  - KB
  - MB
  - GB

Raw values may still be retained internally in bytes.

## HTML Reporting

### Single-run report

`HtmlReporter.generate(...)` should render one table/chart shell whose contents depend on the active report mode.

For `Code Execution Time`:

- `Calls`
- `Min`
- `Avg`
- `Max`
- `Total`

For `Code Memory Allocation`:

- `Calls`
- `Min Alloc`
- `Avg Alloc`
- `Max Alloc`
- `Total Alloc`

### Comparison report

`HtmlReporter.generateComparison(...)` should use the same mode abstraction.

- Time mode compares time metrics across runs.
- Memory mode compares allocation metrics across runs.
- Sorting should follow the active mode, such as average time or average allocation.

## Persistence

`dump()` and `load()` should move toward a versioned format that can carry multiple metric groups.

The first extension should include enough metadata to distinguish:

- execution-time metrics
- memory-allocation metrics
- unsupported or disabled allocation mode

This keeps future report modes compatible with the persistence format instead of forcing another redesign later.

## Error Handling

- If allocation profiling is enabled but unsupported, the profiler should not throw during normal use.
- If allocation counters return invalid values, skip the allocation sample for that event and preserve timing data.
- Reports should clearly distinguish:
  - no allocation data because profiling was disabled
  - no allocation data because the JVM does not support it

## Testing Strategy

### Runtime

- allocation profiling disabled does not change timing behavior
- allocation profiling enabled records allocation stats when supported
- nested sections still record independent allocation deltas
- unsupported JVM path keeps timing metrics valid and marks allocation mode unsupported

### Reporting

- HTML includes both `Code Execution Time` and `Code Memory Allocation` modes
- memory mode formats values as bytes, KB, MB, or GB
- unsupported mode renders `Not Supported`
- comparison report can switch between time and allocation views

### Persistence

- dump/load round-trips both time and allocation metric groups
- load tolerates older dumps that only contain execution-time data

## Files Expected To Change

- `E:/JavaProjects/GaProfiler/src/main/java/net/sixik/ga_profiler/Profiler.java`
- `E:/JavaProjects/GaProfiler/src/main/java/net/sixik/ga_profiler/ProfileData.java`
- `E:/JavaProjects/GaProfiler/src/main/java/net/sixik/ga_profiler/HtmlReporter.java`
- `E:/JavaProjects/GaProfiler/src/main/java/net/sixik/ga_profiler/Main.java`
- `E:/JavaProjects/GaProfiler/src/test/java/net/sixik/ga_profiler/ProfilerTest.java`

## Non-Goals

- No retained-heap attribution per section in this iteration.
- No native/JVMTI/C++ integration in this iteration.
- No off-heap or direct-buffer attribution in this iteration.
- No new metrics beyond execution time and memory allocation in this iteration, even though the design allows future expansion.
