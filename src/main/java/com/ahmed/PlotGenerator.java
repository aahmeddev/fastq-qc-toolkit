package com.ahmed;

import com.ahmed.QualityControl.AnalysisStatus;
import com.ahmed.QualityControl.EvaluationResult;
import com.ahmed.QualityControl.QualityReport;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A utility class responsible for generating all visual representations (plots and tables)
 * for the quality control report. It prepares data from a {@link QualityReport} and uses
 * external Python scripts for rendering the final images.
 */
public class PlotGenerator {

    /**
     * Factory method to create a specific plot or table based on the given plot type.
     *
     * @param plotType The identifier for the plot to create (e.g., "perBaseSequenceQuality").
     * @param report   The data source for the plot.
     * @return A JavaFX {@link Node} (either an ImageView for a plot or a layout pane for a table).
     * @throws IOException          if there is an error during script execution or file I/O.
     * @throws InterruptedException if the script execution process is interrupted.
     */
    public Node createPlot(String plotType, QualityReport report) throws IOException, InterruptedException {
        if (report == null) {
            System.err.println("QualityReport is null for plotType: " + plotType);
            return null;
        }

        Image plotImage;

        switch (plotType) {
            case "baseStatistics":
                return createBaseStatisticsTable(report);
            case "perBaseSequenceQuality":
                plotImage = createPerBaseQualityChart(report);
                break;
            case "perSequenceQualityScores":
                plotImage = createPerSequenceQualityScoresChart(report);
                break;
            case "perBaseSequenceContent":
                plotImage = createPerBaseContentChart(report);
                break;
            case "perSequenceGCContent":
                plotImage = createPerSequenceGcContentChart(report);
                break;
            case "perBaseNContent":
                plotImage = createPerBaseNContentChart(report);
                break;
            case "sequenceLengthDistribution":
                plotImage = createLengthDistributionChart(report);
                break;
            case "sequenceDuplicationLevels":
                plotImage = createDuplicationLevelsChart(report);
                break;
            case "overrepresentedSequences":
                plotImage = createOverrepresentedSequencesTable(report);
                break;
            case "kmerContent":
                plotImage = createKmerContentChart(report);
                break;
            case "adapterContent":
                plotImage = createAdapterContentChart(report);
                break;
            default:
                System.err.println("Unknown plot type requested: " + plotType);
                return null;
        }
        return (plotImage != null) ? new ImageView(plotImage) : null;
    }

    private Node createBaseStatisticsTable(QualityReport report) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #FFFFFF;");
        container.setAlignment(Pos.TOP_CENTER);

        Label summaryTitle = new Label("Analysis Summary");
        summaryTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        GridPane statusGrid = createStatusGrid(report);

        Label detailsTitle = new Label("Basic Statistics");
        detailsTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        GridPane detailsGrid = createBasicStatsGrid(report);

        container.getChildren().addAll(summaryTitle, statusGrid, new Separator(), detailsTitle, detailsGrid);

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: #FFFFFF;");

