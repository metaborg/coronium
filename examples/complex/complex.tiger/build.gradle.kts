plugins {
    `java-library`
    id("org.metaborg.convention.java")
}

dependencies {
    api(platform(project(":complex.platform")))
    annotationProcessor(platform(project(":complex.platform")))

    api(project(":complex.spoofax"))

    compileOnly(libs.checkerframework.android)
    annotationProcessor(libs.dagger.compiler)
}
