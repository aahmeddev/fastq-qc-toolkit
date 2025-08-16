package com.ahmed;

import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A JavaFX Task for trimming adapter sequences and low-quality bases from a FASTQ file.
 * <p>
 * This task runs in a background thread to prevent the UI from freezing. It reads a FASTQ file,
 * performs quality and adapter trimming on each read, and writes the filtered reads to a new
 * compressed FASTQ file. It provides progress updates throughout the process.
 *
 * @author Ahmed
 * @version 1.1
 */
public class TrimmingTask extends Task<TrimmingTask.TrimmingResult> {

    private final String inputFilePath;
    private final String outputFilePath;
    private final Collection<String> adapterSequences;
    private final int qualityThreshold;
    private final int minLength;
    private final long totalReadsToProcess;

    /**
     * A record to hold the summary of the trimming process.
     * @param readsProcessed The total number of reads processed from the input file.
     * @param readsKept The number of reads that were kept after trimming.
     * @param readsDiscarded The number of reads that were discarded for being too short.
     */
    public record TrimmingResult(long readsProcessed, long readsKept, long readsDiscarded) {}

    /**
     * Constructs a new TrimmingTask.
     *
     * @param inputFilePath       The path to the input FASTQ file (.fastq or .fastq.gz).
     * @param outputFilePath      The path to write the trimmed output FASTQ file to.
     * @param adapterSequences    A collection of adapter sequences to trim from the 3' end.
     * @param qualityThreshold    The minimum Phred quality score to keep a base during quality trimming.
     * @param minLength           The minimum length a read must have after trimming to be kept.
     * @param totalReadsToProcess The total number of reads in the input file, used for progress calculation.
     */
    public TrimmingTask(String inputFilePath, String outputFilePath, Collection<String> adapterSequences, int qualityThreshold, int minLength, long totalReadsToProcess) {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
        this.adapterSequences = adapterSequences;
        this.qualityThreshold = qualityThreshold;
        this.minLength = minLength;
        this.totalReadsToProcess = totalReadsToProcess;
    }

    /**
     * The main entry point for the background task.
     * This method performs the file reading, trimming, and writing operations.
     * @return A {@link TrimmingResult} object summarizing the outcome.
     */
    @Override
    protected TrimmingResult call() throws Exception {
        long readsProcessed = 0;
        long readsKept = 0;
        long readsDiscarded = 0;

        boolean isGzipped = inputFilePath.toLowerCase().endsWith(".gz");

        // Use try-with-resources to ensure the reader and writer are automatically closed.
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        isGzipped ? new GZIPInputStream(new FileInputStream(inputFilePath)) : new FileInputStream(inputFilePath)
                ));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        new GZIPOutputStream(new FileOutputStream(outputFilePath)) // Always write compressed output.
                ))
        ) {
            String line1, line2, line3, line4;
            // Read the file record by record (4 lines at a time).
            while ((line1 = reader.readLine()) != null &&
                    (line2 = reader.readLine()) != null &&
                    (line3 = reader.readLine()) != null &&
                    (line4 = reader.readLine()) != null) {

                if (isCancelled()) {
                    break;
                }

                readsProcessed++;
                // Update the UI message periodically to avoid overwhelming the UI thread.
                if (readsProcessed % 1000 == 0) {
                    updateMessage(String.format("Trimming read #%,d", readsProcessed));
                }
                updateProgress(readsProcessed, totalReadsToProcess);

                String sequence = line2;
                String quality = line4;

                // --- Quality Trimming ---
                int trimIndex = sequence.length();
                for (int i = sequence.length() - 1; i >= 0; i--) {
                    // Stop at the first base that meets the quality threshold.
                    if ((quality.charAt(i) - 33) >= qualityThreshold) {
                        break;
                    }
                    trimIndex = i;
                }
                String qualityTrimmedSeq = sequence.substring(0, trimIndex);
                String qualityTrimmedQual = quality.substring(0, trimIndex);

                // --- Adapter Trimming ---
                String finalSeq = qualityTrimmedSeq;
                for (String adapter : adapterSequences) {
                    if (finalSeq.endsWith(adapter)) {
                        finalSeq = finalSeq.substring(0, finalSeq.lastIndexOf(adapter));
                        break; // Assume only one adapter per read.
                    }
                }
                String finalQual = qualityTrimmedQual.substring(0, finalSeq.length());

                // --- Length Filtering ---
                if (finalSeq.length() >= minLength) {
                    // If the read is long enough, write it to the output file.
                    readsKept++;
                    writer.write(line1 + "\n");
                    writer.write(finalSeq + "\n");
                    writer.write(line3 + "\n");
                    writer.write(finalQual + "\n");
                } else {
                    readsDiscarded++;
                }
            }
        }

        updateMessage("Trimming complete!");
        return new TrimmingResult(readsProcessed, readsKept, readsDiscarded);
    }
}
