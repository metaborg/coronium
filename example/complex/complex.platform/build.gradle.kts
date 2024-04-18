plugins {
    `java-platform`
    `maven-publish`
}

val logVersion = "0.3.0"
val slf4jVersion = "1.7.30"
val pieVersion = "0.9.0"
val javaxInjectVersion = "1"
val checkerframeworkVersion = "3.0.0"
val daggerVersion = "2.25.2"

dependencies {
    constraints {
        api("org.metaborg:log.api:$logVersion")
        api("org.metaborg:log.backend.slf4j:$logVersion")
        api("org.slf4j:slf4j-simple:$slf4jVersion")
        api("org.metaborg:pie.api:$pieVersion")
        api("org.metaborg:pie.runtime:$pieVersion")
        api("org.metaborg:pie.dagger:$pieVersion")
        api("javax.inject:javax.inject:$javaxInjectVersion")
        api("org.checkerframework:checker-qual-android:$checkerframeworkVersion")
        api("com.google.dagger:dagger:$daggerVersion")
        api("com.google.dagger:dagger-compiler:$daggerVersion")
    }
}

publishing {
    publications {
        create<MavenPublication>("JavaPlatform") {
            from(components["javaPlatform"])
        }
    }
}
