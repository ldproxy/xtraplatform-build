package de.interactive_instruments.xtraplatform.docs;

import de.interactive_instruments.xtraplatform.Maturity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

class LayerDocs {
    String id;
    String name;
    String version;
    String description;
    Map<String, ModuleDocs> modules;
    Optional<TypeDocs> getType(String qualifiedName) {
        if (Objects.isNull(modules)) {
            return Optional.empty();
        }

        return modules.values().stream()
            .map(moduleDocs -> moduleDocs.getType(qualifiedName))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }
}
