package com.ahmed;

import com.ahmed.report.HtmlReportGenerator;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

/**
 * The main controller for the application's primary window.
 * This class manages the main menu, global UI controls, the central TabPane,
 * and orchestrates background tasks for file analysis and plotting.
 *
 * @author Ahmed
 * @version 3.8
 */
public class MainTabController {

    // --- FXML UI Components ---
    // These fields are injected by the FXMLLoader and link to UI elements in MainView.fxml.

    @FXML private MenuBar menuBar;
    @FXML private TabPane tabPane;
    @FXML private TextField filePathField;
    @FXML private TextField processingLimitField;
    @FXML private Button analyzeButton;
    @FXML private Menu openRecentMenu;
    @FXML private MenuItem exportAllMenuItem;
    @FXML private MenuItem trimFileMenuItem;
    @FXML private MenuItem insertSizeMenuItem;

    // --- Member Variables ---

    private final Map<String, Tab> openTabsByPath = new HashMap<>(); // Tracks open tabs by file path to prevent duplicates.
    private final List<AnalysisTabController> activeTabControllers = new ArrayList<>(); // A list of controllers for all active analysis tabs.
    private File currentSelectedFile; // The file currently selected via the "Open File" dialog.
    private final BooleanProperty isAnyTabBusy = new SimpleBooleanProperty(false); // Tracks if any analysis tab is currently busy.
    private final ExecutorService analysisExecutor; // Thread pool for running FASTQ analysis tasks.
    private final ExecutorService plotExecutor; // Thread pool for generating plots.
    private Tab welcomeTab; // The initial "Welcome" tab.
    private WelcomeController welcomeController; // The controller for the "Welcome" tab.

    // --- Constants for Recent Files ---

    private static final int MAX_RECENT_FILES = 5;
    private static final String PREF_NODE_NAME = "com/ahmed/dnatoolkit";
    private static final String RECENT_FILE_KEY_PREFIX = "recent_file_";

