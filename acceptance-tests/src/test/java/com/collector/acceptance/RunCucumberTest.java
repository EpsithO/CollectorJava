package com.collector.acceptance;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Lanceur JUnit Platform des scénarios Gherkin. Glue, filtre (`not @wip`) et plugins sont dans
 * junit-platform.properties. Lancer une seule user story : -Dcucumber.filter.tags="@US-029".
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class RunCucumberTest {
}
