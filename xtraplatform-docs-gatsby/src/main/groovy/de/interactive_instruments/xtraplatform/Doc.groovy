package de.interactive_instruments.xtraplatform

class Doc  implements Serializable {
    private static final long serialVersionUID = 1L;
    
    String name
    String srcDir
    String assetDir = ""
    String title = ""
    String pathPrefix = ""
    private Style _style = new Style()

    Doc(String name) {
        this.name = name
    }

    Doc(Doc doc) {
        this.name = doc.name
        this.srcDir = doc.srcDir
        this.assetDir = doc.assetDir
        this.title = doc.title
        this.pathPrefix = doc.pathPrefix
        this._style = new Style(doc._style)
    }

    def style(def closure) {
        _style.with(closure)
    }  

    Style getStyle() {
        return _style
    }
}
