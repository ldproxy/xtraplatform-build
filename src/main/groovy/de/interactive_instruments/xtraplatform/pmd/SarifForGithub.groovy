package de.interactive_instruments.xtraplatform.pmd

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class SarifForGithub {
    enum Severity {
        error, warning, recommendation
    }

    static def prepare(File sarifFile, String rootPath, String projectName, String sourceSetName, Severity severity) {
        def sarif = new JsonSlurper().parse(sarifFile)
        def run = sarif.runs[0]
        def ruleGroups = run.tool.driver.rules.groupBy { it.id }
        def ruleIndexes = ruleGroups.keySet() as List

        ruleGroups.each {
            it.value[0].shortDescription.text = "${it.value[0].id} (${it.value[0].properties.ruleset})"
            it.value[0].defaultConfiguration = [level: severity.name()]
            it.value[0].properties.problem = [severity: severity.name()]
        }
        run.tool.driver.rules = ruleGroups.collect { [it.value[0]] }.flatten()

        run.results.each {
            it.ruleIndex = ruleIndexes.indexOf(it.ruleId)
            it.locations[0].physicalLocation.artifactLocation.uri =
                    it.locations[0].physicalLocation.artifactLocation.uri.replaceAll(rootPath, "")
        }

        def category = "${projectName}/${sourceSetName}/"
        run.automationDetails = [id: category]

        def sarifText = JsonOutput.prettyPrint(JsonOutput.toJson(sarif))

        sarifFile.text = sarifText
    }
}
