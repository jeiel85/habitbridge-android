// Pure Kotlin policy engine. No Android dependencies so the same rules run in
// JVM unit tests and can be reasoned about without a device. Mirrors the web
// prototype's src/engine.js function by function (docs/PRD-SDD.md, Appendix C).
plugins {
  alias(libs.plugins.kotlin.jvm)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
}

tasks.withType<Test>().configureEach {
  useJUnit()
  testLogging {
    events("passed", "failed", "skipped")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
