package com.astro.service;

import com.astro.model.AppConfig;
import com.astro.model.ImageAnalysisResult;
import ij.ImagePlus;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.FloatProcessor;
import ij.process.ImageStatistics;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.Header;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImageProcessingService {

    private static final double GAUSSIAN_CORRECTION_FACTOR = 1.7;

    public ImageAnalysisResult analyze(File fitsFile, boolean checkFwhm, boolean checkAberration, boolean checkSeeing) throws Exception {
        try (Fits fits = new Fits(fitsFile)) {
            BasicHDU<?> hdu = fits.getHDU(0);
            Object kernel = hdu.getKernel();
            double[][] data = toDoubleRaw(kernel); 
            
            FloatProcessor ip = new FloatProcessor(data[0].length, data.length);
            float[] px = (float[]) ip.getPixels();
            
            // --- CORRECCIÓN PEDESTAL ---
            double sampleSum = 0;
            int samples = 0;
            int startY = Math.max(0, data.length / 2 - 100);
            int startX = Math.max(0, data[0].length / 2 - 100);
            
            for(int y=startY; y<startY+200 && y<data.length; y++) {
                for(int x=startX; x<startX+200 && x<data[0].length; x++) {
                    sampleSum += data[y][x];
                    samples++;
                }
            }
            double estimateMean = (samples > 0) ? sampleSum / samples : 0;
            double pedestalCorrection = (estimateMean > 20000) ? 32768.0 : 0.0;
            
            for(int y=0; y<data.length; y++) {
                for(int x=0; x<data[0].length; x++) {
                    double val = data[y][x] - pedestalCorrection;
                    if (val < 0) val = 0; 
                    px[y*data[0].length + x] = (float) val;
                }
            }

            // --- DETECCIÓN ---
            ImageStatistics globalStats = ip.getStatistics();
            double skyLevel = globalStats.dmode; 
            if (skyLevel == 0) skyLevel = globalStats.mean; 
            double noise = globalStats.stdDev;

            ip.setThreshold(skyLevel + (5.0 * noise), 65535, FloatProcessor.NO_LUT_UPDATE);

            // CAMBIO IMPORTANTE: Agregamos ELLIPSE a las mediciones
            int measurements = Measurements.MEAN | Measurements.ELLIPSE; 
            if (checkFwhm || checkSeeing) measurements |= Measurements.AREA;

            ResultsTable rt = new ResultsTable();
            ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.SHOW_NONE, measurements, rt, 3, 99999);
            pa.analyze(new ImagePlus("", ip));

            int count = rt.getCounter();
            if (count == 0) return new ImageAnalysisResult(0, 0, 0, 0, false, skyLevel, 0);

            List<Double> fwhmList = new ArrayList<>();
            List<Double> roundnessList = new ArrayList<>();
            double totalSignal = 0;
            int aberrationCount = 0;

            for (int i = 0; i < count; i++) {
                // CAMBIO: Calculamos redondez como (Minor / Major)
                // Esto es mucho más preciso para estrellas que la circularidad geométrica.
                double major = rt.getValue("Major", i);
                double minor = rt.getValue("Minor", i);
                double roundness = (major > 0) ? minor / major : 0.0;
                
                // Corrección de seguridad para valores locos
                if (roundness > 1.0) roundness = 1.0; 

                double starMean = rt.getValue("Mean", i);
                double flux = starMean - skyLevel;
                if (flux > 0) totalSignal += flux;

                // Aberración si es muy ovalada
                if (checkAberration && roundness < 0.70) aberrationCount++;

                if (checkFwhm || checkSeeing) {
                    double area = rt.getValue("Area", i);
                    double blobDiameter = 2 * Math.sqrt(area / Math.PI);
                    double fwhm = blobDiameter / GAUSSIAN_CORRECTION_FACTOR;
                    
                    // Filtros
                    if (fwhm > 0.8 && fwhm < 20.0 && roundness > 0.4) {
                        fwhmList.add(fwhm);
                        roundnessList.add(roundness);
                    }
                }
            }
            
            // --- ESTADÍSTICA ROBUSTA ---
            double medianFwhm = 0;
            double medianRound = 0;

            if (!fwhmList.isEmpty()) {
                Collections.sort(fwhmList);
                int mid = fwhmList.size() / 2;
                if (fwhmList.size() % 2 == 0) medianFwhm = (fwhmList.get(mid-1) + fwhmList.get(mid)) / 2.0;
                else medianFwhm = fwhmList.get(mid);
            }
            
            if (!roundnessList.isEmpty()) {
                Collections.sort(roundnessList);
                int mid = roundnessList.size() / 2;
                if (roundnessList.size() % 2 == 0) medianRound = (roundnessList.get(mid-1) + roundnessList.get(mid)) / 2.0;
                else medianRound = roundnessList.get(mid);
            }

            double seeing = (checkSeeing) ? medianFwhm * AppConfig.getPixelScale() : 0;
            boolean hasAberration = (checkAberration && count > 0) && ((double)aberrationCount/count > 0.20);
            
            double avgSignal = (count > 0) ? totalSignal / count : 0;
            double snr = (noise > 0) ? avgSignal / noise : 0;

            ip = null;
            return new ImageAnalysisResult(count, medianRound, medianFwhm, seeing, hasAberration, skyLevel, snr);
        }
    }

    private double[][] toDoubleRaw(Object k) {
        if(k instanceof short[][]) { 
            short[][] s = (short[][])k; 
            double[][] d = new double[s.length][s[0].length]; 
            for(int i=0; i<s.length; i++) for(int j=0; j<s[0].length; j++) d[i][j] = s[i][j] & 0xFFFF;
            return d;
        }
        if(k instanceof float[][]) { 
            float[][] f = (float[][])k; 
            double[][] d = new double[f.length][f[0].length]; 
            for(int i=0; i<f.length; i++) for(int j=0; j<f[0].length; j++) d[i][j] = f[i][j]; 
            return d;
        }
        return new double[0][0];
    }
}
