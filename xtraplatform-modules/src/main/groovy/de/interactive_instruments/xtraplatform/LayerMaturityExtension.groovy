package de.interactive_instruments.xtraplatform

class LayerMaturityExtension {

    class MaturityConfiguration {
        double minimumCoverage = 0.0
        boolean allowExperimentalComponents = true
        boolean warningsAsErrors = false
    }

    protected final Map<Maturity, MaturityConfiguration> configurations = [:]

    Maturity minimumModuleMaturity = Maturity.EXPERIMENTAL
    boolean lowLevel = false

    LayerMaturityExtension() {
        Maturity.values().each {
            configurations.put(it, new MaturityConfiguration())
        }
        configurations.get(Maturity.PRODUCTION).with {
            minimumCoverage = 1.0
            allowExperimentalComponents = false
            warningsAsErrors = true
        }
        configurations.get(Maturity.CANDIDATE).with {
            minimumCoverage = 0.5
        }
    }

    Object methodMissing(String name, Object args) {
        def maturity = name as Maturity
        def closure = args as List<Closure>
        configurations.get(maturity).with(closure.get(0))
    }

    MaturityConfiguration cfgForMaturity(String maturity) {
        return configurations.get(maturity as Maturity)
    }

    boolean isValid(Maturity maturity) {
        return maturity.ordinal() >= minimumModuleMaturity.ordinal()
    }
}
