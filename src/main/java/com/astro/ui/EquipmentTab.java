package com.astro.ui;

import com.astro.model.AppConfig;
import com.astro.model.ImageAnalysisResult;
import com.astro.service.ImageProcessingService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.List;
import java.util.DoubleSummaryStatistics;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EquipmentTab {

    private final ImageProcessingService imageService = new ImageProcessingService();
    
    // UI Controls
    private ComboBox<String> cmbCamera;
    private ComboBox<String> cmbStrictness;
    private TextField txtFocal, txtPixelSize;
    private TextField txtAstapPath, txtAstapDb; // Nuevos controles
    private Label lblResults, lblCalibInfo;
    private Button btnCalibrate;
    private ProgressBar progressCalib;

    public Tab create() {
        Tab tab = new Tab("⚙️ Configuración");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        VBox content = new VBox(20);
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(700); // Un poco más ancho

        Label title = new Label("🔭 Configuración Global");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));

        // --- 1. CONFIGURACIÓN ÓPTICA ---
        GridPane gridOpt = new GridPane();
        gridOpt.setHgap(15); gridOpt.setVgap(15);
        gridOpt.setStyle("-fx-border-color: #CCC; -fx-padding: 15; -fx-background-color: #F9F9F9; -fx-background-radius: 5;");

        cmbCamera = new ComboBox<>();
        cmbCamera.getItems().addAll("ASI2600MM (3.76μm)", "ASI294MM (4.63μm)", "ASI183MM (2.4μm)", "QHY600M (3.76μm)");
        cmbCamera.setValue(AppConfig.getCameraName());
        cmbCamera.setOnAction(e -> updatePixelFromCombo());

        txtPixelSize = new TextField(String.valueOf(AppConfig.getPixelSize()));
        txtFocal = new TextField(String.valueOf(AppConfig.getFocalLength()));
        
        gridOpt.add(new Label("Cámara:"), 0, 0); gridOpt.add(cmbCamera, 1, 0);
        gridOpt.add(new Label("Pixel Size (μm):"), 0, 1); gridOpt.add(txtPixelSize, 1, 1);
        gridOpt.add(new Label("Distancia Focal (mm):"), 0, 2); gridOpt.add(txtFocal, 1, 2);

        // --- 2. CONFIGURACIÓN ASTAP (NUEVO BLOQUE) ---
        VBox astapBox = new VBox(10);
        astapBox.setStyle("-fx-border-color: #FF9800; -fx-border-width: 1; -fx-padding: 15; -fx-background-radius: 5; -fx-background-color: #FFF3E0;");
        Label lblAstap = new Label("🗺️ Configuración Plate Solving (ASTAP)");
        lblAstap.setFont(Font.font("System", FontWeight.BOLD, 13));

        GridPane gridAstap = new GridPane();
        gridAstap.setHgap(10); gridAstap.setVgap(10);

        txtAstapPath = new TextField(AppConfig.getAstapPath());
        txtAstapPath.setPromptText("Ruta al ejecutable ASTAP");
        txtAstapPath.setPrefWidth(300);
        Button btnFindAstap = new Button("📂 App");
        btnFindAstap.setOnAction(e -> browseFile(txtAstapPath));

        txtAstapDb = new TextField(AppConfig.getAstapDbPath());
        txtAstapDb.setPromptText("Carpeta de Base de Datos (g17/g18/h18)");
        txtAstapDb.setPrefWidth(300);
        Button btnFindDb = new Button("📂 DB");
        btnFindDb.setOnAction(e -> browseDir(txtAstapDb));

        gridAstap.add(new Label("Programa ASTAP:"), 0, 0); gridAstap.add(txtAstapPath, 1, 0); gridAstap.add(btnFindAstap, 2, 0);
        gridAstap.add(new Label("Base de Datos:"), 0, 1); gridAstap.add(txtAstapDb, 1, 1); gridAstap.add(btnFindDb, 2, 1);
        
        astapBox.getChildren().addAll(lblAstap, gridAstap);

        // --- 3. CALIBRACIÓN SEEING ---
        VBox calibBox = new VBox(10);
        calibBox.setStyle("-fx-border-color: #2196F3; -fx-border-width: 1; -fx-padding: 15; -fx-background-radius: 5; -fx-background-color: #E3F2FD;");
        calibBox.setAlignment(Pos.CENTER);
        
        Label lblCalibTitle = new Label("🎯 Calibrar Umbral de Rechazo (Batch)");
        lblCalibTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
        
        HBox strictnessBox = new HBox(10);
        strictnessBox.setAlignment(Pos.CENTER);
        Label lblStrict = new Label("Nivel Exigencia:");
        cmbStrictness = new ComboBox<>();
        cmbStrictness.getItems().addAll("Permisivo (2.5%)", "Estándar (16%)", "Estricto (30%)", "Elite (50%)");
        cmbStrictness.getSelectionModel().select(1);
        strictnessBox.getChildren().addAll(lblStrict, cmbStrictness);

        btnCalibrate = new Button("📸 Analizar Muestras");
        btnCalibrate.setOnAction(e -> calibrateFromSamples(root));
        progressCalib = new ProgressBar(0);
        progressCalib.setVisible(false);
        progressCalib.setMaxWidth(Double.MAX_VALUE);
        lblCalibInfo = new Label("Calcula el FWHM promedio.");
        lblCalibInfo.setStyle("-fx-font-size: 11px;");

        calibBox.getChildren().addAll(lblCalibTitle, strictnessBox, btnCalibrate, progressCalib, lblCalibInfo);

        // --- FOOTER ---
        lblResults = new Label("Umbral Batch: " + String.format("%.2f", AppConfig.getFwhmRejectThreshold()) + " px");
        lblResults.setStyle("-fx-font-weight: bold; -fx-text-fill: #2E7D32;");
        
        Button btnSave = new Button("💾 Guardar TODO");
        btnSave.setStyle("-fx-base: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnSave.setMaxWidth(Double.MAX_VALUE);
        btnSave.setOnAction(e -> saveAllConfig());

        content.getChildren().addAll(title, gridOpt, astapBox, calibBox, btnSave, lblResults);
        
        // Scroll pane por si la pantalla es chica
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent;");
        
        root.setCenter(scroll);
        tab.setContent(root);
        return tab;
    }

    private void updatePixelFromCombo() {
        String s = cmbCamera.getValue();
        if (s.contains("3.76")) txtPixelSize.setText("3.76");
        else if (s.contains("4.63")) txtPixelSize.setText("4.63");
        else if (s.contains("2.4")) txtPixelSize.setText("2.4");
    }

    private void browseFile(TextField tf) {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(tf.getScene().getWindow());
        if(f != null) tf.setText(f.getAbsolutePath());
    }

    private void browseDir(TextField tf) {
        DirectoryChooser dc = new DirectoryChooser();
        File f = dc.showDialog(tf.getScene().getWindow());
        if(f != null) tf.setText(f.getAbsolutePath());
    }

    private void saveAllConfig() {
        try {
            double pix = Double.parseDouble(txtPixelSize.getText());
            double foc = Double.parseDouble(txtFocal.getText());
            double scale = 206.265 * pix / foc;
            
            // Guardar Óptica
            AppConfig.setCameraName(cmbCamera.getValue());
            AppConfig.setPixelSize(pix);
            AppConfig.setFocalLength(foc);
            AppConfig.setPixelScale(scale);
            
            // Guardar ASTAP
            AppConfig.setAstapPath(txtAstapPath.getText());
            AppConfig.setAstapDbPath(txtAstapDb.getText());

            lblResults.setText(String.format("✅ Configuración Guardada (Escala: %.2f \"/px)", scale));
        } catch (Exception e) { lblResults.setText("❌ Error en números"); }
    }

    private void calibrateFromSamples(Pane root) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("FITS", "*.fits", "*.fit"));
        List<File> files = fc.showOpenMultipleDialog(root.getScene().getWindow());
        if (files == null || files.isEmpty()) return;
        
        btnCalibrate.setDisable(true); progressCalib.setVisible(true); progressCalib.setProgress(-1);
        lblCalibInfo.setText("Analizando...");

        new Thread(() -> {
            // Recalcular parámetros temporales por si no han guardado
            double pix = Double.parseDouble(txtPixelSize.getText());
            double foc = Double.parseDouble(txtFocal.getText());
            double scale = 206.265 * pix / foc;
            AppConfig.setPixelScale(scale); // Necesario para el servicio

            AtomicInteger processedCount = new AtomicInteger(0);
            int total = files.size();

            List<Double> vals = files.parallelStream()
                .map(f -> {
                    try {
                        Platform.runLater(() -> progressCalib.setProgress((double)processedCount.incrementAndGet()/total));
                        ImageAnalysisResult res = imageService.analyze(f, true, false, false);
                        if (res.fwhm > 0 && res.fwhm < 50) return res.fwhm;
                        return -1.0;
                    } catch (Exception e) { return -1.0; }
                })
                .filter(v -> v > 0)
                .collect(Collectors.toList());

            Platform.runLater(() -> {
                if (!vals.isEmpty()) {
                    DoubleSummaryStatistics stats = vals.stream().mapToDouble(d->d).summaryStatistics();
                    double avg = stats.getAverage();
                    double stdDev = Math.sqrt(vals.stream().mapToDouble(v -> Math.pow(v-avg, 2)).average().orElse(0));
                    
                    int sel = cmbStrictness.getSelectionModel().getSelectedIndex();
                    double sigma = (sel == 0) ? 2.0 : (sel == 1) ? 1.0 : (sel == 2) ? 0.5 : 0.0;
                    double th = Math.max(avg, avg + (sigma * stdDev));

                    // Guardamos el umbral calculado
                    AppConfig.setFwhmRejectThreshold(th);
                    
                    lblResults.setText(String.format("✅ CALIBRADO: Rechazar > %.2f px", th));
                    lblCalibInfo.setText(String.format("Media: %.2f px | σ: %.2f", avg, stdDev));
                }
                btnCalibrate.setDisable(false); progressCalib.setVisible(false);
            });
        }).start();
    }
}
