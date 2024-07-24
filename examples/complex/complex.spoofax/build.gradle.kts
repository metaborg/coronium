plugins {
    `java-library`
    id("org.metaborg.convention.java")
}

dependencies {
    api(platform(project(":complex.platform")))
    annotationProcessor(platform(project(":complex.platform")))

    api(libs.metaborg.log.api)
    api(libs.metaborg.pie.api)
    api(libs.dagger)

    compileOnly(libs.checkerframework.android)

    annotationProcessor(libs.dagger.compiler)
}
