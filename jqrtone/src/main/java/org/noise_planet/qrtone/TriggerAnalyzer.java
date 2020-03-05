package org.noise_planet.qrtone;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TriggerAnalyzer {
    private AtomicInteger processedWindowAlpha = new AtomicInteger(0);
    private AtomicInteger processedWindowBeta = new AtomicInteger(0);
    private final ApproximatePercentile[] backgroundNoiseEvaluator;
    private final int startProcessBeta;
    private IterativeGeneralizedGoertzel[] frequencyAnalyzersAlpha;
    private IterativeGeneralizedGoertzel[] frequencyAnalyzersBeta;
    private final int windowAnalyze;
    private long totalProcessed = 0;
    private TriggerCallback triggerCallback = null;
    final double[] frequencies;
    final double sampleRate;

    public TriggerAnalyzer(double sampleRate, int windowAnalyze, double[] frequencies) {
        this.windowAnalyze = windowAnalyze;
        this.frequencies = frequencies;
        this.sampleRate = sampleRate;
        // 50% overlap
        startProcessBeta = windowAnalyze / 2;
        frequencyAnalyzersAlpha = new IterativeGeneralizedGoertzel[frequencies.length];
        frequencyAnalyzersBeta = new IterativeGeneralizedGoertzel[frequencies.length];
        backgroundNoiseEvaluator = new ApproximatePercentile[frequencies.length];
        for(int i=0; i<frequencies.length; i++) {
            frequencyAnalyzersAlpha[i] = new IterativeGeneralizedGoertzel(sampleRate, frequencies[i], windowAnalyze);
            frequencyAnalyzersBeta[i] = new IterativeGeneralizedGoertzel(sampleRate, frequencies[i], windowAnalyze);
            backgroundNoiseEvaluator[i] = new ApproximatePercentile(0.5);
        }
    }

    public void setTriggerCallback(TriggerCallback triggerCallback) {
        this.triggerCallback = triggerCallback;
    }

    public long getTotalProcessed() {
        return totalProcessed;
    }

    private void doProcess(float[] samples, AtomicInteger windowProcessed,
                           IterativeGeneralizedGoertzel[] frequencyAnalyzers) {
        int processed = 0;
        while(processed < samples.length) {
            int toProcess = Math.min(samples.length - processed,windowAnalyze - windowProcessed.get());
            QRTone.applyHann(samples,processed, processed + toProcess, windowAnalyze, windowProcessed.get());
            for(int idfreq = 0; idfreq < frequencyAnalyzers.length; idfreq++) {
                frequencyAnalyzers[idfreq].processSamples(samples, processed, processed + toProcess);
            }
            processed += toProcess;
            windowProcessed.addAndGet(toProcess);
            if(windowProcessed.get() == windowAnalyze) {
                windowProcessed.set(0);
                double[] splLevels = new double[frequencies.length];
                for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                    splLevels[idfreq] = 20 * Math.log10(frequencyAnalyzers[idfreq].
                            computeRMS(false).rms);
                    backgroundNoiseEvaluator[idfreq].add(splLevels[idfreq]);
                }
                // TODO process data
                if(triggerCallback != null) {
                    triggerCallback.onNewLevels(this, totalProcessed + processed - windowAnalyze, splLevels);
                }
            }
        }
    }

    public void processSamples(float[] samples) {
        doProcess(Arrays.copyOf(samples, samples.length), processedWindowAlpha, frequencyAnalyzersAlpha);
        if(totalProcessed > startProcessBeta) {
            doProcess(Arrays.copyOf(samples, samples.length), processedWindowBeta, frequencyAnalyzersBeta);
        } else if(startProcessBeta - totalProcessed < samples.length){
            // Start to process on the part used by the offset window
            doProcess(Arrays.copyOfRange(samples, startProcessBeta - (int)totalProcessed,
                    samples.length), processedWindowBeta,
                    frequencyAnalyzersBeta);
        }
        totalProcessed+=samples.length;
    }

    /**
     * Quadratic interpolation of three adjacent samples
     * @param p0 y value of left point
     * @param p1 y value of center point (maximum height)
     * @param p2 y value of right point
     * @return location [-1; 1] relative to center point, height and half-curvature of a parabolic fit through
     * @link https://www.dsprelated.com/freebooks/sasp/Sinusoidal_Peak_Interpolation.html
     * three points
     */
    private static double[] quadraticInterpolation(double p0, double p1, double p2) {
        double location;
        double height;
        double halfCurvature;
        location = (p2 - p0) / (2.0 * (2 * p1 - p2 - p0));
        height = p1 - 0.25 * (p0 - p2) * location;
        halfCurvature = 0.5 * (p0 - 2 * p1 + p2);
        return new double[]{location, height, halfCurvature};
    }

    /**
     * Evaluate peak location of a gaussian
     * @param p0 y value of left point
     * @param p1 y value of center point (maximum height)
     * @param p2 y value of right point
     * @param p1Location x value of p1
     * @param windowLength x delta between points
     * @return Peak x value
     */
    public static long findPeakLocation(double p0, double p1, double p2, long p1Location, int windowLength) {
        double location = quadraticInterpolation(p0, p1, p2)[0];
        return p1Location + (int)(location*windowLength);
    }

    public interface TriggerCallback {
        void onNewLevels(TriggerAnalyzer triggerAnalyzer, long location, double[] spl);
    }
}
