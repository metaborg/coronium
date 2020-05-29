plugins {
  id("org.metaborg.coronium.feature")
}

dependencies {
  featureInclude(project(":complex.spoofax.eclipse.feature"))
  bundle(project(":complex.tiger.eclipse")) {
    // Including a bundle into a feature also includes all reexported bundles. In this case, we want to prevent this
    // because 'complex.spoofax.eclipse' is included into the 'complex.spoofax.eclipse.feature' feature.
    exclude("org.metaborg", "complex.spoofax.eclipse")
  }
}