    /**
     * Constructor for the MainTabController.
     * Initializes the thread pools for background processing.
     */
    public MainTabController() {
        // A factory for creating daemon threads, which won't prevent the application from exiting.
        ThreadFactory daemonThreadFactory = r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        };
        // Allocate 75% of available CPU cores to analysis tasks for high throughput.
        int analysisThreads = Math.max(1, 3 * Runtime.getRuntime().availableProcessors() / 4);
        analysisExecutor = Executors.newFixedThreadPool(analysisThreads, daemonThreadFactory);
        // Use all available cores for plotting to make the UI as responsive as possible.
        plotExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), daemonThreadFactory);
    }

    /**
     * This method is called by the FXMLLoader after the FXML file has been loaded.
     * It's used for initial setup of UI components, bindings, and listeners.
     */
    @FXML
    public void initialize() {
        analyzeButton.setDisable(true);
        populateRecentFilesMenu();

        // Bind the disabled state of global menu items to the busy state or if no tabs are open.
        BooleanBinding noTabsOrBusy = Bindings.isEmpty(tabPane.getTabs()).or(isAnyTabBusy);
        exportAllMenuItem.disableProperty().bind(noTabsOrBusy);
        trimFileMenuItem.disableProperty().bind(noTabsOrBusy);

        setupDragAndDrop();
        createAndShowWelcomeTab();

        // Add a listener to handle tab closure events.
        tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            boolean analysisTabWasClosed = false;
            while (c.next()) {
                if (c.wasRemoved()) {
                    for (Tab removedTab : c.getRemoved()) {
                        // Clean up resources associated with the closed tab.
                        if (removedTab != welcomeTab) {
                            AnalysisTabController controller = (AnalysisTabController) removedTab.getUserData();
                            if (controller != null) {
                                activeTabControllers.remove(controller);
                                openTabsByPath.remove(controller.getAnalysisFile().getAbsolutePath());
                                recalculateBusyState();
                                analysisTabWasClosed = true;
                            }
                        }
                    }
                }
            }
            // If the last analysis tab was closed, show the welcome screen again.
            if (analysisTabWasClosed && tabPane.getTabs().isEmpty()) {
                createAndShowWelcomeTab();
            }
        });
    }

    /**
     * Handles the "Trim Current File" menu item action.
     * Delegates the trimming task to the currently active tab's controller.
     */
    @FXML
    private void handleTrimFileAction(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null && selectedTab.getUserData() instanceof AnalysisTabController) {
            AnalysisTabController controller = (AnalysisTabController) selectedTab.getUserData();
            controller.handleTrimFile();
        } else {
            showAlert(Alert.AlertType.WARNING, "No Active Analysis", "Please select a tab with a completed analysis to trim.");
        }
    }

    /**
     * Creates and displays the welcome tab if it's not already present.
     */
    private void createAndShowWelcomeTab() {
        if (welcomeTab == null || !tabPane.getTabs().contains(welcomeTab)) {
            try {
                URL welcomeFxmlUrl = getClass().getResource("/com/ahmed/WelcomeTab.fxml");
                if (welcomeFxmlUrl == null) {
                    // Handle critical error if FXML is missing.
                    System.err.println("FATAL ERROR: Could not find WelcomeTab.fxml in resources.");
                    return;
                }

                FXMLLoader loader = new FXMLLoader(welcomeFxmlUrl);
                Parent welcomeContent = loader.load();

                welcomeController = loader.getController();
                welcomeController.setMainController(this);
                welcomeController.populateRecentFiles(getRecentFilesListAsFiles());

                welcomeTab = new Tab("Welcome");
                welcomeTab.setContent(welcomeContent);
                welcomeTab.setClosable(false);
                tabPane.getTabs().add(0, welcomeTab);
                tabPane.getSelectionModel().select(welcomeTab);
            } catch (IOException e) {
                System.err.println("Failed to load WelcomeTab.fxml");
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets up drag-and-drop functionality for FASTQ files onto the TabPane.
     */
    private void setupDragAndDrop() {
        tabPane.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                // Accept the drag event only if it contains valid FASTQ files.
                boolean hasValidFile = db.getFiles().stream()
                        .anyMatch(file -> file.getName().toLowerCase().endsWith(".fastq") || file.getName().toLowerCase().endsWith(".fastq.gz"));
                if (hasValidFile) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
            }
            event.consume();
        });

        tabPane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> validFiles = db.getFiles().stream()
                        .filter(file -> file.getName().toLowerCase().endsWith(".fastq") || file.getName().toLowerCase().endsWith(".fastq.gz"))
                        .collect(Collectors.toList());

                if (!validFiles.isEmpty()) {
                    // Process all dropped files.
                    validFiles.forEach(this::addFileToRecentList);
                    populateRecentFilesMenu();
                    validFiles.forEach(this::createNewAnalysisTab);
                    if (welcomeController != null) {
                        welcomeController.populateRecentFiles(getRecentFilesListAsFiles());
                    }
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * Handles the "Open File" menu action, showing a file chooser dialog.
     */
    @FXML
    private void handleOpenFileAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select FASTQ File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("FASTQ Files", "*.fastq", "*.fastq.gz", "*.fq", "*.fq.gz")
        );
        File selectedFile = fileChooser.showOpenDialog(getStage());

        if (selectedFile != null) {
            addFileToRecentList(selectedFile);
            populateRecentFilesMenu();
            setCurrentFile(selectedFile);
        }
    }

    /**
     * Handles the "Analyze" button click.
     * Starts a new analysis or re-runs an existing one.
     */
    @FXML
    private void runAnalysis(ActionEvent event) {
        if (currentSelectedFile == null) return;

        Tab existingTab = openTabsByPath.get(currentSelectedFile.getAbsolutePath());

        if (existingTab != null) {
            // If the file is already open, select its tab and re-run the analysis.
            tabPane.getSelectionModel().select(existingTab);
            AnalysisTabController controller = (AnalysisTabController) existingTab.getUserData();
            if (controller != null) {
                controller.startAnalysis(getProcessingLimit());
            }
        } else {
            // Otherwise, create a new tab for the file.
            createNewAnalysisTab(currentSelectedFile);
        }
    }

    /**
     * Opens a file selected from the welcome screen and starts analysis.
     * @param file The file to open.
     */
    public void openFileFromWelcomeScreen(File file) {
        if (file != null && file.exists()) {
            addFileToRecentList(file);
            populateRecentFilesMenu();
            setCurrentFile(file);
            runAnalysis(null);
        }
    }

    /**
     * Handles the "Export All Reports" menu action.
     */
    @FXML
    private void handleExportAllAction(ActionEvent event) {
        List<AnalysisTabController> exportableControllers = activeTabControllers.stream()
                .filter(c -> c.getLastAnalysisReport() != null)
                .collect(Collectors.toList());

        if (exportableControllers.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Reports to Export", "There are no completed analyses available to export.");
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory to Save All Reports");
        File selectedDirectory = directoryChooser.showDialog(getStage());

        if (selectedDirectory != null) {
            // Perform the export in a background task to keep the UI responsive.
            Task<Void> exportAllTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    HtmlReportGenerator reportGenerator = new HtmlReportGenerator();
                    for (AnalysisTabController controller : exportableControllers) {
                        if (isCancelled()) break;
                        File originalFile = controller.getAnalysisFile();
                        String baseName = originalFile.getName().replaceAll("\\.fastq(\\.gz)?|\\.fq(\\.gz)?|\\.gz$", "");
                        File outputFile = new File(selectedDirectory, baseName + "_quality_report.html");
                        reportGenerator.generateReport(controller.getLastAnalysisReport(), outputFile, originalFile.getName());
                    }
                    return null;
                }
            };

            exportAllTask.setOnSucceeded(e -> {
                showAlert(Alert.AlertType.INFORMATION, "Export Complete",
                        exportableControllers.size() + " reports have been successfully saved.");
                try {
                    java.awt.Desktop.getDesktop().open(selectedDirectory);
                } catch (IOException ex) {
                    System.err.println("Could not open export directory: " + ex.getMessage());
                }
            });

            exportAllTask.setOnFailed(e -> {
                showAlert(Alert.AlertType.ERROR, "Export Failed", "An error occurred during the export process.");
                exportAllTask.getException().printStackTrace();
            });

            new Thread(exportAllTask).start();
        }
    }

    /**
     * Creates a new analysis tab for the given file.
     * @param file The file to be analyzed in the new tab.
     */
    private void createNewAnalysisTab(File file) {
        // Remove the welcome tab if it's present.
        if (welcomeTab != null && tabPane.getTabs().contains(welcomeTab)) {
            tabPane.getTabs().remove(welcomeTab);
        }

        try {
            // If a tab for this file already exists, just select it.
            if (openTabsByPath.containsKey(file.getAbsolutePath())) {
                tabPane.getSelectionModel().select(openTabsByPath.get(file.getAbsolutePath()));
                return;
            }

            // Load the FXML for the analysis tab content.
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ahmed/AnalysisTab.fxml"));
            Parent tabContent = loader.load();

            // Get the controller and configure it.
            AnalysisTabController tabController = loader.getController();
            activeTabControllers.add(tabController);
            tabController.setExecutors(analysisExecutor, plotExecutor);
            tabController.isBusyProperty().addListener((obs, wasBusy, isNowBusy) -> recalculateBusyState());

            // Create and configure the new tab.
            Tab tab = new Tab(file.getName());
            tab.setContent(tabContent);
            tab.setUserData(tabController); // Store controller for later access.

            tabController.setTab(tab);
            tabController.initializeTab(file, getProcessingLimit());

            tab.setContextMenu(createTabContextMenu(tab));

            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            openTabsByPath.put(file.getAbsolutePath(), tab);

        } catch (IOException e) {
            System.err.println("Failed to load FXML for analysis tab: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Recalculates the global busy state based on the state of all active tabs.
     */
    private void recalculateBusyState() {
        isAnyTabBusy.set(activeTabControllers.stream().anyMatch(AnalysisTabController::isBusy));
    }

    /**
     * Populates the "Open Recent" menu with a list of recently opened files.
     */
    private void populateRecentFilesMenu() {
        openRecentMenu.getItems().clear();
        List<File> recentFiles = getRecentFilesListAsFiles();

        if (!recentFiles.isEmpty()) {
            for (File file : recentFiles) {
                MenuItem menuItem = new MenuItem(file.getName());
                menuItem.setOnAction(e -> {
                    if (file.exists()) {
                        setCurrentFile(file);
                    } else {
                        // If the file no longer exists, remove it from the list.
                        removeFileFromRecentList(file);
                        populateRecentFilesMenu();
                    }
                });
                openRecentMenu.getItems().add(menuItem);
            }
            openRecentMenu.getItems().add(new SeparatorMenuItem());
            MenuItem clearItem = new MenuItem("Clear Menu");
            clearItem.setOnAction(e -> {
                clearRecentFilesList();
                populateRecentFilesMenu();
            });
            openRecentMenu.getItems().add(clearItem);
        } else {
            MenuItem emptyItem = new MenuItem("(No recent files)");
            emptyItem.setDisable(true);
            openRecentMenu.getItems().add(emptyItem);
        }
    }

    // --- Recent Files Preference Management ---

    private void addFileToRecentList(File file) {
        List<String> recentFiles = getRecentFilesList();
        String newPath = file.getAbsolutePath();
        recentFiles.remove(newPath);
        recentFiles.add(0, newPath); // Add to the top of the list.
        saveRecentFilesList(recentFiles);
        if (welcomeController != null) {
            welcomeController.populateRecentFiles(getRecentFilesListAsFiles());
        }
    }

    private void removeFileFromRecentList(File file) {
        List<String> recentFiles = getRecentFilesList();
        recentFiles.remove(file.getAbsolutePath());
        saveRecentFilesList(recentFiles);
    }

    private void clearRecentFilesList() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE_NAME);
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            prefs.remove(RECENT_FILE_KEY_PREFIX + i);
        }
    }

    private List<String> getRecentFilesList() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE_NAME);
        List<String> recentFiles = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            String path = prefs.get(RECENT_FILE_KEY_PREFIX + i, null);
            if (path != null) {
                recentFiles.add(path);
            }
        }
        return recentFiles;
    }

    private List<File> getRecentFilesListAsFiles() {
        return getRecentFilesList().stream().map(File::new).collect(Collectors.toList());
    }

    private void saveRecentFilesList(List<String> recentFiles) {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE_NAME);
        clearRecentFilesList();
        for (int i = 0; i < MAX_RECENT_FILES && i < recentFiles.size(); i++) {
            prefs.put(RECENT_FILE_KEY_PREFIX + i, recentFiles.get(i));
        }
    }

    /**
     * Sets the currently selected file and updates the UI accordingly.
     * @param file The file selected by the user.
     */
    private void setCurrentFile(File file) {
        currentSelectedFile = file;
        filePathField.setText(file.getAbsolutePath());
        analyzeButton.setDisable(false);
    }

    /**
     * Creates a context menu for an analysis tab.
     * @param tab The tab to create the context menu for.
     * @return The configured ContextMenu.
     */
    private ContextMenu createTabContextMenu(Tab tab) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem rerunItem = new MenuItem("Re-run Analysis");
        rerunItem.setOnAction(e -> {
            AnalysisTabController controller = (AnalysisTabController) tab.getUserData();
            if (controller != null) {
                controller.startAnalysis(getProcessingLimit());
            }
        });
        MenuItem closeItem = new MenuItem("Close Tab");
        closeItem.setOnAction(e -> tabPane.getTabs().remove(tab));
        MenuItem closeOthersItem = new MenuItem("Close Other Tabs");
        closeOthersItem.setOnAction(e -> {
            List<Tab> tabsToRemove = new ArrayList<>(tabPane.getTabs());
            tabsToRemove.remove(tab);
            tabPane.getTabs().removeAll(tabsToRemove);
        });
        MenuItem closeRightItem = new MenuItem("Close Tabs to the Right");
        closeRightItem.setOnAction(e -> {
            int currentIndex = tabPane.getTabs().indexOf(tab);
            if (currentIndex < tabPane.getTabs().size() - 1) {
                List<Tab> tabsToRemove = new ArrayList<>(tabPane.getTabs().subList(currentIndex + 1, tabPane.getTabs().size()));
                tabPane.getTabs().removeAll(tabsToRemove);
            }
        });

        // Bind the disabled state of menu items to the current tab state.
        closeRightItem.disableProperty().bind(
                tabPane.getSelectionModel().selectedIndexProperty().isEqualTo(tabPane.getTabs().size() - 1)
        );
        closeOthersItem.disableProperty().bind(
                Bindings.size(tabPane.getTabs()).isEqualTo(1)
        );

        contextMenu.getItems().addAll(rerunItem, new SeparatorMenuItem(), closeItem, closeOthersItem, closeRightItem);
        return contextMenu;
    }

    /**
     * Handles the "Exit" menu action, ensuring background threads are shut down.
     */
    @FXML
    private void handleExit() {
        analysisExecutor.shutdownNow();
        plotExecutor.shutdownNow();
        javafx.application.Platform.exit();
    }

    /**
     * Parses the processing limit from the text field.
     * @return The user-defined limit, or -1 (read whole file) if empty or invalid.
     */
    private long getProcessingLimit() {
        try {
            String limitText = processingLimitField.getText();
            return (limitText == null || limitText.trim().isEmpty()) ? -1 : Long.parseLong(limitText);
        } catch (NumberFormatException e) {
            return -1; // Return -1 for invalid number format.
        }
    }

    /**
     * Helper method to get the main application stage.
     * @return The primary Stage.
     */
    private Stage getStage() {
        return (Stage) menuBar.getScene().getWindow();
    }

    /**
     * Helper method to show a simple alert dialog.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
