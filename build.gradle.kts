@file:Suppress("GradlePackageUpdate")

import com.github.jk1.license.LicenseReportExtension
import nebula.plugin.contacts.Contact
import nebula.plugin.contacts.ContactsExtension
import nl.javadude.gradle.plugins.license.LicenseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    `java-library`
    `maven-publish`
    signing

    id("org.jetbrains.kotlin.jvm") version "latest.release"
    id("nebula.maven-resolved-dependencies") version "17.3.2"
    id("nebula.release") version "15.3.1"
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"

    id("com.github.hierynomus.license") version "0.16.1"
    id("com.github.jk1.dependency-license-report") version "1.16"
    id("org.owasp.dependencycheck") version "6.5.3"

    id("nebula.maven-publish") version "17.3.2"
    id("nebula.contacts") version "5.1.0"
    id("nebula.info") version "11.1.0"

    id("nebula.javadoc-jar") version "17.3.2"
    id("nebula.source-jar") version "17.3.2"
    id("nebula.maven-apache-license") version "17.3.2"

    id("org.openrewrite.rewrite") version "latest.release"
}

apply(plugin = "nebula.publish-verification")

rewrite {
    activeRecipe("org.openrewrite.java.format.AutoFormat", "org.openrewrite.java.cleanup.Cleanup")
}

configure<nebula.plugin.release.git.base.ReleasePluginExtension> {
    defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT(project)
}

dependencyCheck {
    analyzers.assemblyEnabled = false
    failBuildOnCVSS = 9.0F
}

group = "org.openrewrite.recipe"

repositories {
    if(!project.hasProperty("releasing")) {
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
    mavenCentral()
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

val signingKey: String? by project
val signingPassword: String? by project
val requireSigning = project.hasProperty("forceSigning") || project.hasProperty("releasing")
if(signingKey != null && signingPassword != null) {
    signing {
        isRequired = requireSigning
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["nebula"])
    }
} else if(requireSigning) {
    throw RuntimeException("Artifact signing is required, but signingKey and/or signingPassword are null")
}

configurations {
    all {
        resolutionStrategy {
            cacheChangingModulesFor(0, TimeUnit.SECONDS)
            cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
        }
    }
}

var rewriteVersion = if(project.hasProperty("releasing")) {
    "latest.release"
} else {
    "latest.integration"
}

dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation("org.openrewrite:rewrite-java:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-xml:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-properties:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-yaml:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-maven:${rewriteVersion}")

    // eliminates "unknown enum constant DeprecationLevel.WARNING" warnings from the build log
    // see https://github.com/gradle/kotlin-dsl-samples/issues/1301 for why (okhttp is leaking parts of kotlin stdlib)
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    runtimeOnly("org.openrewrite:rewrite-java-11:$rewriteVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testImplementation("org.openrewrite:rewrite-test:${rewriteVersion}")

    testImplementation("org.assertj:assertj-core:latest.release")

    testImplementation("org.openrewrite:rewrite-java-11:${rewriteVersion}")
    testImplementation("org.openrewrite:rewrite-java-8:${rewriteVersion}")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()

    options.isFork = true
    options.compilerArgs.addAll(listOf("--release", "8"))
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }

    doFirst {
        destinationDir.mkdirs()
    }
}

configure<ContactsExtension> {
    val j = Contact("jkschneider@gmail.com")
    j.moniker("Jonathan Schneider")

    people["jkschneider@gmail.com"] = j
}

configure<LicenseExtension> {
    ext.set("year", Calendar.getInstance().get(Calendar.YEAR))
    skipExistingHeaders = true
    header = project.rootProject.file("gradle/licenseHeader.txt")
    mapping(mapOf("kt" to "SLASHSTAR_STYLE", "java" to "SLASHSTAR_STYLE"))
    // exclude JavaTemplate shims from license check
    exclude("src/main/resources/META-INF/rewrite/*.java")
    strictCheck = true
}

configure<LicenseReportExtension> {
    renderers = arrayOf(com.github.jk1.license.render.CsvReportRenderer())
}

configure<PublishingExtension> {
    publications {
        named("nebula", MavenPublication::class.java) {
            suppressPomMetadataWarningsFor("runtimeElements")

            pom.withXml {
                (asElement().getElementsByTagName("dependencies").item(0) as org.w3c.dom.Element).let { dependencies ->
                    dependencies.getElementsByTagName("dependency").let { dependencyList ->
                        var i = 0
                        var length = dependencyList.length
                        while (i < length) {
                            (dependencyList.item(i) as org.w3c.dom.Element).let { dependency ->
                                if ((dependency.getElementsByTagName("scope")
                                        .item(0) as org.w3c.dom.Element).textContent == "provided") {
                                    dependencies.removeChild(dependency)
                                    i--
                                    length--
                                }
                            }
                            i++
                        }
                    }
                }
            }
        }
    }
}
