#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o noclobber
set -o nounset
#set -o xtrace #debug


## Publish JDK
## ===========
## Publishes the various versions of a JDK to Metaborg Artifacts.
##
## 1.  Preparation: ensure `~/.m2/settings.xml` contains the following (where $username and $password
##     are your Metaborg Artifacts username and password):
##
##         <?xml version="1.0" ?>
##         <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
##         xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
##           <profiles>
##             <profile>
##               <id>metaborg-release-repos</id>
##               <repositories>
##                 <repository>
##                   <id>metaborg-release-repo</id>
##                   <url>https://artifacts.metaborg.org/content/repositories/releases/</url>
##                 </repository>
##               </repositories>
##             </profile>
##           </profiles>
##           <servers>
##             <server>
##               <id>metaborg-release-repo</id>
##               <username>$username</username>
##               <password>$password</password>
##             </server>
##           </servers>
##         </settings>
##
## 2.  Download from https://adoptium.net/temurin/releases/?version=17 and place in the current directory:
##     - Temurin 17 Linux aarch64 JDK .tar.gz
##     - Temurin 17 Linux x64     JDK .tar.gz
##     - Temurin 17 macOS aarch64 JDK .tar.gz
##     - Temurin 17 macOS x64     JDK .tar.gz
##     - Temurin 17 Windows x64   JDK .zip
##
## 3.  Invoke this script in the current directory.
##


# Settings
repo_id="metaborg-release-repo"       # From ~/.m2/settings.xml
repo_url="https://artifacts.metaborg.org/content/repositories/releases/"
artifact_group="net.adoptium"
artifact_id="jdk"


# Find the JDK files to deploy
regex="^[a-zA-z0-9]*-jdk_([a-zA-Z0-9\-]+)_([a-zA-Z0-9]+)_[a-zA-Z0-9]+_([0-9\.\-\_]+)\.(tar\.gz|zip)$"   # Must be in variable
for file in *.{zip,tar.gz}    # Allow glob expansion
do
    if [[ $file =~ $regex ]]
    then
        # Filename matches pattern
        artifact_version="${BASH_REMATCH[3]/_/-}"                                   # E.g., "17.0.11-9"
        artifact_classifier="${BASH_REMATCH[2]/#mac/macosx}-${BASH_REMATCH[1]}"     # E.g., "macosx-aarch64"

        echo "Deploying ${artifact_group}:${artifact_id}:${artifact_version} for ${artifact_classifier}..."

        mvn deploy:deploy-file \
            --quiet \
            "-Durl=${repo_url}" \
            "-DrepositoryId=${repo_id}" \
            "-DgroupId=${artifact_group}" \
            "-DartifactId=${artifact_id}" \
            "-Dversion=${artifact_version}" \
            "-Dclassifier=${artifact_classifier}" \
            "-Dfile=${file}"
    fi
done