        return scrollPane;
    }

    private GridPane createStatusGrid(QualityReport report) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10, 0, 20, 0));

        ColumnConstraints col1 = new ColumnConstraints(30);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        int rowIndex = 0;
        for (Map.Entry<String, EvaluationResult> entry : report.getModuleStatus().entrySet()) {
            EvaluationResult result = entry.getValue();
            Node statusIcon = createStatusIcon(result.status());

            Label moduleLabel = new Label(entry.getKey());
            moduleLabel.setStyle("-fx-font-weight: bold;");

            VBox moduleDetails = new VBox(2);
            moduleDetails.getChildren().add(moduleLabel);

            if (result.reason() != null && !result.reason().isEmpty()) {
                Label reasonLabel = new Label(result.reason());
                reasonLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #555;");
                moduleDetails.getChildren().add(reasonLabel);
            }

            grid.add(statusIcon, 0, rowIndex);
            grid.add(moduleDetails, 1, rowIndex);
            rowIndex++;
        }
        return grid;
    }

    private Node createStatusIcon(AnalysisStatus status) {
        Color color;
        switch (status) {
            case PASS: color = Color.web("#2ECC71"); break;
            case WARN: color = Color.web("#F39C12"); break;
            case FAIL: color = Color.web("#E74C3C"); break;
            default: color = Color.GRAY; break;
        }
        return new Circle(8, color);
    }

    private GridPane createBasicStatsGrid(QualityReport report) {
        GridPane grid = new GridPane();
        grid.setHgap(40);
        grid.setVgap(5);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.NEVER);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("File Type", report.getFileType());
        stats.put("Encoding", report.getEncoding());
        stats.put("Total Reads", String.format("%,d", report.getTotalReads()));
        stats.put("Total Bases", String.format("%,d", report.getTotalBases()));
        stats.put("Sequence Length", report.getSequenceLengthRange());
        stats.put("Average Read Length", String.format("%.0f", report.getAverageReadLength()));
        stats.put("Overall GC Content", String.format("%.2f%%", report.getOverallGcContent()));
        stats.put("Poor Sequences (< Q20)", String.format("%,d", report.getPoorQualitySequenceCount()));

        int rowIndex = 0;
        for (Map.Entry<String, String> entry : stats.entrySet()) {
            Region background = new Region();
            if (rowIndex % 2 == 0) {
                background.setStyle("-fx-background-color: #f8f8f8; -fx-background-radius: 4;");
            }
            grid.add(background, 0, rowIndex, 2, 1);

            Label metricLabel = new Label(entry.getKey());
            metricLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            metricLabel.setPadding(new Insets(5, 10, 5, 10));

            Label valueLabel = new Label(entry.getValue());
            valueLabel.setStyle("-fx-font-size: 14px;");
            valueLabel.setPadding(new Insets(5, 10, 5, 10));

            grid.add(metricLabel, 0, rowIndex);
            grid.add(valueLabel, 1, rowIndex);
            GridPane.setHalignment(valueLabel, HPos.RIGHT);

            rowIndex++;
        }
        return grid;
    }

    private Image createPerBaseQualityChart(QualityReport report) throws IOException, InterruptedException {
        StringBuilder csvData = new StringBuilder("position,q10,q1,median,q3,q90\n");
        for (int i = 0; i < report.getMaxReadLengthEncountered(); i++) {
            int[] frequencies = report.getPerBaseQualityFrequencies().get(i);
            long totalCount = 0;
            for (int count : frequencies) totalCount += count;
            if (totalCount > 0) {
                csvData.append(String.format("%d,%d,%d,%d,%d,%d%n", i + 1,
                        report.calculatePercentile(frequencies, totalCount, 10),
                        report.calculatePercentile(frequencies, totalCount, 25),
                        report.calculatePercentile(frequencies, totalCount, 50),
                        report.calculatePercentile(frequencies, totalCount, 75),
                        report.calculatePercentile(frequencies, totalCount, 90)));
            }
        }
        return callPython("create_box_plot.py", csvData.toString());
    }

    private Image createPerSequenceQualityScoresChart(QualityReport report) throws IOException, InterruptedException {
        StringBuilder csvData = new StringBuilder("quality_score,count\n");
        report.getPerSequenceQualityDistribution().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> csvData.append(String.format("%d,%d%n", entry.getKey(), entry.getValue())));

        return callPython("create_simple_line_plot.py", csvData.toString(),
                "--title", "Quality Score Distribution over all sequences",
                "--xlabel", "Average Quality Score",
                "--ylabel", "Number of Reads",
                "--xmin","2",
                "--xtick","1",
                "--ymin","0");
    }

    private Image createPerBaseContentChart(QualityReport report) throws IOException, InterruptedException {
        StringBuilder csvData = new StringBuilder("position,A_percent,C_percent,G_percent,T_percent\n");
        for (int i = 0; i < report.getMaxReadLengthEncountered(); i++) {
            long[] baseCounts = report.getPerBaseContent().get(i);
            long totalBases = baseCounts[0] + baseCounts[1] + baseCounts[2] + baseCounts[3] + baseCounts[4];
            if (totalBases > 0) {
                csvData.append(String.format("%d,%.2f,%.2f,%.2f,%.2f%n", i + 1,
                        (double) baseCounts[0] * 100 / totalBases,
                        (double) baseCounts[1] * 100 / totalBases,
                        (double) baseCounts[2] * 100 / totalBases,
                        (double) baseCounts[3] * 100 / totalBases));
            }
        }
        return callPython("create_multi_line_plot.py", csvData.toString());
    }

    private Image createPerSequenceGcContentChart(QualityReport report) throws IOException, InterruptedException {
        Map<Integer, Long> perSequenceGcDistribution = report.getPerSequenceGcDistribution();
        List<Integer> psgcBins = perSequenceGcDistribution.keySet().stream().sorted().collect(Collectors.toList());
        List<Long> psgcCounts = psgcBins.stream().map(perSequenceGcDistribution::get).collect(Collectors.toList());

        StringBuilder csvDataObserved = new StringBuilder("gc_content,count\n");
        if (!psgcBins.isEmpty()) {
            for (int i = 0; i < psgcBins.size(); i++) {
                csvDataObserved.append(String.format("%d,%d%n", psgcBins.get(i), psgcCounts.get(i)));
            }
        } else {
            csvDataObserved.append("0,0\n");
        }

        List<Double> theoreticalGcYUnscaled = new ArrayList<>();
        double theoreticalMean = report.getOverallGcContent();
        if (Double.isNaN(theoreticalMean) || theoreticalMean == 0.0) {
            theoreticalMean = 50.0;
        }
        double theoreticalStdDev = 5.0;

        double maxPdfValue = 0.0;
        for (int i = 0; i <= 100; i++) {
            double pdf = (1 / (theoreticalStdDev * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((i - theoreticalMean) / theoreticalStdDev, 2));
            theoreticalGcYUnscaled.add(pdf);
            if (pdf > maxPdfValue) {
                maxPdfValue = pdf;
            }
        }

        long maxObservedGcCount = psgcCounts.stream().mapToLong(Long::longValue).max().orElse(0L);

        StringBuilder csvDataTheoretical = new StringBuilder("gc_content,count\n");
        for (int i = 0; i <= 100; i++) {
            double scaledY = (maxPdfValue > 0) ? theoreticalGcYUnscaled.get(i) * (maxObservedGcCount / maxPdfValue) : 0.0;
            csvDataTheoretical.append(String.format("%d,%.2f%n", i, scaledY));
        }

        Path observedCsvPath = Files.createTempFile("gc_observed_", ".csv");
        Path theoreticalCsvPath = Files.createTempFile("gc_theoretical_", ".csv");

        try (PrintWriter writerObserved = new PrintWriter(observedCsvPath.toFile());
             PrintWriter writerTheoretical = new PrintWriter(theoreticalCsvPath.toFile())) {
            writerObserved.print(csvDataObserved.toString());
            writerTheoretical.print(csvDataTheoretical.toString());
        }

        return callPython("create_gc_distribution_plot.py", "",
                "--observed_file", observedCsvPath.toString(),
                "--theoretical_file", theoreticalCsvPath.toString(),
                "--title", "GC distribution over all sequences",
                "--xlabel", "Mean GC content (%)",
                "--ylabel", "Number of Reads",
                "--observed_label", "GC count per read",
                "--theoretical_label", "Theoretical Distribution",
                "--observed_color", "#800020",
                "--theoretical_color", "blue",
                "--theoretical_linestyle", "dashed",
                "--xmin", "0", "--xmax", "100",
                "--xtick", "5");
    }

    private Image createPerBaseNContentChart(QualityReport report) throws IOException, InterruptedException {
        long totalNCount = 0;
        for (int i = 0; i < report.getMaxReadLengthEncountered(); i++) {
            totalNCount += report.getPerBaseContent().get(i)[4];
        }

        if (totalNCount == 0) {
            return null;
        }

        StringBuilder csvData = new StringBuilder("position,n_percent\n");
        for (int i = 0; i < report.getMaxReadLengthEncountered(); i++) {
            long[] baseCounts = report.getPerBaseContent().get(i);
            long totalBases = baseCounts[0] + baseCounts[1] + baseCounts[2] + baseCounts[3] + baseCounts[4];
            if (totalBases > 0) {
                double nPercent = (double) baseCounts[4] * 100 / totalBases;
                csvData.append(String.format("%d,%.2f%n", i + 1, nPercent));
            }
        }
        return callPython("create_simple_line_plot.py", csvData.toString(),
                "--title", "Per Base 'N' Content",
                "--xlabel", "Position in Read (bp)",
                "--ylabel", "% N",
                "--ymin", "0",
                "--xmin", "1", "--xmax", String.format("%d", report.getMaxReadLengthEncountered()),
                "--xtick", "5");
    }

    private Image createLengthDistributionChart(QualityReport report) throws IOException, InterruptedException {
        Map<Integer, Long> distribution = report.getSequenceLengthDistribution();

        if (distribution == null || distribution.isEmpty()) {
            return null;
        }

        if (distribution.size() == 1) {
            return null;
        } else {
            StringBuilder csvData = new StringBuilder("length,count\n");
            distribution.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> csvData.append(String.format("%d,%d%n", entry.getKey(), entry.getValue())));

            return callPython("create_simple_line_plot.py", csvData.toString(),
                    "--title", "Sequence Length Distribution",
                    "--xlabel", "Sequence Length (bp)",
                    "--ylabel", "Count");
        }
    }

    private Image createDuplicationLevelsChart(QualityReport report) throws IOException, InterruptedException {
        Map<Long, Long> duplicationCounts = new HashMap<>();
        report.getSequenceDuplicationCounts().values().forEach(count ->
                duplicationCounts.merge(count, 1L, Long::sum));

        long totalSequences = report.getTotalReads();
        if (totalSequences == 0) return null;

        List<Map.Entry<Long, Double>> dataPoints = duplicationCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    double percent = (double) (entry.getValue() * entry.getKey()) * 100 / totalSequences;
                    return new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), percent);
                })
                .collect(Collectors.toList());

        if (dataPoints.size() == 1 && dataPoints.get(0).getValue() >= 99.9) {
            long level = dataPoints.get(0).getKey();
            StringBuilder barChartCsv = new StringBuilder("level,percent\n");
            barChartCsv.append(String.format("%d,0%n", level - 1));
            barChartCsv.append(String.format("%d,100%n", level));
            barChartCsv.append(String.format("%d,0%n", level + 1));

            return callPython("create_bar_chart.py", barChartCsv.toString(),
                    "--title", "Sequence Duplication Levels",
                    "--xlabel", "Sequence Duplication Level",
                    "--ylabel", "% of Total Sequences");

        } else {
            StringBuilder linePlotCsv = new StringBuilder("duplication_level,percent_of_library\n");
            dataPoints.stream()
                    .limit(20)
                    .forEach(entry -> linePlotCsv.append(String.format("%d,%.2f%n", entry.getKey(), entry.getValue())));

            return callPython("create_simple_line_plot.py", linePlotCsv.toString(),
                    "--title", "Sequence Duplication Levels",
                    "--xlabel", "Sequence Duplication Level",
                    "--ylabel", "% of Total Sequences",
                    "--xtick","1");
        }
    }

    private Image createOverrepresentedSequencesTable(QualityReport report) throws IOException, InterruptedException {
        long totalReads = report.getTotalReads();
        if (totalReads == 0) return null;

        List<Map.Entry<String, Long>> topSequences = report.getSequenceDuplicationCounts().entrySet().stream()
                .filter(entry -> entry.getValue() > totalReads * 0.001 && entry.getValue() > 1)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());

        if (topSequences.isEmpty()) return null;

        StringBuilder textData = new StringBuilder();
        textData.append("Sequence\tCount\tPercentage\n");
        topSequences.forEach(entry -> {
            String sequence = entry.getKey();
            double percent = (double) entry.getValue() * 100 / totalReads;
            textData.append(String.format("%s...\t%d\t%.2f%%\n",
                    sequence.substring(0, Math.min(sequence.length(), 30)),
                    entry.getValue(),
                    percent));
        });
        return callPython("create_table.py", textData.toString());
    }

    private Image createKmerContentChart(QualityReport report) throws IOException, InterruptedException {
        long totalKmers = report.getKmerCounts().values().stream().mapToLong(Long::longValue).sum();
        if (totalKmers == 0) return null;

        StringBuilder csvData = new StringBuilder("kmer,percentage\n");
        report.getKmerCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .forEach(entry -> {
                    double percent = (double) entry.getValue() * 100 / totalKmers;
                    csvData.append(String.format("%s,%.3f%n", entry.getKey(), percent));
                });

        return callPython("create_kmer_plot.py", csvData.toString(),
                "--title", "Top K-mer Content", "--xlabel", "% of Total K-mers");
    }

    private Image createAdapterContentChart(QualityReport report) throws IOException, InterruptedException {
        Map<String, Long> adapterCounts = report.getAdapterCounts();
        long totalReads = report.getTotalReads();

        if (adapterCounts == null || adapterCounts.values().stream().allMatch(v -> v == 0) || totalReads == 0) {
            return null;
        }

        StringBuilder csvData = new StringBuilder("adapter,percentage\n");
        adapterCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> {
                    double percentage = (double) entry.getValue() * 100.0 / totalReads;
                    csvData.append(String.format("\"%s\",%.4f%n", entry.getKey(), percentage));
                });

        return callPython("create_bar_chart.py", csvData.toString(),
                "--title", "Adapter Content",
                "--xlabel", "Percentage of Reads (%)",
                "--ylabel", "Adapter",
                "--horizontal");
    }

    /**
     * Executes an external Python script to generate a plot image.
     *
     * @param scriptName The name of the Python script in the 'scripts' resource folder.
     * @param data       The data (usually in CSV format) to be piped to the script's standard input.
     * @param args       Additional command-line arguments for the script.
     * @return An {@link Image} object of the generated plot.
     * @throws IOException          if the script cannot be executed or the output file is not found.
     * @throws InterruptedException if the process is interrupted.
     */
    private Image callPython(String scriptName, String data, String... args) throws IOException, InterruptedException {
        Path imagePath = Files.createTempFile("plot_" + scriptName + "_", ".png");

        String appDir = System.getProperty("app.dir", System.getProperty("user.dir"));

        Path installationRoot = Paths.get(appDir, "..");

        String os = System.getProperty("os.name").toLowerCase();
        String pythonExecutablePath;
        if (os.contains("win")) {
            pythonExecutablePath = Paths.get(appDir, "python_env", "venv", "Scripts", "python.exe").toString();
        } else {
            // Corrected for non-Windows as well
            pythonExecutablePath = Paths.get(appDir, "python_env", "venv", "bin", "python").toString();
        }

        String scriptPath = Paths.get(appDir, "scripts", scriptName).toString();

        String[] command = new String[args.length + 3];
        command[0] = pythonExecutablePath;
        command[1] = scriptPath;
        command[2] = imagePath.toString();
        System.arraycopy(args, 0, command, 3, args.length);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        System.out.println("Executing command: " + String.join(" ", command));

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            String errorMessage = "Could not execute command: " + String.join(" ", command) +
                    "\nPlease ensure a Python environment is bundled at: " + installationRoot.resolve("python_env") +
                    "\nAnd the script exists at: " + scriptPath +
                    "\nOriginal error: " + e.getMessage();
            throw new IOException(errorMessage, e);
        }

        try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
            writer.print(data);
        }

        StringBuilder scriptOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                scriptOutput.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("Python script '" + scriptName + "' executed successfully.");
            if (Files.exists(imagePath) && Files.size(imagePath) > 0) {
                return new Image(imagePath.toUri().toString());
            } else {
                throw new IOException("Python script executed but the output image file is missing or empty: " + imagePath);
            }
        } else {
            String errorDetails = "Command executed: " + String.join(" ", command) +
                    "\n\n--- SCRIPT OUTPUT ---\n" + scriptOutput.toString();
            throw new IOException("Python script failed with exit code " + exitCode + ".\n\n" + errorDetails);
        }
    }
}
