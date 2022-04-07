/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.interactive_instruments.xtraplatform.pmd

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.specs.Spec
import org.gradle.internal.Factory
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.ConsoleRenderer

/**
 * modified version of https://github.com/gradle/gradle/blob/v7.4.2/subprojects/code-quality/src/main/groovy/org/gradle/api/plugins/quality/internal/PmdInvoker.groovy
 * added sarif format
 */
abstract class PmdInvokerSarif {
    static void invoke(Pmd pmdTask) {
        def pmdClasspath = pmdTask.pmdClasspath.filter(new FileExistFilter())
        def targetJdk = pmdTask.targetJdk
        def ruleSets = pmdTask.ruleSets
        def rulePriority = pmdTask.rulesMinimumPriority.get()
        def antBuilder = pmdTask.antBuilder
        def source = pmdTask.source
        def ruleSetFiles = pmdTask.ruleSetFiles
        def ruleSetConfig = pmdTask.ruleSetConfig
        def classpath = pmdTask.classpath?.filter(new FileExistFilter())
        def reports = pmdTask.reports
        def consoleOutput = pmdTask.consoleOutput
        def stdOutIsAttachedToTerminal = consoleOutput ? pmdTask.stdOutIsAttachedToTerminal() : false
        def ignoreFailures = pmdTask.ignoreFailures
        def maxFailures = pmdTask.maxFailures.get()
        def logger = pmdTask.logger
        def incrementalAnalysis = pmdTask.incrementalAnalysis.get()
        def incrementalCacheFile = pmdTask.incrementalCacheFile

        // PMD uses java.class.path to determine it's implementation classpath for incremental analysis
        // Since we run PMD inside the Gradle daemon, this pulls in all of Gradle's runtime.
        // To hide this from PMD, we override the java.class.path to just the PMD classpath from Gradle's POV.
        if (incrementalAnalysis) {
            SystemProperties.instance.withSystemProperty("java.class.path", pmdClasspath.files.join(File.pathSeparator), new Factory<Void>() {
                @Override
                Void create() {
                    runPmd(antBuilder, pmdClasspath, rulePriority, targetJdk, ruleSets, incrementalAnalysis, incrementalCacheFile, maxFailures, source, ruleSetFiles, ruleSetConfig, classpath, reports, consoleOutput, stdOutIsAttachedToTerminal, ignoreFailures, logger)
                    return null
                }
            })
        } else {
            runPmd(antBuilder, pmdClasspath, rulePriority, targetJdk, ruleSets, incrementalAnalysis, incrementalCacheFile, maxFailures, source, ruleSetFiles, ruleSetConfig, classpath, reports, consoleOutput, stdOutIsAttachedToTerminal, ignoreFailures, logger)
        }
    }

    private static runPmd(IsolatedAntBuilder antBuilder, FileCollection pmdClasspath, rulePriority, targetJdk, ruleSets, incrementalAnalysis, incrementalCacheFile, maxFailures, source, ruleSetFiles, ruleSetConfig, classpath, reports, consoleOutput, stdOutIsAttachedToTerminal, ignoreFailures, logger) {
        antBuilder.withClasspath(pmdClasspath).execute { a ->
            def antPmdArgs = [
                    failOnRuleViolation : false,
                    failuresPropertyName: "pmdFailureCount",
                    minimumPriority     : rulePriority,
            ]
            if (incrementalAnalysis) {
                antPmdArgs["cacheLocation"] = incrementalCacheFile
            } else {
                antPmdArgs['noCache'] = true
            }
            if (maxFailures < 0) {
                throw new GradleException("Invalid maxFailures $maxFailures. Valid range is >= 0.")
            }

            ant.taskdef(name: 'pmd', classname: 'net.sourceforge.pmd.ant.PMDTask')
            ant.pmd(antPmdArgs) {
                source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                ruleSets.each {
                    ruleset(it)
                }
                ruleSetFiles.each {
                    ruleset(it)
                }
                if (ruleSetConfig != null) {
                    ruleset(ruleSetConfig.asFile())
                }

                if (classpath != null) {
                    classpath.addToAntBuilder(ant, 'auxclasspath', FileCollection.AntType.ResourceCollection)
                }

                if (reports.html.required.get()) {
                    assert reports.html.outputLocation.asFile.get().parentFile.exists()
                    formatter(type: 'html', toFile: reports.html.outputLocation.asFile.get())
                    formatter(type: 'sarif', toFile: new File(reports.html.outputLocation.asFile.get().absolutePath.replace(".html", ".json")))
                }
                if (reports.xml.required.get()) {
                    formatter(type: 'xml', toFile: reports.xml.outputLocation.asFile.get())
                }

                if (consoleOutput) {
                    def consoleOutputType = 'text'
                    if (stdOutIsAttachedToTerminal) {
                        consoleOutputType = 'textcolor'
                    }
                    a.builder.saveStreams = false
                    formatter(type: consoleOutputType, toConsole: true)
                }
            }
            def failureCount = ant.project.properties["pmdFailureCount"]
            if (failureCount) {
                def message = "$failureCount PMD rule violations were found."
                def report = reports.firstEnabled
                if (report) {
                    def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.outputLocation.asFile.get())
                    message += " See the report at: $reportUrl"
                }
                if (ignoreFailures || ((failureCount as Integer) <= maxFailures)) {
                    logger.warn(message)
                } else {
                    throw new GradleException(message)
                }
            }
        }
    }

    private static class FileExistFilter implements Spec<File> {
        @Override
        boolean isSatisfiedBy(File element) {
            return element.exists()
        }
    }
}