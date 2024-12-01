package de.interactive_instruments.xtraplatform

class XtraplatformExtension {

    private  boolean useMavenLocal = false
    private List<Object> layers = []
    private List<Object> nativeLayers = []

    XtraplatformExtension() {
    }

    void useMavenLocal() {
        this.useMavenLocal = true
    }

    void layer(Object layer) {
        this.layers.add(layer)
    }
    void layerNative(Object layer) {
        this.nativeLayers.add(layer)
    }

    boolean isUseMavenLocal() {
        return this.useMavenLocal
    }

    List<Object> getLayers() {
        return this.layers.collect { parseLayer(it) }
    }

    List<Object> getNativeLayers(String platform) {
        return this.nativeLayers.collect { parseLayer(it, platform) }
    }

    List<Object> getAllLayers() {
        return this.getLayers() + this.getNativeLayers()
    }

    List<Object> getAllLayers(String platform) {
        return this.getLayers() + this.getNativeLayers(platform)
    }

    static Object parseLayer(Object layer, String platform = null) {
        def parsed = layer;
        if (layer instanceof String) {
            def split = layer.split(':')
            parsed = [group: split[0], name: split[1], version: split[2]]
        }
        if (platform && parsed.version) {
           parsed.version = parsed.version.endsWith('-SNAPSHOT')
                   ? parsed.version.replace('-SNAPSHOT', "-${platform}-SNAPSHOT")
                   : parsed.version + "-${platform}"
        }
        return parsed
    }

    static String detectOs() {
        final String osName = System.getProperty("os.name");
        final String osArch = System.getProperty("os.arch");
        final String osVersion = System.getProperty("os.version");
        final String detectedName = normalizeOs(osName);
        final String detectedArch = normalizeArch(osArch).replace('x86_64', 'amd64').replace('aarch_64', 'arm64');

        if (UNKNOWN.equals(detectedName)) {
            throw new IllegalStateException("unknown os.name: " + osName);
        }
        if (UNKNOWN.equals(detectedArch)) {
            throw new IllegalStateException("unknown os.arch: " + osArch);
        }

        return "${detectedName}-${detectedArch}";
    }

    /**
     * OS detection from:
     * https://github.com/trustin/os-maven-plugin/blob/master/src/main/java/kr/motd/maven/os/Detector.java
     */

    private static final String UNKNOWN = 'unknown';

    private static String normalizeOs(String value) {
        value = normalize(value);
        if (value.startsWith('aix')) {
            return 'aix';
        }
        if (value.startsWith('hpux')) {
            return 'hpux';
        }
        if (value.startsWith('os400')) {
            // Avoid the names such as os4000
            if (value.length() <= 5 || !Character.isDigit(value.charAt(5))) {
                return 'os400';
            }
        }
        if (value.startsWith('linux')) {
            return 'linux';
        }
        if (value.startsWith('mac') || value.startsWith('osx')) {
            return 'osx';
        }
        if (value.startsWith('freebsd')) {
            return 'freebsd';
        }
        if (value.startsWith('openbsd')) {
            return 'openbsd';
        }
        if (value.startsWith('netbsd')) {
            return 'netbsd';
        }
        if (value.startsWith('solaris') || value.startsWith('sunos')) {
            return 'sunos';
        }
        if (value.startsWith('windows')) {
            return 'windows';
        }
        if (value.startsWith('zos')) {
            return 'zos';
        }

        return UNKNOWN;
    }

    private static String normalizeArch(String value) {
        value = normalize(value);
        if (value.matches('^(x8664|amd64|ia32e|em64t|x64)$')) {
            return 'x86_64';
        }
        if (value.matches('^(x8632|x86|i[3-6]86|ia32|x32)$')) {
            return 'x86_32';
        }
        if (value.matches('^(ia64w?|itanium64)$')) {
            return 'itanium_64';
        }
        if ('ia64n'.equals(value)) {
            return 'itanium_32';
        }
        if (value.matches('^(sparc|sparc32)$')) {
            return 'sparc_32';
        }
        if (value.matches('^(sparcv9|sparc64)$')) {
            return 'sparc_64';
        }
        if (value.matches('^(arm|arm32)$')) {
            return 'arm_32';
        }
        if ('aarch64'.equals(value)) {
            return 'aarch_64';
        }
        if (value.matches('^(mips|mips32)$')) {
            return 'mips_32';
        }
        if (value.matches('^(mipsel|mips32el)$')) {
            return 'mipsel_32';
        }
        if ('mips64'.equals(value)) {
            return 'mips_64';
        }
        if ('mips64el'.equals(value)) {
            return 'mipsel_64';
        }
        if (value.matches('^(ppc|ppc32)$')) {
            return 'ppc_32';
        }
        if (value.matches('^(ppcle|ppc32le)$')) {
            return 'ppcle_32';
        }
        if ('ppc64'.equals(value)) {
            return 'ppc_64';
        }
        if ('ppc64le'.equals(value)) {
            return 'ppcle_64';
        }
        if ('s390'.equals(value)) {
            return 's390_32';
        }
        if ('s390x'.equals(value)) {
            return 's390_64';
        }
        if (value.matches('^(riscv|riscv32)$')) {
            return 'riscv';
        }
        if ('riscv64'.equals(value)) {
            return 'riscv64';
        }
        if ('e2k'.equals(value)) {
            return 'e2k';
        }
        if ('loongarch64'.equals(value)) {
            return 'loongarch_64';
        }
        return UNKNOWN;
    }


    private static String normalize(String value) {
        if (value == null) {
            return '';
        }
        return value.toLowerCase(Locale.US).replaceAll('[^a-z0-9]+', '');
    }
}
