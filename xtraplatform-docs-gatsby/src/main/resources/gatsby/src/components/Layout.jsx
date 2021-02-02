import React, { useState, useEffect } from "react";
import { useStaticQuery, graphql, Link, navigate } from "gatsby";
import glamorous, { ThemeProvider } from "glamorous";
import { Container, Row, Col } from "glamorous-grid";

import theme from "../theme";
import grid from "../theme/grid";
import { style, pathPrefix, srcDir } from "../../xtraplatform.json";

import Sidebar from "../components/Sidebar";
//import { trimExt } from "upath"

const Container3 = glamorous(Container)({
  //paddingLeft: '0 !important',
  //paddingRight: '0 !important',
  minHeight: "100%",
  height: "100%",
  overflow: "hidden",
});
/*
const Footer = glamorous.footer(
  {
    display: "flex",
    flexWrap: "wrap", // allow us to do the line break for collapsing content
    alignItems: "center",
    justifyContent: "space-between", //'space-between', // space out brand from logo
    paddingTop: "5rem",
    paddingBottom: "5rem",
    paddingLeft: "10rem",
    paddingRight: "10rem",
  },
  ({ theme }) => ({
    color: theme.colors.lightest,
    backgroundColor: theme.colors.primary,
  })
);

const FooterLink = glamorous(Link)(
  {
    textDecoration: "none",
    paddingLeft: "1rem",
  },
  ({ theme }) => ({
    color: theme.colors.lightest,
    ":hover, &.active": {
      color: theme.colors.secondary,
    },
  })
);
*/
const ColSidebar = glamorous(Col)(
  {
    height: "100%",
    overflow: "hidden",
    //boxShadow: '5px 0 10px rgba(50, 50, 50, 0.2)'
  },
  ({ theme }) => ({
    color: theme.colors.primary,
    //backgroundColor: theme.color.lighter
  })
);

const ColContent = glamorous(Col)({
  height: "100%",
  overflow: "auto",
  scrollBehavior: "smooth",
});

export const query = graphql`
  query RouteQuery {
    allMarkdownRemark(sort: { fields: [fields___key], order: ASC }) {
      edges {
        node {
          fields {
            key
            slug
          }
          headings {
            id
            value
            depth
          }
          frontmatter {
            category
          }
        }
      }
    }
  }
`;

const getCurrentLanguage = (path, languages) => {
  const pathLanguage = languages.find(
    (language) => path.indexOf(`/${language}/`) === 0
  );

  return (
    pathLanguage ||
    (typeof navigator !== `undefined` &&
      navigator &&
      navigator.language &&
      navigator.language.split("-")[0]) ||
    "en"
  );
};

const Layout = ({ children, location: loc }) => {
  const [scrollContent, setScrollContent] = useState();
  const data = useStaticQuery(query);
  const allRoutes = extractRoutes(data);
  const languages = extractLanguages(allRoutes);
  const useLanguages = languages.length > 0;
  const path =
    process.env.NODE_ENV === "development"
      ? loc.pathname
      : loc.pathname.substr(pathPrefix.length);
  const currentLanguage = getCurrentLanguage(path, languages);
  const location = {
    pathname: path,
    hash: loc.hash,
    root: useLanguages ? `/${currentLanguage}/` : "/",
  };
  const routes = useLanguages
    ? allRoutes.filter((route) => route.path.indexOf(location.root) === 0)
    : allRoutes;

  console.log("ROUTES", routes);
  console.log("LOCATION", location);

  useEffect(() => {
    if (useLanguages) {
      const hasPathLanguage = languages.some(
        (language) => path.indexOf(`/${language}/`) === 0
      );
      if (!hasPathLanguage) {
        console.log("SWITCH LANG", currentLanguage, path);
        navigate(`/${currentLanguage}${path}`, { replace: true });
      }
    }
  }, [useLanguages, currentLanguage, languages, path]);

  useEffect(() => {
    if (location.hash) {
      const el = document.querySelector(location.hash);
      //console.log('scroll to', el)
      if (el) {
        setTimeout(() => el.scrollIntoView(), 100);
      }
    }
  });

  return (
    <ThemeProvider theme={theme}>
      <Container3 fluid>
        <Row css={{ height: "100%" }}>
          <ColSidebar auto={{ sm: true }}>
            <Sidebar
              scrollContent={scrollContent}
              routes={routes}
              location={location}
            />
          </ColSidebar>
          <ColContent ref={setScrollContent} id="scroll-content">
            <ThemeProvider theme={{ ...grid }}>{children}</ThemeProvider>
          </ColContent>
        </Row>
      </Container3>
    </ThemeProvider>
  );
};

Layout.displayName = "Layout";

export default Layout;

function extractLanguages(routes) {
  const useLang = srcDir.indexOf("/{lng}") > 1;

  return useLang
    ? [
        ...new Set(
          routes
            .filter(
              (route) =>
                route.path.indexOf("/") > -1 &&
                route.path.indexOf("/") < route.path.lastIndexOf("/")
            )
            .map((route) =>
              route.path.substr(
                route.path.indexOf("/") + 1,
                route.path.indexOf("/", route.path.indexOf("/") + 1) - 1
              )
            )
        ),
      ]
    : [];
}

function extractRoutes(data) {
  const routes = [];

  data.allMarkdownRemark.edges.forEach((edge) => {
    const mainHeading = edge.node.headings.find((h) => h.depth <= 2);
    const subHeadings = edge.node.headings.filter(
      (h) => h.depth > mainHeading.depth && h.depth <= style.maxMenuDepth
    );
    const path = edge.node.fields.slug;
    const category = edge.node.frontmatter.category;
    let key = [path];

    routes.push({
      path: path,
      hash: mainHeading ? "#" + mainHeading.id : "",
      label: mainHeading.value,
      depth: mainHeading.depth,
      key: key.join(),
      category: category,
      nested: subHeadings.map((heading) => {
        const slug = heading.id;
        key[heading.depth - mainHeading.depth] = slug;
        key.splice(heading.depth - mainHeading.depth + 1);

        return {
          path: path,
          hash: "#" + slug,
          id: slug,
          label: heading.value,
          depth: heading.depth,
          key: key.join(),
        };
      }),
    });
  });

  return routes;
}
