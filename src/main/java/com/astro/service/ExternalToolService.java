package com.astro.service;

import com.astro.model.AppConfig;
import com.astro.model.CelestialPoint;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

public class ExternalToolService {

    public CelestialPoint solvePlate(File fitsFile, String dbPathIgnored) {
        String astapPath = AppConfig.getAstapPath();
        String dbPath = AppConfig.getAstapDbPath();
        
        if (astapPath == null || astapPath.isEmpty()) return null;
        if (dbPath == null || dbPath.isEmpty()) {
            System.err.println("❌ Error: Ruta de base de datos ASTAP no configurada.");
            return null;
        }

        try {
            // ASTAP CLI: -f (file) -r (radius 30deg) -z (downsample 0=auto)
            ProcessBuilder pb = new ProcessBuilder(
                astapPath, 
                "-f", fitsFile.getAbsolutePath(),
                "-r", "30",
                "-d", dbPath
            );
            
            Process p = pb.start();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS); 
            if (!finished) {
                p.destroy();
                return null;
            }
            
            // Buscamos el archivo .wcs generado
            String basePath = fitsFile.getAbsolutePath().substring(0, fitsFile.getAbsolutePath().lastIndexOf('.'));
            File wcsFile = new File(basePath + ".wcs");
            File iniFile = new File(basePath + ".ini"); // ASTAP a veces crea un ini con el mismo nombre

            CelestialPoint result = null;

            if (wcsFile.exists()) {
                result = parseWcsFile(wcsFile);
                
                // --- LIMPIEZA AUTOMÁTICA ---
                // Una vez leído el dato, borramos el archivo para no ensuciar la carpeta
                try {
                    Files.deleteIfExists(wcsFile.toPath());
                } catch (Exception e) { System.err.println("No se pudo borrar WCS: " + e.getMessage()); }
            }
            
            // Borrar el .ini también si se creó
            try {
                Files.deleteIfExists(iniFile.toPath());
                // A veces ASTAP crea 'astap.ini' en la carpeta local
                Files.deleteIfExists(new File(fitsFile.getParent(), "astap.ini").toPath());
            } catch (Exception e) {}

            return result;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private CelestialPoint parseWcsFile(File wcs) {
        try {
            double ra = 0, dec = 0;
            boolean foundRa = false, foundDec = false;
            
            for (String l : Files.readAllLines(wcs.toPath())) {
                if (l.startsWith("CRVAL1")) {
                    String[] parts = l.split("="); 
                    ra = Double.parseDouble(parts[1].split("/")[0].trim());
                    foundRa = true;
                }
                if (l.startsWith("CRVAL2")) {
                    String[] parts = l.split("=");
                    dec = Double.parseDouble(parts[1].split("/")[0].trim());
                    foundDec = true;
                }
            }
            if (foundRa && foundDec) return new CelestialPoint(ra, dec);
        } catch (Exception e) {}
        return null;
    }
}
