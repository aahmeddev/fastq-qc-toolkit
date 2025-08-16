package com.ahmed;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * The main entry point for the DNA Quality Control Toolkit JavaFX application.
 * This class is responsible for loading the primary view and launching the user interface.
 */
public class App extends Application {

    /**
     * The main entry point for all JavaFX applications.
     * This method is called after the init method has returned, and it is where the
     * primary stage (window) and scene are set up.
     *
     * @param stage The primary stage for this application, onto which the application scene can be set.
     * @throws IOException if the FXML file for the main view cannot be loaded.
     */
    @Override
    public void start(Stage stage) throws IOException {
        // Load the main user interface from the FXML file.
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/com/ahmed/MainView.fxml"));
        Parent root = fxmlLoader.load();

        // Create a new scene with the loaded UI root.
        Scene scene = new Scene(root);

        // Configure and display the primary stage.
        stage.setTitle("DNA Quality Control Toolkit");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * This method is called when the application should stop, and provides a
     * convenient place to handle cleanup operations. The MainTabController's
     * handleExit() method is responsible for shutting down the background executor services.
     */
    @Override
    public void stop() throws Exception {
        // The actual shutdown logic for background threads is handled in MainTabController's exit handler,
        // which is a cleaner approach as it has direct access to the ExecutorService instances.
        System.out.println("Application is closing.");
    }

    /**
     * The main method, which is ignored in correctly deployed JavaFX applications,
     * but serves as a fallback for IDEs and command-line execution.
     *
     * @param args The command line arguments passed to the application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
