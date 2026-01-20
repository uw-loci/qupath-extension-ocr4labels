plugins {
    // To optionally create a shadow/fat jar that bundles up dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

// Configure your extension here
qupathExtension {
    name = "qupath-extension-ocr4labels"
    group = "io.github.michaelsnelson"
    version = "0.1.0-SNAPSHOT"
    description = "A QuPath extension for OCR on slide label images with metadata extraction."
    automaticModule = "io.github.michaelsnelson.extension.ocr4labels"
}

repositories {
    mavenLocal()
    mavenCentral()
}

val javafxVersion = "17.0.2"

dependencies {
    // Main dependencies for QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // Tess4J for OCR (includes Tesseract and Leptonica native bindings)
    implementation("net.sourceforge.tess4j:tess4j:5.11.0")

    // Gson for JSON serialization (template save/load)
    implementation("com.google.code.gson:gson:2.10.1")

    // ZXing for barcode decoding (QR codes, Code128, DataMatrix, etc.)
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation(libs.bundles.logging)
    testImplementation(libs.qupath.fxtras)
    testImplementation("org.openjfx:javafx-base:$javafxVersion")
    testImplementation("org.openjfx:javafx-graphics:$javafxVersion")
    testImplementation("org.openjfx:javafx-controls:$javafxVersion")
}

// For troubleshooting deprecation warnings
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs = listOf(
        "--add-modules", "javafx.base,javafx.graphics,javafx.controls",
        "--add-opens", "javafx.graphics/javafx.stage=ALL-UNNAMED"
    )
}
