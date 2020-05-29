plugins {
  id("org.metaborg.gradle.config.java-library")
}

dependencies {
  api(platform(project(":complex.platform")))
  annotationProcessor(platform(project(":complex.platform")))

  api(project(":complex.spoofax"))

  compileOnly("org.checkerframework:checker-qual-android")
  annotationProcessor("com.google.dagger:dagger-compiler")
}
