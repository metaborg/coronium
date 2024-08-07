# Changelog
All notable changes to this project are documented in this file, based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).


## [Unreleased]

## [0.4.0] - 2024-07-01
### Changed
- Update Bnd to 5.3.0


## [0.3.18] - 2024-05-27
### Fixed
- Version string that end with `+dirty` now get their suffix removed. They are not supported by Bnd, resulting in the error 'Fragment-Host spoofax.eclipse specifies invalid version range 0.22.2.develop-qualifier+dirty'.


## [0.3.17] - 2024-02-09
### Added
- Support for aarch64 architecture.
- Setting default Eclipse version to 2022-06, as 2020-06 does not have aarch64 architecture, consequently dropping Java 8 support.
- Setting default JDK to Eclipse Adoptium 11.0.22+7.


## [0.3.16] - 2022-05-13
### Fixed
- UNIX JVMs embedded into Eclipse installations not working due to JVM not having the executable mode set.


## [0.3.15] - 2021-11-16
### Changed
- Revert default Eclipse version to 2020-06, which still runs under and is compiled with Java 8.
- Update supported Gradle versions to 5.6.4 and 6.9.1 in README.

### Added
- Info on supported Java versions to README.


## [0.3.14] - 2021-11-10
### Added
- Support for the `provider-name` attribute of `feature.xml`
- Support for feature dependencies without `version` attributes (but with the `id` attribute set) in `category.xml`, where the dependency version is discovered through Gradle.


## [0.3.13] - 2021-11-08
### Changed
- Eclipse download mirror to our artifact server at `https://artifacts.metaborg.org/content/repositories/releases/org/eclipse` and changed file scheme, as Eclipse mirrors are slow and unreliable.


## [0.3.12] - 2021-11-03
### Changed
- Eclipse download mirror from `https://mirror.dkm.cz/eclipse/` to `https://mirror.ibcp.fr/pub/eclipse` due to the former suddenly deleting several older versions.


## [0.3.11] - 2021-09-20
### Changed
- Base repositories and install units to be overridden in `EclipseCreateInstallation` tasks by adding `baseRepositories` and `baseInstallUnits` properties.


[Unreleased]: https://github.com/metaborg/coronium/compare/release-0.4.0...HEAD
[0.4.0]: https://github.com/metaborg/coronium/compare/release-0.3.18...release-0.4.0
[0.3.18]: https://github.com/metaborg/coronium/compare/release-0.3.17...release-0.3.18
[0.3.17]: https://github.com/metaborg/coronium/compare/release-0.3.16...release-0.3.17
[0.3.16]: https://github.com/metaborg/coronium/compare/release-0.3.15...release-0.3.16
[0.3.15]: https://github.com/metaborg/coronium/compare/release-0.3.14...release-0.3.15
[0.3.14]: https://github.com/metaborg/coronium/compare/release-0.3.13...release-0.3.14
[0.3.13]: https://github.com/metaborg/coronium/compare/release-0.3.12...release-0.3.13
[0.3.12]: https://github.com/metaborg/coronium/compare/release-0.3.11...release-0.3.12
[0.3.11]: https://github.com/metaborg/coronium/compare/release-0.3.10...release-0.3.11
