package net.sixik.ga_profiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlReporterTest {
    @Test
    void reportIncludesExecutionAndAllocationModes() throws Exception {
        ProfileData.Snapshot snapshot = new ProfileData.Snapshot(
                "render.scene",
                "Scene pass",
                new ProfileData.MetricStats(1_000_000L, 2_000_000L, 3_000_000L, 2L),
                new ProfileData.MetricStats(128L, 512L, 640L, 2L),
                ProfileData.MetricAvailability.COLLECTED
        );

        Path report = Files.createTempFile("profiler-report", ".html");
        HtmlReporter.generate(report.toString(), List.of(snapshot), List.of("Test JVM"));

        String html = Files.readString(report);
        assertTrue(html.contains("Code Execution Time"));
        assertTrue(html.contains("Code Memory Allocation"));
        assertTrue(html.contains("Min Alloc"));
        assertTrue(html.contains("Test JVM"));
    }

    @Test
    void reportMarksUnsupportedAllocationMode() throws Exception {
        ProfileData.Snapshot snapshot = new ProfileData.Snapshot(
                "noise.stage",
                null,
                new ProfileData.MetricStats(5L, 10L, 15L, 2L),
                ProfileData.MetricStats.empty(),
                ProfileData.MetricAvailability.NOT_SUPPORTED
        );

        Path report = Files.createTempFile("profiler-report-unsupported", ".html");
        HtmlReporter.generate(report.toString(), List.of(snapshot), List.of("Unsupported JVM"));

        String html = Files.readString(report);
        assertTrue(html.contains("Not Supported"));
        assertTrue(html.contains("Code Memory Allocation"));
    }

    @Test
    void comparisonReportIncludesAllocationMode() throws Exception {
        ProfileData.Snapshot snapshot = new ProfileData.Snapshot(
                "noise.stage",
                "Noise generation",
                new ProfileData.MetricStats(10L, 20L, 30L, 2L),
                new ProfileData.MetricStats(256L, 512L, 768L, 2L),
                ProfileData.MetricAvailability.COLLECTED
        );
        Map<String, Collection<ProfileData.Snapshot>> datasets = new LinkedHashMap<>();
        datasets.put("Run A", List.of(snapshot));
        datasets.put("Run B", List.of(snapshot));

        Path report = Files.createTempFile("profiler-comparison", ".html");
        HtmlReporter.generateComparison(report.toString(), datasets, List.of("Comparison JVM"));

        String html = Files.readString(report);
        assertTrue(html.contains("Code Memory Allocation"));
        assertTrue(html.contains("Code Execution Time"));
        assertTrue(html.contains("Sort Groups By"));
        assertTrue(html.contains("Min"));
        assertTrue(html.contains("Avg"));
        assertTrue(html.contains("Max"));
        assertTrue(html.contains("Ascending"));
        assertTrue(html.contains("Descending"));
    }

    @Test
    void reportUsesIndexBasedSectionSelectionHandler() throws Exception {
        ProfileData.Snapshot snapshot = new ProfileData.Snapshot(
                "safe.section",
                "Selection should not break",
                new ProfileData.MetricStats(5L, 10L, 15L, 2L),
                ProfileData.MetricStats.empty(),
                ProfileData.MetricAvailability.DISABLED
        );

        Path report = Files.createTempFile("profiler-selection", ".html");
        HtmlReporter.generate(report.toString(), List.of(snapshot), List.of("Selection JVM"));

        String html = Files.readString(report);
        assertTrue(html.contains("selectSectionByIndex"));
        assertFalse(html.contains("JSON.stringify(section.name)"));
    }
}
