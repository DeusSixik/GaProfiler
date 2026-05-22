# GaProfiler

A module for profiling and measuring performance, the main goal is to be understandable for the average user.

GaProfiler allows you to easily track execution time of various code sections and generate beautiful, interactive HTML reports with visual "candles" (min, max, avg). It also supports benchmarking by comparing different runs side-by-side.

## Features

- **Simple API**: Use `push`/`pop` or `try-with-resources` scopes.
- **Visual Reports**: Interactive HTML charts powered by ApexCharts.
- **Statistical "Candles"**: Each section shows Min, Max, and Average execution times.
- **Comparison Mode**: Compare performance between different versions or competitors.
- **Interactive Tooltips**: Add descriptions to your profiling sections for better clarity.
- **System Metadata**: Include PC specs (CPU, GPU, RAM) directly in the report and on exported images.
- **Image Export**: Save charts as PNG/SVG with watermarks and metadata included.
- **Dark Theme**: Modern and easy-on-the-eyes interface.

## Installation

You can use GaProfiler via [JitPack](https://jitpack.io/).

### Gradle

Add the JitPack repository to your `build.gradle`:
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

Add the dependency:
```gradle
dependencies {
    implementation 'com.github.DeusSixik:GaProfiler:1.0.0'
}
```

## Quick Start

### Basic Profiling

```java
import net.sixik.ga_profiler.Profiler;

// Using push/pop
Profiler.push("render.scene");
// ... your code ...
Profiler.pop();

// Using try-with-resources (Recommended)
try (Profiler.ProfileScope scope = Profiler.scope("physics.update")) {
    // ... your code ...
}
```

### Adding Descriptions (Tooltips)

```java
Profiler.setTooltip("render.scene", "Main rendering pass for the 3D scene");
```

### Generating Reports

```java
import net.sixik.ga_profiler.HtmlReporter;
import java.util.Arrays;
import java.util.List;

List<String> specs = Arrays.asList(
    "Ryzen 9 5900X",
    "RTX 3080",
    "32GB RAM"
);

// Generate a standard report
HtmlReporter.generate("report.html", Profiler.getData(), specs);
```

### Benchmarking (Comparison)

```java
// Dump data from different runs
Profiler.dump("baseline.dump");
// ... run optimized version ...
Profiler.dump("optimized.dump");

// Load and compare
Map<String, Collection<ProfileData.Snapshot>> comparison = new LinkedHashMap<>();
comparison.put("Version 1.0", Profiler.load("baseline.dump"));
comparison.put("Version 1.1", Profiler.load("optimized.dump"));

HtmlReporter.generateComparison("comparison.html", comparison, specs);
```

## License

This project is licensed under the LGPL-3.0 License - see the [LICENSE](LICENSE) file for details.
