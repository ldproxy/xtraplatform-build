package de.interactive_instruments.xtraplatform

import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.initialization.Settings

/**
 * @author zahnen
 */
class SettingsPlugin implements Plugin<Settings> {

    @Override
    void apply(Settings settings) {

        def version = getVersion(settings)
        println "XtraPlatform version: $version"

        settings.with {
            dependencyResolutionManagement {
                repositories {
                    maven {
                        url "https://dl.interactive-instruments.de/repository/maven-releases/"
                    }
                    maven {
                        url "https://dl.interactive-instruments.de/repository/maven-snapshots/"
                    }
                }
                versionCatalogs {
                    xtraplatform {
                        from("de.interactive_instruments:xtraplatform-catalog:${version}")
                    }
                }
            }
        }
    }

    static String getVersion(Settings settings) {
        final Configuration classpath = settings.getBuildscript().getConfigurations().getByName("classpath");
        return classpath.getResolvedConfiguration().getResolvedArtifacts().stream()
                .map(artifact -> artifact.getModuleVersion().getId())
                .filter(id -> "de.interactive_instruments".equals(id.getGroup()) && id.getName().startsWith("xtraplatform-"))
                .findAny()
                .map(ModuleVersionIdentifier::getVersion)
                .orElse("+");
    }

}
