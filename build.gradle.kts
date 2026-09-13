import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}
group = "ai.rever.boss.plugin.dynamic"
version = "0.4.0"
repositories { google(); mavenCentral() }
kotlin { jvmToolchain(17); compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
val apiJar = files(providers.gradleProperty("bossApiJar").getOrElse("libs/boss-plugin-api-1.0.89.jar"))
dependencies {
    compileOnly(apiJar)
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-desktop:1.10.0")
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")
    implementation("com.arkivanov.decompose:decompose:3.3.0")
    implementation("com.arkivanov.essenty:lifecycle:2.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test-junit5"))
    testImplementation(apiJar)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform(); maxParallelForks = 1 }
tasks.processResources {
    filesMatching("**/plugin.json") { expand("version" to project.version) }
}
tasks.register<Jar>("buildPluginJar") {
    archiveFileName.set("boss-plugin-blackout-${project.version}.jar")
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.build { dependsOn("buildPluginJar") }
tasks.register<JavaExec>("runPrototype") {
    group = "application"
    description = "Open the standalone Compose harness."
    classpath = sourceSets.main.get().runtimeClasspath + apiJar
    mainClass.set("ai.rever.boss.plugin.dynamic.blackout.PrototypeKt")
}
// Where this BOSS build loads dynamic plugins from. Override with -PbossPluginDir=...
val bossPluginDir = providers.gradleProperty("bossPluginDir")
    .getOrElse("${System.getProperty("user.home")}/.boss_debug/plugins")

tasks.register<Copy>("installPlugin") {
    group = "distribution"
    description = "Copy the plugin JAR into the BOSS plugins directory. Close BOSS first."
    dependsOn("buildPluginJar")
    from(layout.buildDirectory.file("libs/boss-plugin-blackout-${project.version}.jar"))
    into(bossPluginDir)
    doLast { logger.lifecycle("Installed boss-plugin-blackout-${project.version}.jar into $bossPluginDir") }
}

tasks.register<JavaExec>("smokeUi") {
    group = "verification"
    description = "Render the board headfully for a few seconds and exit, to prove it draws."
    classpath = sourceSets.main.get().runtimeClasspath + apiJar
    mainClass.set("ai.rever.boss.plugin.dynamic.blackout.PrototypeKt")
    systemProperty("blackout.smokeSeconds", providers.gradleProperty("smokeSeconds").getOrElse("6"))
    providers.gradleProperty("capturePath").orNull?.let { systemProperty("blackout.capturePath", it) }
}
