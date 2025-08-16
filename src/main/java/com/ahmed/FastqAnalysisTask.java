package com.ahmed;

import com.ahmed.QualityControl.QualityReport;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

/**
 * A JavaFX Task designed to analyze a FASTQ file in a background thread.
 * This prevents the user interface from freezing during potentially long file processing operations.
 * It encapsulates the entire analysis logic, handles progress updates for the UI,
 * and returns a comprehensive QualityReport upon completion.
 *
 * @author Ahmed
 * @version 1.0
 */
public class FastqAnalysisTask extends Task<QualityReport> {

    private final String fastqFilePath;
    private final long processingLimit;
    private final ReadOnlyLongWrapper readsProcessed = new ReadOnlyLongWrapper(0);

    /**
     * Constructs a new analysis task.
     *
     * @param fastqFilePath The path to the FASTQ file (can be plain text or gzipped).
     * @param processingLimit The maximum number of reads to process. Use a value <= 0 to process the entire file.
     */
    public FastqAnalysisTask(String fastqFilePath, long processingLimit) {
        this.fastqFilePath = fastqFilePath;
        this.processingLimit = processingLimit;
    }

    /**
     * Exposes the number of processed reads as a read-only property for UI binding.
     * @return The read-only property for the number of reads processed.
     */
    public ReadOnlyLongProperty getReadsProcessedProperty() {
        return readsProcessed.getReadOnlyProperty();
    }

    /**
     * The main entry point for the background task, executed on a separate thread.
     * This method reads the FASTQ file record by record, processes each read, and updates the task's progress.
     *
     * @return A QualityReport object containing the complete analysis results.
     * @throws Exception if a critical error occurs during file processing.
     */
    @Override
    protected QualityReport call() throws Exception {
        QualityReport report = new QualityReport();

        // Determine if the file is gzipped to create the appropriate reader.
        boolean isGzipped = fastqFilePath.toLowerCase().endsWith(".gz") || fastqFilePath.toLowerCase().endsWith(".fq.gz");

        try (BufferedReader reader = isGzipped
                ? new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(fastqFilePath))))
                : new BufferedReader(new FileReader(fastqFilePath))) {

            // If there's no processing limit, the total work is unknown. Set progress to indeterminate.
            if (processingLimit <= 0) {
                updateProgress(-1, -1); // Signals indeterminate progress.
            }

            String line1, line2, line3, line4;
            long readCount = 0;

            // Loop through the file, reading 4 lines at a time for each complete FASTQ record.
            while (!isCancelled() && (line1 = reader.readLine()) != null &&
                    (line2 = reader.readLine()) != null &&
                    (line3 = reader.readLine()) != null &&
                    (line4 = reader.readLine()) != null) {

                report.processNewRead(line2, line4);
                readCount++;
                readsProcessed.set(readCount);

                // Update the message periodically to avoid overwhelming the UI thread.
                if (readCount % 1000 == 0) {
                    updateMessage(String.format("Analyzing read #%,d", readCount));
                }

                // If a processing limit is set, check if it has been reached.
                if (processingLimit > 0 && readCount >= processingLimit) {
                    break;
                }
            }

            if (isCancelled()) {
                return null;
            }

            // After all reads are processed, perform final calculations.
            updateMessage("Finalizing report...");
            report.performFinalCalculations();
            updateProgress(1, 1); // Mark the task as 100% complete.
            updateMessage("Analysis complete.");
        }
        return report;
    }
}
