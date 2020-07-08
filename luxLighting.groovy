/**
 *  Import Url:   https://raw.githubusercontent.com/arnbme/hubitat/master/luxLighting.groovy
 *
 *  luxLighting App
 *  Functions:
 *
 *		1. Turn lights on and off based on illumination (Lux), HSM armstate, time of day
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
 *  Jul 08, 2020 v0.0.3 Set state.currLux in hsmStatusHandler
 *						luxHandler clean up compare for On or Off, make it more understandable
 *  Jul 07, 2020 v0.0.2 Add Time input field and logic for lights off
 *  Jul 07, 2020 v0.0.1 Handle HSM status changes
 *						comment out sunset sunrise logic for now
 *  Jul 05, 2020 v0.0.0 Create with logic from RM lux lighing rule
 */

definition(
    name: "luxLighting",
    namespace: "arnbme",
    author: "Arn Burkhoff",
    description: "(${version()}) Lux Lighting app",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/arnbme/hubitat/master/luxLighting.groovy")

preferences {
    page(name: "mainPage")
	}

def version()
	{
	return "0.0.3";
	}

def mainPage()
	{
	dynamicPage(name: "mainPage", title: "(V${version()}) Lux Lighting Settings", install: true, uninstall: true)
		{
		section
			{
			input "globalDisable", "bool", required: true, defaultValue: false,
				title: "Disable All Functions. Default: Off/False"
			input "logDebugs", "bool", required: true, defaultValue: false,
				title: "Do debug logging. Shuts off after 30 minutes Default: Off/False"
			input "globalLuxSensors", "capability.illuminanceMeasurement", required: true, multiple: true,
				title: "Lux sensors. When more than one, the average lux value is used"
			input "globalTimeOff", "time", title: "Optional: Turn off lighting devices at this time daily. Leave blank to ignore", required: false
			input "globalLights", "capability.switch", required: true, multiple: true, submitOnChange: true,
				title: "One or more Bulbs, Leds or Switches"

//			for each globalLights get a brightness and optional motion sensor if active leave light on
			globalLights.each
				{
				if (it.hasCommand('setLevel'))
					{
					input "global${it.id}Dim", "number", required: false, multiple: false, range: "1..100",
						title: "${it.name} Brightness Level 1 to 100, leave blank for ON with no level (Optional)"
					}
				input "global${it.id}Motion", "capability.motionSensor", required: false, multiple: false,
					title: "${it.name} If this Motion Sensor is active, do not set light Off (Optional)"
				}
			}
		}
	}

def installed() {
    log.info "Installed with settings: ${settings}"
	initialize()
	state.currLux= new Integer("0")
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize()
	{
	if(settings.logDebugs)
		runIn(1800,logsOff)			//turns off debug logging after 30 min
	else
		unschedule(logsOff)
	unschedule(timeOffHandler)		//clear any pending scheduled events
	if (globalDisable)
		{}
	else
		{
		subscribe(globalLuxSensors, "illuminance", luxHandler)
		subscribe(location, "hsmStatus", hsmStatusHandler)
		if (globalTimeOff)
			schedule(globalTimeOff, timeOffHandler)
		}
	}

void logsOff(){
//	stops debug logging
	log.info "luxLighting: debug logging disabled"
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}

def luxHandler(evt)
	{
	def lastLux = state.currLux
	if (settings.logDebugs) log.debug "luxLighting: luxHandler entered, lastLux: $lastLux ${lastLux.class.name}"
	def total = new Integer("0")
	def currLux = new Integer("0")
	def luxCount = new Integer("0")
	globalLuxSensors.each
		{
		luxCount++
		total+=it.currentValue('illuminance').intValueExact()
		}
	if (luxCount>1)
		currLux = total.intdiv(luxCount)
	else
		currLux = total
	if (settings.logDebugs) log.debug "currLux: $currLux ${currLux.class.name}"
	state.currLux = currLux
	def testLux = new Integer("300")
	def mmdd = new Date().format( 'MMdd')	// mmdd is a text String, est accordingly
	if (mmdd > '0430' && mmdd < '1001') 	// May 1 to Sep 30 use 125 lux due to leaves on trees
		testLux = new Integer("125")

	if (location.hsmStatus == 'armedNight')
		{
		state.currLux = testLux + 1
		return
		}

//	def sunRiseSet = getSunriseAndSunset(sunriseOffset: +45, sunsetOffset: -45)
//	if (settings.logDebugs) log.debug "sunRise+45 ${sunRiseSet.sunrise} sunSet-45 ${sunRiseSet.sunset} ${sunRiseSet.sunrise.class.name} now ${new Date()}"
//	if (!timeOfDayIsBetween(sunRiseSet.sunrise, sunRiseSet.sunset, new Date(), location.timeZone))
//		{
//		if (settings.logDebugs) log.debug "Not between sunrise+45 and sunset-45 due to sunset rule"
//		return
//		}
    if (settings.logDebugs) log.debug "currLux: $currLux lastLux: $lastLux testLux: $testLux"
	if (currLux <= testLux)
		{
		if (lastLux > testLux)		//lights on
			lightsOn()
		}
	else
	if (lastLux <= testLux)			//lights off
		lightsOff()
	}

void hsmStatusHandler(evt)
	{
//	HSM arming status changed
	if (settings.logDebugs) log.debug  "luxLighting hsmStatusHandler entered Value: ${evt.value}"
	if (evt.value=='disarmed')
		luxHandler(evt)
	else
	if (evt.value == 'armedNight')
        {
        lightsOff()
    	def testLux = new Integer("300")
	    def mmdd = new Date().format( 'MMdd')	// mmdd is a text String, est accordingly
	    if (mmdd > '0430' && mmdd < '1001') 	// May 1 to Sep 30 use 125 lux due to leaves on trees
		    testLux = new Integer("125")
        state.currLux = testLux + 1
        }
    }

void timeOffHandler()
	{
//	Timed lights off, could be direct to lightsOff, but leave incase more logic is needed
	if (settings.logDebugs) log.debug  "luxLighting timeOffHandler"
	lightsOff()
	}

void lightsOn()
	{
	def settingName=""
	globalLights.each
		{
		settingName="global${it.id}Dim"
		if (settings."$settingName")
			{
			if (settings.logDebugs) log.debug "doing setlevel ${it.name} ${it.id} ${settingName}: " + settings."$settingName"
			it.setLevel(settings."$settingName", 5)
			}
		else
			{
			if (settings.logDebugs) log.debug "doing on ${it.name} ${it.id} ${settingName} not found"
			it.on()
			}
		}
	}

void lightsOff()
	{
	globalLights.each
		{
		settingName="global${it.id}Motion"
		if (settings."$settingName" && settings."$settingName".currentValue('motion') == 'active')
			{
			if (settings.logDebugs) log.debug "leaving ${it.name} On"
			}
		else
			{
			if (settings.logDebugs) log.debug "doing off ${it.name} ${it.id} ${settingName}"
			it.off()
			}
		}
	}