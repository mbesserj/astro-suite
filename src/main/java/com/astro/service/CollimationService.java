package com.astro.service;

import com.astro.model.CollimationData;
import ij.ImagePlus;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.FloatProcessor;
import ij.process.ImageStatistics;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import java.io.File;

public class CollimationService {

    public CollimationData analyze(File f) throws Exception {
        // Liberación explicita de memoria FITS con try-with-resources
        try (Fits fits = new Fits(f)) {
            BasicHDU<?> hdu = fits.getHDU(0);
            double[][] data = toDouble(hdu.getKernel());
            
            FloatProcessor ip = new FloatProcessor(data[0].length, data.length);
            float[] px = (float[]) ip.getPixels();
            for(int y=0; y<data.length; y++) 
                for(int x=0; x<data[0].length; x++) 
                    px[y*data[0].length + x] = (float) data[y][x];

            ImageStatistics stats = ip.getStatistics();
            ip.setThreshold(stats.dmode + (5.0 * stats.stdDev), 65535, FloatProcessor.NO_LUT_UPDATE);

            ResultsTable rt = new ResultsTable();
            new ParticleAnalyzer(ParticleAnalyzer.SHOW_NONE, Measurements.CIRCULARITY|Measurements.CENTER_OF_MASS, rt, 8, 99999)
                .analyze(new ImagePlus("", ip));

            CollimationData result = calculateZones(rt, data[0].length, data.length);
            
            // Ayuda al GC
            ip = null;
            return result;
        }
    }

    private CollimationData calculateZones(ResultsTable rt, int w, int h) {
        CollimationData data = new CollimationData();
        ZoneStats zC=new ZoneStats(), zTL=new ZoneStats(), zTR=new ZoneStats(), zBL=new ZoneStats(), zBR=new ZoneStats();
        int mx=(int)(w*0.2), my=(int)(h*0.2);

        for(int i=0;i<rt.getCounter();i++){
            double x=rt.getValue("XM",i), y=rt.getValue("YM",i), c=rt.getValue("Circ.",i);
            if(x>mx && x<w-mx && y>my && y<h-my) zC.add(c);
            else if(x<mx && y<my) zTL.add(c); else if(x>w-mx && y<my) zTR.add(c);
            else if(x<mx && y>h-my) zBL.add(c); else if(x>w-mx && y>h-my) zBR.add(c);
        }
        
        data.center = zC.avg(); data.tl = zTL.avg(); data.tr = zTR.avg();
        data.bl = zBL.avg(); data.br = zBR.avg();
        
        return calculateScore(data);
    }

    private CollimationData calculateScore(CollimationData d) {
        double scoreCentro = (d.center >= 0.92) ? 40 : (d.center >= 0.88) ? 35 : (d.center >= 0.85) ? 30 : 10;
        
        double avgEsquinas = (d.tl + d.tr + d.bl + d.br) / 4.0;
        double maxDiff = Math.max(Math.max(Math.abs(d.tl - avgEsquinas), Math.abs(d.tr - avgEsquinas)),
                                Math.max(Math.abs(d.bl - avgEsquinas), Math.abs(d.br - avgEsquinas)));
        
        double scoreUniformidad = (maxDiff < 0.03) ? 30 : (maxDiff < 0.08) ? 20 : 5;
        double scoreEsquinas = (avgEsquinas >= 0.88) ? 20 : 10;
        
        double avgIzq = (d.tl + d.bl) / 2.0; double avgDer = (d.tr + d.br) / 2.0;
        double diffLR = Math.abs(avgIzq - avgDer);
        double scoreSimetria = (diffLR < 0.04) ? 10 : 0;

        d.scoreTotal = (int)(scoreCentro + scoreUniformidad + scoreEsquinas + scoreSimetria);
        
        if (d.scoreTotal >= 90) d.clasificacion = "⭐ EXCELENTE";
        else if (d.scoreTotal >= 75) d.clasificacion = "✅ BUENA";
        else d.clasificacion = "⚠️ REGULAR";
        
        d.diagnostico = String.format("SCORE: %d (%s)\nCentro: %.2f\nDiff Esquinas: %.3f\nAsimetría L/R: %.3f", 
                             d.scoreTotal, d.clasificacion, d.center, maxDiff, diffLR);
        return d;
    }
    
    static class ZoneStats { double s=0; int c=0; void add(double v){s+=v;c++;} double avg(){return c==0?0:s/c;} }
    
    private double[][] toDouble(Object k) {
        if(k instanceof short[][]) { short[][] s=(short[][])k; double[][] d=new double[s.length][s[0].length]; for(int i=0;i<s.length;i++)for(int j=0;j<s[0].length;j++)d[i][j]=s[i][j]&0xFFFF; return d;}
        if(k instanceof float[][]) { float[][] f=(float[][])k; double[][] d=new double[f.length][f[0].length]; for(int i=0;i<f.length;i++)for(int j=0;j<f[0].length;j++)d[i][j]=f[i][j]; return d;}
        return new double[0][0];
    }
}
