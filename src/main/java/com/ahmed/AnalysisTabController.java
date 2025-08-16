package com.ahmed;

import com.ahmed.report.HtmlReportGenerator;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller for a single analysis tab in the main application.
 * Each instance of this class manages the state and UI for one FASTQ file analysis,
 * including running the analysis, generating plots, and handling user interactions
 * like exporting reports or trimming files.
 */
public class AnalysisTabController {

    // --- FXML UI Components ---
    // These fields are injected by the FXMLLoader and link to UI elements in AnalysisTab.fxml.

    @FXML private Button exportReportButton;
    @FXML private ToggleGroup DataSelection;
    @FXML private Pane chartParentPane;
    @FXML private StackPane chartContainer;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private Label progressLabel;
    @FXML private RadioButton baseStatisticsbtn;

    // --- Member Variables ---

    private File analysisFile; // The FASTQ file associated with this tab.
    private QualityControl.QualityReport lastAnalysisReport; // Caches the results of the most recent analysis.
    private final Map<String, Node> plotNodeCache = Collections.synchronizedMap(new HashMap<>()); // Thread-safe cache for generated plot nodes.
    private final HtmlReportGenerator htmlReportGenerator = new HtmlReportGenerator();
    private final PlotGenerator plotGenerator = new PlotGenerator();
    private ExecutorService analysisExecutor; // Provided by MainTabController for analysis tasks.
    private ExecutorService plotExecutor; // Provided by MainTabController for plot generation tasks.
    private boolean isFirstRun = true; // Flag to show a one-time recommendation dialog.
    private Tab controlledTab; // A reference to the Tab UI element this controller manages.
    private final ReadOnlyBooleanWrapper isBusy = new ReadOnlyBooleanWrapper(false); // Tracks if a background task is running.

    /**
     * Exposes the busy status as a read-only property for the MainTabController to observe.
     * This allows the main controller to disable global controls when any tab is busy.
     * @return The read-only boolean property representing the busy state.
     */
    public ReadOnlyBooleanProperty isBusyProperty() { return isBusy.getReadOnlyProperty(); }
    public boolean isBusy() { return isBusy.get(); }
    public QualityControl.QualityReport getLastAnalysisReport() { return lastAnalysisReport; }
    public File getAnalysisFile() { return analysisFile; }

    /**
     * Injects the shared executor services from the main controller.
     * @param analysisExecutor The executor for running FASTQ analysis tasks.
     * @param plotExecutor The executor for generating plots.
     */
    public void setExecutors(ExecutorService analysisExecutor, ExecutorService plotExecutor) {
        this.analysisExecutor = analysisExecutor;
        this.plotExecutor = plotExecutor;
    }

    /**
     * Sets the reference to the JavaFX Tab this controller is responsible for.
     * @param tab The Tab instance from the UI.
     */
    public void setTab(Tab tab) {
        this.controlledTab = tab;
    }

