package com.ahmed;

import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.io.File;
import java.util.List;

/**
 * Controller for the content of the "Welcome" tab.
 * <p>
 * This class is responsible for displaying a list of recently opened files as
 * clickable hyperlinks. It collaborates with the {@link MainTabController} to
 * open a file when a hyperlink is clicked.
 *
 * @author Ahmed
 * @version 1.0
 */
public class WelcomeController {

    /** The VBox container that will hold the hyperlinks for recent files. */
    @FXML
    private VBox recentFilesVBox;

    /** A reference to the main controller to delegate file-opening actions. */
    private MainTabController mainController;

    /**
     * Sets the reference to the main controller.
     * This is necessary so that clicking a hyperlink can trigger the file opening
     * logic in the main application window.
     *
     * @param mainController The instance of the main application controller.
     */
    public void setMainController(MainTabController mainController) {
        this.mainController = mainController;
    }

    /**
     * Populates the VBox with hyperlinks for each recent file.
     * If the list is empty, it displays a "No recent files" message.
     *
     * @param recentFiles A list of {@link File} objects to display.
     */
    public void populateRecentFiles(List<File> recentFiles) {
        // Clear any existing items before populating.
        recentFilesVBox.getChildren().clear();

        if (recentFiles == null || recentFiles.isEmpty()) {
            recentFilesVBox.getChildren().add(new Label("(No recent files)"));
        } else {
            for (File file : recentFiles) {
                Hyperlink link = new Hyperlink(file.getName());
                link.setStyle("-fx-font-size: 18px;");

                // Set the action for the hyperlink to call the main controller's method.
                link.setOnAction(e -> {
                    if (mainController != null) {
                        mainController.openFileFromWelcomeScreen(file);
                    }
                });
                recentFilesVBox.getChildren().add(link);
            }
        }
    }
}
