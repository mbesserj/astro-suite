package com.astro.model;

import java.util.prefs.Preferences;

public class AppConfig {
    private static final Preferences prefs = Preferences.userNodeForPackage(AppConfig.class);

    // Claves existentes
    private static final String KEY_CAMERA = "camera_name";
    private static final String KEY_PIXEL = "pixel_size";
    private static final String KEY_FOCAL = "focal_length";
    private static final String KEY_SCALE = "pixel_scale";
    private static final String KEY_FWHM_TH = "fwhm_threshold";
    
    // NUEVAS CLAVES PARA ASTAP
    private static final String KEY_ASTAP_PATH = "astap_path";
    private static final String KEY_ASTAP_DB = "astap_db_path";

    // --- GETTERS & SETTERS EXISTING ---
    public static String getCameraName() { return prefs.get(KEY_CAMERA, "ASI2600MM (3.76μm)"); }
    public static void setCameraName(String v) { prefs.put(KEY_CAMERA, v); }

    public static double getPixelSize() { return prefs.getDouble(KEY_PIXEL, 3.76); }
    public static void setPixelSize(double v) { prefs.putDouble(KEY_PIXEL, v); }

    public static double getFocalLength() { return prefs.getDouble(KEY_FOCAL, 500.0); }
    public static void setFocalLength(double v) { prefs.putDouble(KEY_FOCAL, v); }
    
    public static double getPixelScale() { return prefs.getDouble(KEY_SCALE, 1.55); }
    public static void setPixelScale(double v) { prefs.putDouble(KEY_SCALE, v); }

    public static double getFwhmRejectThreshold() { return prefs.getDouble(KEY_FWHM_TH, 3.5); }
    public static void setFwhmRejectThreshold(double v) { prefs.putDouble(KEY_FWHM_TH, v); }

    // --- NUEVOS GETTERS & SETTERS ASTAP ---
    public static String getAstapPath() {
        // Default Mac Path
        return prefs.get(KEY_ASTAP_PATH, "/Applications/Astap.app/Contents/MacOS/astap");
    }
    public static void setAstapPath(String v) { prefs.put(KEY_ASTAP_PATH, v); }

    public static String getAstapDbPath() {
        return prefs.get(KEY_ASTAP_DB, ""); // Sin default, usuario debe elegir
    }
    public static void setAstapDbPath(String v) { prefs.put(KEY_ASTAP_DB, v); }
}
