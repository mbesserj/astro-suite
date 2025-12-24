package com.astro.ui;

import com.astro.model.ExposureResult;
import com.astro.model.ImageAnalysisResult;
import com.astro.service.ExposureCalculatorService;
import com.astro.service.FitsHeaderService;
import com.astro.service.ImageProcessingService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import java.io.File;

public class ImageAnalysisTab {

    private final ImageProcessingService imageService = new ImageProcessingService();
    private final FitsHeaderService headerService = new FitsHeaderService();
    private final ExposureCalculatorService exposureService = new ExposureCalculatorService();

    private Label lblFilename;
    private Label valFwhm, valSeeing, valSnr, valRoundness, valBackground;
    private TextArea txtExposureReport;
    private Label lblRecommendedTime; 
    private Pane pnlExposureStatus;

    public Tab create() {
        Tab tab = new Tab("🔬 Image Lab");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        // HEADER
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        Label title = new Label("Laboratorio de Imagen");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        Button btnLoad = new Button("📂 Cargar FITS");
        btnLoad.setStyle("-fx-base: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        btnLoad.setOnAction(e -> analyzeImage(root));
        lblFilename = new Label("...");
        header.getChildren().addAll(title, btnLoad, lblFilename);

        // GRID DATOS (Visualización rápida)
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(20); statsGrid.setVgap(15);
        statsGrid.setPadding(new Insets(15));
        statsGrid.setStyle("-fx-border-color: #DDD; -fx-border-radius: 8; -fx-background-color: #FAFAFA;");
        statsGrid.setAlignment(Pos.CENTER);

        valFwhm = createStatCard(statsGrid, "FWHM (px)", 0, 0);
        valSeeing = createStatCard(statsGrid, "Seeing (\")", 1, 0);
        valSnr = createStatCard(statsGrid, "SNR", 2, 0);
        valRoundness = createStatCard(statsGrid, "Redondez", 0, 1);
        valBackground = createStatCard(statsGrid, "Fondo (ADU)", 1, 1);

        // BOX EXPOSICIÓN Y REPORTE
        VBox expBox = new VBox(10);
        expBox.setPadding(new Insets(15));
        expBox.setStyle("-fx-border-color: #66BB6A; -fx-border-radius: 8; -fx-background-color: #F1F8E9;");
        expBox.setAlignment(Pos.CENTER);
        
        Label lblExpTitle = new Label("⏱️ Calculadora de Tiempo Exacto");
        lblExpTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        lblRecommendedTime = new Label("-- seg");
        lblRecommendedTime.setFont(Font.font("System", FontWeight.BOLD, 36));
        lblRecommendedTime.setStyle("-fx-text-fill: #2E7D32;");
        
        Label lblSubRec = new Label("Tiempo sugerido para Swamp Factor 10x");
        lblSubRec.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        // Área de texto ampliada para el reporte completo
        txtExposureReport = new TextArea();
        txtExposureReport.setEditable(false);
        txtExposureReport.setPrefRowCount(8); // Más espacio
        txtExposureReport.setStyle("-fx-font-family: 'monospaced'; -fx-font-size: 11px;");
        
        pnlExposureStatus = new Pane();
        pnlExposureStatus.setPrefHeight(6);
        pnlExposureStatus.setStyle("-fx-background-color: #CCC;");

        expBox.getChildren().addAll(lblExpTitle, lblRecommendedTime, lblSubRec, txtExposureReport, pnlExposureStatus);

        VBox centerLayout = new VBox(20, statsGrid, expBox);
        centerLayout.setMaxWidth(800);
        centerLayout.setAlignment(Pos.TOP_CENTER);
        centerLayout.setPadding(new Insets(20,0,0,0));

        root.setTop(header);
        root.setCenter(centerLayout);
        tab.setContent(root);
        return tab;
    }

    private Label createStatCard(GridPane grid, String title, int col, int row) {
        VBox box = new VBox(5, new Label(title), new Label("--"));
        box.setAlignment(Pos.CENTER); box.setPrefWidth(120);
        ((Label)box.getChildren().get(0)).setStyle("-fx-text-fill: #666; -fx-font-size: 10px;");
        ((Label)box.getChildren().get(1)).setFont(Font.font("System", FontWeight.BOLD, 16));
        grid.add(box, col, row);
        return (Label)box.getChildren().get(1);
    }

    private void analyzeImage(Pane root) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("FITS", "*.fits", "*.fit"));
        File f = fc.showOpenDialog(root.getScene().getWindow());
        if (f == null) return;

        lblFilename.setText("Analizando...");
        
        new Thread(() -> {
            try {
                ImageAnalysisResult res = imageService.analyze(f, true, true, true);
                FitsHeaderService.FitsMetadata meta = headerService.readHeader(f);
                ExposureResult expRes = exposureService.calculate(meta, res.skyBackground);

                Platform.runLater(() -> {
                    lblFilename.setText(f.getName());
                    valFwhm.setText(String.format("%.2f", res.fwhm));
                    valSeeing.setText(String.format("%.2f\"", res.seeing));
                    valSnr.setText(String.format("%.1f", res.snr));
                    valRoundness.setText(String.format("%.3f", res.roundness));
                    valBackground.setText(String.format("%.0f", res.skyBackground));
                    
                    lblRecommendedTime.setText(String.format("%.1f s", expRes.exactTargetTime));
                    
                    // --- CONSTRUCCIÓN DEL REPORTE COMPLETO ---
                    StringBuilder sb = new StringBuilder();
                    
                    // 1. Diagnóstico de Exposición
                    sb.append(expRes.message).append("\n");
                    sb.append("--------------------------------------------------\n");
                    
                    // 2. Datos de Cámara y Señal
                    sb.append(String.format("📸 ADQ: Exp: %.1fs | Gain: %.0f | Fondo: %.0f ADU\n", 
                            meta.exposureTime, meta.gain, res.skyBackground));
                    sb.append(String.format("🔌 SEN: ReadNoise: %.2fe- | SkySignal: %.2fe-\n", 
                            expRes.readNoise, expRes.skyElectrons));
                    
                    sb.append("\n"); // Separador
                    
                    // 3. Calidad de Imagen (LO NUEVO)
                    sb.append(String.format("📊 CALIDAD: FWHM: %.2f px | Seeing: %.2f\"\n", 
                            res.fwhm, res.seeing));
                    sb.append(String.format("✨ SEÑAL:   SNR: %.1f    | Estrellas: %d\n", 
                            res.snr, res.starCount));
                    sb.append(String.format("⭕ FORMA:   Redondez: %.3f", 
                            res.roundness));
                    
                    if (res.hasAberration) sb.append(" [⚠️ ABERRACIÓN DETECTADA]");

                    txtExposureReport.setText(sb.toString());
                    
                    // Color de estado
                    String color = "#4CAF50"; 
                    if (expRes.status == ExposureResult.Status.UNDER_EXPOSED) {
                        color = "#D32F2F"; 
                        lblRecommendedTime.setStyle("-fx-text-fill: #D32F2F;");
                    } else if (expRes.status == ExposureResult.Status.OVER_EXPOSED) {
                        color = "#F57C00"; 
                        lblRecommendedTime.setStyle("-fx-text-fill: #F57C00;");
                    } else {
                        lblRecommendedTime.setStyle("-fx-text-fill: #388E3C;");
                    }
                    pnlExposureStatus.setStyle("-fx-background-color: " + color + ";");
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}
