package com.ahmed.report;

import com.ahmed.QualityControl;
import com.ahmed.QualityControl.QualityReport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * Generates an interactive HTML quality report from a QualityReport object.
 * This report uses Plotly.js for interactive visualizations.
 */
public class HtmlReportGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void generateReport(QualityReport report, File outputFile, String originalFileName) throws IOException {
        try (PrintWriter writer = new PrintWriter(outputFile, "UTF-8")) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("    <meta charset=\"UTF-8\">");
            writer.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            writer.println("    <title>DNA Quality Report for " + originalFileName + "</title>");
            writer.println("    <script src=\"https://cdn.plot.ly/plotly-2.30.0.min.js\"></script>");
            writer.println("    <style>");
            writer.println("        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f4f4f4; color: #333; }");
            writer.println("        h1, h2 { color: #0056b3; text-align: center; margin-bottom: 20px; }");
            writer.println("        .section { background-color: #fff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; padding: 20px; }");
            writer.println("        .plot-container { width: 100%; height: 400px; margin-bottom: 20px; border: 1px solid #eee; }");
            writer.println("        table { width: 100%; border-collapse: collapse; margin-top: 15px; }");
            writer.println("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
            writer.println("        th { background-color: #f2f2f2; }");
            writer.println("    </style>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("    <h1>DNA Quality Report for " + originalFileName + "</h1>");

            // Section: Basic Statistics Table
            writer.println("<div class=\"section\">");
            writer.println("    <h2>Basic Statistics</h2>");
            writer.println("    <table>");
            writer.println("        <tr><th>Metric</th><th>Value</th></tr>");
            writer.println("        <tr><td>File Type</td><td>" + report.getFileType() + "</td></tr>");
            writer.println("        <tr><td>Encoding</td><td>" + report.getEncoding() + "</td></tr>");
            writer.println("        <tr><td>Total Reads</td><td>" + String.format("%,d", report.getTotalReads()) + "</td></tr>");
            writer.println("        <tr><td>Total Bases</td><td>" + String.format("%,d", report.getTotalBases()) + "</td></tr>");
            writer.println("        <tr><td>Sequence Length</td><td>" + report.getSequenceLengthRange() + "</td></tr>");
            writer.println("        <tr><td>Average Read Length</td><td>" + String.format("%.2f", report.getAverageReadLength()) + "</td></tr>");
            writer.println("        <tr><td>Overall GC Content</td><td>" + String.format("%.2f", report.getOverallGcContent()) + "%</td></tr>");
            writer.println("        <tr><td>Poor Quality Sequences (&lt; Q20 Avg)</td><td>" + String.format("%,d", report.getPoorQualitySequenceCount()) + "</td></tr>");
            writer.println("    </table>");
            writer.println("</div>");

            // Section: Interactive Plots Containers
            writer.println("<div class=\"section\">");
            writer.println("    <h2>Interactive Plots</h2>");
            writer.println("    <div class=\"plot-container\" id=\"perBaseQualityPlot\"></div>");
            writer.println("    <div class=\"plot-container\" id=\"perSequenceQualityDistributionPlot\"></div>");
            writer.println("    <div class=\"plot-container\" id=\"perBaseSequenceContentPlot\"></div>");
            writer.println("    <div class=\"plot-container\" id=\"perSequenceGcDistributionPlot\"></div>");
            writer.println("    <div class=\"plot-container\" id=\"perBaseNContentPlot\"></div>");
            writer.println("    <div class=\"plot-container\" id=\"sequenceLengthDistributionPlot\"></div>");
            writer.println("    <div class=\"plot-container\" id=\"sequenceDuplicationLevelPlot\"></div>");
            writer.println("    <div class=\"plot-container\" id=\"kmerContentPlot\"></div>");
            writer.println("    <div class=\"plot-container\" id=\"adapterContentPlot\"></div>");
            writer.println("</div>");

            // Section: Overrepresented Sequences Table
            writer.println("<div class=\"section\">");
            writer.println("    <h2>Overrepresented Sequences (Top 20)</h2>");
            if (report.getTotalReads() > 0) {
                double overrepresentationThreshold = 0.001;
                writer.println("    <p>Sequences appearing more than " + String.format("%.1f%%", overrepresentationThreshold * 100) + " of total reads.</p>");
                writer.println("    <table>");
                writer.println("        <tr><th>Sequence</th><th>Count</th><th>Percentage</th></tr>");
                List<Map.Entry<String, Long>> overrepresented = report.getSequenceDuplicationCounts().entrySet().stream()
                        .filter(entry -> entry.getValue() > (report.getTotalReads() * overrepresentationThreshold))
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .limit(20)
                        .collect(Collectors.toList());

                if (overrepresented.isEmpty()) {
                    writer.println("        <tr><td colspan=\"3\">No sequences found above threshold.</td></tr>");
                } else {
                    for (Map.Entry<String, Long> entry : overrepresented) {
                        writer.println("        <tr>");
                        writer.println("            <td>" + entry.getKey() + "</td>");
                        writer.println("            <td>" + entry.getValue() + "</td>");
                        writer.println("            <td>" + String.format("%.2f%%", (double) entry.getValue() * 100 / report.getTotalReads()) + "</td>");
                        writer.println("        </tr>");
                    }
                }
                writer.println("    </table>");
            } else {
                writer.println("    <p>No reads processed.</p>");
            }
            writer.println("</div>");

            // Start of JavaScript Section
            writer.println("<script>");

            // --- Data preparation for JavaScript ---

            // Per Base Quality Data
            List<Map<String, Object>> perBaseQualityPlotData = new ArrayList<>();
            for (int pos = 0; pos < report.getMaxReadLengthEncountered(); pos++) {
                if (pos < report.getPerBaseQualityFrequencies().size()) {
                    int[] frequencies = report.getPerBaseQualityFrequencies().get(pos);
                    long totalCount = 0;
                    for(int count : frequencies) totalCount += count;

                    if (totalCount > 0) {
                        Map<String, Object> boxStats = new HashMap<>();
                        boxStats.put("x", pos + 1);
                        // Using 10th and 90th percentiles for whiskers is a common and robust practice
                        boxStats.put("lowerfence", report.calculatePercentile(frequencies, totalCount, 10));
                        boxStats.put("q1", report.calculatePercentile(frequencies, totalCount, 25));
                        boxStats.put("median", report.calculatePercentile(frequencies, totalCount, 50));
                        boxStats.put("q3", report.calculatePercentile(frequencies, totalCount, 75));
                        boxStats.put("upperfence", report.calculatePercentile(frequencies, totalCount, 90));
                        perBaseQualityPlotData.add(boxStats);
                    }
                }
            }
            writer.println("    var perBaseQualityData = " + objectMapper.writeValueAsString(perBaseQualityPlotData) + ";");

            // Per Sequence Quality Distribution Data
            List<Integer> psqdBins = report.getPerSequenceQualityDistribution().keySet().stream().sorted().collect(Collectors.toList());
            List<Long> psqdCounts = psqdBins.stream().map(report.getPerSequenceQualityDistribution()::get).collect(Collectors.toList());
            writer.println("    var psqdBins = " + objectMapper.writeValueAsString(psqdBins) + ";");
            writer.println("    var psqdCounts = " + objectMapper.writeValueAsString(psqdCounts) + ";");


            // Per Base Sequence Content Data
            List<Integer> pbcPositions = new ArrayList<>();
            List<Double> pbcAContent = new ArrayList<>();
            List<Double> pbcCContent = new ArrayList<>();
            List<Double> pbcGContent = new ArrayList<>();
            List<Double> pbcTContent = new ArrayList<>();
            List<Double> pbcNContent = new ArrayList<>();
            for (int i = 0; i < report.getMaxReadLengthEncountered(); i++) {
                if (i < report.getPerBaseContent().size()) {
                    long[] baseCounts = report.getPerBaseContent().get(i);
                    long totalBasesAtPos = baseCounts[0] + baseCounts[1] + baseCounts[2] + baseCounts[3] + baseCounts[4];
                    if (totalBasesAtPos > 0) {
                        pbcPositions.add(i + 1);
                        pbcAContent.add((double) baseCounts[0] * 100 / totalBasesAtPos);
                        pbcCContent.add((double) baseCounts[1] * 100 / totalBasesAtPos);
                        pbcGContent.add((double) baseCounts[2] * 100 / totalBasesAtPos);
                        pbcTContent.add((double) baseCounts[3] * 100 / totalBasesAtPos);
                        pbcNContent.add((double) baseCounts[4] * 100 / totalBasesAtPos);
                    }
                }
            }
            writer.println("    var pbcPositions = " + objectMapper.writeValueAsString(pbcPositions) + ";");
            writer.println("    var pbcAContent = " + objectMapper.writeValueAsString(pbcAContent) + ";");
            writer.println("    var pbcCContent = " + objectMapper.writeValueAsString(pbcCContent) + ";");
            writer.println("    var pbcGContent = " + objectMapper.writeValueAsString(pbcGContent) + ";");
            writer.println("    var pbcTContent = " + objectMapper.writeValueAsString(pbcTContent) + ";");
            writer.println("    var pbcNContent = " + objectMapper.writeValueAsString(pbcNContent) + ";");


            // Per Sequence GC Distribution Data
            List<Integer> psgcBins = report.getPerSequenceGcDistribution().keySet().stream().sorted().collect(Collectors.toList());
            List<Long> psgcCounts = psgcBins.stream().map(report.getPerSequenceGcDistribution()::get).collect(Collectors.toList());
            writer.println("    var psgcBins = " + objectMapper.writeValueAsString(psgcBins) + ";");
            writer.println("    var psgcCounts = " + objectMapper.writeValueAsString(psgcCounts) + ";");

            final List<Integer> theoreticalGcX = new ArrayList<>();
            final List<Double> theoreticalGcYUnscaled = new ArrayList<>();
            final double theoreticalMean = report.getOverallGcContent();
            final double theoreticalStdDev = 5.0;
            double tempMaxPdfValue = 0.0;
            for (int i = 0; i <= 100; i++) {
                theoreticalGcX.add(i);
                double pdf = (1 / (theoreticalStdDev * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((i - theoreticalMean) / theoreticalStdDev, 2));
                theoreticalGcYUnscaled.add(pdf);
                if (pdf > tempMaxPdfValue) {
                    tempMaxPdfValue = pdf;
                }
            }
            final double maxPdfValue = tempMaxPdfValue;
            long tempMaxObservedGcCount = psgcCounts.stream().mapToLong(Long::longValue).max().orElse(0L);
            final long maxObservedGcCount = tempMaxObservedGcCount;
            final List<Double> scaledTheoreticalGcY = theoreticalGcYUnscaled.stream()
                    .map(y -> (maxPdfValue > 0) ? y * (maxObservedGcCount / maxPdfValue) : 0.0)
                    .collect(Collectors.toList());
            writer.println("    var theoreticalGcX = " + objectMapper.writeValueAsString(theoreticalGcX) + ";");
            writer.println("    var theoreticalGcY = " + objectMapper.writeValueAsString(scaledTheoreticalGcY) + ";");


            // Per Base N Content Data
            List<Integer> pbnPositions = new ArrayList<>();
            List<Double> pbnNPercentages = new ArrayList<>();
            double maxNPercent = 0.0;
            for (int i = 0; i < report.getMaxReadLengthEncountered(); i++) {
                if (i < report.getPerBaseContent().size()) {
                    long n_count = report.getPerBaseContent().get(i)[4];
                    long[] baseCounts = report.getPerBaseContent().get(i);
                    long totalBasesAtPos = baseCounts[0] + baseCounts[1] + baseCounts[2] + baseCounts[3] + baseCounts[4];

                    if (totalBasesAtPos > 0) {
                        pbnPositions.add(i + 1);
                        double nPercent = (double) n_count * 100 / totalBasesAtPos;
                        pbnNPercentages.add(nPercent);
                        if (nPercent > maxNPercent) {
                            maxNPercent = nPercent;
                        }
                    }
                }
            }
            writer.println("    var pbnPositions = " + objectMapper.writeValueAsString(pbnPositions) + ";");
            writer.println("    var pbnNPercentages = " + objectMapper.writeValueAsString(pbnNPercentages) + ";");


            // Sequence Length Distribution Data
            List<Integer> sldLengths = report.getSequenceLengthDistribution().keySet().stream().sorted().collect(Collectors.toList());
            List<Long> sldCounts = sldLengths.stream().map(report.getSequenceLengthDistribution()::get).collect(Collectors.toList());
            writer.println("    var sldLengths = " + objectMapper.writeValueAsString(sldLengths) + ";");
            writer.println("    var sldCounts = " + objectMapper.writeValueAsString(sldCounts) + ";");


            // Sequence Duplication Levels Data
            Map<Integer, Long> duplicationLevelCounts = new HashMap<>();
            long totalUniqueSequences = report.getSequenceDuplicationCounts().size();
            if (totalUniqueSequences > 0) {
                for (Long count : report.getSequenceDuplicationCounts().values()) {
                    duplicationLevelCounts.merge(count.intValue(), 1L, Long::sum);
                }
            }
            List<Integer> sdLevels = new ArrayList<>();
            List<Double> sdPercentages = new ArrayList<>();
            if (totalUniqueSequences > 0) {
                int maxObservedLevel = duplicationLevelCounts.keySet().stream().max(Integer::compare).orElse(0);
                int endLevel = Math.max(maxObservedLevel + 3, 4);
                for (int level = 1; level <= endLevel; level++) {
                    sdLevels.add(level);
                    long countAtLevel = duplicationLevelCounts.getOrDefault(level, 0L);
                    sdPercentages.add((double) countAtLevel * 100.0 / totalUniqueSequences);
                }
            }
            writer.println("    var sdLevels = " + objectMapper.writeValueAsString(sdLevels) + ";");
            writer.println("    var sdPercentages = " + objectMapper.writeValueAsString(sdPercentages) + ";");
            writer.println("    var totalUniqueSequences = " + totalUniqueSequences + ";");


            // K-mer Content Data
            List<Map<String, Object>> kmerPlotData = report.getKmerCounts().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(50)
                    .map(entry -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("kmer", entry.getKey());
                        item.put("count", entry.getValue());
                        return item;
                    })
                    .collect(Collectors.toList());
            writer.println("    var kmerPlotData = " + objectMapper.writeValueAsString(kmerPlotData) + ";");

            List<Map<String, Object>> adapterPlotData = new ArrayList<>();
            if (report.getTotalReads() > 0) {
                adapterPlotData = report.getAdapterCounts().entrySet().stream()
                        .map(entry -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("name", entry.getKey());
                            item.put("percentage", (double) entry.getValue() * 100.0 / report.getTotalReads());
                            return item;
                        })
                        .sorted(Comparator.comparingDouble((Map<String, Object> m) -> (double) m.get("percentage")).reversed())
                        .collect(Collectors.toList());
            }
            writer.println("    var adapterPlotData = " + objectMapper.writeValueAsString(adapterPlotData) + ";");

            // --- Plotly.js Rendering ---

            // Per Base Sequence Quality
            writer.println("    var pbqPlotContainer = document.getElementById('perBaseQualityPlot');");
            writer.println("    if (!perBaseQualityData || perBaseQualityData.length === 0) {");
            writer.println("        pbqPlotContainer.innerHTML = '<div style=\"text-align: center; margin-top: 100px; font-size: 1.2em; color: gray;\">No base quality data to display.</div>';");
            writer.println("    } else {");
            writer.println("        var pbqTrace = {");
            writer.println("            x: perBaseQualityData.map(item => item.x),");
            writer.println("            q1: perBaseQualityData.map(item => item.q1),");
            writer.println("            median: perBaseQualityData.map(item => item.median),");
            writer.println("            q3: perBaseQualityData.map(item => item.q3),");
            writer.println("            lowerfence: perBaseQualityData.map(item => item.lowerfence),");
            writer.println("            upperfence: perBaseQualityData.map(item => item.upperfence),");
            writer.println("            type: 'box',");
            writer.println("            name: 'Quality',");
            writer.println("            boxpoints: false");
            writer.println("        };");
            writer.println("        var pbqLayout = { title: 'Per Base Sequence Quality', xaxis: { title: 'Position in Read (bp)', type: 'category' }, yaxis: { title: 'Quality Score (Phred)', range: [0, " + QualityControl.MAX_PHRED_SCORE + "] }, showlegend: false, shapes: [ { type: 'rect', xref: 'paper', yref: 'y', x0: 0, y0: 0, x1: 1, y1: 20, fillcolor: 'rgba(255, 0, 0, 0.1)', line: { width: 0 }, layer: 'below' }, { type: 'rect', xref: 'paper', yref: 'y', x0: 0, y0: 20, x1: 1, y1: 28, fillcolor: 'rgba(255, 255, 0, 0.1)', line: { width: 0 }, layer: 'below' }, { type: 'rect', xref: 'paper', yref: 'y', x0: 0, y0: 28, x1: 1, y1: " + QualityControl.MAX_PHRED_SCORE + ", fillcolor: 'rgba(0, 255, 0, 0.1)', line: { width: 0 }, layer: 'below' } ] };");
            writer.println("        Plotly.newPlot('perBaseQualityPlot', [pbqTrace], pbqLayout);");
            writer.println("    }");


            // Sequence Quality Distribution
            writer.println("    var psqdTrace = { x: psqdBins, y: psqdCounts, type: 'bar', marker: { color: 'teal' } };");
            writer.println("    var psqdLayout = { title: 'Per Sequence Quality Scores Distribution', xaxis: { title: 'Average Quality Score (Phred)', dtick: 1, tickmode: 'linear' }, yaxis: { title: 'Number of Reads' } };");
            writer.println("    Plotly.newPlot('perSequenceQualityDistributionPlot', [psqdTrace], psqdLayout);");


            // Per Base Sequence Content
            writer.println("    var pbcTraces = [ { x: pbcPositions, y: pbcAContent, mode: 'lines', name: 'A', line: { color: 'red' } }, { x: pbcPositions, y: pbcCContent, mode: 'lines', name: 'C', line: { color: 'blue' } }, { x: pbcPositions, y: pbcGContent, mode: 'lines', name: 'G', line: { color: 'green' } }, { x: pbcPositions, y: pbcTContent, mode: 'lines', name: 'T', line: { color: 'purple' } }, { x: pbcPositions, y: pbcNContent, mode: 'lines', name: 'N', line: { color: 'gray' } } ];");
            writer.println("    var pbcLayout = { title: 'Per Base Sequence Content', xaxis: { title: 'Position in Read (bp)' }, yaxis: { title: 'Percentage (%)', range: [0, 100] } };");
            writer.println("    Plotly.newPlot('perBaseSequenceContentPlot', pbcTraces, pbcLayout);");


            // Per Sequence GC Distribution
            writer.println("    var psgcPlotContainer = document.getElementById('perSequenceGcDistributionPlot');");
            writer.println("    if (psgcBins.length === 0) { psgcPlotContainer.innerHTML = '<div style=\"text-align: center; margin-top: 100px; font-size: 1.2em; color: gray;\">No GC content data found.</div>'; } else {");
            writer.println("        var psgcTrace = { x: psgcBins, y: psgcCounts, mode: 'lines', name: 'GC count per read', line: { color: '#800020' } };");
            writer.println("        var theoreticalTrace = { x: theoreticalGcX, y: theoreticalGcY, mode: 'lines', name: 'Theoretical Distribution', line: { color: 'blue', dash: 'dash' } };");
            writer.println("        var psgcLayout = { title: 'Per Sequence GC Content Distribution', xaxis: { title: 'GC Content (%)', dtick: 5, tickmode: 'linear', range: [0, 100] }, yaxis: { title: 'Number of Reads' }, showlegend: true };");
            writer.println("        Plotly.newPlot('perSequenceGcDistributionPlot', [psgcTrace, theoreticalTrace], psgcLayout); }");


            // Per Base N Content
                double yAxisMax = (maxNPercent == 0) ? 5 : (maxNPercent * 1.5);
            writer.println("    var pbnPlotContainer = document.getElementById('perBaseNContentPlot');");
            writer.println("    if (pbnNPercentages.length === 0) {");
            writer.println("        pbnPlotContainer.innerHTML = '<div style=\"text-align: center; margin-top: 100px; font-size: 1.2em; color: gray;\">No N content found.</div>';");
            writer.println("    } else {");
            writer.println("        var pbnTrace = { x: pbnPositions, y: pbnNPercentages, mode: 'lines+markers', name: 'N Content', line: { color: 'black' } };");
            writer.println("        var pbnLayout = {");
            writer.println("            title: 'Per Base N Content',");
            writer.println("            xaxis: { title: 'Position in Read (bp)' },");
            writer.println(String.format("            yaxis: { title: 'Percentage N (%%)', range: [0, %.2f] }", yAxisMax));
            writer.println("        };");
            writer.println("        Plotly.newPlot('perBaseNContentPlot', [pbnTrace], pbnLayout);");
            writer.println("    }");


            // Sequence Length Distribution
            writer.println("    var sldTrace = { x: sldLengths, y: sldCounts, type: 'bar', marker: { color: 'skyblue' } };");
            writer.println("    var sldLayout = { title: 'Sequence Length Distribution', xaxis: { title: 'Sequence Length (bp)', type: 'category', tickmode: 'array', tickvals: sldLengths, ticktext: sldLengths.map(String) }, yaxis: { title: 'Count' } };");
            writer.println("    Plotly.newPlot('sequenceLengthDistributionPlot', [sldTrace], sldLayout);");


            // Sequence Duplication Levels
            writer.println("    var sldLinePlotContainer = document.getElementById('sequenceDuplicationLevelPlot');");
            writer.println("    if (totalUniqueSequences === 0 || sdLevels.length === 0) { sldLinePlotContainer.innerHTML = '<div style=\"text-align: center; margin-top: 100px; font-size: 1.2em; color: gray;\">No unique sequences found to analyze duplication levels.</div>'; } else {");
            writer.println("        var sldLineTrace = { x: sdLevels, y: sdPercentages, mode: 'lines+markers', line: { color: '#8b008b' }, marker: { size: 8, color: '#8b008b' } };");
            writer.println("        var sldLineLayout = { title: 'Sequence Duplication Levels', xaxis: { title: 'Sequence Duplication Level', type: 'linear', range: [1, sdLevels[sdLevels.length - 1]], dtick: 1, tickmode: 'linear' }, yaxis: { title: '% of Total Sequences', range: [0, Math.min(100, Math.ceil(Math.max(...sdPercentages) / 10) * 10 || 10)], ticksuffix: '%' } };");
            writer.println("        Plotly.newPlot('sequenceDuplicationLevelPlot', [sldLineTrace], sldLineLayout); }");


            // K-mer Content
            writer.println("    var kmerPlotContainer = document.getElementById('kmerContentPlot');");
            writer.println("    if (kmerPlotData.length === 0) { kmerPlotContainer.innerHTML = '<div style=\"text-align: center; margin-top: 100px; font-size: 1.2em; color: gray;\">No K-mers found.</div>'; } else {");
            writer.println("        var kmerTraces = [{ x: kmerPlotData.map(item => item.kmer), y: kmerPlotData.map(item => item.count), type: 'bar', marker: { color: 'purple' }, hovertemplate: '<b>K-mer:</b> %{x}<br><b>Count:</b> %{y}<extra></extra>' }];");
            writer.println("        var kmerLayout = { title: 'Top ' + kmerPlotData.length + ' K-mer Content (k=5)', xaxis: { title: 'K-mer Sequence', automargin: true, tickangle: -45 }, yaxis: { title: 'Count' } };");
            writer.println("        Plotly.newPlot('kmerContentPlot', kmerTraces, kmerLayout); }");

            writer.println("    var adapterPlotContainer = document.getElementById('adapterContentPlot');");
            writer.println("    if (!adapterPlotData || adapterPlotData.length === 0) {");
            writer.println("        adapterPlotContainer.innerHTML = '<div style=\"text-align: center; margin-top: 100px; font-size: 1.2em; color: gray;\">No adapter content found.</div>';");
            writer.println("    } else {");
            writer.println("        var adapterTraces = [{");
            writer.println("            y: adapterPlotData.map(item => item.name).reverse(),"); // .reverse() to show largest bar on top
            writer.println("            x: adapterPlotData.map(item => item.percentage).reverse(),");
            writer.println("            type: 'bar',");
            writer.println("            orientation: 'h',");
            writer.println("            marker: { color: '#6A5ACD' }");
            writer.println("        }];");
            writer.println("        var adapterLayout = {");
            writer.println("            title: 'Adapter Content',");
            writer.println("            yaxis: { title: '', automargin: true },"); // No title needed, adapter names are clear
            writer.println("            xaxis: { title: 'Percentage of Reads (%)', ticksuffix: '%' }");
            writer.println("        };");
            writer.println("        Plotly.newPlot('adapterContentPlot', adapterTraces, adapterLayout);");
            writer.println("    }");

            writer.println("</script>");
            writer.println("</body>");
            writer.println("</html>");
        }
    }
}