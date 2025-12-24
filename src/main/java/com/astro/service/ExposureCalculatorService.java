package com.astro.service;

import com.astro.model.ExposureResult;
import com.astro.service.FitsHeaderService.FitsMetadata;

public class ExposureCalculatorService {

    // ASI2600MM Pro (IMX571) Specs
    private static final double RN_LOW_GAIN = 3.5;  
    private static final double RN_HIGH_GAIN = 1.5; 
    private static final double GAIN_E_ADU_0 = 0.78;
    private static final double GAIN_E_ADU_100 = 0.25;
    private static final double BIAS_ADU_DEFAULT = 500.0;
    
    // Full Well Capacity típica (e-)
    private static final double FULL_WELL_CAPACITY = 50000.0; 

    public ExposureResult calculate(FitsMetadata meta, double skyBackgroundADU) {
        boolean isHCG = (meta.gain >= 100);
        double readNoise = isHCG ? RN_HIGH_GAIN : RN_LOW_GAIN;
        double systemGain = isHCG ? GAIN_E_ADU_100 : GAIN_E_ADU_0;
        
        double bias = (meta.offset > 0) ? meta.offset * 10 : BIAS_ADU_DEFAULT;
        double skySignalADU = skyBackgroundADU - bias;
        if (skySignalADU < 1) skySignalADU = 1.0; 
        
        double skyElectrons = skySignalADU * systemGain;
        double rnSquared = readNoise * readNoise;
        
        // 1. SWAMP FACTOR (¿Enterramos el ruido de lectura?)
        double currentSwamp = skyElectrons / rnSquared;
        
        // 2. WELL FILL PERCENTAGE (¿Estamos saturando el sensor con luz de fondo?)
        double wellFillPercent = (skyElectrons / FULL_WELL_CAPACITY) * 100.0;
        
        ExposureResult.Status status;
        String msg;
        double exactTime = meta.exposureTime;

        // LÓGICA CORREGIDA PARA CIELO PROFUNDO
        
        if (currentSwamp < 3.0) {
            // Caso 1: Falta luz. El ruido de lectura molesta.
            status = ExposureResult.Status.UNDER_EXPOSED;
            double target = 10.0; // Queremos llegar a 10x
            double ratio = target / Math.max(0.1, currentSwamp);
            exactTime = meta.exposureTime * ratio;
            msg = String.format("❌ SUB-EXPUESTO (Factor %.1fx). Aumenta el tiempo.", currentSwamp);
            
        } else if (wellFillPercent > 40.0) {
            // Caso 2: Demasiada luz de fondo. Estamos perdiendo rango dinámico.
            // Si el fondo ocupa el 40% del pozo, las estrellas se saturarán muy rápido.
            status = ExposureResult.Status.OVER_EXPOSED;
            // Sugerimos bajar para que el fondo ocupe solo el 10% (seguro) o 20%
            double ratio = 10.0 / wellFillPercent; 
            exactTime = meta.exposureTime * ratio;
            msg = String.format("⚠️ SOBRE-EXPUESTO (Fondo al %.1f%% del pozo). Estrellas saturadas.", wellFillPercent);
            
        } else {
            // Caso 3: Óptimo.
            // Tenemos suficiente señal (>3x RN^2) Y tenemos espacio en el pozo (<40%).
            status = ExposureResult.Status.OPTIMAL;
            exactTime = meta.exposureTime; // Mantener tiempo actual
            
            // Mensaje informativo
            if (currentSwamp > 100) {
                msg = String.format("✅ EXCELENTE (Swamp %.0fx | Fondo %.1f%%). Señal muy limpia.", currentSwamp, wellFillPercent);
            } else {
                msg = String.format("✅ CORRECTO (Swamp %.1fx | Fondo %.1f%%).", currentSwamp, wellFillPercent);
            }
        }
        
        // Alerta de posible error BZERO si el ADU es sospechosamente cercano a 32768
        if (skyBackgroundADU > 32000 && skyBackgroundADU < 33500) {
            msg += "\n⚠️ ADVERTENCIA: El fondo es ~32k ADU. Podría ser un error de formato FITS (BZERO).";
        }

        return new ExposureResult(status, currentSwamp, exactTime, msg, readNoise, skyElectrons);
    }
}
