package de.interactive_instruments.xtraplatform

class Style  implements Serializable {
    private static final long serialVersionUID = 1L;

    String logo = ""
    int maxMenuDepth = 3
    Map<String,String> colors = [:]

    Style() {
        this.logo = ""
        this.maxMenuDepth = 3
        this.colors = [:]
    }

    Style(Style style) {
        this.logo = style.logo
        this.maxMenuDepth = style.maxMenuDepth
        this.colors = style.colors
    }
}
