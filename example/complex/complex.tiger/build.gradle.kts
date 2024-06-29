plugins {
    id("org.metaborg.gradle.config.java-library")
    `maven-publish`
}

dependencies {
    api(platform(project(":complex.platform")))
    annotationProcessor(platform(project(":complex.platform")))

    api(project(":complex.spoofax"))

    compileOnly(libs.checkerframework.android)
    annotationProcessor(libs.dagger.compiler)
}
