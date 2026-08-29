plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "io.github.goodgoodjm.otter"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Core module
    api(project(":otter-core"))

    // Kotlin
    implementation(kotlin("stdlib"))

    // PostgreSQL JDBC Driver
    implementation("org.postgresql:postgresql:42.7.0")

    // HikariCP connection pool
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("Otter PostgreSQL Adapter")
                description.set("PostgreSQL adapter for Otter database migration tool")
                url.set("https://github.com/GoodGoodJM/Otter")
                
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("goodgoodjm")
                        name.set("GoodGoodJM")
                    }
                }
            }
        }
    }
}