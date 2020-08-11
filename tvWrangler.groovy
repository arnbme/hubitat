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
 *		Note: The cable box may to have a 20 second time frame where it wont react to the blaster after a blaster valid command
 *				however it always reacts to manual remote
 *				Cable box is very sensitive to IR blaster's distance and position sometimes refusing to respond
 *
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
 *  Aug 11, 2020 v0.0.3 fix minor but with debugging
 *  Aug 10, 2020 v0.0.2 Make debugging a button
 *  Aug 09, 2020 v0.0.1 Make codes external text fields, taken from IrBlaster
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
	return "0.0.3";
	}

def mainPage()
	{
	dynamicPage(name: "mainPage", title: "TV Wrangler V${version()} Settings", install: true, uninstall: true)
		{
		section
			{
			if (settings.logDebugs)
				input "buttonDebugOff", "button", title: "Stop Debug Logging"
			else
				input "buttonDebugOn", "button", title: "Debug For 30 minutes"
			input "globalDisable", "bool", required: true, defaultValue: false,
				title: "Disable All Functions. Default: Off/False"
			input "globalIrBlaster", "capability.actuator", required: true, multiple: false,
				title: "One IR Blaster"
			input "globalTvPower","text",required:true,title:"Name of TV Power Code"
			input "globalAudioPower","text",required:false,title:"Name of Audio Power Code, Leave blank to ignore"
			input "globalCablePower","text",required:false,title:"Name of Cable Box Power Code, leave blank to ignore"
			input "globalVirtualSwitch", "capability.switch", required: true, multiple: false,
				title: "One Virtual Switch, used on a Dashboard triggering On and Off"
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
	if (globalDisable)
		{}
	else
		{
		subscribe(globalVirtualSwitch, "switch", switchHandler)
		}
	}

//	Process Pause and debug buttons
void appButtonHandler(btn)
	{
	switch(btn)
		{
		case "buttonDebugOff":
			debugOff()
			break
		case "buttonDebugOn":
			app.updateSetting("logDebugs",[value:"true",type:"bool"])
			runIn(1800,debugOff)		//turns off debug logging after 30 Minutes
			break
		}
	}

void debugOff(){
//	stops debug logging
	log.info "tvWrangler: debug logging disabled"
	unschedule(debugOff)
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
			if (settings.globalCablePower)
				globalIrBlaster.SendStoredCode(settings.globalCablePower)
			if (settings.globalTvPower)
				globalIrBlaster.SendStoredCode(settings.globalTvPower)
			if (settings.globalAudioPower)
				{
				pauseExecution(1000)						//Wait for Tv to get partially on, avoiding audio pop sound
				globalIrBlaster.SendStoredCode(settings.globalAudioPower)
				}
			}
		else
			{
			if (settings.globalAudioPower)
				globalIrBlaster.SendStoredCode(settings.globalAudioPower)
			if (settings.globalTvPower)
				globalIrBlaster.SendStoredCode(settings.globalTvPower)
			if (settings.globalCablePower)
				globalIrBlaster.SendStoredCode(settings.globalCablePower)
			}
		}
	}