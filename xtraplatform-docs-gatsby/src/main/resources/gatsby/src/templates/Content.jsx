import React from "react"
import { graphql } from 'gatsby'
import { navigateTo } from "gatsby-link"
import rehypeReact from "rehype-react"
import glamorous from 'glamorous'
import { Helmet } from 'react-helmet'

import Mermaid from '../components/Mermaid'

if (typeof window !== 'undefined') {
    window.goto = function (id) {
        navigateTo(`/modules/${id}/`)
    }

    //TODO
    window.gotoComponent = function (id, modeName) {
        navigateTo(`/modules/${modeName}/${id}/`)
    }
}

const renderAst = new rehypeReact({
    createElement: React.createElement,
    Fragment: React.Fragment,
    components: {
        "mermaid": Mermaid
    }
}).Compiler

const Content = glamorous.div({
    padding: '2rem 2rem',
    /*'& h1': {
        display: 'none'
    },*/
    '& h1:first-of-type': {
        marginTop: 0
    }
});

export default ({ data }) =>
    <Content>
        <Helmet title={`XtraProxy User Manual - ${data.markdownRemark.headings[0].value}`} />
        {data && data.markdownRemark && renderAst(data.markdownRemark.htmlAst)}
    </Content>


export const query = graphql`
    query ContentQuery($key: String!) {
        markdownRemark(fields: {key: {eq: $key } }) {
            htmlAst
            headings {
                value
                depth
            }
        }
    }
`
