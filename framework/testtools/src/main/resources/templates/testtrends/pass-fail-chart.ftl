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
<#-- Renders passFailChart (a TestTrendChartData.PassFailChart, from the enclosing model) - null means no runs to chart. -->
<#if passFailChart??>
<svg width="${passFailChart.width}" height="${passFailChart.height}"
        viewBox="0 0 ${passFailChart.width} ${passFailChart.height}"
        xmlns="http://www.w3.org/2000/svg" class="trend-chart">
<line x1="10" y1="${passFailChart.baselineY?string('0.0')}" x2="${passFailChart.width - 10}"
        y2="${passFailChart.baselineY?string('0.0')}" class="trend-baseline"/>
<#list passFailChart.points as point>
<circle cx="${point.x?string('0.0')}" cy="${point.y?string('0.0')}" r="12" fill="transparent">
<title>${point.tooltip?html}</title></circle>
<circle cx="${point.x?string('0.0')}" cy="${point.y?string('0.0')}" r="6" class="trend-dot-${point.status}">
<title>${point.tooltip?html}</title></circle>
</#list>
<#list passFailChart.ticks as tick>
<text x="${tick.x?string('0.0')}" y="${passFailChart.ticksY?string('0.0')}" class="trend-tick-label"
        text-anchor="middle">${tick.label?html}</text>
</#list>
</svg>
<#else>
<p>No runs to chart yet.</p>
</#if>
