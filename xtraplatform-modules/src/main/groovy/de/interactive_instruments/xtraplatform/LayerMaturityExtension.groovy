package de.interactive_instruments.xtraplatform

class LayerMaturityExtension {

    class MaturityConfiguration {
        double minimumCoverage = 0.0
        Maturity minimumComponentMaturity = Maturity.EXPERIMENTAL
        Set<String> forbiddenImports = []
        boolean isMinimumModuleMaturity = false

        @Override
        public String toString() {
            return "MaturityConfiguration{" +
                    "minimumCoverage=" + minimumCoverage +
                    ", minimumComponentMaturity=" + minimumComponentMaturity +
                    ", forbiddenImports=" + forbiddenImports +
                    ", isMinimumModuleMaturity=" + isMinimumModuleMaturity +
                    '}';
        }
    }

    final Map<Maturity, MaturityConfiguration> configurations = [:]

    LayerMaturityExtension() {
        Maturity.values().each {
            configurations.put(it, new MaturityConfiguration())
        }
        configurations.get(Maturity.PRODUCTION).with {
            minimumCoverage = 1.0
            minimumComponentMaturity = 'PRODUCTION'
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

    MaturityConfiguration forMaturity(String maturity) {
        return configurations.get(maturity as Maturity)
    }

    Maturity getMinimumModuleMaturity() {
        return configurations.entrySet()
                .stream()
                .filter(entry -> entry.getValue().isMinimumModuleMaturity)
                .map(entry -> entry.getKey())
                .findFirst()
                .orElse(Maturity.EXPERIMENTAL)
    }

    boolean isValid(Maturity maturity) {
        Maturity minimumModuleMaturity = getMinimumModuleMaturity()
        return maturity.ordinal() <= minimumModuleMaturity.ordinal()
    }
}
