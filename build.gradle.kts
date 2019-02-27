import au.com.dius.pact.provider.gradle.PactPublish
import au.com.dius.pact.provider.gradle.VerificationReports
import org.gradle.internal.impldep.org.fusesource.jansi.AnsiRenderer.test
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val logbackVersion = "1.2.3"
val ktorVersion = "1.1.2"
val jacksonVersion = "2.9.8"
val wiremockVersion = "2.19.0"
val logstashLogbackVersion = "5.3"
val prometheusVersion = "0.6.0"

val mainClass = "no.nav.helse.PleiepengerSakKt"

plugins {
    kotlin("jvm") version "1.3.21"
    id("au.com.dius.pact") version "3.6.2"
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.21")
        classpath("au.com.dius:pact-jvm-provider-gradle_2.12:3.6.2")
    }
}

dependencies {
    compile(kotlin("stdlib-jdk8"))

    // Ktor Server
    compile("io.ktor:ktor-server-netty:$ktorVersion")
    compile("io.ktor:ktor-auth-jwt:$ktorVersion")
    compile ("io.ktor:ktor-jackson:$ktorVersion")

    // JSON Serialization
    compile ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Logging
    compile ( "ch.qos.logback:logback-classic:$logbackVersion")
    compile ("net.logstash.logback:logstash-logback-encoder:$logstashLogbackVersion")

    // Prometheus
    compile("io.prometheus:simpleclient_common:$prometheusVersion")
    compile("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    // Ktor Client
    compile ("io.ktor:ktor-client-core:$ktorVersion")
    compile ("io.ktor:ktor-client-core-jvm:$ktorVersion")
    compile ("io.ktor:ktor-client-json-jvm:$ktorVersion")
    compile ("io.ktor:ktor-client-jackson:$ktorVersion")
    compile ("io.ktor:ktor-client-apache:$ktorVersion")

    // Test
    testCompile ("com.github.tomakehurst:wiremock:$wiremockVersion")
    testCompile("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testCompile ("com.nimbusds:oauth2-oidc-sdk:5.56")
    testCompile("au.com.dius:pact-jvm-consumer-junit_2.12:3.6.2")
    testCompile("org.mockito:mockito-core:2.24.5")
}



pact {
    publish(closureOf<PactPublish> {
        val proxyHost = System.getenv("PACT_BROKER_PROXY_HOST")
        val proxyPort = System.getenv("PACT_BROKER_PROXY_PORT")
        val brokerUrl = System.getenv("PACT_BROKER_URL")
        val brokerUsername = System.getenv("PACT_BROKER_USERNAME")
        val brokerPassword = System.getenv("PACT_BROKER_PASSWORD")

        if (!proxyHost.isNullOrBlank() && !proxyHost.isNullOrBlank()) {
            System.setProperty("http.nonProxyHosts", "localhost")
            System.setProperty("http.proxyHost", proxyHost)
            System.setProperty("http.proxyPort", proxyPort)
            System.setProperty("https.proxyHost", proxyHost)
            System.setProperty("https.proxyPort", proxyPort)
        }

        if (brokerUrl.isNullOrBlank() || brokerUsername.isNullOrBlank() || brokerPassword.isNullOrBlank()) {
            throw GradleException("brokerUrl, brokerUsername og brokerPassword må settes.")
        }

        pactDirectory = "$rootDir/pacts"
        pactBrokerUrl = brokerUrl
        pactBrokerUsername = brokerUsername
        pactBrokerPassword = brokerPassword
    })
}

repositories {
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://kotlin.bintray.com/kotlinx")
    maven("http://packages.confluent.io/maven/")

    jcenter()
    mavenLocal()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.named<Jar>("jar") {
    baseName = "app"

    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations["compile"].map {
            it.name
        }.joinToString(separator = " ")
    }

    configurations["compile"].forEach {
        val file = File("$buildDir/libs/${it.name}")
        if (!file.exists())
            it.copyTo(file)
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.2.1"
}