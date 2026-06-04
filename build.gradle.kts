plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}

group = "com.onexeor.gemos"
version = "0.1.0"

val ktorVersion = "3.3.1"
val logbackVersion = "1.5.21"
val hopliteVersion = "2.9.0"
val flywayVersion = "10.22.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.onexeor.gemos.scheduler.SchedulerMainKt")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.1")
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "192m"
    maxParallelForks = 1
}

tasks.register<JavaExec>("admin") {
    group = "application"
    description = "Run Gem OS admin service"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.onexeor.gemos.admin.AdminMainKt")
}

tasks.register<JavaExec>("providerRouter") {
    group = "application"
    description = "Run Gem OS provider router service"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.onexeor.gemos.provider.ProviderRouterMainKt")
}

tasks.register<JavaExec>("brain") {
    group = "application"
    description = "Run Gem OS brain service"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.onexeor.gemos.brain.BrainMainKt")
}

tasks.register<JavaExec>("schedulerStatus") {
    group = "application"
    description = "Print scheduler status"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.onexeor.gemos.scheduler.SchedulerMainKt")
    args("--status")
}

tasks.register<JavaExec>("asoStub") {
    group = "application"
    description = "Run ASO Fabric stub"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.onexeor.gemos.aso.AsoFabricMainKt")
}
