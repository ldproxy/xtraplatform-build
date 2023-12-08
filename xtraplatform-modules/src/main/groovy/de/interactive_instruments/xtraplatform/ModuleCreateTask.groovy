package de.interactive_instruments.xtraplatform

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues

class ModuleCreateTask extends DefaultTask {

    private String name
    private Maturity maturity = Maturity.PROPOSAL;
    private Maintenance maintenance = Maintenance.NONE;

    public ModuleCreateTask() {
    }

    @Option(option = "name", description = "module name")
    void setName(String name) {
        this.name = name;
    }

    @Option(option = "maturity", description = "module maturity")
    void setMaturity(Maturity maturity) {
        this.maturity = maturity;
    }

    @Option(option = "maintenance", description = "module maintenance")
    void setMaturity(Maintenance maintenance) {
        this.maintenance = maintenance;
    }

    @OptionValues("maturity")
    Collection<Maturity> getSupportedMaturity() {
        return EnumSet.allOf(Maturity.class);
    }

    @OptionValues("maintenance")
    Collection<Maintenance> getSupportedMaintenance() {
        return EnumSet.allOf(Maintenance.class);
    }

    @TaskAction
    void createModule() {
        if (Objects.isNull(name) || name.isBlank()) {
            System.out.println("Please provide a name, e.g. './gradlew createModule --name xtraplatform-example'");
            return;
        }
        getProject().file(name).mkdirs()
        getProject().file("${name}/build.gradle").text = "\nmaturity = '${maturity.name()}'\nmaintenance = '${maintenance.name()}'\n"
        getProject().file("${name}/src/main/java/de/ii/${asPath(name)}/app").mkdirs()
        getProject().file("${name}/src/main/java/de/ii/${asPath(name)}/domain").mkdirs()
        getProject().file("${name}/src/test/groovy/de/ii/${asPath(name)}/app").mkdirs()
        getProject().file("${name}/src/test/groovy/de/ii/${asPath(name)}/domain").mkdirs()
    }

    private static String asPath(String name) {
        return String.join("/", name.split("-"))
    }
}