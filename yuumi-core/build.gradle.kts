plugins {
    java
}

group = "kr.yuumi97"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Compile directly against the downloaded plugin jars in ../server/plugins for 100% binary compatibility
    compileOnly(fileTree("../server/plugins") {
        include("VaultUnlocked*.jar", "AuraSkills*.jar", "WorldEdit*.jar")
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
