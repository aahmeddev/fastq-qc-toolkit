package com.ahmed;

import com.ahmed.QualityControl.AnalysisStatus;
import com.ahmed.QualityControl.EvaluationResult;
import com.ahmed.QualityControl.QualityReport;

import java.util.Arrays;
import java.util.Map;

/**
 * A utility class containing the logic for evaluating quality control modules.
 * This class provides static methods to check various metrics from a {@link QualityReport}
 * against predefined thresholds and assign a PASS, WARN, or FAIL status, including a
 * reason for any issues found.
 *
 * @author Ahmed
 * @version 1.0
 */
public class ModuleEvaluator {

    /**
     * Evaluates all quality control modules and populates the status map in the given report.
     * @param report The QualityReport instance to evaluate.
     */
    public static void evaluateAllModules(QualityReport report) {
        report.getModuleStatus().put("Per Base Sequence Quality", evaluatePerBaseQuality(report));
        report.getModuleStatus().put("Per Sequence Quality Scores", evaluatePerSequenceQuality(report));
        report.getModuleStatus().put("Per Base Sequence Content", evaluatePerBaseContent(report));
        report.getModuleStatus().put("Per Sequence GC Content", evaluatePerSequenceGcContent(report));
        report.getModuleStatus().put("Per Base N Content", evaluatePerBaseNContent(report));
        report.getModuleStatus().put("Sequence Length Distribution", evaluateSequenceLengthDistribution(report));
        report.getModuleStatus().put("Sequence Duplication Levels", evaluateDuplicationLevels(report));
        report.getModuleStatus().put("Overrepresented Sequences", evaluateOverrepresentedSequences(report));
        report.getModuleStatus().put("Adapter Content", evaluateAdapterContent(report));
    }

    /**
     * Evaluates the quality scores at each base position across all reads.
     * It fails if any base has a median quality below 10 and warns if below 20.
     */
    private static EvaluationResult evaluatePerBaseQuality(QualityReport report) {
        if (report.getPerBaseQualityFrequencies().isEmpty()) {
            return new EvaluationResult(AnalysisStatus.PASS, "");
        }
        int worstWarnPos = -1, worstFailPos = -1;
        int worstWarnMedian = 100, worstFailMedian = 100;

        for (int i = 0; i < report.getPerBaseQualityFrequencies().size(); i++) {
            int[] frequencies = report.getPerBaseQualityFrequencies().get(i);
            long totalCount = Arrays.stream(frequencies).asLongStream().sum();
            if (totalCount > 0) {
                int median = report.calculatePercentile(frequencies, totalCount, 50);
                // FAIL threshold: median quality < 10
                if (median < 10) {
                    if (median < worstFailMedian) {
                        worstFailMedian = median;
                        worstFailPos = i + 1;
                    }
                    // WARN threshold: median quality < 20
                } else if (median < 20) {
                    if (median < worstWarnMedian) {
                        worstWarnMedian = median;
                        worstWarnPos = i + 1;
                    }
                }
            }
        }
        if (worstFailPos != -1) {
            return new EvaluationResult(AnalysisStatus.FAIL, String.format("Lowest median quality is %d at base %d", worstFailMedian, worstFailPos));
        }
        if (worstWarnPos != -1) {
            return new EvaluationResult(AnalysisStatus.WARN, String.format("Lowest median quality is %d at base %d", worstWarnMedian, worstWarnPos));
        }
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }

    /**
     * Evaluates the average quality score for each sequence.
     * It fails if >25% of reads have an average quality < 20, and warns if >10%.
     */
    private static EvaluationResult evaluatePerSequenceQuality(QualityReport report) {
        if (report.getTotalReads() == 0) return new EvaluationResult(AnalysisStatus.PASS, "");
        double percentBelowQ20 = (double) report.getPoorQualitySequenceCount() * 100 / report.getTotalReads();
        if (percentBelowQ20 > 25) {
            return new EvaluationResult(AnalysisStatus.FAIL, String.format("%.1f%% of reads have average quality < 20", percentBelowQ20));
        }
        if (percentBelowQ20 > 10) {
            return new EvaluationResult(AnalysisStatus.WARN, String.format("%.1f%% of reads have average quality < 20", percentBelowQ20));
        }
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }

    /**
     * Evaluates the base content (A, C, G, T) at each position.
     * It looks for significant bias where A% != T% or G% != C%.
     */
    private static EvaluationResult evaluatePerBaseContent(QualityReport report) {
        for (int i = 0; i < report.getMaxReadLengthEncountered(); i++) {
            long[] baseCounts = report.getPerBaseContent().get(i);
            long total = Arrays.stream(baseCounts).sum();
            if (total > 0) {
                double a = (double) baseCounts[0] * 100 / total;
                double c = (double) baseCounts[1] * 100 / total;
                double g = (double) baseCounts[2] * 100 / total;
                double t = (double) baseCounts[3] * 100 / total;
                // FAIL threshold: difference > 20%
                if (Math.abs(a - t) > 20 || Math.abs(g - c) > 20) {
                    return new EvaluationResult(AnalysisStatus.FAIL, String.format("High base bias found at position %d", i + 1));
                }
                // WARN threshold: difference > 10%
                if (Math.abs(a - t) > 10 || Math.abs(g - c) > 10) {
                    return new EvaluationResult(AnalysisStatus.WARN, String.format("Base bias found at position %d", i + 1));
                }
            }
        }
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }

