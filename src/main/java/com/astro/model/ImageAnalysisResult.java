package com.astro.model;

public class ImageAnalysisResult {
    public final int starCount;
    public final double roundness;
    public final double fwhm;
    public final double seeing;
    public final boolean hasAberration;
    
    // Nuevas métricas fotométricas
    public final double skyBackground; // Nivel medio del fondo (ADU)
    public final double snr;           // Calidad de la señal

    public ImageAnalysisResult(int starCount, double roundness, double fwhm, double seeing, boolean hasAberration, double skyBackground, double snr) {
        this.starCount = starCount;
        this.roundness = roundness;
        this.fwhm = fwhm;
        this.seeing = seeing;
        this.hasAberration = hasAberration;
        this.skyBackground = skyBackground;
        this.snr = snr;
    }
}
