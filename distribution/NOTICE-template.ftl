<#--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
Apache Seata (incubating)
Copyright 2019-2025 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).

================================================================================

The following dependencies are included in this binary package:

<#if dependencyMap?size == 0>
No dependencies found.
<#else>
<#list dependencyMap as entry>
<#assign project = entry.getKey()/>
<#assign licenses = entry.getValue()/>
    - ${project.name} (${project.groupId}:${project.artifactId}:${project.version})
<#if licenses?size != 0>
<#list licenses as license>
      License: ${license}
</#list>
<#else>
      License: Not specified
</#if>

</#list>
</#if> 