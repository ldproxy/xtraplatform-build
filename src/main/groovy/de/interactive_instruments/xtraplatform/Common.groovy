package de.interactive_instruments.xtraplatform

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

class Common {

    static void addPublishingRepos(Project project) {
        PublishingExtension publishing = project.extensions.publishing

        publishing.with {
            repositories {
                maven {
                    def releasesRepoUrl = "https://dl.interactive-instruments.de/repository/maven-releases/"
                    def snapshotsRepoUrl = "https://dl.interactive-instruments.de/repository/maven-snapshots/"

                    url project.rootProject.version.endsWith('SNAPSHOT') ? snapshotsRepoUrl : releasesRepoUrl
                    credentials {
                        username project.rootProject.findProperty('deployUser') ?: ''
                        password project.rootProject.findProperty('deployPassword') ?: ''
                    }
                }
            }
        }
    }

}
