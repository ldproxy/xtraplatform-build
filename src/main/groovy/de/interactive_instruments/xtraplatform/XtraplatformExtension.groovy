package de.interactive_instruments.xtraplatform

class XtraplatformExtension {

    private List<Object> layers = []
    private List<Object> nativeLayers = []

    XtraplatformExtension() {
    }

    void layer(Object layer) {
        this.layers.add(layer)
    }
    void layerNative(Object layer) {
        this.nativeLayers.add(layer)
    }

    List<Object> getLayers() {
        return this.layers.collect { parseLayer(it) }
    }

    List<Object> getNativeLayers() {
        return this.nativeLayers.collect { parseLayer(it) }
    }

    List<Object> getAllLayers() {
        return this.getLayers() + this.getNativeLayers()
    }

    static Object parseLayer(Object layer) {
        if (layer instanceof String) {
            def split = layer.split(':')
            return "${split[0]}:${split[1]}:${split[2]}"
        }
        return layer
    }
}
