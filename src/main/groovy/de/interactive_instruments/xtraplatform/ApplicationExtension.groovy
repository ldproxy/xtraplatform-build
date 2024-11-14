package de.interactive_instruments.xtraplatform

import org.gradle.api.Project

class ApplicationExtension {

    final Project project;
    String name;
    String version;
    List<String> additionalBaseConfigs;

    ApplicationExtension(Project project) {
        this.project = project;
        this.additionalBaseConfigs = [];
    }

    String getName2() {
        return Optional.ofNullable(this.name).orElse(this.project.name);
    }

    String getVersion2() {
        return Optional.ofNullable(this.version).orElse(this.project.version as String);
    }
}
