import React, { memo, Fragment } from "react";
import glamorous from "glamorous";

import { Link, useStaticQuery, graphql } from "gatsby";
import Img from "gatsby-image";
import Scrollspy from "react-scrollspy";

import theme from "../theme";
import { title, style } from "../../xtraplatform.json";

const Navbar = glamorous.nav({
  position: "relative",
  display: "flex",
  flexDirection: "column",
  //flexWrap: 'wrap', // allow us to do the line break for collapsing content
  alignItems: "left",
  justifyContent: "spaceBetween", // space out brand from logo
  height: "100%",
  padding: "2rem 2rem",
  overflow: "auto",
  //opacity: 0.9,
  /*'@supports (position: sticky)': {
        position: 'sticky',
        top: 0,
        zIndex: 1020
    }*/
});

const NavContent = glamorous.div({
  display: "flex",
  flexDirection: "column",
  //flexWrap: 'wrap', // allow us to do the line break for collapsing content
  alignItems: "left",
  justifyContent: "center", //'space-between', // space out brand from logo
});

const NavItems = glamorous.ul({
  display: "flex",
  flexDirection: "column",
  padding: 0,
  margin: 0,
  listStyle: "none",
});

const NavItem = glamorous.li(
  {
    margin: 0,
    marginBottom: "0.75em",
  },
  ({ theme }) => ({
    color: theme.inverted ? theme.colors.lightest : theme.colors.secondary,
    ":hover, &.active": {
      color: theme.inverted ? theme.colors.accent : theme.colors.primary,
    },
  })
);

const NavCategory = glamorous.li(
  {
    margin: 0,
    marginBottom: "1.25em",
  },
  ({ theme, space }) => ({
    color: theme.inverted ? theme.colors.lightest : theme.colors.secondary,
    ":hover, &.active": {
      color: theme.inverted ? theme.colors.accent : theme.colors.primary,
    },
    marginTop: space ? "1.25em" : 0,
  })
);

const NavLink = glamorous(Link)(
  {
    transition: "all 0.4s ease-in-out",
    paddingLeft: "1rem",
    textDecoration: "none",
    //fontFamily: 'Sansation Regular',
    //textTransform: 'uppercase',
    //fontWeight: 'bold',
    display: "block",
    borderLeft: "2px solid transparent",
  },
  ({ theme }) => ({
    color: theme.colors.secondary,
    ":hover, &.active": {
      color: theme.colors.primary,
      textDecoration: "none",
      borderLeftColor: theme.colors.primary,
    },
    /*'&:not(.active) + ul': {
        display: 'none'
    }*/
  })
);

const NavSection = glamorous.a(
  {
    transition: "all 0.4s ease-in-out",
    paddingLeft: "1rem",
    textDecoration: "none",
    //fontFamily: 'Sansation Regular',
    //textTransform: 'uppercase',
    //fontWeight: 'bold',
    display: "block",
    borderLeft: "2px solid transparent",
  },
  ({ theme }) => ({
    color: theme.colors.secondary,
    ":hover, &.active": {
      color: theme.colors.primary,
      textDecoration: "none",
      borderLeftColor: theme.colors.primary,
    },
    /*'&:not(.active) + ul': {
        display: 'none'
    }*/
  })
);

const NavScrollspy = glamorous(Scrollspy)({
  display: "flex",
  flexDirection: "column",
  padding: 0,
  margin: 0,
  marginTop: 0,
  listStyle: "none",
});

const NavSubItems = glamorous.ul({
  display: "flex",
  flexDirection: "column",
  padding: 0,
  margin: 0,
  marginTop: 0,
  listStyle: "none",
});

const NavSubItem = glamorous.li(
  {
    transition: "all 0.4s ease-in-out",
    margin: 0,
    borderLeft: "2px solid " + theme.colors.primaryLight,
  },
  ({ theme }) => ({
    ":hover, &.active": {
      borderLeftColor: theme.colors.primary,
    },
  })
);

const NavSubLink = glamorous.a(
  {
    transition: "all 0.4s ease-in-out",
    paddingLeft: "1.5rem",
    textDecoration: "none",
    fontSize: "0.9em",
  },
  ({ theme }) => ({
    color: theme.colors.secondary,
    ":hover, .active &": {
      color: theme.colors.primary,
      textDecoration: "none",
    },
  })
);

const Title = glamorous.h1(
  {
    margin: 0,
    //marginTop: '0.5rem',
    marginBottom: "2rem",
    marginLeft: "1rem",
    border: "0 solid",
  },
  ({ theme }) => ({
    color: theme.colors.primary,
  })
);

const Logo = glamorous.div(
  {
    margin: 0,
    //marginTop: '0.5rem',
    marginBottom: "2rem",
    marginLeft: "1rem",
    border: "0 solid",
  },
  ({ theme }) => ({
    color: theme.colors.primary,
  })
);

