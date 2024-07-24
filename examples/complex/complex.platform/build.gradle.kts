plugins {
    `java-platform`
}

dependencies {
    constraints {
        api(libs.metaborg.log.api)
        api(libs.metaborg.log.backend.slf4j)
        api(libs.slf4j.simple)
        api(libs.metaborg.pie.api)
        api(libs.metaborg.pie.runtime)
        api(libs.metaborg.pie.dagger)
        api(libs.javax.inject)
        api(libs.checkerframework.android)
        api(libs.dagger)
        api(libs.dagger.compiler)
    }
}

publishing {
    publications {
        create<MavenPublication>("JavaPlatform") {
            from(components["javaPlatform"])
        }
    }
}
