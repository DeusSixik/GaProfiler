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
        assertTrue(html.contains("Visible Metrics"));
        assertTrue(html.contains("toggleMetricVisibility"));
        assertTrue(html.contains("getVisibleMetricKeys"));
        assertTrue(html.contains("shouldRenderDataLabel"));
        assertTrue(html.contains("renderCustomChartLabels"));
        assertTrue(html.contains("buildSectionDisplayName"));
        assertTrue(html.contains("Median"));
        assertTrue(html.contains("P95"));
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
        datasets.put("Run A (Baseline)", List.of(snapshot));
        datasets.put("Run B", List.of(snapshot));

        Path report = Files.createTempFile("profiler-comparison", ".html");
        HtmlReporter.generateComparison(report.toString(), datasets, List.of("Comparison JVM"));

        String html = Files.readString(report);
        assertTrue(html.contains("Code Memory Allocation"));
        assertTrue(html.contains("Code Execution Time"));
        assertTrue(html.contains("Sort Groups By"));
        assertTrue(html.contains("Min"));
        assertTrue(html.contains("Median"));
        assertTrue(html.contains("Avg"));
        assertTrue(html.contains("P95"));
        assertTrue(html.contains("Max"));
        assertTrue(html.contains("Ascending"));
        assertTrue(html.contains("Descending"));
        assertTrue(html.contains("Baseline Run"));
        assertTrue(html.contains("Visible Metrics"));
        assertTrue(html.contains("function setBaselineRun(runLabel) {\n            baselineRunLabel = runLabel;\n            renderTable();\n            renderComparisonChart();\n        }"));
        assertTrue(html.contains("Delta vs Baseline"));
        assertTrue(html.contains("getComparisonMetricHeader"));
        assertTrue(html.contains("buildComparisonSeries"));
        assertTrue(html.contains("buildComparisonSeriesColors"));
        assertTrue(html.contains("buildComparisonSeriesOpacities"));
        assertTrue(html.contains("buildChartSeriesData"));
        assertTrue(html.contains("let currentChartSeriesMeta = [];"));
        assertTrue(html.contains("currentChartSeriesMeta = supportedRuns.map(run => ({"));
        assertTrue(html.contains("getVisibleMetricCategories"));
        assertTrue(html.contains("shouldRenderDataLabel"));
        assertTrue(html.contains("buildDeltaPercentLabel"));
        assertTrue(html.contains("if (Math.abs(percentDelta) < 0.05) { return '0%'; }"));
        assertTrue(html.contains("const rounded = absolutePercent >= 10 ? absolutePercent.toFixed(0) : absolutePercent.toFixed(1);"));
        assertTrue(html.contains("return sign + rounded + '%'"));
        assertTrue(html.contains("const anchor = extractBarLabelAnchor(barPath);"));
        assertTrue(html.contains("const percentLabelY = 18;"));
        assertTrue(html.contains("appendSvgText(labelLayer, anchor.x, percentLabelY, percentLabel"));
        assertTrue(html.contains("function extractBarLabelAnchor(barPath) {"));
        assertTrue(html.contains("options.dataLabels = { enabled: false };"));
        assertTrue(html.contains("currentChartSeriesMeta = supportedRuns.map(run => ({ label: buildComparisonSeriesName(run), rawLabel: run.label, data: buildChartSeriesDataForRun(run[currentMode]) }));"));
        assertTrue(html.contains("renderBarValueBadge(labelLayer, bounds, valueLabel, baselineSeriesIndex >= 0, seriesMetaIndex, currentChartSeriesMeta.length);"));
        assertTrue(html.contains("function renderBarValueBadge(layer, bounds, text, reserveTopBand, seriesIndex, totalSeriesCount) {"));
        assertTrue(html.contains("const stackOffset = totalSeriesCount > 1 ? seriesIndex * 14 : 0;"));
        assertTrue(html.contains("appendSvgBadge(layer, bounds.centerX, badgeTop, text, '#020617', '#f8fafc', '9px');"));
        assertTrue(html.contains("function appendSvgBadge(layer, centerX, topY, text, backgroundColor, textColor, fontSize) {"));
        assertTrue(html.contains("function queueCustomChartLabelRender() {"));
        assertTrue(html.contains("if (customLabelRenderTimer) { clearTimeout(customLabelRenderTimer); }"));
        assertTrue(html.contains("customLabelRenderTimer = setTimeout(() => {"));
        assertTrue(html.contains("mounted: () => queueCustomChartLabelRender()"));
        assertTrue(html.contains("updated: () => queueCustomChartLabelRender()"));
        assertTrue(html.contains("animations: { enabled: false }"));
        assertTrue(html.contains("const seriesGroups = Array.from(svg.querySelectorAll('.apexcharts-bar-series .apexcharts-series'));"));
        assertTrue(html.contains("const seriesMetaIndex = Number.isFinite(rel) && rel > 0 ? rel - 1 : -1;"));
        assertTrue(html.contains("const seriesMeta = currentChartSeriesMeta[seriesMetaIndex];"));
        assertTrue(html.contains("const groupPaths = Array.from(seriesGroup.querySelectorAll('path'));"));
        assertTrue(html.contains("groupPaths.forEach((barPath, dataPointIndex) => {"));
        assertFalse(html.contains("const labelGroups = svg.querySelectorAll('.apexcharts-data-labels');"));
        assertFalse(html.contains("clientPointToSvg"));
        assertTrue(html.contains("return value.toFixed(2) + ' "));
        assertTrue(html.contains("return value.toFixed(2);"));
        assertTrue(html.contains("const mutedPalette = ['#22c55e', '#f59e0b', '#14b8a6', '#a855f7', '#f97316', '#0ea5e9'];"));
        assertTrue(html.contains("colors: ['#10b981', '#f59e0b', '#14b8a6', '#38bdf8', '#f97316', '#a855f7']"));
        assertFalse(html.contains("colors: ['#10b981', '#f59e0b', '#ef4444', '#38bdf8', '#f97316', '#a855f7']"));
        assertFalse(html.contains("const mutedPalette = ['#22c55e', '#f59e0b', '#ef4444', '#a855f7', '#f97316', '#14b8a6'];"));
        assertTrue(html.contains("formatChartBarValue"));
        assertTrue(html.contains("lower is better"));
        assertFalse(html.contains("% lower"));
        assertFalse(html.contains("% higher"));
        assertTrue(html.contains("baselineDeltaClass"));
        assertTrue(html.contains("(Baseline)"));
        assertTrue(html.contains("!run.label.includes('(Baseline)')"));
        assertFalse(html.contains("<th>Section Name</th><th>Available in Runs</th><th>Best Avg</th>"));
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
