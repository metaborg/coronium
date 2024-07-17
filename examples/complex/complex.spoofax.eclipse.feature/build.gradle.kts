plugins {
    `java-library`
    `maven-publish`
    id("org.metaborg.convention.java")
    id("org.metaborg.convention.maven-publish")
    id("org.metaborg.coronium.feature")
}

dependencies {
    bundle(project(":complex.spoofax.eclipse"))
}
