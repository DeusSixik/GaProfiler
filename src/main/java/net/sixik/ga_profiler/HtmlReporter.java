package net.sixik.ga_profiler;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class HtmlReporter {
    public static void generate(String filePath, Collection<ProfileData.Snapshot> data, List<String> specs) {
        StringBuilder html = new StringBuilder();
        appendDocumentStart(html, "GaProfiler Performance Dashboard");
        appendHeader(html, "GaProfiler", specs);
        appendModeControls(html);
        html.append("        <div class=\"card\"><div id=\"chart\"></div></div>\n");
        html.append("        <div class=\"card\" style=\"padding: 0; overflow: hidden;\">\n");
        html.append("            <table>\n");
        html.append("                <thead id=\"table-head\"></thead>\n");
        html.append("                <tbody id=\"table-body\"></tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"hint\">Switch between Code Execution Time and Code Memory Allocation to compare the same sections from different angles.</div>\n");
        html.append("    </div>\n");
        html.append("    <script>\n");
        appendModeScriptData(html);
        appendSpecsScriptData(html, specs);
        appendSingleReportData(html, data);
        appendSharedScriptHelpers(html);
        html.append("        let currentMode = '" + ReportMetricMode.CODE_EXECUTION_TIME.key() + "';\n");
        html.append("        let selectedSection = null;\n");
        html.append("        let chart = null;\n");
        html.append("        function renderTable() {\n");
        html.append("            const mode = reportModes[currentMode];\n");
        html.append("            const head = document.getElementById('table-head');\n");
        html.append("            head.innerHTML = '<tr><th>Section Name</th>' + mode.columns.map(c => '<th>' + c + '</th>').join('') + '</tr>';\n");
        html.append("            const body = document.getElementById('table-body');\n");
        html.append("            body.innerHTML = sections.map((section, index) => renderSingleRow(section, index)).join('');\n");
        html.append("            if (!selectedSection && sections.length > 0) { selectedSection = sections[0].name; }\n");
        html.append("            highlightSelectedRow();\n");
        html.append("            if (window.tippy) { tippy('[data-tippy-content]', { theme: 'material' }); }\n");
        html.append("        }\n");
        html.append("        function renderSingleRow(section, index) {\n");
        html.append("            const metric = section[currentMode];\n");
        html.append("            const tooltipAttr = section.tooltip ? ' data-tippy-content=\"' + escapeHtml(section.tooltip) + '\" style=\"font-weight: 600; color: #10b981; border-bottom: 1px dashed #334155;\"' : ' style=\"font-weight: 600; color: #10b981;\"';\n");
        html.append("            let cells = '<td' + tooltipAttr + '>' + escapeHtml(section.name) + '</td>';\n");
        html.append("            if (metric.supported) {\n");
        html.append("                cells += '<td>' + metric.count + '</td>';\n");
        html.append("                cells += '<td class=\"val\">' + formatMetricValue(metric.min, currentMode) + '</td>';\n");
        html.append("                cells += '<td class=\"val\" style=\"color: #f59e0b;\">' + formatMetricValue(metric.avg, currentMode) + '</td>';\n");
        html.append("                cells += '<td class=\"val\">' + formatMetricValue(metric.max, currentMode) + '</td>';\n");
        html.append("                cells += '<td class=\"val\">' + formatMetricValue(metric.total, currentMode) + '</td>';\n");
        html.append("            } else {\n");
        html.append("                cells += '<td class=\"val\">' + metric.unavailable + '</td>'.repeat(5);\n");
        html.append("            }\n");
        html.append("            return '<tr id=\"row-' + index + '\" onclick=\"selectSectionByIndex(' + index + ')\">' + cells + '</tr>';\n");
        html.append("        }\n");
        html.append("        function selectSectionByIndex(index) { const section = sections[index]; if (!section) { return; } selectedSection = section.name; highlightSelectedRow(); renderSingleChart(); }\n");
        html.append("        function highlightSelectedRow() {\n");
        html.append("            document.querySelectorAll('#table-body tr').forEach(row => row.classList.remove('selected'));\n");
        html.append("            const index = sections.findIndex(section => section.name === selectedSection);\n");
        html.append("            if (index >= 0) { const row = document.getElementById('row-' + index); if (row) { row.classList.add('selected'); } }\n");
        html.append("        }\n");
        html.append("        function renderSingleChart() {\n");
        html.append("            const section = sections.find(item => item.name === selectedSection);\n");
        html.append("            if (!section) { return; }\n");
        html.append("            document.getElementById('current-section-name').innerText = section.name;\n");
        html.append("            const mode = reportModes[currentMode];\n");
        html.append("            const metric = section[currentMode];\n");
        html.append("            const options = buildBaseChartOptions(mode, section.tooltip || '');\n");
        html.append("            options.title.text = section.name;\n");
        html.append("            if (metric.supported) {\n");
        html.append("                options.series = [{ name: mode.label, data: [metric.min, metric.avg, metric.max] }];\n");
        html.append("                options.xaxis.categories = ['Min', 'Avg', 'Max'];\n");
        html.append("                options.yaxis = { title: { text: mode.axisLabel, style: { color: '#94a3b8', fontWeight: 500 } }, labels: { style: { colors: '#94a3b8' }, formatter: (v) => formatMetricValue(v, currentMode) } };\n");
        html.append("                options.dataLabels = { enabled: true, formatter: (val) => formatMetricValue(val, currentMode), offsetY: -28, style: { fontSize: '11px', colors: ['#f8fafc'], fontFamily: 'JetBrains Mono' } };\n");
        html.append("                options.tooltip = { theme: 'dark', y: { formatter: (v) => formatMetricValue(v, currentMode) } };\n");
        html.append("            } else {\n");
        html.append("                options.series = [];\n");
        html.append("                options.noData = { text: metric.unavailable, align: 'center', verticalAlign: 'middle', style: { color: '#f8fafc' } };\n");
        html.append("            }\n");
        html.append("            renderChart(options);\n");
        html.append("        }\n");
        html.append("        function setMode(modeKey) {\n");
        html.append("            currentMode = modeKey;\n");
        html.append("            document.querySelectorAll('.mode-button').forEach(button => button.classList.remove('active'));\n");
        html.append("            document.getElementById('mode-' + modeKey).classList.add('active');\n");
        html.append("            renderTable();\n");
        html.append("            renderSingleChart();\n");
        html.append("        }\n");
        html.append("        window.onload = () => { setMode('" + ReportMetricMode.CODE_EXECUTION_TIME.key() + "'); };\n");
        html.append("    </script>\n");
        html.append("</body>\n</html>");
        writeHtml(filePath, html.toString(), "HTML report");
    }

    public static void generateComparison(String filePath, Map<String, Collection<ProfileData.Snapshot>> datasets, List<String> specs) {
        StringBuilder html = new StringBuilder();
        appendDocumentStart(html, "GaProfiler Comparison Dashboard");
        appendHeader(html, "GaProfiler Comparison", specs);
        appendModeControls(html);
        appendComparisonSortControls(html);
        html.append("        <div class=\"card\"><div id=\"chart\"></div></div>\n");
        html.append("        <div class=\"card\" style=\"padding: 0; overflow: hidden;\">\n");
        html.append("            <table>\n");
        html.append("                <thead id=\"table-head\"></thead>\n");
        html.append("                <tbody id=\"table-body\"></tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"hint\">Compare each section by runtime cost or allocation pressure across multiple runs.</div>\n");
        html.append("    </div>\n");
        html.append("    <script>\n");
        appendModeScriptData(html);
        appendSpecsScriptData(html, specs);
        appendComparisonData(html, datasets);
        appendSharedScriptHelpers(html);
        html.append("        let currentMode = '" + ReportMetricMode.CODE_EXECUTION_TIME.key() + "';\n");
        html.append("        let selectedSection = null;\n");
        html.append("        let currentGroupSort = 'none';\n");
        html.append("        let currentSortDirection = 'asc';\n");
        html.append("        let chart = null;\n");
        html.append("        function renderTable() {\n");
        html.append("            const mode = reportModes[currentMode];\n");
            html.append("            const head = document.getElementById('table-head');\n");
        html.append("            head.innerHTML = '<tr><th>Section Name</th><th>Available in Runs</th><th>Best Avg</th></tr>';\n");
        html.append("            const body = document.getElementById('table-body');\n");
        html.append("            body.innerHTML = comparisonSections.map((section, index) => renderComparisonRow(section, index)).join('');\n");
        html.append("            if (!selectedSection && comparisonSections.length > 0) { selectedSection = comparisonSections[0].name; }\n");
        html.append("            highlightSelectedRow();\n");
        html.append("            if (window.tippy) { tippy('[data-tippy-content]', { theme: 'material' }); }\n");
        html.append("        }\n");
        html.append("        function renderComparisonRow(section, index) {\n");
        html.append("            const supportedRuns = section.runs.filter(run => run[currentMode].supported);\n");
        html.append("            const metricKey = currentGroupSort === 'none' ? 'avg' : currentGroupSort;\n");
        html.append("            const bestAvg = supportedRuns.length > 0 ? Math.min(...supportedRuns.map(run => run[currentMode][metricKey])) : null;\n");
        html.append("            const tooltipAttr = section.tooltip ? ' data-tippy-content=\"' + escapeHtml(section.tooltip) + '\" style=\"font-weight: 600; color: #10b981; border-bottom: 1px dashed #334155;\"' : ' style=\"font-weight: 600; color: #10b981;\"';\n");
        html.append("            const bestAvgText = bestAvg === null ? unavailableFromRuns(section.runs) : formatMetricValue(bestAvg, currentMode);\n");
        html.append("            return '<tr id=\"row-' + index + '\" onclick=\"selectComparisonSectionByIndex(' + index + ')\">' +\n");
        html.append("                '<td' + tooltipAttr + '>' + escapeHtml(section.name) + '</td>' +\n");
        html.append("                '<td>' + supportedRuns.length + ' / ' + section.runs.length + '</td>' +\n");
        html.append("                '<td class=\"val\">' + bestAvgText + '</td>' +\n");
        html.append("                '</tr>';\n");
        html.append("        }\n");
        html.append("        function selectComparisonSectionByIndex(index) { const section = comparisonSections[index]; if (!section) { return; } selectedSection = section.name; highlightSelectedRow(); renderComparisonChart(); }\n");
        html.append("        function highlightSelectedRow() {\n");
        html.append("            document.querySelectorAll('#table-body tr').forEach(row => row.classList.remove('selected'));\n");
        html.append("            const index = comparisonSections.findIndex(section => section.name === selectedSection);\n");
        html.append("            if (index >= 0) { const row = document.getElementById('row-' + index); if (row) { row.classList.add('selected'); } }\n");
        html.append("        }\n");
        html.append("        function renderComparisonChart() {\n");
        html.append("            const section = comparisonSections.find(item => item.name === selectedSection);\n");
        html.append("            if (!section) { return; }\n");
        html.append("            document.getElementById('current-section-name').innerText = section.name;\n");
        html.append("            const mode = reportModes[currentMode];\n");
        html.append("            const supportedRuns = section.runs.filter(run => run[currentMode].supported).slice();\n");
        html.append("            if (currentGroupSort !== 'none') {\n");
        html.append("                supportedRuns.sort((left, right) => {\n");
        html.append("                    const delta = left[currentMode][currentGroupSort] - right[currentMode][currentGroupSort];\n");
        html.append("                    return currentSortDirection === 'asc' ? delta : -delta;\n");
        html.append("                });\n");
        html.append("            }\n");
        html.append("            const options = buildBaseChartOptions(mode, section.tooltip || '');\n");
        html.append("            options.title.text = section.name;\n");
        html.append("            if (supportedRuns.length > 0) {\n");
        html.append("                options.series = supportedRuns.map(run => ({ name: run.label, data: [run[currentMode].min, run[currentMode].avg, run[currentMode].max] }));\n");
        html.append("                options.xaxis.categories = ['Min', 'Avg', 'Max'];\n");
        html.append("                options.yaxis = { title: { text: mode.axisLabel, style: { color: '#94a3b8' } }, labels: { style: { colors: '#94a3b8' }, formatter: (v) => formatMetricValue(v, currentMode) } };\n");
        html.append("                options.dataLabels = { enabled: true, formatter: (val) => formatMetricValue(val, currentMode), offsetY: -28, style: { fontSize: '10px', colors: ['#f8fafc'] } };\n");
        html.append("                options.tooltip = { theme: 'dark', y: { formatter: (v) => formatMetricValue(v, currentMode) } };\n");
        html.append("            } else {\n");
        html.append("                options.series = [];\n");
        html.append("                options.noData = { text: unavailableFromRuns(section.runs), align: 'center', verticalAlign: 'middle', style: { color: '#f8fafc' } };\n");
        html.append("            }\n");
        html.append("            renderChart(options);\n");
        html.append("        }\n");
        html.append("        function unavailableFromRuns(runs) {\n");
        html.append("            const unsupported = runs.find(run => run[currentMode].unavailable === 'Not Supported');\n");
        html.append("            if (unsupported) { return 'Not Supported'; }\n");
        html.append("            const disabled = runs.find(run => run[currentMode].unavailable === 'Disabled');\n");
        html.append("            return disabled ? 'Disabled' : 'Unavailable';\n");
        html.append("        }\n");
        html.append("        function setMode(modeKey) {\n");
        html.append("            currentMode = modeKey;\n");
        html.append("            document.querySelectorAll('.mode-button').forEach(button => button.classList.remove('active'));\n");
        html.append("            document.getElementById('mode-' + modeKey).classList.add('active');\n");
        html.append("            renderTable();\n");
        html.append("            renderComparisonChart();\n");
        html.append("        }\n");
        html.append("        function setGroupSort(metric) {\n");
        html.append("            currentGroupSort = metric;\n");
        html.append("            document.querySelectorAll('.group-sort-button').forEach(button => button.classList.remove('active'));\n");
        html.append("            document.getElementById('group-sort-' + metric).classList.add('active');\n");
        html.append("            renderTable();\n");
        html.append("            renderComparisonChart();\n");
        html.append("        }\n");
        html.append("        function setSortDirection(direction) {\n");
        html.append("            currentSortDirection = direction;\n");
        html.append("            document.querySelectorAll('.group-direction-button').forEach(button => button.classList.remove('active'));\n");
        html.append("            document.getElementById('group-direction-' + direction).classList.add('active');\n");
        html.append("            renderComparisonChart();\n");
        html.append("        }\n");
        html.append("        window.onload = () => { setMode('" + ReportMetricMode.CODE_EXECUTION_TIME.key() + "'); setGroupSort('none'); setSortDirection('asc'); };\n");
        html.append("    </script>\n");
        html.append("</body>\n</html>");
        writeHtml(filePath, html.toString(), "HTML comparison report");
    }

    private static void appendDocumentStart(StringBuilder html, String title) {
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <title>").append(title).append("</title>\n");
        html.append("    <script src=\"https://cdn.jsdelivr.net/npm/apexcharts\"></script>\n");
        html.append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;600&family=JetBrains+Mono&display=swap\" rel=\"stylesheet\">\n");
        html.append("    <script src=\"https://unpkg.com/@popperjs/core@2\"></script>\n");
        html.append("    <script src=\"https://unpkg.com/tippy.js@6\"></script>\n");
        html.append("    <style>\n");
        html.append("        body { background: #0f172a; color: #f8fafc; font-family: 'Inter', sans-serif; margin: 0; padding: 20px; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; }\n");
        html.append("        .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; border-bottom: 1px solid #334155; padding-bottom: 20px; }\n");
        html.append("        h1 { color: #38bdf8; margin: 0; font-weight: 600; font-size: 28px; }\n");
        html.append("        .card { background: #1e293b; border-radius: 12px; padding: 24px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); border: 1px solid #334155; margin-bottom: 24px; }\n");
        html.append("        table { width: 100%; border-collapse: collapse; }\n");
        html.append("        th { text-align: left; padding: 12px 16px; border-bottom: 2px solid #334155; color: #94a3b8; font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; }\n");
        html.append("        td { padding: 14px 16px; border-bottom: 1px solid #334155; cursor: pointer; transition: all 0.2s; }\n");
        html.append("        tr:hover td { background: #1e293b; color: #38bdf8; }\n");
        html.append("        tr.selected td { background: #0ea5e922 !important; color: #38bdf8; border-bottom-color: #0ea5e9; }\n");
        html.append("        .val { font-family: 'JetBrains Mono', monospace; font-size: 13px; }\n");
        html.append("        .specs-list { list-style: none; padding: 0; margin: 0; text-align: right; font-size: 12px; color: #f8fafc; }\n");
        html.append("        .spec-item { margin-bottom: 2px; border-bottom: 1px solid #1e293b; padding-bottom: 2px; }\n");
        html.append("        .spec-item:last-child { border-bottom: none; }\n");
        html.append("        .header-left { display: flex; flex-direction: column; gap: 4px; }\n");
        html.append("        .header-right { display: flex; align-items: center; gap: 20px; }\n");
        html.append("        .hint { color: #64748b; font-size: 14px; text-align: center; margin-top: 10px; }\n");
        html.append("        #current-section-name { color: #38bdf8; font-weight: 600; font-size: 18px; }\n");
        html.append("        .controls { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }\n");
        html.append("        .mode-button { background: #334155; color: #f8fafc; border: none; padding: 8px 16px; border-radius: 999px; cursor: pointer; font-size: 14px; transition: background 0.2s; }\n");
        html.append("        .mode-button:hover { background: #475569; }\n");
        html.append("        .mode-button.active { background: #0ea5e9; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");
        html.append("    <div class=\"container\">\n");
    }

    private static void appendHeader(StringBuilder html, String title, List<String> specs) {
        html.append("        <div class=\"header\">\n");
        html.append("            <div class=\"header-left\">\n");
        html.append("                <h1>").append(escapeHtml(title)).append("</h1>\n");
        html.append("                <div id=\"current-section-name\">Select a section</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"header-right\">\n");
        html.append("                <ul class=\"specs-list\">\n");
        for (String spec : specs) {
            html.append("                    <li class=\"spec-item\">").append(escapeHtml(spec)).append("</li>\n");
        }
        html.append("                </ul>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
    }

    private static void appendModeControls(StringBuilder html) {
        html.append("        <div class=\"controls card\" style=\"padding: 16px 24px;\">\n");
        html.append("            <span>Report Mode:</span>\n");
        for (ReportMetricMode mode : ReportMetricMode.values()) {
            html.append("            <button class=\"mode-button\" id=\"mode-")
                    .append(mode.key())
                    .append("\" onclick=\"setMode('")
                    .append(mode.key())
                    .append("')\">")
                    .append(escapeHtml(mode.displayName()))
                    .append("</button>\n");
        }
        html.append("        </div>\n");
    }

    private static void appendComparisonSortControls(StringBuilder html) {
        html.append("        <div class=\"controls card\" style=\"padding: 16px 24px;\">\n");
        html.append("            <span>Sort Groups By:</span>\n");
        html.append("            <button class=\"mode-button group-sort-button active\" id=\"group-sort-none\" onclick=\"setGroupSort('none')\">Original</button>\n");
        html.append("            <button class=\"mode-button group-sort-button\" id=\"group-sort-min\" onclick=\"setGroupSort('min')\">Min</button>\n");
        html.append("            <button class=\"mode-button group-sort-button\" id=\"group-sort-avg\" onclick=\"setGroupSort('avg')\">Avg</button>\n");
        html.append("            <button class=\"mode-button group-sort-button\" id=\"group-sort-max\" onclick=\"setGroupSort('max')\">Max</button>\n");
        html.append("            <span style=\"margin-left: 16px;\">Direction:</span>\n");
        html.append("            <button class=\"mode-button group-direction-button active\" id=\"group-direction-asc\" onclick=\"setSortDirection('asc')\">Ascending</button>\n");
        html.append("            <button class=\"mode-button group-direction-button\" id=\"group-direction-desc\" onclick=\"setSortDirection('desc')\">Descending</button>\n");
        html.append("        </div>\n");
    }

    private static void appendModeScriptData(StringBuilder html) {
        html.append("        const reportModes = {\n");
        for (ReportMetricMode mode : ReportMetricMode.values()) {
            html.append("            '").append(mode.key()).append("': {");
            html.append("label: '").append(escapeJs(mode.displayName())).append("', ");
            html.append("axisLabel: '").append(escapeJs(mode.axisLabel())).append("', ");
            html.append("columns: [");
            for (int i = 0; i < mode.columnLabels().size(); i++) {
                if (i > 0) {
                    html.append(", ");
                }
                html.append("'").append(escapeJs(mode.columnLabels().get(i))).append("'");
            }
            html.append("]},\n");
        }
        html.append("        };\n");
    }

    private static void appendSpecsScriptData(StringBuilder html, List<String> specs) {
        html.append("        const chartSpecs = [");
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                html.append(", ");
            }
            html.append("'").append(escapeJs(specs.get(i))).append("'");
        }
        html.append("];\n");
    }

    private static void appendSingleReportData(StringBuilder html, Collection<ProfileData.Snapshot> data) {
        html.append("        const sections = [\n");
        int index = 0;
        for (ProfileData.Snapshot snapshot : data) {
            if (index++ > 0) {
                html.append(",\n");
            }
            html.append("            ").append(snapshotLiteral(snapshot));
        }
        html.append("\n        ];\n");
    }

    private static void appendComparisonData(StringBuilder html, Map<String, Collection<ProfileData.Snapshot>> datasets) {
        Map<String, String> tooltips = new LinkedHashMap<>();
        Map<String, Map<String, ProfileData.Snapshot>> bySection = new TreeMap<>();

        for (Map.Entry<String, Collection<ProfileData.Snapshot>> entry : datasets.entrySet()) {
            for (ProfileData.Snapshot snapshot : entry.getValue()) {
                bySection.computeIfAbsent(snapshot.getName(), ignored -> new LinkedHashMap<>()).put(entry.getKey(), snapshot);
                if (snapshot.getTooltip() != null && !snapshot.getTooltip().isEmpty()) {
                    tooltips.put(snapshot.getName(), snapshot.getTooltip());
                }
            }
        }

        html.append("        const comparisonSections = [\n");
        int sectionIndex = 0;
        for (Map.Entry<String, Map<String, ProfileData.Snapshot>> entry : bySection.entrySet()) {
            if (sectionIndex++ > 0) {
                html.append(",\n");
            }
            html.append("            { name: '").append(escapeJs(entry.getKey())).append("', tooltip: ");
            String tooltip = tooltips.get(entry.getKey());
            if (tooltip == null) {
                html.append("null");
            } else {
                html.append("'").append(escapeJs(tooltip)).append("'");
            }
            html.append(", runs: [");
            int runIndex = 0;
            for (Map.Entry<String, Collection<ProfileData.Snapshot>> dataset : datasets.entrySet()) {
                if (runIndex++ > 0) {
                    html.append(", ");
                }
                ProfileData.Snapshot snapshot = entry.getValue().get(dataset.getKey());
                if (snapshot == null) {
                    snapshot = new ProfileData.Snapshot(entry.getKey(), tooltip, ProfileData.MetricStats.empty(), ProfileData.MetricStats.empty(), ProfileData.MetricAvailability.DISABLED);
                }
                html.append("{ label: '").append(escapeJs(dataset.getKey())).append("', ");
                appendModeMetrics(html, snapshot);
                html.append(" }");
            }
            html.append("] }");
        }
        html.append("\n        ];\n");
    }

    private static void appendModeMetrics(StringBuilder html, ProfileData.Snapshot snapshot) {
        html.append("executionTime: ").append(metricLiteral(ReportMetricMode.CODE_EXECUTION_TIME, snapshot)).append(", ");
        html.append("memoryAllocation: ").append(metricLiteral(ReportMetricMode.CODE_MEMORY_ALLOCATION, snapshot));
    }

    private static String snapshotLiteral(ProfileData.Snapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("{ name: '").append(escapeJs(snapshot.getName())).append("', tooltip: ");
        if (snapshot.getTooltip() == null) {
            builder.append("null");
        } else {
            builder.append("'").append(escapeJs(snapshot.getTooltip())).append("'");
        }
        builder.append(", ");
        appendModeMetrics(builder, snapshot);
        builder.append(" }");
        return builder.toString();
    }

    private static String metricLiteral(ReportMetricMode mode, ProfileData.Snapshot snapshot) {
        ProfileData.MetricStats stats = mode.stats(snapshot);
        boolean supported = mode.isSupported(snapshot);
        return String.format(Locale.US,
                "{ supported: %s, unavailable: '%s', count: %d, min: %.6f, avg: %.6f, max: %.6f, total: %.6f }",
                supported ? "true" : "false",
                escapeJs(mode.unavailableLabel(snapshot)),
                stats.getCount(),
                mode.normalize(stats.getMin()),
                mode.normalize(Math.round(stats.getAvg())),
                mode.normalize(stats.getMax()),
                mode.normalize(stats.getTotal()));
    }

    private static void appendSharedScriptHelpers(StringBuilder html) {
        html.append("        function escapeHtml(value) { return (value || '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('\\\"', '&quot;'); }\n");
        html.append("        function formatMetricValue(value, modeKey) {\n");
        html.append("            if (modeKey === '" + ReportMetricMode.CODE_EXECUTION_TIME.key() + "') {\n");
        html.append("                return value.toFixed(4) + ' " + escapeJs(Profiler.getDisplayUnit().label()) + "';\n");
        html.append("            }\n");
        html.append("            if (value < 1024) { return value.toFixed(0) + ' B'; }\n");
        html.append("            if (value < 1024 * 1024) { return (value / 1024).toFixed(2) + ' KB'; }\n");
        html.append("            if (value < 1024 * 1024 * 1024) { return (value / (1024 * 1024)).toFixed(2) + ' MB'; }\n");
        html.append("            return (value / (1024 * 1024 * 1024)).toFixed(2) + ' GB';\n");
        html.append("        }\n");
        html.append("        function buildBaseChartOptions(mode, subtitle) {\n");
        html.append("            return {\n");
        html.append("                title: { text: '', align: 'left', style: { color: '#38bdf8', fontSize: '20px' } },\n");
        html.append("                subtitle: { text: subtitle || '', align: 'left', style: { color: '#94a3b8', fontSize: '14px' } },\n");
        html.append("                chart: { type: 'bar', height: 360, background: '#1e293b', toolbar: { show: true, tools: { download: true, selection: false, zoom: false, zoomin: false, zoomout: false, pan: false, reset: false } } },\n");
        html.append("                annotations: { texts: [{ x: '98%', y: 12, text: chartSpecs.join(' | '), textAnchor: 'end', style: { fontSize: '11px', fontFamily: 'Inter', color: '#f8fafc' } }] },\n");
        html.append("                series: [],\n");
        html.append("                colors: ['#10b981', '#f59e0b', '#ef4444', '#38bdf8', '#f97316', '#a855f7'],\n");
        html.append("                plotOptions: { bar: { columnWidth: '55%', borderRadius: 6, dataLabels: { position: 'top' } } },\n");
        html.append("                grid: { borderColor: '#334155', strokeDashArray: 4, padding: { top: 40 } },\n");
        html.append("                theme: { mode: 'dark' },\n");
        html.append("                legend: { position: 'top', horizontalAlign: 'center', labels: { colors: '#f8fafc' } },\n");
        html.append("                xaxis: { categories: [], labels: { style: { colors: '#94a3b8', fontSize: '12px' } }, axisBorder: { show: false }, axisTicks: { show: false } },\n");
        html.append("                noData: { text: '', align: 'center', verticalAlign: 'middle', style: { color: '#f8fafc' } }\n");
        html.append("            };\n");
        html.append("        }\n");
        html.append("        function renderChart(options) {\n");
        html.append("            const chartDiv = document.querySelector('#chart');\n");
        html.append("            if (chart) { chart.destroy(); }\n");
        html.append("            chart = new ApexCharts(chartDiv, options);\n");
        html.append("            chart.render();\n");
        html.append("        }\n");
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeJs(String value) {
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private static void writeHtml(String filePath, String html, String description) {
        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(html);
        } catch (IOException e) {
            System.err.println("Failed to write " + description + ": " + e.getMessage());
        }
    }
}
