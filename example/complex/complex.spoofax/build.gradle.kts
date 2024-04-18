plugins {
    id("org.metaborg.gradle.config.java-library")
    `maven-publish`
}

dependencies {
    api(platform(project(":complex.platform")))
    annotationProcessor(platform(project(":complex.platform")))

    api("org.metaborg:log.api")
    api("org.metaborg:pie.api")
    api("com.google.dagger:dagger")

    compileOnly("org.checkerframework:checker-qual-android")

    annotationProcessor("com.google.dagger:dagger-compiler")
}
