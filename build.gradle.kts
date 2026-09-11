import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}
group = "ai.rever.boss.plugin.dynamic"
version = "0.2.0"
repositories { google(); mavenCentral() }
kotlin { jvmToolchain(17); compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
val apiJar = files(providers.gradleProperty("bossApiJar").getOrElse("libs/boss-plugin-api-1.0.87.jar"))
dependencies {
    compileOnly(apiJar)
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
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
tasks.register<JavaExec>("balanceReport") {
    group = "verification"
    description = "Play scripted crew against scripted crew and print measured outcomes."
    classpath = sourceSets.main.get().runtimeClasspath + apiJar
    mainClass.set("ai.rever.boss.plugin.dynamic.blackout.engine.BalanceKt")
}
tasks.register<JavaExec>("runPrototype") {
    classpath = sourceSets.main.get().runtimeClasspath + apiJar
    mainClass.set("ai.rever.boss.plugin.dynamic.blackout.PrototypeKt")
}
