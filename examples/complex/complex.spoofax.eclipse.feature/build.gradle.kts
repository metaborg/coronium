plugins {
    id("org.metaborg.coronium.feature")
    `maven-publish`
}

dependencies {
    bundle(project(":complex.spoofax.eclipse"))
}
