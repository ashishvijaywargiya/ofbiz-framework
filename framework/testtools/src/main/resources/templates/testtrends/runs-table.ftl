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
<#-- Renders runRows (a List<TestTrendReportModel.RunRow>, from the enclosing model) newest-first. -->
<h2>Runs (newest first<#if displayedRunCount < totalRunCount>, showing latest ${displayedRunCount} of ${totalRunCount}</#if>)</h2>
<table class="bs-table">
<thead><tr><th>Archived at</th><th>Outcome</th><th>Total</th><th>Failed</th><th>Skipped</th><th>Duration</th><th>Flags</th><th>Filter</th></tr></thead>
<tbody>
<#list runRows as row>
<tr<#if row.run.filtered> class="row-filtered"</#if>>
<td>${row.run.archivedAt?html}</td>
<td>${row.run.outcome?html}</td>
<td><#if row.run.counts??>${row.run.counts.total}<#else>-</#if></td>
<td><#if row.run.counts??>${row.run.counts.failed}<#else>-</#if></td>
<td><#if row.run.counts??>${row.run.counts.skipped}<#else>-</#if></td>
<td>${row.durationText?html}</td>
<td class="flag"><#list row.flags as flag><#if flag.muted><span class="flag-filtered">${flag.label?html}</span><#else>${flag.label?html}</#if><#if flag_has_next>, </#if></#list></td>
<td><#if row.run.filterDetail??>${row.run.filterDetail?html}</#if></td>
</tr>
</#list>
</tbody>
</table>
