plugins {
    id("org.metaborg.gradle.config.java-library")
    `maven-publish`
}

dependencies {
    api(platform(project(":complex.platform")))
    annotationProcessor(platform(project(":complex.platform")))

    api(libs.spoofax3.log.api)
    api(libs.spoofax3.pie.api)
    api(libs.dagger)

    compileOnly(libs.checkerframework.android)

    annotationProcessor(libs.dagger.compiler)
}
