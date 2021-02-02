const visit = require('unist-util-visit')
const crypto = require('crypto');

module.exports = ({ markdownAST }, { language = 'mermaid' } = {}) => {
    visit(markdownAST, 'code', node => {
        const lang = (node.lang || '').toLowerCase()
        if (lang === language) {
            const hash = crypto.createHmac('sha1', 'gatsby-remark-mermaid').update(node.value).digest('hex');
            node.type = 'html';
            node.value = `<Mermaid name="mermaid-${hash}">\n${node.value}\n</Mermaid>`;
            //console.log(node.value);
        }
    })
}
