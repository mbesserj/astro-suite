package com.astro.ui;

import com.astro.model.AppConfig;
import com.astro.model.CelestialPoint;
import com.astro.model.ImageAnalysisResult;
import com.astro.service.ExternalToolService;
import com.astro.service.ImageProcessingService;
import com.astro.service.SimbadService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import nom.tam.fits.Fits;
import nom.tam.fits.Header;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchProcessorTab {
    
    private final ImageProcessingService imageService = new ImageProcessingService();
    private final SimbadService simbadService = new SimbadService();
    private final ExternalToolService astapService = new ExternalToolService();
    
    private ExecutorService exec; 
    private Task<Void> currentTask;
    
    // UI Controls
    private TextField txtInput;
    private TextField txtRa, txtDec, txtObjectName;
    private TextField txtMinRound, txtMaxFWHM;
    private TextField txtMaxDither;
    private TextField txtMergeTol; 
    private CheckBox chkFWHM, chkAberracion, chkSeeing, chkAutoSort;
    private CheckBox chkAstapVerify;
    private RadioButton rbManual, rbSmart;
    private TextArea logArea;
    private ProgressBar progressBar;
    private Button btnStart, btnStop, btnSearchSim, btnSync;
    private Label lblSmartInfo;

    // Stats UI Controls
    private Label lblStatTotal, lblStatAccepted, lblStatRejected;
    private Label lblStatAvgFwhm, lblStatStdDev, lblStatElite;
    private Label lblStatAvgRound, lblStatWorstRound;
    private Button btnRefreshStats; 
    private VBox statsPanel;

    private static class FrameInfo {
        File file;
        double ra, dec;
        boolean validCoords;
        public FrameInfo(File f, double r, double d, boolean v) { file=f; ra=r; dec=d; validCoords=v; }
    }

    private static class Cluster {
        int id;
        double centerRa, centerDec;
        List<FrameInfo> frames = new ArrayList<>();
        public Cluster(int id, double firstRa, double firstDec) {
            this.id = id; this.centerRa = firstRa; this.centerDec = firstDec;
        }
        public boolean belongs(double r, double d) {
            double dist = calculateDistance(centerRa, centerDec, r, d);
            return dist < 0.1; 
        }
        public void recalculateMedianCenter() {
            if (frames.isEmpty()) return;
            List<Double> ras = new ArrayList<>();
            List<Double> decs = new ArrayList<>();
            for (FrameInfo f : frames) { ras.add(f.ra); decs.add(f.dec); }
            Collections.sort(ras); Collections.sort(decs);
            this.centerRa = ras.get(ras.size()/2);
            this.centerDec = decs.get(decs.size()/2);
        }
        public void merge(Cluster other) {
            this.frames.addAll(other.frames);
            recalculateMedianCenter();
        }
    }
    
    private static double calculateDistance(double ra1, double dec1, double ra2, double dec2) {
        return Math.sqrt(Math.pow((ra1-ra2)*Math.cos(Math.toRadians(dec1)), 2) + Math.pow(dec1-dec2, 2));
    }

    public Tab create() {
        Tab tab = new Tab("Batch Processor");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        
        VBox topContainer = new VBox(10);
        
        HBox folderBox = new HBox(10);
        folderBox.setAlignment(Pos.CENTER_LEFT);
        txtInput = new TextField(); 
        txtInput.setPromptText("Carpeta de imágenes...");
        txtInput.setPrefWidth(350);
        Button btnBrowse = new Button("📂 Seleccionar");
        btnBrowse.setOnAction(e -> browseDir(txtInput));
        folderBox.getChildren().addAll(new Label("Carpeta FITS:"), txtInput, btnBrowse);
        
        VBox coordsGroup = new VBox(8);
        coordsGroup.setStyle("-fx-border-color: #DDD; -fx-padding: 10; -fx-background-radius: 5; -fx-border-radius: 5;");
        Label lblStrategy = new Label("📍 Modo de Procesamiento");
        lblStrategy.setStyle("-fx-font-weight: bold;");
        
        chkAutoSort = new CheckBox("📂 AUTO-CLASIFICAR (Detectar Sesiones)");
        chkAutoSort.setStyle("-fx-font-weight: bold; -fx-text-fill: #1565C0;");
        chkAutoSort.setSelected(true);
        
        ToggleGroup tgMode = new ToggleGroup();
        rbSmart = new RadioButton("Una sola sesión (Promedio)");
        rbSmart.setToggleGroup(tgMode);
        rbManual = new RadioButton("Una sola sesión (Manual)");
        rbManual.setToggleGroup(tgMode);
        rbSmart.setSelected(true); 
        
        HBox manualBox = new HBox(10);
        manualBox.setAlignment(Pos.CENTER_LEFT);
        manualBox.setDisable(true); 
        
        txtObjectName = new TextField("M 45"); txtObjectName.setPrefWidth(60);
        btnSearchSim = new Button("🔍");
        btnSearchSim.setOnAction(e -> buscarEnSimbad());
        txtRa = new TextField("0.000"); txtRa.setPrefWidth(70);
        txtDec = new TextField("0.000"); txtDec.setPrefWidth(70);
        manualBox.getChildren().addAll(new Label("Obj:"), txtObjectName, btnSearchSim, new Label("RA:"), txtRa, new Label("DEC:"), txtDec);
        
        chkAstapVerify = new CheckBox("🛡️ Verificación Rigurosa (100% ASTAP)");
        chkAstapVerify.setTooltip(new Tooltip("Verifica cada imagen. Si el Header miente > 1 grado, se RECHAZA."));
        
        Runnable updateState = () -> {
            boolean sorting = chkAutoSort.isSelected();
            rbSmart.setDisable(sorting);
            rbManual.setDisable(sorting);
            manualBox.setDisable(sorting || rbSmart.isSelected());
        };
        chkAutoSort.setOnAction(e -> updateState.run());
        rbSmart.setOnAction(e -> updateState.run());
        rbManual.setOnAction(e -> updateState.run());

        GridPane tolGrid = new GridPane();
        tolGrid.setHgap(10); tolGrid.setVgap(5);
        txtMaxDither = new TextField("120"); txtMaxDither.setPrefWidth(50);
        txtMergeTol = new TextField("1.0"); txtMergeTol.setPrefWidth(50); 
        tolGrid.add(new Label("Tolerancia Dither (arcsec):"), 0, 0); tolGrid.add(txtMaxDither, 1, 0);
        tolGrid.add(new Label("Radio Fusión (arcmin):"), 0, 1); tolGrid.add(txtMergeTol, 1, 1);

        coordsGroup.getChildren().addAll(lblStrategy, chkAutoSort, new Separator(), new HBox(20, rbSmart, rbManual), manualBox, chkAstapVerify, tolGrid);

        GridPane filtersGrid = new GridPane();
        filtersGrid.setHgap(10); filtersGrid.setVgap(10);
        filtersGrid.setPadding(new Insets(5, 0, 5, 0));
        
        txtMinRound = new TextField("0.80"); txtMinRound.setPrefWidth(50);
        filtersGrid.add(new Label("Min Redondez:"), 0, 0); filtersGrid.add(txtMinRound, 1, 0);
        
        chkFWHM = new CheckBox("Max FWHM:"); chkFWHM.setSelected(true);
        txtMaxFWHM = new TextField(String.format(Locale.US, "%.2f", AppConfig.getFwhmRejectThreshold())); 
        txtMaxFWHM.setPrefWidth(50);
        btnSync = new Button("🔄");
        btnSync.setOnAction(e -> txtMaxFWHM.setText(String.format(Locale.US, "%.2f", AppConfig.getFwhmRejectThreshold())));
        
        filtersGrid.add(chkFWHM, 2, 0); filtersGrid.add(new HBox(5, txtMaxFWHM, btnSync), 3, 0);
        chkAberracion = new CheckBox("Aberración"); chkSeeing = new CheckBox("Log Seeing");
        filtersGrid.add(new HBox(10, chkAberracion, chkSeeing), 4, 0);

        topContainer.getChildren().addAll(folderBox, coordsGroup, new Separator(), filtersGrid);
        
        // --- STATS DASHBOARD ---
        statsPanel = new VBox(10); // Inicialización
        statsPanel.setPadding(new Insets(10));
        statsPanel.setPrefWidth(220);
        statsPanel.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #ccc; -fx-border-width: 0 0 0 1;");
        
        HBox statsHeader = new HBox(10);
        statsHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblTitleStats = new Label("📊 Estadísticas");
        lblTitleStats.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");
        
        btnRefreshStats = new Button("🔄 Analizar");
        btnRefreshStats.setTooltip(new Tooltip("Calcular estadísticas SIN mover archivos (Simulación)"));
        btnRefreshStats.setOnAction(e -> startPreScan(true)); 
        
        statsHeader.getChildren().addAll(lblTitleStats, btnRefreshStats);
        
        lblStatTotal = new Label("Total: -");
        lblStatAccepted = new Label("✅ Aceptadas: -"); lblStatAccepted.setStyle("-fx-text-fill: green;");
        lblStatRejected = new Label("❌ Rechazadas: -"); lblStatRejected.setStyle("-fx-text-fill: red;");
        
        VBox qualBox = new VBox(5);
        qualBox.setStyle("-fx-padding: 8; -fx-background-color: white; -fx-background-radius: 5; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        lblStatAvgFwhm = new Label("Promedio FWHM: -");
        lblStatStdDev = new Label("Desviación σ: -");
        lblStatElite = new Label("💎 Elite (< Avg-2σ): -");
        qualBox.getChildren().addAll(new Label("Calidad (FWHM):"), new Separator(), lblStatAvgFwhm, lblStatStdDev, lblStatElite);
        
        VBox roundBox = new VBox(5);
        roundBox.setStyle("-fx-padding: 8; -fx-background-color: white; -fx-background-radius: 5; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        lblStatAvgRound = new Label("Promedio R: -");
        lblStatWorstRound = new Label("Peor R: -");
        roundBox.getChildren().addAll(new Label("Redondez:"), new Separator(), lblStatAvgRound, lblStatWorstRound);
        
        statsPanel.getChildren().addAll(statsHeader, lblStatTotal, lblStatAccepted, lblStatRejected, new Separator(), qualBox, roundBox);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: 'monospaced'; -fx-font-size: 11px;");
        
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(logArea, statsPanel);
        splitPane.setDividerPositions(0.75);
        
        btnStart = new Button("🚀 INICIAR PROCESAMIENTO");
        btnStart.setStyle("-fx-base: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnStart.setMaxWidth(Double.MAX_VALUE);
        
        btnStop = new Button("🛑 DETENER");
        btnStop.setStyle("-fx-base: #F44336; -fx-text-fill: white; -fx-font-weight: bold;");
        btnStop.setDisable(true);
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        
        root.setTop(topContainer);
        root.setCenter(splitPane);
        root.setBottom(new VBox(5, new HBox(10, btnStart, btnStop), progressBar));
        
        btnStart.setOnAction(e -> startPreScan(false)); 
        btnStop.setOnAction(e -> detenerProceso());
        
        updateState.run();
        tab.setContent(root);
        return tab;
    }

    private void updateFileCountOnly() {
        String path = txtInput.getText();
        if (path != null && !path.isEmpty()) {
            File dir = new File(path);
            if (dir.exists()) {
                File[] fits = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".fits"));
                int count = (fits != null) ? fits.length : 0;
                lblStatTotal.setText("Total: " + count);
            }
        }
    }

    private void detenerProceso() {
        btnStop.setDisable(true);
        if (currentTask != null) currentTask.cancel();
        if (exec != null) exec.shutdownNow();
    }

    private void browseDir(TextField tf) {
        DirectoryChooser dc = new DirectoryChooser();
        File f = dc.showDialog(tf.getScene().getWindow());
        if(f != null) { 
            tf.setText(f.getAbsolutePath());
            updateFileCountOnly(); 
        }
    }
    
    private void buscarEnSimbad() {
        String name = txtObjectName.getText().trim();
        if (name.isEmpty()) { logArea.appendText("⚠️ Ingresa un nombre.\n"); return; }
        logArea.appendText("🔍 Buscando '" + name + "'...\n");
        btnSearchSim.setDisable(true);
        new Thread(() -> {
            try {
                CelestialPoint cp = simbadService.search(name);
                Platform.runLater(() -> {
                    if (cp != null) {
                        txtRa.setText(String.format(Locale.US, "%.5f", cp.ra()));
                        txtDec.setText(String.format(Locale.US, "%.5f", cp.dec()));
                        logArea.appendText("✅ SIMBAD: Encontrado (RA " + String.format("%.4f", cp.ra()) + ")\n");
                    } else logArea.appendText("❌ SIMBAD: No encontrado.\n");
                    btnSearchSim.setDisable(false);
                });
            } catch (Exception e) { Platform.runLater(() -> { logArea.appendText("❌ Error SIMBAD.\n"); btnSearchSim.setDisable(false); }); }
        }).start();
    }

    private void startPreScan(boolean dryRun) {
        String path = txtInput.getText();
        if (path.isEmpty()) { logArea.appendText("⚠️ Selecciona carpeta.\n"); return; }
        File dir = new File(path);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".fits"));
        if (files == null || files.length == 0) { logArea.appendText("⚠️ No hay FITS.\n"); return; }
        
        boolean doSort = chkAutoSort.isSelected();
        if (!doSort) { startProcessing(files, null, dir, dryRun); return; }
        
        double tolMergeArcMin = 1.0;
        try { tolMergeArcMin = Double.parseDouble(txtMergeTol.getText()); } catch(Exception e){}
        final double finalMergeTolDeg = tolMergeArcMin / 60.0; 

        btnStart.setDisable(true); 
        btnRefreshStats.setDisable(true);
        logArea.clear();
        String modeStr = dryRun ? "SIMULACIÓN (Stats)" : "PROCESAMIENTO REAL";
        logArea.appendText("📡 FASE 1: Escaneando grupos... [" + modeStr + "]\n");
        updateFileCountOnly();
        
        Task<List<Cluster>> scanTask = new Task<>() {
            @Override protected List<Cluster> call() throws Exception {
                List<Cluster> clusters = new ArrayList<>();
                int count = 0;
                for (File f : files) {
                    if (isCancelled()) break;
                    double ra = -1, dec = -999;
                    try (Fits fits = new Fits(f)) {
                        Header h = fits.getHDU(0).getHeader();
                        ra = h.getDoubleValue("RA", -1);
                        if (ra == -1) ra = h.getDoubleValue("OBJCTRA", -1);
                        dec = h.getDoubleValue("DEC", -999);
                    } catch (Exception e) {}

                    if (ra != -1 && dec != -999) {
                        FrameInfo info = new FrameInfo(f, ra, dec, true);
                        boolean added = false;
                        for (Cluster c : clusters) {
                            if (c.belongs(ra, dec)) { c.frames.add(info); added = true; break; }
                        }
                        if (!added) {
                            Cluster newC = new Cluster(clusters.size() + 1, ra, dec);
                            newC.frames.add(info);
                            clusters.add(newC);
                        }
                    }
                    updateProgress(++count, files.length);
                }
                for(Cluster c : clusters) c.recalculateMedianCenter();
                
                Platform.runLater(() -> logArea.appendText("🔄 Verificando fusión (Tolerancia: " + txtMergeTol.getText() + "')...\n"));
                List<Cluster> mergedClusters = new ArrayList<>();
                List<Cluster> pending = new ArrayList<>(clusters);
                
                while (!pending.isEmpty()) {
                    Cluster current = pending.remove(0);
                    Iterator<Cluster> it = pending.iterator();
                    while (it.hasNext()) {
                        Cluster other = it.next();
                        double dist = calculateDistance(current.centerRa, current.centerDec, other.centerRa, other.centerDec);
                        if (dist < finalMergeTolDeg) {
                            current.merge(other);
                            it.remove();
                        }
                    }
                    mergedClusters.add(current);
                }
                int newId = 1; for(Cluster c : mergedClusters) c.id = newId++;
                return mergedClusters;
            }
        };
        progressBar.progressProperty().bind(scanTask.progressProperty());
        scanTask.setOnSucceeded(e -> showClusterSelectionDialog(scanTask.getValue(), files, dir, dryRun));
        new Thread(scanTask).start();
    }

    private void showClusterSelectionDialog(List<Cluster> clusters, File[] allFiles, File dir, boolean dryRun) {
        btnStart.setDisable(false);
        btnRefreshStats.setDisable(false);
        
        Dialog<List<Cluster>> dialog = new Dialog<>();
        dialog.setTitle("Selección de Grupos");
        String actionStr = dryRun ? "Analizar (Sin mover)" : "PROCESAR Y MOVER";
        dialog.setHeaderText("Grupos detectados. Selecciona para " + actionStr + ":");
        
        ButtonType processButtonType = new ButtonType(actionStr, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(processButtonType, ButtonType.CANCEL);
        
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        List<CheckBox> checks = new ArrayList<>();
        
        for (Cluster c : clusters) {
            String label = String.format("Grupo %d (RA %.2f / DEC %.2f) - %d imágenes", c.id, c.centerRa, c.centerDec, c.frames.size());
            CheckBox cb = new CheckBox(label);
            if (c.frames.size() < 10) { cb.setText(label + " ⚠️ (Pocas)"); cb.setSelected(false); cb.setStyle("-fx-text-fill: #E65100;"); }
            else { cb.setSelected(true); cb.setStyle("-fx-font-weight: bold;"); }
            cb.setUserData(c);
            checks.add(cb);
            vbox.getChildren().add(cb);
        }
        ScrollPane sp = new ScrollPane(vbox); sp.setFitToWidth(true); sp.setPrefHeight(300);
        dialog.getDialogPane().setContent(sp);
        
        dialog.setResultConverter(b -> {
            if (b == processButtonType) {
                List<Cluster> sel = new ArrayList<>();
                for (CheckBox cb : checks) if (cb.isSelected()) sel.add((Cluster) cb.getUserData());
                return sel;
            }
            return null;
        });
        
        Optional<List<Cluster>> result = dialog.showAndWait();
        result.ifPresent(sel -> { if(!sel.isEmpty()) startProcessing(allFiles, sel, dir, dryRun); });
    }

    private void startProcessing(File[] allFiles, List<Cluster> clustersToProcess, File dir, boolean dryRun) {
        final double minR = Double.parseDouble(txtMinRound.getText());
        final boolean useFwhm = chkFWHM.isSelected();
        final double maxFwhm = Double.parseDouble(txtMaxFWHM.getText());
        final boolean useAb = chkAberracion.isSelected(); 
        final boolean useSee = chkSeeing.isSelected();     
        final double ditherTolArcsec = Double.parseDouble(txtMaxDither.getText());
        final boolean doSort = chkAutoSort.isSelected();
        final boolean isSmartMode = rbSmart.isSelected();
        final boolean deepVerify = chkAstapVerify.isSelected();
        
        double mR=0, mD=0;
        try { mR=Double.parseDouble(txtRa.getText()); mD=Double.parseDouble(txtDec.getText()); } catch(Exception e){}
        final double finalManualRa = mR; final double finalManualDec = mD;

        List<FrameInfo> framesToProcess = new ArrayList<>();
        if (doSort && clustersToProcess != null) {
            logArea.appendText("✅ " + (dryRun ? "Simulando" : "Procesando") + " " + clustersToProcess.size() + " grupos.\n");
            for (Cluster c : clustersToProcess) framesToProcess.addAll(c.frames);
        } else {
            for (File f : allFiles) framesToProcess.add(new FrameInfo(f, 0,0, true));
        }
        
        final List<FrameInfo> workload = framesToProcess;
        final List<Cluster> refClusters = clustersToProcess; 
        final AtomicBoolean panicStop = new AtomicBoolean(false);
        final AtomicInteger suspicionLevel = new AtomicInteger(0);

        if (!dryRun) new File(dir.getAbsolutePath() + "/Rechazadas").mkdirs();
        btnStart.setDisable(true); btnStop.setDisable(false); btnRefreshStats.setDisable(true);
        
        Platform.runLater(() -> {
            lblStatAccepted.setText("✅ Aceptadas: 0");
            lblStatRejected.setText("❌ Rechazadas: 0");
            lblStatAvgFwhm.setText("Promedio FWHM: -");
            lblStatStdDev.setText("Desviación σ: -");
            lblStatElite.setText("💎 Elite: -");
            lblStatAvgRound.setText("Promedio R: -");
            lblStatWorstRound.setText("Peor R: -");
        });

        currentTask = new Task<>() {
            @Override protected Void call() throws Exception {
                Platform.runLater(() -> logArea.appendText("🔬 FASE 2: Analizando " + workload.size() + " imágenes...\n"));
                exec = Executors.newFixedThreadPool(4); 
                AtomicInteger processed = new AtomicInteger(0);
                
                List<Double> allFwhm = Collections.synchronizedList(new ArrayList<>());
                List<Double> allRoundness = Collections.synchronizedList(new ArrayList<>());
                AtomicInteger countAccepted = new AtomicInteger(0);
                AtomicInteger countRejected = new AtomicInteger(0);

                for (FrameInfo info : workload) {
                    if (isCancelled() || exec.isShutdown() || panicStop.get()) break;
                    exec.submit(() -> {
                        if (panicStop.get()) return;
                        try {
                            String logMsg = "";
                            boolean reject = false;
                            boolean headerError = false;
                            String destFolder = ""; 
                            double targetRa = 0, targetDec = 0;
                            
                            if (!doSort) { try (Fits fits = new Fits(info.file)) { /* legacy */ } catch(Exception e){} }

                            int currentSuspicion = suspicionLevel.get();
                            boolean forceCheck = deepVerify || (currentSuspicion > 0) || (Math.random() < 0.05); 
                            boolean verifiedByAstap = false;
                            
                            if (forceCheck && info.validCoords) {
                                CelestialPoint solved = astapService.solvePlate(info.file, "");
                                if (solved != null) {
                                    double distCheck = calculateDistance(info.ra, info.dec, solved.ra(), solved.dec());
                                    if (distCheck > 1.0) { 
                                        logMsg = String.format("❌ HEADER FALSO (Desvío %.1f°)", distCheck);
                                        reject = true; headerError = true; suspicionLevel.set(5); 
                                    } else {
                                        info.ra = solved.ra(); info.dec = solved.dec();
                                        verifiedByAstap = true;
                                        if (currentSuspicion > 0) suspicionLevel.decrementAndGet();
                                    }
                                }
                            }

                            if (!reject) {
                                if (doSort && refClusters != null) {
                                    for (Cluster c : refClusters) {
                                        if (c.frames.contains(info)) { 
                                            targetRa = c.centerRa; targetDec = c.centerDec;
                                            destFolder = String.format("%s/Sesion_%d", dir.getAbsolutePath(), c.id);
                                            break; 
                                        }
                                    }
                                } else { targetRa = finalManualRa; targetDec = finalManualDec; }
                                if (destFolder.isEmpty() && doSort) { reject=true; logMsg="❌ NO ASIGNADA"; }
                                else if (!destFolder.isEmpty() && !dryRun) new File(destFolder).mkdirs();
                            }

                            if (!reject && info.validCoords && targetRa != 0) {
                                double distArcSec = calculateDistance(info.ra, info.dec, targetRa, targetDec) * 3600.0;
                                if (distArcSec > ditherTolArcsec) {
                                    logMsg = String.format("❌ DESCENTRADA (%.1f\")", distArcSec);
                                    reject = true;
                                }
                            }

                            ImageAnalysisResult q = null;
                            if (!reject) {
                                q = imageService.analyze(info.file, useFwhm, useAb, useSee);
                                if (q.starCount < 20) { logMsg = "❌ NUBES"; reject=true; }
                                else if (q.roundness < minR) { logMsg = String.format("❌ TRAZAS (R:%.3f)", q.roundness); reject=true; }
                                else if (useFwhm && q.fwhm > maxFwhm) { logMsg = String.format("❌ FWHM (%.2f)", q.fwhm); reject=true; }
                                else {
                                    String extra = "";
                                    if (verifiedByAstap) extra = " [🔍]";
                                    String gTag = doSort ? destFolder.substring(destFolder.lastIndexOf("_")+1) : "Ref";
                                    logMsg = String.format("✅ OK (G%s)%s | R:%.3f | F:%.2f", gTag, extra, q.roundness, q.fwhm);
                                }
                            }
                            
                            // --- STATS LOGIC UPDATE: COLLECT ALL RAW DATA IF ANALYSIS SUCCEEDED ---
                            if (q != null) {
                                allFwhm.add(q.fwhm);
                                allRoundness.add(q.roundness);
                            }
                            
                            if (reject) countRejected.incrementAndGet();
                            else countAccepted.incrementAndGet();
                            
                            if (processed.get() % 5 == 0 || processed.get() == workload.size()) {
                                updateStatsUI(countAccepted.get(), countRejected.get(), new ArrayList<>(allFwhm), new ArrayList<>(allRoundness));
                            }

                            if (!dryRun) { 
                                if (reject) {
                                    String sub = headerError ? "Error_Geometria" : "Rechazadas";
                                    String base = (doSort && !destFolder.isEmpty()) ? destFolder : dir.getAbsolutePath();
                                    String finalPath = base + "/" + sub;
                                    new File(finalPath).mkdirs();
                                    mover(info.file, finalPath);
                                } else if (doSort) {
                                    mover(info.file, destFolder);
                                }
                            } else {
                                logMsg = "[SIM] " + logMsg;
                            }

                            final String msg = logMsg;
                            Platform.runLater(() -> logArea.appendText(info.file.getName() + " -> " + msg + "\n"));
                            
                        } catch (Exception ex) { ex.printStackTrace(); }
                        finally { updateProgress(processed.incrementAndGet(), workload.size()); }
                    });
                }
                exec.shutdown();
                exec.awaitTermination(24, TimeUnit.HOURS);
                return null;
            }
        };
        progressBar.progressProperty().bind(currentTask.progressProperty());
        currentTask.setOnSucceeded(e -> { 
            if(!panicStop.get()) { 
                btnStart.setDisable(false); 
                btnStop.setDisable(true); 
                btnRefreshStats.setDisable(false);
                logArea.appendText("🏁 FIN " + (dryRun ? "SIMULACIÓN" : "PROCESO") + ".\n"); 
            }
        });
        new Thread(currentTask).start();
    }
    
    private void updateStatsUI(int accepted, int rejected, List<Double> fwhms, List<Double> rounds) {
        Platform.runLater(() -> {
            lblStatAccepted.setText("✅ Aceptadas: " + accepted);
            lblStatRejected.setText("❌ Rechazadas: " + rejected);
            
            if (!fwhms.isEmpty()) {
                double sum = 0; for(double d : fwhms) sum+=d;
                double avg = sum / fwhms.size();
                double sumSq = 0; for(double d : fwhms) sumSq += Math.pow(d - avg, 2);
                double stdDev = Math.sqrt(sumSq / fwhms.size());
                long eliteCount = fwhms.stream().filter(v -> v < (avg - 2*stdDev)).count();
                
                lblStatAvgFwhm.setText(String.format("Promedio FWHM: %.2f", avg));
                lblStatStdDev.setText(String.format("Desviación σ: %.2f", stdDev));
                lblStatElite.setText("💎 Elite (<" + String.format("%.2f", avg-2*stdDev) + "): " + eliteCount);
            }
            if (!rounds.isEmpty()) {
                double avgR = rounds.stream().mapToDouble(d->d).average().orElse(0);
                double minR = rounds.stream().mapToDouble(d->d).min().orElse(0);
                lblStatAvgRound.setText(String.format("Promedio R: %.3f", avgR));
                lblStatWorstRound.setText(String.format("Peor R: %.3f", minR));
            }
        });
    }
    
    private void mover(File f, String d) { try{Files.move(f.toPath(), Paths.get(d,f.getName()), StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){} }
}
