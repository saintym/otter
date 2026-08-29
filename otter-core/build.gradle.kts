plugins {
    kotlin("jvm") apply true
    jacoco
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(kotlin("scripting-jsr223"))
    implementation("org.slf4j", "slf4j-api", "1.7.30")

    // Coroutines support (for async operations)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    // Connection pooling (required for adapters)
    implementation("com.zaxxer:HikariCP:5.1.0")

    // JDBC driver support (for database adapters)
    // Note: Exposed dependencies have been completely removed as part of the refactoring
    // The project now uses a custom DatabaseAdapter pattern instead

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("com.h2database:h2:1.4.200")
    testImplementation("org.postgresql:postgresql:42.3.1")
    testImplementation("ch.qos.logback:logback-classic:1.2.5")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(false)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }
}

jacoco {
    toolVersion = "0.8.7"
}