package mb.coronium.util

import mb.coronium.mavenize.toEclipse
import mb.coronium.model.maven.MavenVersion
import org.gradle.api.Project

val Project.mavenVersion get() = MavenVersion.parse(this.version.toString())
val Project.eclipseVersion get() = mavenVersion.toEclipse()
