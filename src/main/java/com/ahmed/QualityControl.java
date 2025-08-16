package com.ahmed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A container class for FASTQ quality control logic and data structures.
 * <p>
 * This class defines the {@link QualityReport} inner class, which stores all the metrics
 * gathered during a file analysis. It also provides helper methods and constants
 * used throughout the quality control process.
 *
 * @author Ahmed
 * @version 1.7
 */
public class QualityControl {

    /** The maximum Phred score supported by the analysis (Sanger/Illumina 1.8+). */
    public static final int MAX_PHRED_SCORE = 42;

    /** A map of common adapter sequences loaded from a resource file. */
    private static final Map<String, String> ADAPTER_LIST = loadAdaptersFromFile("/com/ahmed/adapter_list.txt");

    /**
     * Represents the status of an analysis module (PASS, WARN, or FAIL).
     */
    public enum AnalysisStatus {
        PASS, WARN, FAIL
    }

    /**
     * A record to hold the result of a module evaluation.
     * @param status The Pass/Warn/Fail status.
     * @param reason A brief explanation for the status, or an empty string if PASS.
     */
    public record EvaluationResult(AnalysisStatus status, String reason) {}

    /**
     * Loads a list of adapter sequences from a text file in the application's resources.
     * @param resourcePath The path to the resource file.
     * @return A map of adapter names to their sequences.
     */
    private static Map<String, String> loadAdaptersFromFile(String resourcePath) {
        Map<String, String> adapters = new LinkedHashMap<>();
        try (InputStream is = QualityControl.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("CRITICAL ERROR: Adapter file not found in resources: " + resourcePath);
                return adapters;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split(";", 2);
                    if (parts.length == 2) {
                        adapters.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load adapters file from resources: " + resourcePath);
            e.printStackTrace();
        }
        return adapters;
    }

    /**
     * A data class holding all calculated statistics from a FASTQ file analysis.
     * This class is designed to be thread-safe for parallel processing.
     */
    public static class QualityReport {
        // --- Fields ---
        private long totalReads = 0;
        private long totalBases = 0;
        private double averageReadLength = 0.0;
        private int maxReadLengthEncountered = 0;
        private int minLengthEncountered = Integer.MAX_VALUE;
        private int maxLengthForRange = 0;
        private long poorQualitySequenceCount = 0;
        private String sequenceLengthRange = "N/A";
        private double overallGcContent = 0.0;
        private long accumulatedGcCount = 0;

        // Data structures are chosen to be thread-safe for parallel analysis.
        private final List<int[]> perBaseQualityFrequencies = new ArrayList<>();
        private final Map<Integer, Long> perSequenceQualityDistribution = new ConcurrentHashMap<>();
        private final List<long[]> perBaseContent = new ArrayList<>();
        private final Map<Integer, Long> perSequenceGcDistribution = new ConcurrentHashMap<>();
        private final List<Long> perBaseNContent = new ArrayList<>();
        private final Map<Integer, Long> sequenceLengthDistribution = new ConcurrentHashMap<>();
        private final Map<String, Long> sequenceDuplicationCounts = new ConcurrentHashMap<>();
        private final Map<String, Long> kmerCounts = new ConcurrentHashMap<>();
        private final Map<String, Long> adapterCounts = new ConcurrentHashMap<>();
        private final Map<String, EvaluationResult> moduleStatus = new LinkedHashMap<>();

        // --- Getters ---
        public long getTotalReads() { return totalReads; }
        public long getTotalBases() { return totalBases; }
        public double getAverageReadLength() { return averageReadLength; }
        public List<int[]> getPerBaseQualityFrequencies() { return perBaseQualityFrequencies; }
        public int getMaxReadLengthEncountered() { return maxReadLengthEncountered; }
        public Map<Integer, Long> getPerSequenceQualityDistribution() { return perSequenceQualityDistribution; }
        public List<long[]> getPerBaseContent() { return perBaseContent; }
        public Map<Integer, Long> getPerSequenceGcDistribution() { return perSequenceGcDistribution; }
        public List<Long> getPerBaseNContent() { return perBaseNContent; }
        public Map<Integer, Long> getSequenceLengthDistribution() { return sequenceLengthDistribution; }
        public Map<String, Long> getSequenceDuplicationCounts() { return sequenceDuplicationCounts; }
        public Map<String, Long> getKmerCounts() { return kmerCounts; }
        public String getFileType() { return "Conventional base calls"; }
        public String getEncoding() { return "Sanger / Illumina 1.8+"; }
        public long getPoorQualitySequenceCount() { return poorQualitySequenceCount; }
        public String getSequenceLengthRange() { return sequenceLengthRange; }
        public double getOverallGcContent() { return overallGcContent; }
        public Map<String, Long> getAdapterCounts() { return adapterCounts; }
        public Map<String, EvaluationResult> getModuleStatus() { return moduleStatus; }


        /**
         * Merges another QualityReport into this one. This method is synchronized
         * to ensure thread-safe aggregation of results from parallel chunk processing.
         *
         * @param other The QualityReport from a worker thread to merge.
         */
        public synchronized void merge(QualityReport other) {
            // Aggregate simple numeric fields.
            this.totalReads += other.totalReads;
            this.totalBases += other.totalBases;
            this.poorQualitySequenceCount += other.poorQualitySequenceCount;
            this.accumulatedGcCount += other.accumulatedGcCount;
            this.minLengthEncountered = Math.min(this.minLengthEncountered, other.minLengthEncountered);
            this.maxLengthForRange = Math.max(this.maxLengthForRange, other.maxLengthForRange);

            // Merge map-based data by summing counts for matching keys.
            other.perSequenceQualityDistribution.forEach((key, value) -> this.perSequenceQualityDistribution.merge(key, value, Long::sum));
            other.perSequenceGcDistribution.forEach((key, value) -> this.perSequenceGcDistribution.merge(key, value, Long::sum));
            other.sequenceLengthDistribution.forEach((key, value) -> this.sequenceLengthDistribution.merge(key, value, Long::sum));
            other.sequenceDuplicationCounts.forEach((key, value) -> this.sequenceDuplicationCounts.merge(key, value, Long::sum));
            other.kmerCounts.forEach((key, value) -> this.kmerCounts.merge(key, value, Long::sum));
            other.adapterCounts.forEach((key, value) -> this.adapterCounts.merge(key, value, Long::sum));

            // Merge list-based data structures by adding counts element-wise.
            this.expandPerBaseDataStructures(other.maxReadLengthEncountered);

            for (int i = 0; i < other.perBaseQualityFrequencies.size(); i++) {
                int[] otherFreqs = other.perBaseQualityFrequencies.get(i);
                int[] thisFreqs = this.perBaseQualityFrequencies.get(i);
                for (int j = 0; j < otherFreqs.length; j++) {
                    thisFreqs[j] += otherFreqs[j];
                }
            }

            for (int i = 0; i < other.perBaseContent.size(); i++) {
                long[] otherContent = other.perBaseContent.get(i);
                long[] thisContent = this.perBaseContent.get(i);
                for (int j = 0; j < otherContent.length; j++) {
                    thisContent[j] += otherContent[j];
                }
            }

            for (int i = 0; i < other.perBaseNContent.size(); i++) {
                this.perBaseNContent.set(i, this.perBaseNContent.get(i) + other.perBaseNContent.get(i));
            }
        }

        /**
         * Dynamically expands list-based data structures to accommodate longer reads.
         * @param newLength The length of the new read being processed.
         */
        public synchronized void expandPerBaseDataStructures(int newLength) {
            if (newLength > maxReadLengthEncountered) {
                maxReadLengthEncountered = newLength;
                while (perBaseQualityFrequencies.size() < newLength) perBaseQualityFrequencies.add(new int[MAX_PHRED_SCORE + 1]);
                while (perBaseContent.size() < newLength) perBaseContent.add(new long[5]); // A, C, G, T, N
                while (perBaseNContent.size() < newLength) perBaseNContent.add(0L);
            }
        }

        /**
         * Processes a single FASTQ record and updates all relevant metrics.
         * @param sequence The DNA sequence string.
         * @param qualityScores The Phred quality score string.
         */
        public void processNewRead(String sequence, String qualityScores) {
            totalReads++;
            int readLength = sequence.length();
            totalBases += readLength;
            expandPerBaseDataStructures(readLength);

            // Update distributions and counts.
            sequenceLengthDistribution.merge(readLength, 1L, Long::sum);
            if (readLength < minLengthEncountered) minLengthEncountered = readLength;
            if (readLength > maxLengthForRange) maxLengthForRange = readLength;
            sequenceDuplicationCounts.merge(sequence, 1L, Long::sum);

            // Process per-base metrics.
            int currentReadGcCount = 0;
            for (int i = 0; i < readLength; i++) {
                switch (sequence.charAt(i)) {
                    case 'A': perBaseContent.get(i)[0]++; break;
                    case 'C': perBaseContent.get(i)[1]++; currentReadGcCount++; break;
                    case 'G': perBaseContent.get(i)[2]++; currentReadGcCount++; break;
                    case 'T': perBaseContent.get(i)[3]++; break;
                    case 'N':
                        perBaseContent.get(i)[4]++;
                        perBaseNContent.set(i, perBaseNContent.get(i) + 1);
                        break;
                }
            }
            accumulatedGcCount += currentReadGcCount;
            double gcPercent = (readLength > 0) ? (double) currentReadGcCount * 100 / readLength : 0;
            perSequenceGcDistribution.merge((int) Math.round(gcPercent), 1L, Long::sum);

            // Process quality scores.
            int totalQualityScore = 0;
            for (int i = 0; i < readLength; i++) {
                int phredScore = qualityScores.charAt(i) - 33;
                if (phredScore >= 0 && phredScore <= MAX_PHRED_SCORE) {
                    perBaseQualityFrequencies.get(i)[phredScore]++;
                    totalQualityScore += phredScore;
                }
            }
            int avgQuality = readLength > 0 ? totalQualityScore / readLength : 0;
            if (avgQuality < 20) poorQualitySequenceCount++;
            perSequenceQualityDistribution.merge(avgQuality, 1L, Long::sum);

            // Check for adapter content.
            for (Map.Entry<String, String> adapter : ADAPTER_LIST.entrySet()) {
                if (sequence.contains(adapter.getValue())) {
                    adapterCounts.merge(adapter.getKey(), 1L, Long::sum);
                }
            }

            // K-mer counting.
            final int k = 5;
            if (readLength >= k) {
                for (int i = 0; i <= readLength - k; i++) {
                    kmerCounts.merge(sequence.substring(i, i + k), 1L, Long::sum);
                }
            }
        }

        /**
         * Performs final calculations after all reads have been processed.
         */
        public void performFinalCalculations() {
            if (totalReads > 0) {
                averageReadLength = (double) totalBases / totalReads;
                overallGcContent = (totalBases > 0) ? (double) accumulatedGcCount * 100 / totalBases : 0.0;
                sequenceLengthRange = (minLengthEncountered == maxLengthForRange)
                        ? String.valueOf(minLengthEncountered)
                        : minLengthEncountered + "-" + maxLengthForRange;
            } else {
                sequenceLengthRange = "0";
            }
            // Delegate the final evaluation of all modules to the ModuleEvaluator class.
            ModuleEvaluator.evaluateAllModules(this);
        }

        /**
         * Calculates the score at a given percentile from a frequency distribution.
         * @param frequencies An array where the index is the score and the value is its frequency.
         * @param totalCount The total number of observations in the distribution.
         * @param percentile The percentile to calculate (e.g., 50 for median).
         * @return The score at the specified percentile.
         */
        public int calculatePercentile(int[] frequencies, long totalCount, int percentile) {
            if (totalCount == 0) return 0;
            double rank = (double) percentile / 100.0 * totalCount;
            long cumulativeCount = 0;
            for (int score = 0; score <= MAX_PHRED_SCORE; score++) {
                cumulativeCount += frequencies[score];
                if (cumulativeCount >= rank) return score;
            }
            return MAX_PHRED_SCORE;
        }
    }
}
