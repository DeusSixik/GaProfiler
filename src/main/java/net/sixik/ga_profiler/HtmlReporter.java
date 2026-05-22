package net.sixik.ga_profiler;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class HtmlReporter {
    public static void generate(String filePath, Collection<ProfileData.Snapshot> data, List<String> specs) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <title>GaProfiler Performance Dashboard</title>\n");
        html.append("    <script src=\"https://cdn.jsdelivr.net/npm/apexcharts\"></script>\n");
        html.append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;600&family=JetBrains+Mono&display=swap\" rel=\"stylesheet\">\n");
        html.append("    <script src=\"https://unpkg.com/@popperjs/core@2\"></script>\n");
        html.append("    <script src=\"https://unpkg.com/tippy.js@6\"></script>\n");
        html.append("    <style>\n");
        html.append("        body { background: #0f172a; color: #f8fafc; font-family: 'Inter', sans-serif; margin: 0; padding: 20px; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; }\n");
        html.append("        .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; border-bottom: 1px solid #334155; padding-bottom: 20px; }\n");
        html.append("        h1 { color: #38bdf8; margin: 0; font-weight: 600; font-size: 28px; }\n");
        html.append("        .card { background: #1e293b; border-radius: 12px; padding: 24px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); border: 1px solid #334155; margin-bottom: 24px; }\n");
        html.append("        #chart-container { height: 400px; }\n");
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
        html.append("        .apexcharts-menu {\n");
        html.append("            background: #1e293b !important;\n");
        html.append("            border-color: #334155 !important;\n");
        html.append("            color: #f8fafc !important;\n");
        html.append("        }\n");
        html.append("        .apexcharts-menu-item:hover {\n");
        html.append("            background: #334155 !important;\n");
        html.append("        }\n");
        html.append("        .apexcharts-toolbar svg {\n");
        html.append("            fill: #94a3b8 !important;\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <div class=\"header\">\n");
        html.append("            <div class=\"header-left\">\n");
        html.append("                <h1>GaProfiler</h1>\n");
        html.append("                <div id=\"current-section-name\">Select a section</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"header-right\">\n");
        html.append("                <ul class=\"specs-list\">\n");
        for (String spec : specs) {
            html.append("                    <li class=\"spec-item\">").append(spec).append("</li>\n");
        }
        html.append("                </ul>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"card\">\n");
        html.append("            <div id=\"chart\"></div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"card\" style=\"padding: 0; overflow: hidden;\">\n");
        html.append("            <table>\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>Section Name</th>\n");
        html.append("                        <th>Calls</th>\n");
        html.append("                        <th>Min (ms)</th>\n");
        html.append("                        <th>Avg (ms)</th>\n");
        html.append("                        <th>Max (ms)</th>\n");
        html.append("                        <th>Total (ms)</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody id=\"table-body\">\n");

        int idx = 0;
        for (ProfileData.Snapshot d : data) {
            double minMs = d.getMin() / 1_000_000.0;
            double avgMs = d.getAvg() / 1_000_000.0;
            double maxMs = d.getMax() / 1_000_000.0;
            double totalMs = d.getTotal() / 1_000_000.0;

            String escapedTooltip = d.getTooltip() == null ? "" : d.getTooltip().replace("'", "\\'");
            html.append(String.format(Locale.US,
                "                    <tr onclick=\"updateChart('%s', %.6f, %.6f, %.6f, '%s', this)\" id=\"row-%d\">\n",
                d.getName(), minMs, avgMs, maxMs, escapedTooltip, idx++
            ));
            
            String tooltipAttr = (d.getTooltip() != null && !d.getTooltip().isEmpty()) 
                ? String.format(" data-tippy-content=\"%s\" style=\"font-weight: 600; color: #10b981; border-bottom: 1px dashed #334155;\"", d.getTooltip())
                : " style=\"font-weight: 600; color: #10b981;\"";

            html.append("                        <td").append(tooltipAttr).append(">").append(d.getName()).append("</td>\n");
            html.append("                        <td>").append(d.getCount()).append("</td>\n");
            html.append(String.format(Locale.US, "                        <td class=\"val\">%.4f</td>\n", minMs));
            html.append(String.format(Locale.US, "                        <td class=\"val\" style=\"color: #f59e0b;\">%.4f</td>\n", avgMs));
            html.append(String.format(Locale.US, "                        <td class=\"val\">%.4f</td>\n", maxMs));
            html.append(String.format(Locale.US, "                        <td class=\"val\">%.2f</td>\n", totalMs));
            html.append("                    </tr>\n");
        }

        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"hint\">Click on any row to visualize performance columns</div>\n");
        html.append("    </div>\n");
        
        html.append("    <script>\n");
        html.append("        let chart = null;\n");
        html.append("        const chartSpecs = [");
        for (int i = 0; i < specs.size(); i++) {
            html.append("'").append(specs.get(i).replace("'", "\\'")).append("'");
            if (i < specs.size() - 1) html.append(", ");
        }
        html.append("];\n");
        html.append("        \n");
        html.append("        function updateChart(name, min, avg, max, tooltip, rowElement) {\n");
        html.append("            document.getElementById('current-section-name').innerText = name;\n");
        html.append("            \n");
        html.append("            // Highlight selected row\n");
        html.append("            document.querySelectorAll('tr').forEach(r => r.classList.remove('selected'));\n");
        html.append("            if (rowElement) rowElement.classList.add('selected');\n");
        html.append("            \n");
        html.append("            const options = {\n");
        html.append("                title: { text: name, align: 'left', style: { color: '#38bdf8', fontSize: '20px' }, offsetY: 0 },\n");
        html.append("                subtitle: { text: tooltip || '', align: 'left', style: { color: '#94a3b8', fontSize: '14px' }, offsetY: 25 },\n");
        html.append("                annotations: {\n");
        html.append("                    texts: [{\n");
        html.append("                        x: '98%', y: 15, text: chartSpecs, textAnchor: 'end',\n");
        html.append("                        style: { fontSize: '11px', fontFamily: 'Inter', color: '#f8fafc' }\n");
        html.append("                    }]\n");
        html.append("                },\n");
        html.append("                series: [{\n");
        html.append("                    name: 'Duration',\n");
        html.append("                    data: [\n");
        html.append("                        { x: 'Min', y: parseFloat(min.toFixed(6)) },\n");
        html.append("                        { x: 'Avg', y: parseFloat(avg.toFixed(6)) },\n");
        html.append("                        { x: 'Max', y: parseFloat(max.toFixed(6)) }\n");
        html.append("                    ]\n");
        html.append("                }],\n");
        html.append("                chart: {\n");
        html.append("                    type: 'bar',\n");
        html.append("                    height: 350,\n");
        html.append("                    toolbar: { \n");
        html.append("                        show: true, \n");
        html.append("                        tools: { download: true, selection: false, zoom: false, zoomin: false, zoomout: false, pan: false, reset: false }\n");
        html.append("                    },\n");
        html.append("                    background: '#1e293b',\n");
        html.append("                    animations: { enabled: true, easing: 'easeinout', speed: 400 }\n");
        html.append("                },\n");
        html.append("                colors: ['#10b981', '#f59e0b', '#ef4444'],\n");
        html.append("                plotOptions: {\n");
        html.append("                    bar: {\n");
        html.append("                        columnWidth: '50%',\n");
        html.append("                        distributed: true,\n");
        html.append("                        borderRadius: 6,\n");
        html.append("                        dataLabels: { position: 'top' }\n");
        html.append("                    }\n");
        html.append("                },\n");
        html.append("                dataLabels: {\n");
        html.append("                    enabled: true,\n");
        html.append("                    formatter: (val) => val.toFixed(4) + ' ms',\n");
        html.append("                    offsetY: -30,\n");
        html.append("                    style: { fontSize: '12px', colors: ['#f8fafc'], fontFamily: 'JetBrains Mono' }\n");
        html.append("                },\n");
        html.append("                xaxis: {\n");
        html.append("                    title: { text: 'GA Profiler', style: { color: '#475569', fontSize: '10px' } },\n");
        html.append("                    categories: ['Min (Fastest)', 'Average', 'Max (Slowest)'],\n");
        html.append("                    labels: { style: { colors: '#94a3b8', fontSize: '12px' } },\n");
        html.append("                    axisBorder: { show: false },\n");
        html.append("                    axisTicks: { show: false }\n");
        html.append("                },\n");
        html.append("                yaxis: {\n");
        html.append("                    title: { text: 'Time in Milliseconds (ms)', style: { color: '#94a3b8', fontWeight: 500 } },\n");
        html.append("                    labels: { style: { colors: '#94a3b8' }, formatter: (v) => v.toFixed(2) }\n");
        html.append("                },\n");
        html.append("                tooltip: { theme: 'dark', y: { formatter: (v) => v.toFixed(6) + ' ms' } },\n");
        html.append("                grid: { borderColor: '#334155', strokeDashArray: 4, padding: { top: 40 } },\n");
        html.append("                theme: { mode: 'dark' },\n");
        html.append("                legend: { show: true, position: 'top', horizontalAlign: 'center', offsetY: 0, labels: { colors: '#f8fafc' } }\n");
        html.append("            };\n");
        html.append("            \n");
        html.append("            const chartDiv = document.querySelector(\"#chart\");\n");
        html.append("            if (chart) {\n");
        html.append("                chart.updateOptions(options);\n");
        html.append("            } else {\n");
        html.append("                chart = new ApexCharts(chartDiv, options);\n");
        html.append("                chart.render();\n");
        html.append("            }\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        window.onload = () => {\n");
        html.append("            const firstRow = document.querySelector('#table-body tr');\n");
        html.append("            if (firstRow) firstRow.click();\n");
        html.append("            tippy('[data-tippy-content]', { theme: 'material' });\n");
        html.append("        };\n");
        html.append("    </script>\n");
        html.append("</body>\n</html>");

        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        } catch (IOException e) {
            System.err.println("Failed to write HTML report: " + e.getMessage());
        }
    }

    public static void generateComparison(String filePath, Map<String, Collection<ProfileData.Snapshot>> datasets, List<String> specs) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <title>GaProfiler Comparison Dashboard</title>\n");
        html.append("    <script src=\"https://cdn.jsdelivr.net/npm/apexcharts\"></script>\n");
        html.append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;600&family=JetBrains+Mono&display=swap\" rel=\"stylesheet\">\n");
        html.append("    <script src=\"https://unpkg.com/@popperjs/core@2\"></script>\n");
        html.append("    <script src=\"https://unpkg.com/tippy.js@6\"></script>\n");
        html.append("    <style>\n");
        html.append("        body { background: #0f172a; color: #f8fafc; font-family: 'Inter', sans-serif; margin: 0; padding: 20px; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; }\n");
        html.append("        .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; border-bottom: 1px solid #334155; padding-bottom: 20px; }\n");
        html.append("        h1 { color: #38bdf8; margin: 0; font-weight: 600; font-size: 28px; }\n");
        html.append("        .card { background: #1e293b; border-radius: 12px; padding: 24px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); border: 1px solid #334155; margin-bottom: 24px; }\n");
        html.append("        .controls { display: flex; gap: 10px; align-items: center; margin-bottom: 15px; }\n");
        html.append("        button { background: #334155; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-size: 14px; transition: background 0.2s; }\n");
        html.append("        button:hover { background: #475569; }\n");
        html.append("        button.active { background: #0ea5e9; }\n");
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
        html.append("        #current-section-name { color: #38bdf8; font-weight: 600; font-size: 18px; }\n");
        html.append("        .apexcharts-menu {\n");
        html.append("            background: #1e293b !important;\n");
        html.append("            border-color: #334155 !important;\n");
        html.append("            color: #f8fafc !important;\n");
        html.append("        }\n");
        html.append("        .apexcharts-menu-item:hover {\n");
        html.append("            background: #334155 !important;\n");
        html.append("        }\n");
        html.append("        .apexcharts-toolbar svg {\n");
        html.append("            fill: #94a3b8 !important;\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <div class=\"header\">\n");
        html.append("            <div class=\"header-left\">\n");
        html.append("                <h1>GaProfiler Comparison</h1>\n");
        html.append("                <div id=\"current-section-name\">Select a section</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"header-right\">\n");
        html.append("                <ul class=\"specs-list\">\n");
        for (String spec : specs) {
            html.append("                    <li class=\"spec-item\">").append(spec).append("</li>\n");
        }
        html.append("                </ul>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"card\">\n");
        html.append("            <div class=\"controls\">\n");
        html.append("                <span>Sort by:</span>\n");
        html.append("                <button id=\"sort-none\" class=\"active\" onclick=\"setSort('none')\">Original</button>\n");
        html.append("                <button id=\"sort-avg\" onclick=\"setSort('avg')\">Best Avg</button>\n");
        html.append("            </div>\n");
        html.append("            <div id=\"chart\"></div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"card\" style=\"padding: 0; overflow: hidden;\">\n");
        html.append("            <table>\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>Section Name</th>\n");
        html.append("                        <th>Available in Runs</th>\n");
        html.append("                        <th>Best Avg (ms)</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody id=\"table-body\">\n");

        Set<String> allSections = new TreeSet<>();
        Map<String, String> tooltips = new HashMap<>();
        for (Collection<ProfileData.Snapshot> runData : datasets.values()) {
            for (ProfileData.Snapshot d : runData) {
                allSections.add(d.getName());
                if (d.getTooltip() != null) tooltips.put(d.getName(), d.getTooltip());
            }
        }

        for (String section : allSections) {
            int runsCount = 0;
            double bestAvg = Double.MAX_VALUE;
            for (Collection<ProfileData.Snapshot> runData : datasets.values()) {
                for (ProfileData.Snapshot d : runData) {
                    if (d.getName().equals(section)) {
                        runsCount++;
                        double avg = d.getAvg() / 1_000_000.0;
                        if (avg < bestAvg) bestAvg = avg;
                    }
                }
            }

            String tooltip = tooltips.get(section);
            String tooltipAttr = (tooltip != null) 
                ? String.format(" data-tippy-content=\"%s\" style=\"font-weight: 600; color: #10b981; border-bottom: 1px dashed #334155;\"", tooltip)
                : " style=\"font-weight: 600; color: #10b981;\"";

            html.append(String.format("                    <tr onclick=\"selectSection('%s', this)\">\n", section));
            html.append("                        <td").append(tooltipAttr).append(">").append(section).append("</td>\n");
            html.append("                        <td>").append(runsCount).append(" / ").append(datasets.size()).append("</td>\n");
            html.append(String.format(Locale.US, "                        <td class=\"val\">%.4f</td>\n", bestAvg));
            html.append("                    </tr>\n");
        }

        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        
        html.append("    <script>\n");
        html.append("        const comparisonData = {\n");
        for (String section : allSections) {
            html.append("            '").append(section).append("': [\n");
            for (Map.Entry<String, Collection<ProfileData.Snapshot>> entry : datasets.entrySet()) {
                String runLabel = entry.getKey();
                ProfileData.Snapshot d = entry.getValue().stream()
                    .filter(p -> p.getName().equals(section))
                    .findFirst()
                    .orElse(null);
                
                if (d != null) {
                    double minMs = d.getMin() / 1_000_000.0;
                    double avgMs = d.getAvg() / 1_000_000.0;
                    double maxMs = d.getMax() / 1_000_000.0;
                    html.append(String.format(Locale.US, 
                        "                { label: '%s', min: %.6f, avg: %.6f, max: %.6f },\n",
                        runLabel, minMs, avgMs, maxMs));
                }
            }
            html.append("            ],\n");
        }
        html.append("        };\n\n");

        html.append("        const sectionTooltips = {\n");
        for (Map.Entry<String, String> entry : tooltips.entrySet()) {
            html.append("            '").append(entry.getKey().replace("'", "\\'")).append("': '")
                .append(entry.getValue().replace("'", "\\'")).append("',\n");
        }
        html.append("        };\n");

        html.append("        let chart = null;\n");
        html.append("        const chartSpecs = [");
        for (int i = 0; i < specs.size(); i++) {
            html.append("'").append(specs.get(i).replace("'", "\\'")).append("'");
            if (i < specs.size() - 1) html.append(", ");
        }
        html.append("];\n");
        html.append("        let currentSection = null;\n");
        html.append("        let currentSort = 'none';\n\n");
        
        html.append("        function setSort(mode) {\n");
        html.append("            currentSort = mode;\n");
        html.append("            document.querySelectorAll('.controls button').forEach(b => b.classList.remove('active'));\n");
        html.append("            document.getElementById('sort-' + mode).classList.add('active');\n");
        html.append("            if (currentSection) updateChart();\n");
        html.append("        }\n\n");

        html.append("        function selectSection(name, rowElement) {\n");
        html.append("            currentSection = name;\n");
        html.append("            document.getElementById('current-section-name').innerText = name;\n");
        html.append("            document.querySelectorAll('tr').forEach(r => r.classList.remove('selected'));\n");
        html.append("            if (rowElement) rowElement.classList.add('selected');\n");
        html.append("            updateChart();\n");
        html.append("        }\n\n");

        html.append("        function updateChart() {\n");
        html.append("            let data = [...comparisonData[currentSection]];\n");
        html.append("            const tooltip = sectionTooltips[currentSection] || '';\n");
        html.append("            if (currentSort === 'avg') {\n");
        html.append("                data.sort((a, b) => a.avg - b.avg);\n");
        html.append("            }\n\n");
        
        html.append("            const options = {\n");
        html.append("                title: { text: currentSection, align: 'left', style: { color: '#38bdf8', fontSize: '20px' }, offsetY: 0 },\n");
        html.append("                subtitle: { text: tooltip || '', align: 'left', style: { color: '#94a3b8', fontSize: '14px' }, offsetY: 25 },\n");
        html.append("                annotations: {\n");
        html.append("                    texts: [{\n");
        html.append("                        x: '98%', y: 15, text: chartSpecs, textAnchor: 'end',\n");
        html.append("                        style: { fontSize: '11px', fontFamily: 'Inter', color: '#f8fafc' }\n");
        html.append("                    }]\n");
        html.append("                },\n");
        html.append("                series: [\n");
        html.append("                    { name: 'Min', data: data.map(d => parseFloat(d.min.toFixed(4))) },\n");
        html.append("                    { name: 'Avg', data: data.map(d => parseFloat(d.avg.toFixed(4))) },\n");
        html.append("                    { name: 'Max', data: data.map(d => parseFloat(d.max.toFixed(4))) }\n");
        html.append("                ],\n");
        html.append("                chart: {\n");
        html.append("                    type: 'bar',\n");
        html.append("                    height: 400,\n");
        html.append("                    background: '#1e293b',\n");
        html.append("                    toolbar: { \n");
        html.append("                        show: true,\n");
        html.append("                        offsetY: -20,\n");
        html.append("                        tools: { download: true, selection: false, zoom: false, zoomin: false, zoomout: false, pan: false, reset: false }\n");
        html.append("                    }\n");
        html.append("                },\n");
        html.append("                colors: ['#10b981', '#f59e0b', '#ef4444'],\n");
        html.append("                plotOptions: {\n");
        html.append("                    bar: {\n");
        html.append("                        horizontal: false,\n");
        html.append("                        columnWidth: '55%',\n");
        html.append("                        borderRadius: 4,\n");
        html.append("                        dataLabels: { position: 'top' }\n");
        html.append("                    }\n");
        html.append("                },\n");
        html.append("                dataLabels: {\n");
        html.append("                    enabled: true,\n");
        html.append("                    formatter: (val) => val.toFixed(2),\n");
        html.append("                    offsetY: -30,\n");
        html.append("                    style: { fontSize: '10px', colors: ['#f8fafc'] }\n");
        html.append("                },\n");
        html.append("                xaxis: {\n");
        html.append("                    title: { text: 'GA Profiler', style: { color: '#475569', fontSize: '10px' } },\n");
        html.append("                    categories: data.map(d => d.label),\n");
        html.append("                    labels: { style: { colors: '#94a3b8' } }\n");
        html.append("                },\n");
        html.append("                yaxis: {\n");
        html.append("                    title: { text: 'Time (ms)', style: { color: '#94a3b8' } },\n");
        html.append("                    labels: { style: { colors: '#94a3b8' } }\n");
        html.append("                },\n");
        html.append("                tooltip: { theme: 'dark' },\n");
        html.append("                grid: { borderColor: '#334155', padding: { top: 40 } },\n");
        html.append("                theme: { mode: 'dark' },\n");
        html.append("                legend: { position: 'top', horizontalAlign: 'center', offsetY: 0, labels: { colors: '#f8fafc' } }\n");
        html.append("            };\n\n");

        html.append("            const chartDiv = document.querySelector(\"#chart\");\n");
        html.append("            if (chart) {\n");
        html.append("                chart.updateOptions(options);\n");
        html.append("            } else {\n");
        html.append("                chart = new ApexCharts(chartDiv, options);\n");
        html.append("                chart.render();\n");
        html.append("            }\n");
        html.append("        }\n\n");

        html.append("        window.onload = () => {\n");
        html.append("            const firstRow = document.querySelector('#table-body tr');\n");
        html.append("            if (firstRow) firstRow.click();\n");
        html.append("            tippy('[data-tippy-content]', { theme: 'material' });\n");
        html.append("        };\n");
        html.append("    </script>\n");
        html.append("</body>\n</html>");

        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        } catch (IOException e) {
            System.err.println("Failed to write HTML comparison report: " + e.getMessage());
        }
    }
}
