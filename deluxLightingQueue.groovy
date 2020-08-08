/**
 *  Import Url:   https://raw.githubusercontent.com/arnbme/hubitat/master/deluxLightingQueue.groovy
 *
 *  deluxLightingQueue Child App
 *  Functions:
 *
 *		1. child app to automate creating device queue code rather than have to hand code in parent module
 *
 *  Copyright 2020 Arn Burkhoff
 *
 * 	Changes to Apache License
 *	4. Redistribution. Add paragraph 4e.
 *	4e. This software is free for Private Use. All derivatives and copies of this software must be free of any charges,
 *	 	and cannot be used for commercial purposes.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	Aug 08, 2020 v1.0.0 create from deluxLighting code
 */

definition(
    name: "deluxLightingQueue",
    namespace: "arnbme",
    author: "Arn Burkhoff",
    description: "(${version()}) Delux Lighting Queue Child app",
    parent: "arnbme:deluxLighting",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/arnbme/hubitat/master/deluxLightingQueue.groovy")

preferences {
    page(name: "mainPage")
	}

def version()
	{
	return "1.0.0";
	}

def mainPage()
	{
	dynamicPage(name: "mainPage", title: "(V${version()}) ${app.label} Lux Lighting Queue", install: true, uninstall: true)
		{
		section
			{
			}
		}
	}

void installed() {}
void updated() {}
void initialize() {}

//	methods are executed by parent deluxLighting app
void runInQueue(seconds, qName, lightIndex, lightId, triggerSettingName, triggerIndex, triggerId)
	{
	if (parent.logDebugs) log.debug "{app.label} runInQueue: seconds:$seconds, qName:$qName, lightIndex:$lightIndex,lightId:$lightId, triggerSettingName:$triggerSettingName, triggerIndex:$triggerIndex, triggerId:$triggerId"
	runIn (seconds, "qOffHandler",
					[data: [lightIndex: lightIndex,
					lightId: lightId,
					triggerSettingName: triggerSettingName,
					triggerIndex: triggerIndex,
					triggerId: triggerId]])
	}

void qOffHandler(mapData=false)
	{
	if (parent.logDebugs) log.debug "${app.label} qOffHandler entered ${mapData}"
	parent.qOffHandler(mapData)
	}