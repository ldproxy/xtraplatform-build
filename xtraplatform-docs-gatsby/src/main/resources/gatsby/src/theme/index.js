import { css } from "glamor";
import color from "color";
import deepExtend from "deep-extend";
import { noGutter } from "./grid";
import { style as customTheme } from "../../xtraplatform.json";

const defaultTheme = {
  inverted: false,
  logoTop: false,
  colors: {
    primary: "#469c95",
    //primary: 'rgb(130,205,177)',
    secondary: "#777777",
    accent: "#ee7103",
    lightest: "#fff",
    lighter: "rgb(39,40,51,0.3)",
    light: "#999",
    dark: "#333333",
    darkest: color("#444").darken(0.3),
    primaryLight: color("#469c95").lighten(1), //color('#469c95').fade(0.6),
    info: "#5bc0de",
    warning: "#f0ad4e",
    neutral: "#999",
  },
  typography: "github",
  ...noGutter,
};

const theme = deepExtend(defaultTheme, customTheme);

css.global("html, body, #___gatsby, #gatsby-focus-wrapper", {
  padding: 0,
  margin: 0,
  //position: 'relative',
  minHeight: "100%",
  height: "100%",
  overflow: "hidden",
});

css.global("body", {
  color: theme.colors.secondary,
});

css.global("h1,h2,h3,h4,h5,h6", {
  color: theme.colors.dark,
});

css.global("a", {
  color: theme.colors.primary,
  textDecoration: "none",
  backgroundImage: "none !important",
  boxShadow: "none !important",
});

css.global(".gatsby-resp-image-wrapper", {
  marginLeft: "0px !important ",
  //border: `1px solid ${theme.colors.secondary}`,
  boxShadow: "rgba(0, 0, 0, 0.16) 0px 1px 4px",
});

css.global(".gatsby-resp-image-figcaption", {
  fontStyle: "italic",
  fontSize: "14px",
  paddingLeft: "8px",
});
/*
css.global('code', {
    backgroundColor: color(theme.colors.neutral).fade(0.9),
    borderRadius: '3px',
    fontFamily: 'Consolas,"Roboto Mono","Liberation Mono",Menlo,Courier,monospace',
    padding: '0.2em',
})
*/
/*
css.global('pre[class*="language-"]', {
    paddingLeft: '0px !important',
})
*/
css.global(".custom-block", {
  padding: "1rem",
  borderLeft: ".25rem solid",
  "& p": {
    marginBottom: "0px",
  },
});
css.global(".custom-block p", {
  marginBottom: "0px",
});
css.global(".custom-block.info", {
  backgroundColor: color(theme.colors.info).fade(0.9),
  borderLeftColor: theme.colors.info,
});
css.global(".custom-block.info .custom-block-heading", {
  color: theme.colors.info,
  fontWeight: "bold",
});
css.global(".custom-block.warning", {
  backgroundColor: color(theme.colors.warning).fade(0.9),
  borderLeftColor: theme.colors.warning,
});
css.global(".custom-block.warning .custom-block-heading", {
  color: theme.colors.warning,
  fontWeight: "bold",
});
css.global(".custom-block.neutral", {
  padding: "0.5rem 1rem",
  marginBottom: "0.5rem",
  backgroundColor: color(theme.colors.neutral).fade(0.9),
  borderLeftColor: theme.colors.neutral,
});
css.global(".custom-block.neutral .custom-block-heading", {
  color: theme.colors.neutral,
  fontWeight: "bold",
});

export default theme;
