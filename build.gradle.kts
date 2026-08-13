import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.5.10"
    id("org.cyclonedx.bom") version "3.4.1"
    checkstyle
    jacoco
}

group   = "com.agentforge"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val azureOpenAiVersion = "1.0.0-beta.16"
val resilience4jVersion = "2.4.0"
extra["commons-lang3.version"] = "3.20.0"

// ── Security dependency floors (override Spring Boot-managed versions) ──
// Pin patched releases of shipped transitives to clear known HIGH advisories
// surfaced by the Trivy vulnerability gate. All are drop-in patch/minor bumps
// on the versions Spring Boot's BOM would otherwise select.
extra["netty.version"] = "4.1.136.Final"      // CVE-2026-55831/55833/56745/56819/59901
extra["postgresql.version"] = "42.7.12"        // CVE-2026-54291

val testcontainersVersion = "1.20.6"
val wiremockVersion = "3.13.2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // ── Security: OAuth2/OIDC JWT resource server ──
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // ── Persistence: PostgreSQL + Flyway + JPA ──
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // ── Distributed coordination / cache: Redis ──
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("com.azure:azure-ai-openai:$azureOpenAiVersion")

    implementation("io.github.resilience4j:resilience4j-spring-boot3:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // logstash-logback-encoder 9.x pulls Jackson 3.x (tools.jackson); pin the
    // Jackson 3 BOM to a patched release (CVE-2026-29062/54512/54513, GHSA-r7wm-3cxj-wff9).
    implementation(platform("tools.jackson:jackson-bom:3.1.4"))
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ── Observability: OpenTelemetry tracing via the Micrometer bridge ──
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("com.h2database:h2")

    // ── Optional real-infrastructure integration tests (skipped when Docker is absent) ──
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")

    // ── Deterministic mock HTTP server for live-adapter contract tests ──
    // Standalone (shaded) build avoids Jetty/Jackson version clashes with Spring Boot.
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // The second flag is test-only: WireMock's self-signed TLS cert is not issued
    // for "localhost", and the JDK HttpClient reads this property once at startup.
    // It relaxes hostname verification for the in-JVM mock server in the live-adapter
    // contract tests; production adapter clients use real trust stores and are
    // unaffected (the flag is never set outside the test JVM).
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Djdk.internal.httpclient.disableHostnameVerification=true")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("${project.name}-${project.version}.jar")
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

spotbugs {
    ignoreFailures.set(false)
    reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
}

tasks.spotbugsMain {
    reports.create("html") { required.set(true) }
}

tasks.spotbugsTest {
    reports.create("html") { required.set(true) }
}

// ── Coverage: JaCoCo ──
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn("evalGate")
    dependsOn("perfGate")
    dependsOn("resilienceGate")
}

val evalSmoke = tasks.register<JavaExec>("evalSmoke") {
    group = "verification"
    description = "Runs the fast offline agentic evaluation smoke subset."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.agentforge.commerce.eval.EvalRunner")
    args("smoke")
}

tasks.register<JavaExec>("evalGate") {
    group = "verification"
    description = "Runs the full offline agentic evaluation gate with deterministic report drift detection."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.agentforge.commerce.eval.EvalRunner")
    if (project.hasProperty("evalUpdateBaseline")) {
        args("gate", "--update")
    } else {
        args("gate")
    }
}

tasks.register<JavaExec>("perfSmoke") {
    group = "verification"
    description = "Runs the fast offline performance/concurrency smoke subset."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.agentforge.commerce.perf.PerfRunner")
    args("smoke")
}

tasks.register<JavaExec>("perfGate") {
    group = "verification"
    description = "Runs the full offline performance/concurrency gate with explicit budgets."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.agentforge.commerce.perf.PerfRunner")
    args("gate")
}

tasks.register<JavaExec>("resilienceSmoke") {
    group = "verification"
    description = "Runs the fast offline resilience/chaos smoke subset."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.agentforge.commerce.resilience.ResilienceRunner")
    args("smoke")
}

tasks.register<JavaExec>("resilienceGate") {
    group = "verification"
    description = "Runs the full offline resilience/chaos gate asserting documented fault behaviour."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.agentforge.commerce.resilience.ResilienceRunner")
    args("gate")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Resolves all configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "Run with --write-locks to regenerate dependency locks"
        }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
