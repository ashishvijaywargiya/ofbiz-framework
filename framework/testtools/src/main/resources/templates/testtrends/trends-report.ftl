<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Test Trend Report - ${suiteName?html}</title>
<link rel="stylesheet" href="test-report.css">
</head>
<body>
<div class="navbar-report"><h1>Trend report: ${suiteName?html}</h1></div>
<div class="container">
<div class="summary">${runCount} run(s) archived<#if filteredRunCount gt 0>, ${filteredRunCount} filtered/partial (excluded from the stats below)</#if></div>
<#if notEnoughHistory>
<p>Not enough history yet - need at least 2 archived runs.</p>
<#else>
<div class="stat-cards">
<div class="stat-card"><div class="stat-value">${failureRateText}</div><div class="stat-label">Failure rate</div></div>
<div class="stat-card"><div class="stat-value">${streakDirection?html} x${streakLength}</div><div class="stat-label">Streak</div></div>
<div class="stat-card"><div class="stat-value">${averageDurationText}</div><div class="stat-label">Average duration</div></div>
</div>

<h2>Pass/fail over time</h2>
<p class="chart-caption">Each dot is one archived run, oldest to newest, left to right. Hover a dot for its date and outcome.</p>
<div class="legend">
<span class="legend-item"><span class="legend-dot legend-dot-pass"></span>Passed</span>
<span class="legend-item"><span class="legend-dot legend-dot-fail"></span>Failed</span>
</div>
<#include "pass-fail-chart.ftl">

<h2>Duration over time</h2>
<p class="chart-caption">Each dot is one archived run's duration, oldest to newest, left to right. The muted horizontal line marks the average across all full runs; amber-ringed dots deviated from it by more than the configured threshold. Hover a dot for its exact value.</p>
<div class="legend">
<span class="legend-item"><span class="legend-line"></span>Duration</span>
<span class="legend-item"><span class="legend-ring"></span>Deviation flagged</span>
</div>
<#include "duration-chart.ftl">

<#include "runs-table.ftl">

<h2>Flag legend</h2>
<ul>
<li><strong>filtered</strong> - this run only executed a narrowed-down subset of the suite (a --tests filter, or suitename=/component=/case=/method= for testIntegration), not the whole suite. See its own Filter column for which one. Excluded from the failure rate, streak, duration baseline, and the two drift flags below.</li>
<li><strong>duration</strong> - this run's duration differed from the average duration of full runs by more than the configured threshold (test.trend.duration.deviation.percent, default 25%), either slower or faster.</li>
<li><strong>count-decrease</strong> - this run executed fewer tests in total than the previous full run - possibly tests were skipped, removed, or the run didn't complete.</li>
<li><strong>skipped-increase</strong> - this run had more skipped tests than the previous full run - possibly new @Disabled/@Ignore tests or an environment issue.</li>
</ul>
</#if>
</div>
</body>
</html>
