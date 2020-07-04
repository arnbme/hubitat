/**
 *  Import Url:   https://raw.githubusercontent.com/arnbme/hubitat/master/tvWrangler.groovy
 *
 *  tvWrangler App
 *  Functions:
 *		Controls on and off for TV, Cable Box, and sound system using a virtual switch displayed on a dashboard
 *           triggering an Ir Blaster
 *      Stops sound system popping by controlling sequence of on and off. Logic copied from replaced RM rules.
 *		Allow 10 seconds for TV to fully turn on before it responds to Off commands
 *		Allow 3 seconds for TV to fully turn off before it responds to On commands
 *      Just to much of a pain doing this with Hubitat Rule Machine
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
 *  Jul 04, 2020 v0.0.0 Create
 */

definition(
    name: "tvWrangler",
    namespace: "arnbme",
    author: "Arn Burkhoff",
    description: "(${version()}) TV Wrangler app",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/arnbme/hubitat/master/tvWrangler.groovy")

preferences {
    page(name: "mainPage")
	}

def version()
	{
	return "0.0.0";
	}

def mainPage()
	{
	dynamicPage(name: "main", title: "Mini Split Settings", install: true, uninstall: true)
		{
		section
			{
			input "globalDisable", "bool", required: true, defaultValue: false,
				title: "Disable All Functions. Default: Off/False"
			input "logDebugs", "bool", required: true, defaultValue: false,
				title: "Do debug logging. Shuts off after 30 minutes Default: Off/False"
			input "globalIrBlaster", "capability.actuator", required: true, multiple: false,
				title: "One IR Blaster"
			input "globalVirtualSwitch", "capability.switch", required: true, multiple: false,
				title: "One Virual Switch"
			}
		}
	}

def installed() {
    log.info "Installed with settings: ${settings}"
    state.lastTime= now()
    state.lastState='unknown'
	initialize()
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize()
	{
	if(settings.logDebugs)
		runIn(1800,logsOff)			// turns off debug logging after 30 min
	else
		unschedule(logsOff)
	if (globalDisable)
		{}
	else
		{
		subscribe(globalVirtualSwitch, "switch", switchHandler)
		}
	}

void logsOff(){
//	stops debug logging
	log.info "tvWrangler: debug logging disabled"
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}

void switchHandler(evt)
	{
	if (settings.logDebugs) log.debug  "tvWrangler: switchHandler entered Value: ${evt.value}"
	if (state?.lastState == 'on' && (now() - state.lastTime) < 10000)
		{
		if (evt.value == 'off')
			globalVirtualSwitch.on()
		}
	else
	if (state?.lastState == 'off' && (now() - state.lastTime) < 3000)
		{
		if (evt.value == 'on')
			globalVirtualSwitch.off()
		}
	else
		{
		state.lastTime=now()
		state.lastState=evt.value
		if (evt.value == 'on')
			{
			globalIrBlaster.SendStoredCode('Cable On')
			globalIrBlaster.SendStoredCode('tvPower')
			pauseExecution(1000)						//Wait for Tv to get partially on, avoiding audio pop sound
			globalIrBlaster.SendStoredCode('Audio power')
			}
		else
			{
			globalIrBlaster.SendStoredCode('Audio power')
			globalIrBlaster.SendStoredCode('tvPower')
			globalIrBlaster.SendStoredCode('Cable On')
			}
		}
	}