const visit = require('unist-util-visit')

module.exports = ({markdownAST}, {language = 'mermaid'} = {}) => {
    visit(markdownAST, 'code', node => {
        let lang = (node.lang || '').toLowerCase()
        if (lang === language) {
            node.lang += '-svg';
        }
    })
}