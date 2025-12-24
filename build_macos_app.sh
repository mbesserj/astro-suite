#!/bin/bash

# --- CONFIGURACIÓN ---
APP_NAME="AstroSuite"
APP_VERSION="4.0"
MAIN_JAR="astro-suite-4.0-MODULAR.jar"
MAIN_CLASS="com.astro.main.AppLauncher"
ICON_PATH="AstroSuite.icns" # <--- Icono actualizado

# 1. DEFINIR Y EXPORTAR JAVA_HOME (CRÍTICO PARA MAVEN)
# Usamos tu ruta exacta detectada en los logs
export JAVA_HOME="/Users/maxbesser/NetBeansJDKs/bellsoft-jdk25.0.1+13-macos-aarch64/jdk-25.0.1.jdk"
JPACKAGE="$JAVA_HOME/bin/jpackage"

echo "☕ Usando Java en: $JAVA_HOME"

# --- 2. LIMPIEZA Y COMPILACIÓN ---
echo "🧹 Limpiando y compilando proyecto..."

# Intentamos usar el mvn del sistema. Si falla, intenta usar el de NetBeans.
if command -v mvn &> /dev/null; then
    mvn clean install -DskipTests
else
    echo "⚠️ 'mvn' no encontrado en el PATH global. Intentando ruta de NetBeans..."
    # Ruta alternativa común si usas el Maven dentro de NetBeans
    "/Applications/Apache NetBeans.app/Contents/Resources/netbeans/java/maven/bin/mvn" clean install -DskipTests
fi

if [ $? -ne 0 ]; then
    echo "❌ Error en la compilación Maven. Revisa que JAVA_HOME sea correcto."
    exit 1
fi

# --- 3. PREPARAR CARPETA DE SALIDA (STAGING) ---
echo "📦 Recolectando librerías..."
rm -rf target/staging
mkdir -p target/staging

# Copiar el JAR principal
cp target/$MAIN_JAR target/staging/

# Copiar todas las dependencias
echo "   Copiando dependencias..."
if command -v mvn &> /dev/null; then
    mvn dependency:copy-dependencies -DoutputDirectory=target/staging
else
    "/Applications/Apache NetBeans.app/Contents/Resources/netbeans/java/maven/bin/mvn" dependency:copy-dependencies -DoutputDirectory=target/staging
fi

# --- 4. GENERAR LA APP CON JPACKAGE ---
echo "🍎 Generando AstroSuite.app..."

ICON_OPT=""
if [ -f "$ICON_PATH" ]; then
    ICON_OPT="--icon $ICON_PATH"
    echo "   ✅ Icono detectado: $ICON_PATH"
else
    echo "   ⚠️ AVISO: No se encontró '$ICON_PATH'. Se usará icono por defecto."
fi

"$JPACKAGE" \
  --type app-image \
  --dest target/dist \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --input target/staging \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  $ICON_OPT \
  --java-options "-Xmx4096m" \
  --java-options "--enable-preview" \
  --mac-package-name "AstroSuite" \
  --verbose

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 ¡ÉXITO! Tu aplicación está lista."
    echo "📂 Ubicación: $(pwd)/target/dist/AstroSuite.app"
    echo "🚀 Ve a esa carpeta y ejecútala."
else
    echo "❌ Falló la generación de la App."
fi
