const path = require(`path`);
//const fs = require('fs-extra');
const { createFilePath } = require(`gatsby-source-filesystem`);
const _ = require("lodash");
const xtraplatform = require("./xtraplatform.json");
//const config = require('./gatsby-config');
//const HtmlWebpackPlugin = require('html-webpack-plugin');
//const HtmlWebpackInlineSourcePlugin = require('html-webpack-inline-source-plugin');

const useLang = xtraplatform.srcDir.indexOf("/{lng}") > 1;

exports.onCreateNode = (props) => {
  const { node, getNode, getNodesByType, actions } = props;
  const { createNodeField } = actions;

  if (node.internal.type === `File`) {
    const relativePath = useLang
      ? node.relativePath.substr(node.relativePath.indexOf("/") + 1)
      : node.relativePath;

    let itemType = path.basename(path.dirname(relativePath));
    //const basePath = 'content'

    if (xtraplatform.style.logo && relativePath === xtraplatform.style.logo) {
      itemType = "LOGO";
    }

    let slug = createFilePath({
      node,
      getNode,
      //basePath: basePath
    });

    createNodeField({
      node,
      name: `itemType`,
      value: itemType,
    });
    createNodeField({
      node,
      name: `slug`,
      value: slug,
    });
    console.log("FILE", itemType, slug, relativePath);
  } else if (node.internal.type === `MarkdownRemark`) {
    const fileNode = getNode(node.parent);

    const currentLanguage = useLang
      ? fileNode.fields.slug.substr(1, fileNode.fields.slug.indexOf("/", 1) - 1)
      : null;
    const isRoot = node.frontmatter.isRoot;

    const rootSlug = useLang ? `/${currentLanguage}/` : "/";

    const slug = isRoot
      ? rootSlug
      : fileNode.fields.slug.replace(new RegExp(`/[0-9]+_`), "/");

    if (fileNode.fields && fileNode.fields.itemType) {
      createNodeField({
        node,
        name: `itemType`,
        value: fileNode.fields.itemType,
      });
      createNodeField({
        node,
        name: `key`,
        value: fileNode.fields.slug,
      });
      createNodeField({
        node,
        name: `slug`,
        value: slug,
      });
      console.log(
        "MD",
        fileNode.fields.itemType,
        fileNode.fields.slug,
        slug,
        node.frontmatter
      );
    }
  }
};

exports.createPages = ({ graphql, actions }) => {
  const { createPage } = actions;
  let isFirst = true;

  return new Promise((resolve, reject) => {
    graphql(`
      {
        allMarkdownRemark(sort: { fields: [fields___key], order: ASC }) {
          edges {
            node {
              id
              fields {
                itemType
                key
                slug
              }
            }
          }
        }
      }
    `).then((result) => {
      result.data.allMarkdownRemark.edges.map(({ node }, i) => {
        console.log("PAGE", node.fields.slug);

        const contentTemplate = path.resolve(`./src/templates/Content.jsx`);

        if (useLang && i === 0) {
          isFirst = false;
          console.log("PAGE", "/");
          createPage({
            path: "/",
            component: contentTemplate,
            context: {
              key: node.fields.key,
            },
          });
        }
        createPage({
          path: node.fields.slug,
          component: contentTemplate,
          context: {
            key: node.fields.key,
          },
        });
      });
      resolve();
    });
  });
};

//TODO: copy recursive from assets
/*exports.onPostBuild = (args, pluginOptions) => {
    const src = 'src/assets/img';
    const dst = 'public/static/img'

    return fs.copy(src, dst)
        .catch((err) => {
            console.error(src, dst, err);
        });
};*/

// omit mermaid generation for ssr
exports.onCreateWebpackConfig = ({ stage, actions }) => {
  switch (stage) {
    case "build-html":
      actions.setWebpackConfig({
        module: {
          rules: [
            {
              test: /mermaid/,
              use: ["null-loader"],
            },
          ],
        },
        /*plugins: [
                    new HtmlWebpackPlugin({
                        template: 'public/index.html',  //template file to embed the source
                        inlineSource: '.(js|css)$' // embed all javascript and css inline
                    }),
                    new HtmlWebpackInlineSourcePlugin()
                ]*/
      });

      break;
  }
};