    /**
     * Evaluates the distribution of GC content across all sequences.
     * A simple PASS is returned as a complex check (e.g., for contamination) is non-trivial.
     */
    private static EvaluationResult evaluatePerSequenceGcContent(QualityReport report) {
        // A real implementation might check for multi-modal distributions, which could indicate contamination.
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }

    /**
     * Evaluates the percentage of 'N' bases at each position.
     * Fails if N content > 20%, warns if > 5%.
     */
    private static EvaluationResult evaluatePerBaseNContent(QualityReport report) {
        for (int i = 0; i < report.getMaxReadLengthEncountered(); i++) {
            long n_count = report.getPerBaseNContent().get(i);
            long totalBases = Arrays.stream(report.getPerBaseContent().get(i)).sum();
            if (totalBases > 0) {
                double nPercent = (double) n_count * 100 / totalBases;
                if (nPercent > 20) {
                    return new EvaluationResult(AnalysisStatus.FAIL, String.format("High N content (%.1f%%) at base %d", nPercent, i + 1));
                }
                if (nPercent > 5) {
                    return new EvaluationResult(AnalysisStatus.WARN, String.format("N content (%.1f%%) at base %d exceeds 5%%", nPercent, i + 1));
                }
            }
        }
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }

    /**
     * Checks if all sequences have the same length.
     * Warns if multiple sequence lengths are detected.
     */
    private static EvaluationResult evaluateSequenceLengthDistribution(QualityReport report) {
        if (report.getSequenceLengthDistribution().size() > 1) {
            return new EvaluationResult(AnalysisStatus.WARN, "Multiple sequence lengths detected");
        }
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }

    /**
     * Evaluates the level of sequence duplication.
     * Fails if >50% of sequences are duplicates, warns if >20%.
     */
    private static EvaluationResult evaluateDuplicationLevels(QualityReport report) {
        if (report.getTotalReads() == 0) return new EvaluationResult(AnalysisStatus.PASS, "");
        long duplicatedReads = 0;
        // Sum up all reads that appear more than once.
        for (long count : report.getSequenceDuplicationCounts().values()) {
            if (count > 1) {
                duplicatedReads += count;
            }
        }
        double duplicationPercent = (double) duplicatedReads * 100 / report.getTotalReads();
        if (duplicationPercent > 50) {
            return new EvaluationResult(AnalysisStatus.FAIL, String.format("%.1f%% of sequences are duplicates", duplicationPercent));
        }
        if (duplicationPercent > 20) {
            return new EvaluationResult(AnalysisStatus.WARN, String.format("%.1f%% of sequences are duplicates", duplicationPercent));
        }
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }

    /**
     * Checks for individual sequences that are highly overrepresented.
     * Fails if any single sequence makes up >1% of the total, warns if >0.1%.
     */
    private static EvaluationResult evaluateOverrepresentedSequences(QualityReport report) {
        if (report.getTotalReads() == 0) return new EvaluationResult(AnalysisStatus.PASS, "");
        for (Map.Entry<String, Long> entry : report.getSequenceDuplicationCounts().entrySet()) {
            double percentage = (double) entry.getValue() * 100 / report.getTotalReads();
            if (percentage > 1.0) {
                return new EvaluationResult(AnalysisStatus.FAIL, String.format("Sequence found at %.2f%%", percentage));
            }
            if (percentage > 0.1) {
                return new EvaluationResult(AnalysisStatus.WARN, String.format("Sequence found at %.2f%%", percentage));
            }
        }
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }

    /**
     * Checks for the presence of common adapter sequences.
     * Fails if >10% of sequences contain adapters, warns if >5%.
     */
    private static EvaluationResult evaluateAdapterContent(QualityReport report) {
        if (report.getTotalReads() == 0) return new EvaluationResult(AnalysisStatus.PASS, "");
        long totalAdapterHits = report.getAdapterCounts().values().stream().mapToLong(Long::longValue).sum();
        double percentContamination = (double) totalAdapterHits * 100 / report.getTotalReads();
        if (percentContamination > 10) {
            return new EvaluationResult(AnalysisStatus.FAIL, String.format("%.2f%% of sequences contained adapters", percentContamination));
        }
        if (percentContamination > 5) {
            return new EvaluationResult(AnalysisStatus.WARN, String.format("%.2f%% of sequences contained adapters", percentContamination));
        }
        return new EvaluationResult(AnalysisStatus.PASS, "");
    }
}
