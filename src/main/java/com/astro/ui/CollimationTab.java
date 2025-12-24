package com.astro.ui;

import com.astro.model.CollimationData;
import com.astro.service.CollimationService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import java.io.File;

public class CollimationTab {

    private final CollimationService service = new CollimationService();
    
    private Label lblScoreGlobal, lblClass;
    private ProgressBar barScore;
    private TextArea txtDiag;
    private Label lblTL, lblTR, lblC, lblBL, lblBR;
    private Pane pTL, pTR, pC, pBL, pBR;

    public Tab create() {
        Tab tab = new Tab("🩺 Collimation Doctor");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        Button btnLoad = new Button("📸 Cargar FITS para Análisis");
        btnLoad.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        btnLoad.setOnAction(e -> loadFile(root));
        
        VBox topBox = new VBox(10, btnLoad);
        topBox.setAlignment(Pos.CENTER);
        
        lblScoreGlobal = new Label("--");
        lblScoreGlobal.setFont(Font.font("System", FontWeight.BOLD, 40));
        lblClass = new Label("");
        barScore = new ProgressBar(0);
        barScore.setPrefWidth(300);
        
        VBox scoreBox = new VBox(5, new Label("SCORE TOTAL"), lblScoreGlobal, lblClass, barScore);
        scoreBox.setAlignment(Pos.CENTER);
        scoreBox.setStyle("-fx-background-color: #EEE; -fx-padding: 15; -fx-background-radius: 10;");

        GridPane matrix = new GridPane();
        matrix.setAlignment(Pos.CENTER);
        matrix.setHgap(10); matrix.setVgap(10);
        
        pTL = createZone("TL", lblTL = new Label("-"));
        pTR = createZone("TR", lblTR = new Label("-"));
        pC = createZone("CENTER", lblC = new Label("-"));
        pBL = createZone("BL", lblBL = new Label("-"));
        pBR = createZone("BR", lblBR = new Label("-"));

        matrix.add(pTL, 0, 0); matrix.add(pTR, 2, 0);
        matrix.add(pC, 1, 1);
        matrix.add(pBL, 0, 2); matrix.add(pBR, 2, 2);

        VBox centerBox = new VBox(20, scoreBox, matrix);
        centerBox.setAlignment(Pos.CENTER);

        txtDiag = new TextArea();
        txtDiag.setPrefRowCount(5);
        
        root.setTop(topBox);
        root.setCenter(centerBox);
        root.setBottom(new VBox(5, new Label("Diagnóstico:"), txtDiag));

        tab.setContent(root);
        return tab;
    }

    private VBox createZone(String title, Label valLbl) {
        VBox v = new VBox(5, new Label(title), valLbl);
        v.setPrefSize(100, 80);
        v.setAlignment(Pos.CENTER);
        v.setStyle("-fx-border-color: #999; -fx-background-color: #FFF;");
        valLbl.setFont(Font.font("System", FontWeight.BOLD, 16));
        return v;
    }

    private void loadFile(Pane root) {
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(root.getScene().getWindow());
        if(f == null) return;

        new Thread(() -> {
            try {
                CollimationData res = service.analyze(f);
                Platform.runLater(() -> updateUI(res));
            } catch (Exception e) {
                Platform.runLater(() -> txtDiag.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void updateUI(CollimationData d) {
        lblScoreGlobal.setText(String.valueOf(d.scoreTotal));
        lblClass.setText(d.clasificacion);
        barScore.setProgress(d.scoreTotal / 100.0);
        txtDiag.setText(d.diagnostico);
        
        updateZone(pTL, lblTL, d.tl);
        updateZone(pTR, lblTR, d.tr);
        updateZone(pC, lblC, d.center);
        updateZone(pBL, lblBL, d.bl);
        updateZone(pBR, lblBR, d.br);
    }

    private void updateZone(Pane p, Label l, double val) {
        l.setText(String.format("%.2f", val));
        String color = val > 0.90 ? "#C8E6C9" : val > 0.85 ? "#FFF9C4" : "#FFCDD2"; 
        p.setStyle("-fx-background-color: " + color + "; -fx-border-color: #666;");
    }
}
