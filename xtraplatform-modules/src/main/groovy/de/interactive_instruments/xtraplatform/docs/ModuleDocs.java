package de.interactive_instruments.xtraplatform.docs;

class ModuleDocs {

    String name;
    String version;

    ModuleDocs() {
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "ModuleDocs{" +
            "name='" + name + '\'' +
            ", version='" + version + '\'' +
            '}';
    }
}