const Category = glamorous.h4(
  {
    margin: 0,
    //marginTop: '0.5rem',
    marginBottom: "1rem",
    marginLeft: "1rem",
    border: "0 solid",
    textTransform: "uppercase",
  },
  ({ theme }) => ({
    color: theme.colors.lighter,
  })
);

const isImgEqual = ({ fixed: prevFixed }, { fixed: nextFixed }) => {
  const prevKeys = Object.keys(prevFixed);
  const nextKeys = Object.keys(nextFixed);

  if (prevKeys.length !== nextKeys.length) {
    //console.log('NOT EQUAL', prefixed, nextFixed);
    return false;
  }
  for (let key of prevKeys) {
    if (prevKeys[key] !== nextKeys[key]) {
      //console.log('NOT EQUAL', prefixed, nextFixed);
      return false;
    }
  }
  //console.log('EQUAL');
  return true;
};

const ImgMemo = memo((props) => <Img {...props} />, isImgEqual);

const logoQuery = graphql`
  query LogoQuery {
    file(fields: { itemType: { eq: "LOGO" } }) {
      childImageSharp {
        # Specify the image processing specifications right in the query.
        fixed(height: 32, quality: 80) {
          ...GatsbyImageSharpFixed_withWebp_noBase64
        }
      }
    }
  }
`;
const isRouteActive = (to, location, activeOnRoot) =>
  to.indexOf(location.pathname) === 0 &&
  (location.pathname !== location.root || activeOnRoot);

export default ({ routes, scrollContent, location }) => {
  //console.log('SC', scrollContent)

  let logoImg;
  if (style.logo) {
    logoImg = useStaticQuery(logoQuery);
    //console.log('LOGO', logoImg.file.childImageSharp.fixed)
  }

  const h1 = routes.find((route) => route.depth === 1);
  const titleText = title.length ? title : h1 ? h1.label : null;
  let isFirstRoute = 1;
  return (
    <Navbar>
      <NavContent>
        {logoImg ? (
          <Logo>
            <ImgMemo
              fixed={logoImg.file.childImageSharp.fixed}
              fadeIn={false}
              loading="eager"
            />
          </Logo>
        ) : (
          titleText && <Title>{titleText}</Title>
        )}
        <NavItems>
          {/*routes.map(route => route.depth === 1 &&
                    <>
                        {route.category && <NavCategory key={route.key + route.category}>
                            <Category>{route.category}</Category>
                        </NavCategory>}
                        {route.nested.map(nested => <NavItem key={nested.key}>
                            <NavLink to={nested.path + nested.hash} className={isRouteActive(nested.path + nested.hash, location, location.pathname === '/') && 'active'}>
                                {nested.label}
                            </NavLink>
                        </NavItem>)}
                    </>
                        )*/}
          {routes.map(
            (route, index) =>
              route.depth > 0 && (
                <Fragment key={route.key}>
                  {route.category && (
                    <NavCategory
                      key={route.key + route.category}
                      space={index > 0}
                    >
                      <Category>{route.category}</Category>
                    </NavCategory>
                  )}
                  <NavItem key={route.key}>
                    {location.pathname === route.path ? (
                      <NavSection
                        href={route.hash}
                        className={
                          isRouteActive(
                            route.path + route.hash,
                            location,
                            isFirstRoute &&
                              isFirstRoute-- &&
                              location.pathname === location.root
                          ) && "active"
                        }
                      >
                        {route.label}
                      </NavSection>
                    ) : (
                      <NavLink
                        to={route.path}
                        className={
                          isRouteActive(
                            route.path,
                            location,
                            isFirstRoute &&
                              isFirstRoute-- &&
                              location.pathname === "/"
                          ) && "active"
                        }
                      >
                        {route.label}
                      </NavLink>
                    )}
                    {scrollContent && location.pathname === route.path ? (
                      <NavScrollspy
                        items={route.nested.map((nested) => nested.id)}
                        rootEl={
                          scrollContent ? "#" + scrollContent.props.id : null
                        }
                        currentClassName="active"
                      >
                        {route.nested.map((nested) => (
                          <NavSubItem
                            css={{
                              paddingLeft: (nested.depth - 3) * 0.5 + "rem",
                            }}
                            key={nested.key}
                          >
                            <NavSubLink href={nested.hash}>
                              {nested.label}
                            </NavSubLink>
                          </NavSubItem>
                        ))}
                      </NavScrollspy>
                    ) : (
                      <NavSubItems>
                        {route.nested.map((nested) => (
                          <NavSubItem
                            css={{
                              height: "0px",
                              overflow: "hidden",
                              paddingLeft: (nested.depth - 3) * 0.5 + "rem",
                            }}
                            key={nested.key}
                          >
                            <NavSubLink
                              href={nested.hash}
                              css={{ height: "0px", overflow: "hidden" }}
                            >
                              {nested.label}
                            </NavSubLink>
                          </NavSubItem>
                        ))}
                      </NavSubItems>
                    )}
                  </NavItem>
                </Fragment>
              )
          )}
        </NavItems>
      </NavContent>
    </Navbar>
  );
};
