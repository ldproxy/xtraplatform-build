package de.interactive_instruments.xtraplatform

class ModuleInfoExtension {

    boolean enabled = true
    String name = ""
    Set<String> exports = []
    Set<String> requires = []
    Map<String, List<String>> provides = [:]
    Set<String> uses = []
    List<String> additions = []

    ModuleInfoExtension() {
    }

    ModuleInfoExtension(ModuleInfoExtension other) {
        this.enabled = other.enabled
        this.name = other.name
        this.exports = [] + other.exports
        this.requires = [] + other.requires
        this.provides = [:] + other.provides
        this.uses = [] + other.uses
        this.additions = [] + other.additions
    }
}
