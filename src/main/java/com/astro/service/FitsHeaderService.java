package com.astro.service;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.Header;
import java.io.File;

public class FitsHeaderService {
    
    public static class FitsMetadata {
        public double exposureTime = 0;
        public double gain = 0;
        public double offset = 0;
    }

    public FitsMetadata readHeader(File f) {
        FitsMetadata meta = new FitsMetadata();
        try (Fits fits = new Fits(f)) {
            BasicHDU<?> hdu = fits.getHDU(0);
            Header header = hdu.getHeader();
            
            // Intentar leer claves estándar y variantes
            meta.exposureTime = header.getDoubleValue("EXPTIME", 0);
            if (meta.exposureTime == 0) meta.exposureTime = header.getDoubleValue("EXPOSURE", 0);
            
            meta.gain = header.getDoubleValue("GAIN", -1);
            if (meta.gain == -1) meta.gain = header.getDoubleValue("EGAIN", 100); 
            
            meta.offset = header.getDoubleValue("OFFSET", 0);
            if (meta.offset == 0) meta.offset = header.getDoubleValue("BRIGHTNESS", 50);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return meta;
    }
}
