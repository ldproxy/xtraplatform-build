import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { mermaidAPI } from "mermaid";
import glamorous from "glamorous";
import color from "color";

if (typeof mermaidAPI !== "undefined") {
  mermaidAPI.initialize({
    startOnLoad: false,
    securityLevel: "loose",
    flowchart: {
      htmlLabels: false,
      curve: "basis",
    },
  });
}

const renderJobs = {};
const renderGraph = (id, graph, callback) => {
  renderJobs[id] = {
    id: id,
    graph: graph,
    callback: callback,
    status: "tbd",
  };
  checkJobs();
};
const bindGraph = (id, container) => {
  if (renderJobs[id] && container) {
    console.log("MM BIND");
    const job = renderJobs[id];
    if (job.status === "running") {
      job.bindFunctions(container);
      job.status = "done";
      delete renderJobs[id];
      checkJobs();
    }
  }
};
const checkJobs = () => {
  if (Object.keys(renderJobs).length > 0) {
    const job = renderJobs[Object.keys(renderJobs)[0]];
    if (job.status === "tbd") {
      job.status = "running";
      console.log(job.graph);
      mermaidAPI.render(job.id, job.graph, (svg, bindFunctions) => {
        job.bindFunctions = bindFunctions;
        job.callback(svg);
      });
    }
  }
};

const Graph = glamorous.div({}, ({ theme, children }) => ({
  [`& #${children.props.name} .node rect`]: {
    fill: theme.colors.primaryLight,
    stroke: theme.colors.primary,
  },
  [`& #${children.props.name} .node text`]: {
    fill: theme.colors.dark,
  },
  [`& #${children.props.name} .cluster rect`]: {
    fill: theme.colors.lighter + "!important",
    stroke: theme.colors.secondary + "!important",
    //width: children.props.direction === 'TD' ? '90%' : null
  },
  [`& #${children.props.name} .cluster text`]: {
    fill: theme.colors.secondary + "!important",
  },
  [`& #${children.props.name} .edgeLabel rect`]: {
    fill: theme.colors.lightest + "!important",
  },
  [`& #${children.props.name} .edgeLabel text`]: {
    fill: theme.colors.dark,
  },
  [`& #${children.props.name} .light rect`]: {
    fill: theme.colors.lighter,
    stroke: theme.colors.secondary,
  },
  [`& #${children.props.name} .light.clickable:hover rect`]: {
    fill: theme.colors.primaryLight,
    stroke: theme.colors.secondary,
  },
  [`& #${children.props.name} .red rect`]: {
    fill: color("red").fade(0.6),
    stroke: theme.colors.secondary,
  },
  [`& #${children.props.name} .blue rect`]: {
    fill: color("blue").fade(0.6),
    stroke: theme.colors.secondary,
  },
  [`& #${children.props.name} .yellow rect`]: {
    fill: color("yellow").fade(0.6),
    stroke: theme.colors.secondary,
  },
  [`& #${children.props.name} .wide rect`]: {
    width: "90%",
  },
}));

const Mermaid = ({ name, children }) => {
  const [svg, setSvg] = useState(null);
  const [container, setContainer] = useState(null);
  const graph = children[0];

  useEffect(() => {
    console.log("MM RENDER", name, graph);
    renderGraph(name, graph, setSvg);
  }, [name, graph]);

  useEffect(() => {
    bindGraph(name, container);
  }, [name, container]);

  return (
    <Graph>
      <div
        ref={setContainer}
        name={name}
        direction={
          children[0].match(/graph ([A-Z]{2})/) &&
          children[0].match(/graph ([A-Z]{2})/)[1]
        }
        dangerouslySetInnerHTML={{ __html: svg || "Loading..." }}
      />
    </Graph>
  );
};

Mermaid.propTypes = {
  name: PropTypes.string.isRequired,
};

export default Mermaid;
