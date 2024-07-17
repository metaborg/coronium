plugins {
    `java-library`
    `maven-publish`
    id("org.metaborg.convention.java")
    id("org.metaborg.convention.maven-publish")
}

dependencies {
    api(platform(project(":complex.platform")))
    annotationProcessor(platform(project(":complex.platform")))

    api(project(":complex.spoofax"))

    compileOnly(libs.checkerframework.android)
    annotationProcessor(libs.dagger.compiler)
}
