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
<#-- Renders durationChart (a TestTrendChartData.DurationChart, from the enclosing model). Null
     means no runs to chart at all; non-null with hasData=false means runs exist but none recorded
     a duration - two different fallback messages for two different situations. -->
<#if !durationChart??>
<p>No runs to chart yet.</p>
<#elseif !durationChart.hasData>
<p>No duration data recorded for any archived run yet.</p>
<#else>
<svg width="${durationChart.width}" height="${durationChart.height}"
        viewBox="0 0 ${durationChart.width} ${durationChart.height}"
        xmlns="http://www.w3.org/2000/svg" class="trend-chart">
<line x1="${durationChart.plotLeft}" y1="${durationChart.minLabel.y?string('0.0')}" x2="${durationChart.plotRight}"
        y2="${durationChart.minLabel.y?string('0.0')}" class="trend-gridline"/>
<text x="0" y="${(durationChart.minLabel.y + 3)?string('0.0')}" class="trend-axis-label">${durationChart.minLabel.text?html}</text>
<text x="0" y="${(durationChart.maxLabel.y + 3)?string('0.0')}" class="trend-axis-label">${durationChart.maxLabel.text?html}</text>
<#if durationChart.avgLine??>
<line x1="${durationChart.plotLeft}" y1="${durationChart.avgLine.y?string('0.0')}" x2="${durationChart.plotRight}"
        y2="${durationChart.avgLine.y?string('0.0')}" class="trend-avg-line"/>
<text x="${durationChart.width - 8}" y="${(durationChart.avgLine.y - 3)?string('0.0')}" class="trend-axis-label"
        text-anchor="end">${durationChart.avgLine.text?html}</text>
</#if>
<polyline points="<#list durationChart.points as point>${point.x?string('0.0')},${point.y?string('0.0')}<#if point_has_next> </#if></#list>"
        fill="none" class="trend-line"/>
<#list durationChart.points as point>
<circle cx="${point.x?string('0.0')}" cy="${point.y?string('0.0')}" r="12" fill="transparent">
<title>${point.tooltip?html}</title></circle>
<circle cx="${point.x?string('0.0')}" cy="${point.y?string('0.0')}" r="${point.flagged?then('6','4')}"
        class="trend-dot-duration<#if point.flagged> trend-dot-duration-flagged</#if>">
<title>${point.tooltip?html}</title></circle>
</#list>
<#list durationChart.ticks as tick>
<text x="${tick.x?string('0.0')}" y="${durationChart.ticksY?string('0.0')}" class="trend-tick-label"
        text-anchor="middle">${tick.label?html}</text>
</#list>
</svg>
</#if>
