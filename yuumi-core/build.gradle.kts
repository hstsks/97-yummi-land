plugins {
    java
}

group = "kr.yuumi97"
version = "3.1.0"

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

tasks.register<JavaExec>("v3Checks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("kr.yuumi97.core.V3Checks")
    jvmArgs("-ea")
}

tasks.check { dependsOn("v3Checks") }

tasks.register<JavaExec>("v31Fixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("kr.yuumi97.core.V31Fixture")
    args(layout.buildDirectory.dir("server-integration").get().asFile.absolutePath)
}
