package de.interactive_instruments.xtraplatform

import com.github.jengelman.gradle.plugins.shadow.transformers.CacheableTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.gradle.api.tasks.Input

/**
 * Merges service files like {@link ServiceFileTransformer}, but drops single providers.
 * The built-in include/exclude patterns only match whole service files.
 */
@CacheableTransformer
class ExcludingServiceFileTransformer extends ServiceFileTransformer {

    private final Map<String, Set<String>> excludedProviders

    ExcludingServiceFileTransformer(Map<String, Set<String>> excludedProviders) {
        this.excludedProviders = excludedProviders
    }

    @Input
    Map<String, Set<String>> getExcludedProviders() {
        return excludedProviders
    }

    @Override
    void transform(TransformerContext context) {
        String service = context.path.substring(context.path.lastIndexOf('/') + 1)
        Set<String> excluded = excludedProviders.getOrDefault(service, [] as Set<String>)

        if (excluded.isEmpty()) {
            super.transform(context)
            return
        }

        String kept = context.inputStream.newReader('UTF-8').readLines()
                .findAll { !excluded.contains(it.trim()) }
                .join('\n')

        super.transform(new TransformerContext(context.path, new ByteArrayInputStream(kept.getBytes('UTF-8')), context.relocators))
    }
}
