plugins {
    kotlin("jvm") apply true
    jacoco
}

repositories {
    mavenCentral()
}

val exposedVersion: String by project
dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(kotlin("scripting-jsr223"))
    implementation("org.slf4j", "slf4j-api", "1.7.30")

    implementation(platform("org.jetbrains.exposed:exposed-bom:0.38.2"))
    implementation("org.jetbrains.exposed", "exposed-core")
    implementation("org.jetbrains.exposed", "exposed-dao")
    implementation("org.jetbrains.exposed", "exposed-jdbc")
    implementation("org.jetbrains.exposed", "exposed-java-time")

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