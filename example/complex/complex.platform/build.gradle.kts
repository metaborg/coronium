plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        api(libs.spoofax3.log.api)
        api(libs.spoofax3.log.backend.slf4j)
        api(libs.slf4j.simple)
        api(libs.spoofax3.pie.api)
        api(libs.spoofax3.pie.runtime)
        api(libs.spoofax3.pie.dagger)
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
