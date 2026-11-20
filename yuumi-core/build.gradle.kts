plugins {
    java
}

group = "kr.yuumi97"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Compile directly against the downloaded plugin jars in ../server/plugins for 100% binary compatibility
    compileOnly(fileTree("../server/plugins") {
        include("VaultUnlocked*.jar", "AuraSkills*.jar", "WorldEdit*.jar", "TAB-*.jar")
    })
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveFileName.set("YuumiLandCore.jar")
}

tasks.register<JavaExec>("coreChecks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("kr.yuumi97.core.CoreChecks")
    jvmArgs("-ea")
}

tasks.test { dependsOn("coreChecks") }
tasks.check { dependsOn("coreChecks") }

tasks.register<JavaExec>("integrationFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("kr.yuumi97.core.IntegrationFixture")
    args(layout.buildDirectory.dir("server-integration").get().asFile.absolutePath)
}

// Compile the pack catalogue for reflection-only tests; never execute PackBuilder.main.
sourceSets.test { java.srcDir("../resourcepack-gen") }

// Test companion is a separate artifact and is never included in the production plugin.
tasks.register<Jar>("integrationProbe") {
    dependsOn(tasks.testClasses)
    archiveFileName.set("CoreProbe.jar")
    destinationDirectory.set(layout.buildDirectory.dir("integration-probe"))
    from(sourceSets["test"].output) { include("kr/yuumi97/core/integration/**") }
    from("src/test/integration/probe-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<JavaExec>("dataPreservation") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("kr.yuumi97.core.DataPreservationCheck")
    args(layout.buildDirectory.file("verification-core/baseline-data.yml").get().asFile.absolutePath,
         layout.buildDirectory.file("server-integration/plugins/YuumiLandCore/data.yml").get().asFile.absolutePath,
         layout.buildDirectory.dir("verification-core/baseline-vaults").get().asFile.absolutePath,
         layout.buildDirectory.dir("server-integration/plugins/YuumiLandCore/vaults").get().asFile.absolutePath)
}

tasks.register<JavaExec>("fallbackFixtures") {
    dependsOn(tasks.jar,tasks.testClasses)
    classpath=sourceSets["test"].runtimeClasspath
    mainClass.set("kr.yuumi97.core.PackFixtures")
    args(layout.buildDirectory.file("libs/YuumiLandCore.jar").get().asFile.absolutePath,
         layout.buildDirectory.file("server-integration/resourcepack/Yuumi97Pack.zip").get().asFile.absolutePath,
         layout.buildDirectory.file("verification-core/YuumiLandCore-fallback.jar").get().asFile.absolutePath,
         layout.buildDirectory.file("verification-core/Yuumi97Pack-core-fallback.zip").get().asFile.absolutePath)
}
