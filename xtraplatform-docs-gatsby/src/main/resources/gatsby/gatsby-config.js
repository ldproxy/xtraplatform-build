const xtraplatform = require("./xtraplatform.json");
const theme = require("typography-theme-github").default;

const googleFonts = theme.googleFonts
  ? theme.googleFonts.map((font) => ({
      family: font.name,
      variants: font.styles,
      strategy: "selfHosted",
    }))
  : [];
console.log("FONTS", googleFonts);

const plugins = [
  {
    resolve: `gatsby-plugin-layout`,
    options: {
      component: require.resolve(`./src/components/Layout.jsx`),
    },
  },
  `gatsby-plugin-react-helmet`,
  `gatsby-transformer-sharp`,
  `gatsby-plugin-sharp`,
  {
    resolve: `gatsby-source-filesystem`,
    options: {
      name: `src`,
      path: `${xtraplatform.srcDir.replace("/{lng}", "")}`,
    },
  },
  {
    resolve: `gatsby-plugin-typography`,
    options: {
      pathToConfigModule: `src/theme/typography.js`,
      omitGoogleFont: true,
    },
  },
  {
    resolve: `gatsby-transformer-remark`,
    options: {
      plugins: [
        /*{
                    resolve: `gatsby-remark-relative-images`,
                },*/
        {
          resolve: `gatsby-remark-images`,
          options: {
            // It's important to specify the maxWidth (in pixels) of
            // the content container as this plugin uses this as the
            // base for generating different widths of each image.
            maxWidth: 1600,
            // Remove the default behavior of adding a link to each
            // image.
            linkImagesToOriginal: false,
            showCaptions: true,
            sizeByPixelDensity: false,
            backgroundColor: "none",
            quality: 80,
            withWebp: true,
          },
        },
        /*{
                    resolve: 'gatsby-remark-graph',
                    options: {
                        // this is the language in your code-block that triggers mermaid parsing
                        language: 'mermaid', // default
                        theme: 'default' // could also be dark, forest, or neutral
                    }
                },*/
        /*{
                    resolve: `gatsby-remark-draw-preprocessor`
                },
                {
                    resolve: `gatsby-remark-draw`,
                    options: {
                        //strategy: "img",
                        mermaid: {
                            theme: 'default' // could also be dark, forest, or neutral
                        }
                    }
                },*/
        {
          resolve: `gatsby-remark-mermaid`,
        },
        /*{
                    resolve: `gatsby-remark-draw`
                },*/
        {
          resolve: `gatsby-remark-autolink-headers`,
          options: {
            enableCustomId: true,
          },
        },
        {
          resolve: `gatsby-remark-copy-linked-files`,
        },
        {
          resolve: "gatsby-remark-external-links",
          options: {
            target: "_blank",
            rel: null,
          },
        },
        {
          resolve: "gatsby-remark-custom-blocks",
          options: {
            blocks: {
              neutral: {
                classes: "neutral",
                title: "optional",
              },
              info: {
                classes: "info",
                title: "optional",
              },
              warning: {
                classes: "warning",
                title: "optional",
              },
            },
          },
        },
        {
          resolve: `gatsby-remark-prismjs`,
          options: {
            // Class prefix for <pre> tags containing syntax highlighting;
            // defaults to 'language-' (eg <pre class="language-js">).
            // If your site loads Prism into the browser at runtime,
            // (eg for use with libraries like react-live),
            // you may use this to prevent Prism from re-processing syntax.
            // This is an uncommon use-case though;
            // If you're unsure, it's best to use the default value.
            classPrefix: "language-",
            // This is used to allow setting a language for inline code
            // (i.e. single backticks) by creating a separator.
            // This separator is a string and will do no white-space
            // stripping.
            // A suggested value for English speakers is the non-ascii
            // character '›'.
            inlineCodeMarker: ">",
            // This lets you set up language aliases.  For example,
            // setting this to '{ sh: "bash" }' will let you use
            // the language "sh" which will highlight using the
            // bash highlighter.
            aliases: {},
            // This toggles the display of line numbers globally alongside the code.
            // To use it, add the following line in src/layouts/index.js
            // right after importing the prism color scheme:
            //  `require("prismjs/plugins/line-numbers/prism-line-numbers.css");`
            // Defaults to false.
            // If you wish to only show line numbers on certain code blocks,
            // leave false and use the {numberLines: true} syntax below
            showLineNumbers: false,
            // If setting this to true, the parser won't handle and highlight inline
            // code used in markdown i.e. single backtick code like `this`.
            noInlineHighlight: false,
            // This adds a new language definition to Prism or extend an already
            // existing language definition. More details on this option can be
            // found under the header "Add new language definition or extend an
            // existing language" below.
            languageExtensions: [],
            // Customize the prompt used in shell output
            // Values below are default
            prompt: {
              user: "root",
              host: "localhost",
              global: false,
            },
          },
        },
      ],
    },
  },
  `gatsby-plugin-glamor`,
  `gatsby-plugin-catch-links`,
];

if (googleFonts.length)
  plugins.push({
    resolve: `gatsby-plugin-webfonts`,
    options: {
      fonts: {
        google: googleFonts,
      },
    },
  });

module.exports = {
  pathPrefix: xtraplatform.pathPrefix,
  siteMetadata: {
    layout: "index",
  },
  plugins: plugins,
};