    /**
     * This method is called by the FXMLLoader after the FXML file has been loaded.
     * It's used for initial setup of UI components and listeners.
     */
    @FXML
    public void initialize() {
        // Bind the size of the plot pane to its container for responsive resizing.
        chartParentPane.prefWidthProperty().bind(chartContainer.widthProperty());
        chartParentPane.prefHeightProperty().bind(chartContainer.heightProperty());

        // Add a listener to the radio button group to display the selected plot.
        DataSelection.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                displaySelectedPlot();
            }
        });

        // Disable controls until an analysis is complete.
        DataSelection.getToggles().forEach(toggle -> ((Node) toggle).setDisable(true));
        exportReportButton.setDisable(true);
    }

    /**
     * Initializes the tab with a file and starts the analysis.
     * This is called by MainTabController when a new tab is created.
     * @param file The FASTQ file to analyze.
     * @param limit The maximum number of reads to process (-1 for no limit).
     */
    public void initializeTab(File file, long limit) {
        this.analysisFile = file;
        startAnalysis(limit);
    }

    /**
     * Kicks off the FASTQ analysis process in a background thread.
     * @param limit The maximum number of reads to process.
     */
    public void startAnalysis(long limit) {
        // On the first run with a small limit, show a recommendation to the user.
        if (isFirstRun && limit > 0 && limit < 500000) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Processing Limit Recommendation");
            alert.setHeaderText("Before You Start the Analysis");
            alert.setContentText("For a quick quality check, a limit of around 500,000 reads is recommended.\n\n" +
                    "For a final, publication-quality report, leave the limit blank to process the entire file.\n\n" +
                    "Do you want to continue with the current settings?");
            alert.setResizable(true);
            alert.getDialogPane().setPrefSize(480, 240);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }
        isFirstRun = false;

        // --- Reset UI and State for a New Analysis ---
        isBusy.set(true);
        setTabGraphic(new ProgressIndicator()); // Indicate busyness on the tab header.

        plotNodeCache.clear();
        chartContainer.getChildren().clear();
        lastAnalysisReport = null;
        DataSelection.getToggles().forEach(toggle -> ((Node) toggle).setDisable(true));
        exportReportButton.setDisable(true);

        statusLabel.setText("Waiting in queue...");
        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressLabel.setText("");

        // --- Create and Configure the Analysis Task ---
        FastqAnalysisTask analysisTask = new FastqAnalysisTask(analysisFile.getAbsolutePath(), limit);

        // Bind UI elements to the task's properties for live updates.
        statusLabel.textProperty().bind(analysisTask.messageProperty());
        progressBar.progressProperty().bind(analysisTask.progressProperty());

        // Add a listener for the percentage label if a limit is set.
        if (limit > 0) {
            analysisTask.getReadsProcessedProperty().addListener((obs, oldVal, newVal) -> {
                Platform.runLater(() -> progressLabel.setText(String.format("%d%%", (int) (newVal.doubleValue() * 100 / limit))));
            });
        }

        // --- Define Task Completion Logic ---
        analysisTask.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();

            lastAnalysisReport = analysisTask.getValue();
            statusLabel.setText("Analysis complete. Generating plots...");
            generateAllPlotsInBackground();
        });

        analysisTask.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            Throwable ex = analysisTask.getException();
            statusLabel.setText("Error: " + (ex != null ? ex.getMessage() : "Unknown error"));
            if (ex != null) ex.printStackTrace();
            progressBar.setVisible(false);
            progressLabel.setVisible(false);
            isBusy.set(false);
            setTabGraphic(null);
        });

        // Submit the task to the executor to run in the background.
        analysisExecutor.submit(analysisTask);
    }

    /**
     * Generates all plots in the background after the analysis is complete.
     * This is done in parallel to speed up the process.
     */
    private void generateAllPlotsInBackground() {
        List<Toggle> toggles = DataSelection.getToggles();
        final AtomicInteger plotsToGenerate = new AtomicInteger(toggles.size());

        if (lastAnalysisReport == null || toggles.isEmpty()) {
            Platform.runLater(() -> {
                statusLabel.setText("Analysis finished, but no data to plot.");
                isBusy.set(false);
                setTabGraphic(null);
            });
            return;
        }

        Platform.runLater(() -> {
            progressBar.setProgress(0);
            progressLabel.setText("0%");
            progressBar.setVisible(true);
            progressLabel.setVisible(true);
        });

        for (Toggle t : toggles) {
            final RadioButton button = (RadioButton) t;
            final String plotType = button.getId().replace("btn", "");

            Task<Node> plotTask = new Task<>() {
                @Override
                protected Node call() throws Exception {
                    return plotGenerator.createPlot(plotType, lastAnalysisReport);
                }
            };

            plotTask.setOnSucceeded(event -> {
                plotNodeCache.put(plotType, plotTask.getValue());
                handlePlotGenerationProgress(toggles, plotsToGenerate.decrementAndGet());
            });

            plotTask.setOnFailed(event -> {
                Throwable exception = plotTask.getException();
                System.err.println("Failed to generate plot: " + plotType);
                if (exception != null) {
                    exception.printStackTrace();
                    Platform.runLater(() -> showErrorAlert("Python Script Error",
                            "Failed to generate the plot for: " + plotType,
                            exception.getMessage()));
                }
                handlePlotGenerationProgress(toggles, plotsToGenerate.decrementAndGet());
            });

            plotExecutor.submit(plotTask);
        }
    }

    /**
     * Updates the UI as each plot is generated, providing feedback on the overall progress.
     * @param toggles The list of all plot toggles.
     * @param remaining The number of plots still waiting to be generated.
     */
    private void handlePlotGenerationProgress(List<Toggle> toggles, int remaining) {
        Platform.runLater(() -> {
            double progress = (double) (toggles.size() - remaining) / toggles.size();
            progressBar.setProgress(progress);
            progressLabel.setText(String.format("%d%%", (int) (progress * 100)));
            statusLabel.setText("Generating plots (" + (toggles.size() - remaining) + "/" + toggles.size() + ")...");

            if (remaining == 0) {
                statusLabel.setText("");
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
                exportReportButton.setDisable(false);
                DataSelection.getToggles().forEach(toggle -> ((Node) toggle).setDisable(false));

                if (baseStatisticsbtn != null) {
                    baseStatisticsbtn.setSelected(true);
                } else if (!toggles.isEmpty()) {
                    toggles.get(0).setSelected(true);
                }
                displaySelectedPlot();
                isBusy.set(false);
                setTabGraphic(null);
            }
        });
    }

    /**
     * Displays the currently selected plot from the cache in the chartContainer.
     * If the plot is not yet cached, it shows a loading indicator.
     */
    private void displaySelectedPlot() {
        RadioButton selectedButton = (RadioButton) DataSelection.getSelectedToggle();
        if (selectedButton == null || lastAnalysisReport == null) return;

        String plotType = selectedButton.getId().replace("btn", "");
        if (plotNodeCache.containsKey(plotType)) {
            Node plotNode = plotNodeCache.get(plotType);
            if (plotNode != null) {
                if (plotNode instanceof ImageView) {
                    ImageView imageView = (ImageView) plotNode;
                    imageView.setPreserveRatio(true);
                    imageView.fitWidthProperty().bind(chartContainer.widthProperty());
                }
                chartContainer.getChildren().setAll(plotNode);
            } else {
                // Handle cases where a plot could not be generated (e.g., no data).
                if ("perBaseNContent".equals(plotType)) {
                    displayPlaceholder("No N content found.");
                } else if ("overrepresentedSequences".equals(plotType)) {
                    displayPlaceholder("No overrepresented sequences found.");
                } else if ("sequenceLengthDistribution".equals(plotType)) {
                    Map<Integer, Long> distribution = lastAnalysisReport.getSequenceLengthDistribution();
                    if (distribution != null && distribution.size() == 1) {
                        Map.Entry<Integer, Long> singleEntry = distribution.entrySet().iterator().next();
                        displayPlaceholder(String.format("All %,d sequences are %d bp long.", singleEntry.getValue(), singleEntry.getKey()));
                    } else {
                        displayPlaceholder("No data available for this plot.");
                    }
                } else if ("adapterContent".equals(plotType)) {
                    displayPlaceholder("No adapter content found.");
                } else {
                    displayPlaceholder("No data available for this plot.");
                }
            }
        } else {
            displayLoadingIndicator();
        }
    }

    /**
     * Handles the "Export Report" button click, saving the analysis as an HTML file.
     */
    @FXML
    private void handleExportReport() {
        if (lastAnalysisReport == null) {
            showAlert(Alert.AlertType.WARNING, "Not Ready", "Please wait for the current analysis to complete before exporting.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Quality Report HTML");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files", "*.html"));

        String originalFilePath = analysisFile.getAbsolutePath();
        String baseName = originalFilePath.replaceAll("\\.fastq(\\.gz)?|\\.fq(\\.gz)?|\\.gz$", "");
        fileChooser.setInitialFileName(baseName + "_quality_report.html");

        File file = fileChooser.showSaveDialog(chartContainer.getScene().getWindow());

        if (file != null) {
            statusLabel.setText("Exporting report...");
            Task<Void> exportTask = new Task<>() {
                @Override
                protected Void call() throws IOException {
                    htmlReportGenerator.generateReport(lastAnalysisReport, file, analysisFile.getName());
                    return null;
                }
            };

            exportTask.setOnSucceeded(e -> {
                statusLabel.setText("Report exported successfully.");
                try {
                    java.awt.Desktop.getDesktop().browse(file.toURI());
                } catch (IOException ex) {
                    System.err.println("Could not open exported report in browser: " + ex.getMessage());
                }
            });

            exportTask.setOnFailed(e -> {
                statusLabel.setText("Failed to export report: " + exportTask.getException().getMessage());
                e.getSource().getException().printStackTrace();
            });

            analysisExecutor.submit(exportTask);
        }
    }

    /**
     * Handles the "Trim File" menu action, saving a trimmed version of the FASTQ file.
     */
    public void handleTrimFile() {
        if (lastAnalysisReport == null || isBusy.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Ready", "Please wait for the current analysis to complete before trimming.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Trimmed FASTQ File");

        String originalFileName = analysisFile.getName();
        String baseName = originalFileName.replaceAll("(\\.fastq|\\.fq)(\\.gz)?$", "");
        fileChooser.setInitialFileName(baseName + "_trimmed.fastq.gz");
        File outputFile = fileChooser.showSaveDialog(chartContainer.getScene().getWindow());

        if (outputFile != null) {
            Collection<String> adaptersToTrim = lastAnalysisReport.getAdapterCounts().keySet();
            final int qualityThreshold = 20;
            final int minLength = 20;
            long totalReadsToProcess = lastAnalysisReport.getTotalReads();

            TrimmingTask trimmingTask = new TrimmingTask(
                    analysisFile.getAbsolutePath(),
                    outputFile.getAbsolutePath(),
                    adaptersToTrim,
                    qualityThreshold,
                    minLength,
                    totalReadsToProcess
            );

            statusLabel.textProperty().bind(trimmingTask.messageProperty());
            progressBar.progressProperty().bind(trimmingTask.progressProperty());
            progressBar.setVisible(true);
            progressLabel.setVisible(true);
            isBusy.set(true);
            setTabGraphic(new ProgressIndicator());

            trimmingTask.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 0) {
                    progressLabel.setText(String.format("%d%%", (int) (newVal.doubleValue() * 100)));
                } else {
                    progressLabel.setText("Trimming...");
                }
            });

            trimmingTask.setOnSucceeded(e -> {
                statusLabel.textProperty().unbind();
                progressBar.progressProperty().unbind();
                TrimmingTask.TrimmingResult result = trimmingTask.getValue();
                String summary = String.format("Trimming complete. Reads kept: %,d. Reads discarded: %,d.", result.readsKept(), result.readsDiscarded());
                showAlert(Alert.AlertType.INFORMATION, "Trimming Successful", summary);
                statusLabel.setText("");
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
                isBusy.set(false);
                setTabGraphic(null);
            });

            trimmingTask.setOnFailed(e -> {
                statusLabel.textProperty().unbind();
                progressBar.progressProperty().unbind();
                showAlert(Alert.AlertType.ERROR, "Trimming Failed", "An error occurred during trimming. Please check the console.");
                trimmingTask.getException().printStackTrace();
                statusLabel.setText("Trimming failed.");
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
                isBusy.set(false);
                setTabGraphic(null);
            });

            analysisExecutor.submit(trimmingTask);
        }
    }

    // --- Helper Methods ---

    private void displayLoadingIndicator() {
        chartContainer.getChildren().setAll(new ProgressIndicator());
    }

    private void displayPlaceholder(String message) {
        Label label = new Label(message);
        label.setStyle("-fx-font-size: 1.5em; -fx-text-fill: grey;");
        chartContainer.getChildren().setAll(label);
    }

    private void setTabGraphic(Node node) {
        if (controlledTab != null) {
            Platform.runLater(() -> {
                if (node instanceof ProgressIndicator) {
                    ((ProgressIndicator) node).setPrefSize(16, 16);
                }
                controlledTab.setGraphic(node);
            });
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);

        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        VBox vbox = new VBox(textArea);
        vbox.setPrefSize(600, 400);

        alert.getDialogPane().setContent(vbox);
        alert.showAndWait();
    }
}
