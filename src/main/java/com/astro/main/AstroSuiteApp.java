package com.astro.main;

import com.astro.ui.BatchProcessorTab;
import com.astro.ui.CollimationTab;
import com.astro.ui.EquipmentTab;
import com.astro.ui.ImageAnalysisTab; // Nueva
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class AstroSuiteApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("🔭 AstroSuite v4.5 (Full Modular M1)");

        TabPane tabPane = new TabPane();
        
        // ORDEN DE PESTAÑAS
        tabPane.getTabs().add(new EquipmentTab().create());     // 1. Configurar
        tabPane.getTabs().add(new ImageAnalysisTab().create()); // 2. Analizar UNA imagen (NUEVO)
        tabPane.getTabs().add(new BatchProcessorTab().create());// 3. Procesar LOTE
        tabPane.getTabs().add(new CollimationTab().create());   // 4. Diagnóstico Óptico
        
        Scene scene = new Scene(tabPane, 1024, 850);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
