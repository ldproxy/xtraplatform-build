package de.interactive_instruments.xtraplatform

class WildcardError extends AssertionError {
    WildcardError(String var1) {
        super((Object) "Wildcard imports are not allowed: ${var1}");
    }

    @Override
    StackTraceElement[] getStackTrace() {
        return []
    }

    @Override
    String toString() {
        return ""
    }
}