# GaProfiler

GaProfiler is a lightweight Java profiler focused on being understandable for regular users, not only engine developers. It records section execution time, can optionally track per-thread memory allocation, and generates interactive HTML reports for both single-run inspection and side-by-side comparisons.

## Features

- Simple low-level API based on registered `Section` handles
- `push`/`pop` and `try-with-resources` profiling scopes
- Single-run HTML reports with Min / Median / Avg / P95 / Max / Total
- Comparison reports for multiple dumps or synthetic datasets
- Optional memory allocation profiling per section
- Tooltips, hardware specs, baseline comparisons, group sorting, and image export in the HTML UI
- Configurable display units and sample limits

## Installation

You can use GaProfiler via [JitPack](https://jitpack.io/).

### Gradle

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.DeusSixik:GaProfiler:1.0.0'
}
```

## Core API

### 1. Configure the profiler

```java
import net.sixik.ga_profiler.Profiler;

Profiler.configureDefaultSampleLimit(5_000);
Profiler.setDisplayUnit(Profiler.TimeUnit.MILLISECONDS);
Profiler.setAllocationProfilingEnabled(true);
```

- `configureDefaultSampleLimit(0)` means unlimited samples
- `setDisplayUnit(...)` affects HTML formatting for time values
- `setAllocationProfilingEnabled(true)` enables allocation collection when the JVM supports it

### 2. Register sections

Sections are registered once and then reused.

```java
Profiler.Section renderScene = Profiler.register(
    "render.scene",
    "Main full-scene rendering pass",
    0
);

Profiler.Section physicsUpdate = Profiler.register(
    "physics.update",
    "Physics simulation and collision processing",
    0
);
```

Parameters:

- `name` - unique section id shown in reports
- `tooltip` - optional description shown in the HTML UI
- `maxSamples` - per-section sample cap, `0` uses the current default limit

### 3. Record samples

#### Scoped profiling

```java
try (Profiler.ProfileScope scope = Profiler.scope(renderScene)) {
    // code being measured
}
```

#### Manual push/pop

```java
Profiler.push(physicsUpdate);
try {
    // code being measured
} finally {
    Profiler.pop();
}
```

#### Add synthetic or external timing samples

```java
Profiler.addSample(renderScene, 16_500_000L); // nanoseconds
```

This is useful for importing values from another benchmark source or building comparison datasets manually.

## Generating reports

### Single report

```java
import net.sixik.ga_profiler.HtmlReporter;
import java.util.List;

List<String> specs = List.of(
    "Ryzen Threadripper 1950X",
    "RTX 5070",
    "RAM 64 GB (10 GB assigned)",
    "NVMe SSD"
);

HtmlReporter.generate("performance_report.html", Profiler.getData(), specs);
```

The report includes two modes:

- `Code Execution Time`
- `Code Memory Allocation`

If allocation profiling is disabled or unsupported, the memory mode is shown as `Disabled` or `Not Supported`.

### Save and load dumps

```java
Profiler.dump("run_v1.dump");

var snapshots = Profiler.load("run_v1.dump");
HtmlReporter.generate("loaded_report.html", snapshots, specs);
```

`dump(...)` writes the current aggregated profiler state, and `load(...)` restores snapshots for later reporting or comparison.

### Comparison report

```java
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

Map<String, Collection<ProfileData.Snapshot>> comparison = new LinkedHashMap<>();
comparison.put("Version 1.0 (Baseline)", Profiler.load("baseline.dump"));
comparison.put("Version 1.1 (Target)", Profiler.load("optimized.dump"));
comparison.put("Competitor Engine", competitorSnapshots);

HtmlReporter.generateComparison("comparison_report.html", comparison, specs);
```

The comparison UI supports:

- baseline selection
- sorting runs by `Min`, `Median`, `Avg`, `P95`, or `Max`
- chart-only metric visibility toggles
- execution time and memory allocation modes
- delta values versus the selected baseline

## Resetting between runs

If you are collecting multiple benchmark passes in one process, reset the profiler between them:

```java
Profiler.reset();
```

This clears the current aggregated runtime state while keeping registered sections available for reuse.

## Extra utilities

### Update all registered sections to the same sample limit

```java
Profiler.applySampleLimitToAllSections(10_000);
```

### Read back current profiler data

```java
var snapshots = Profiler.getData();
```

### Check whether allocation profiling is enabled

```java
boolean enabled = Profiler.isAllocationProfilingEnabled();
```

## End-to-end example

```java
import net.sixik.ga_profiler.HtmlReporter;
import net.sixik.ga_profiler.Profiler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Example {
    public static void main(String[] args) {
        Profiler.configureDefaultSampleLimit(5_000);
        Profiler.setDisplayUnit(Profiler.TimeUnit.MILLISECONDS);
        Profiler.setAllocationProfilingEnabled(true);

        Profiler.Section renderScene = Profiler.register(
            "render.scene",
            "Main full-scene rendering pass",
            0
        );
        Profiler.Section physicsUpdate = Profiler.register(
            "physics.update",
            "Physics simulation and collision processing",
            0
        );

        for (int i = 0; i < 100; i++) {
            try (Profiler.ProfileScope ignored = Profiler.scope(renderScene)) {
                work(12);
            }

            try (Profiler.ProfileScope ignored = Profiler.scope(physicsUpdate)) {
                work(6);
            }
        }

        Profiler.dump("v1.dump");

        List<String> specs = List.of(
            "Ryzen Threadripper 1950X",
            "RTX 5070",
            "RAM 64 GB (10 GB assigned)",
            "NVMe SSD"
        );

        HtmlReporter.generate("performance_report.html", Profiler.getData(), specs);

        Map<String, Collection<net.sixik.ga_profiler.ProfileData.Snapshot>> comparison = new LinkedHashMap<>();
        comparison.put("Version 1.0 (Baseline)", Profiler.load("v1.dump"));
        HtmlReporter.generateComparison("comparison_report.html", comparison, specs);
    }

    private static void work(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

## Notes

- Execution samples are stored internally in nanoseconds
- Allocation metrics rely on JVM thread allocation counters and may not be available on every runtime
- `register(...)` requires unique section names; duplicate names throw an exception

## License

This project is licensed under the LGPL-3.0 License - see the [LICENSE](LICENSE) file for details.
