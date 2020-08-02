/**
 *  Import Url:   https://raw.githubusercontent.com/arnbme/hubitat/master/deluxLighting.groovy
 *
 *  deluxLighting App
 *  Functions:
 *
 *		1. Turn lights on and off based on illumination (Lux), HSM armstate, time of day, motion sensors, switches
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
 *	Aug 01, 2020 v0.2.6 deviceHandler method globalTimeOff may be one day old, add one day of millis when millisToTimeOff is negative
 *	Jul 31, 2020 v0.2.5 issue LR and front Door light turned off upon arrival home. Cause Qnnnn created on Ring switch
 *							updated yesterdays logic
 *	Jul 30, 2020 v0.2.5 issue: Light shutting off lux test point when it should not. Cause: atomicState.Qnnnn was not set.
 *						Adjust logic of deviceHandler to insure setting Qnnnn.
 *						Had previously stopped setting Qnnnn as much as possible, but it must be set to avoid this issue
 *  Jul 28, 2020 v0.2.4A Add optional dimmer level for motion, switch, contact
 *  Jul 28, 2020 v0.2.4 Add capability and settings allowing switch, motion, or contacts to operate only in selected hsmStatus states
 *  Jul 27, 2020 v0.2.3 For each lights settings: create an openable section. Makes for easier viewing and adjustment
 *  Jul 26, 2020 v0.2.2 Speed up luxHandler by setting luxMax and luxMin lux points, and lastLux
 *						Add handler hsmStatusHandler, directly processing night mode lights off
 *						Add NightFlag for each lighting device
 *  Jul 24, 2020 v0.2.1A Use xref on contacts and switches, a bit more complex than contacts
 *  Jul 24, 2020 v0.2.1 Create cross reference for better performance when relating devices to lights, use for contacts
 *  Jul 24, 2020 v0.2.0 Add control by contact sensors, but seemed slow compared to simple automation so not using it
 *							Likely due to all the searching have to fix that by using parent child setup or Xref!
 *  Jul 23, 2020 v0.1.9 timeOffHandler stop using luxHandler to shut lights, use specific time off logic
 *  Jul 23, 2020 v0.1.8 Add bool TimerOffFlag for each device, On: timer shuts device, off timer has no effect (default)
 *  					20 minutes prior to forced time off, start queing to prevent forced light Off on active motions or switches
 *						   this is still a workaround until a logical solution can be created for this issue without
 *						   queuing all the motions and switch tirggers that does work
 *  Jul 22, 2020 v0.1.7 Change lux on/off flag to luxOn and luxOff flags, adjust code accordingly
 *  Jul 21, 2020 v0.1.6 In luxLighting: when setting light on remove any queued off jobs and atomicState variables for the light
 *  Jul 20, 2020 v0.1.5C Fix bug: light shutting off when lux > set point when it should not. missing queued atomic state
 *							Adjust deviceHandler logic
 *  Jul 19, 2020 v0.1.5B Unable to get Jobs Scheduled queue, create atomicState.Qnnn variables
 *							that determine if an off job is queud during a lux setting change
 *  Jul 19, 2020 v0.1.5A Allow for multiple motion triggers
 *  Jul 19, 2020 v0.1.4 Text cleanup, don't create runIn when light remains off from motion or switch trigger
 *  Jul 18, 2020 v0.1.3 Input Lux flag controls how lux is used with a light. Auto On/Off or ignored
 *                      added flag controlling how Lux is used when turning light off with motion or switch
 *						already had a flag for On
 *						renamed deluxLighting from luxLighting
 *
 *  Jul 14, 2020 v0.1.2 Add Manually coded Qnnn methods for each globalLights. Eliminates overhead from using runIn overwrite: true
 *							WARNING must manually add some simple code for best perfformance
 *							Change runIn to pass it.id only, not the huge full object that Hubitat wont use for commands
 *  Jul 13, 2020 v0.1.1 Change light on duration when motion or switch triggered an input setting
 *  					in getEvents fix returnValue not defined
 *  Jul 13, 2020 v0.1.0 in luxHandler set luxCount to globalLuxSensors.size() vs counting in each loop
 *                      in deviceHandler: when light already On don't turn on again
 *  Jul 11, 2020 v0.0.9 Issue office light turning off before 10 minutes and no motion
 *						Add routine getEvents to insure light stays on 10 minutes
 *						use overwrite:false on runIn for Motion and Switch turning light on
 *  Jul 11, 2020 v0.0.8 Change base lux, date period and second Lux to input parameters vs hard coded
 *  Jul 10, 2020 v0.0.7 Add subscribe to a switch, setting light on for 10 minutes
 *							bool determines if Lux participates in light On decision, or switch forces On
 * 							Use luxHandler to turn light off as needed
 *  Jul 10, 2020 v0.0.6 Add bool that determines if Lux participates in Motion On decision, or Motion forces light on
 *  Jul 09, 2020 v0.0.5 Add subscribe to a motion sensor turning light on for 10 minutes
 * 							Use luxHandler to turn light off as needed
 *  Jul 08, 2020 v0.0.4 add optional Lux point for each globalLights
 *						eliminate use of state fields
 *						rewrite all on/off logic
 *  Jul 08, 2020 v0.0.3 Set state.currLux in hsmStatusHandler
 *						luxHandler clean up compare for On or Off, make it more understandable
 *  Jul 07, 2020 v0.0.2 Add Time input field and logic for lights off
 *  Jul 07, 2020 v0.0.1 Handle HSM status changes
 *						comment out sunset sunrise logic for now
 *  Jul 05, 2020 v0.0.0 Create with logic from RM lux lighing rule
 */

definition(
    name: "deluxLighting",
    namespace: "arnbme",
    author: "Arn Burkhoff",
    description: "(${version()}) Delux Lighting app",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/arnbme/hubitat/master/deluxLighting.groovy")

preferences {
    page(name: "mainPage")
	}

def version()
	{
	return "0.2.6";
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
			input "globalTestLux", "number", required: true, multiple: false, range: "1..10000",
				title: "Standard Lux point"
			input "globalDateLux", "number", required: false, multiple: false, range: "1..10000",submitOnChange: true,
				title: "Date based Lux point used between two dates (Optional) Date fields appear when entered."
			if (globalDateLux)
				{
				input "globalFromMonth", "enum", required: true, width: 15,title: "Starting Month", submitOnChange: true,
					options: ['January','February','March','April','May','June','July','August','September','October','November','December']
				switch (globalFromMonth)
					{
					case ('April'):
					case ('June'):
					case ('September'):
					case ('November'):
						input "globalFromDate", "enum", required: true, width: 4, title: "Starting date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28,29,30]
					break
					case ('February'):
						input "globalFromDate", "enum", required: true, width: 4, title: "Starting date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28]
					break
					default:
						input "globalFromDate", "enum", required: true, width: 4, title: "Starting date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28,29,30,31]
					}
				paragraph ""
				input "globalToMonth", "enum", required: true, width: 15, title: "Ending Month", submitOnChange: true,
					options: ['January','February','March','April','May','June','July','August','September','October','November','December']
				switch (globalToMonth)
					{
					case ('April'):
					case ('June'):
					case ('September'):
					case ('November'):
						input "globalToDate", "enum", required: true, width: 4, title: "Ending date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28,29,30]
					break
					case ('February'):
						input "globalToDate", "enum", required: true,width:4, title: "Ending date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28]
					break
					default:
						input "globalToDate", "enum", required: true,width: 4, title: "Ending date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28,29,30,31]
					}
				}
			input "globalLuxSensors", "capability.illuminanceMeasurement", required: true, multiple: true,
				title: "Lux sensors. When more than one, the average lux value is used"
			input "globalTimeOff", "time",submitOnChange: true,
				title: "Optional: Turn off lighting devices with Lux On flag true at this time daily. Leave blank to ignore", required: false
			input "globalLights", "capability.switch", required: true, multiple: true, submitOnChange: true,
				title: "One or more Bulbs, Leds or Switches"
			}
//			for each globalLights get a brightness and optional motion and switch sensors if active leave light on
		if (globalLights)
			{
			globalLights.each
				{itx ->
					section ("${itx.label} Settings, Id:${itx.id}", hideable: true, hidden: true)
					{
					if (settings.globalTimeOff)
						{
						input "global${itx.id}TimerOffFlag", "bool", required: false,
							title: "${itx.label}<br />Device turns Off with timer<br />Device does not respond to timer Off (Default)"
						}
					if (itx.hasCommand('setLevel'))
						{
						input "global${itx.id}Dim", "number", required: false, multiple: false, range: "1..100",
							title: "${itx.label}<br />Brightness Level 1 to 100, leave blank for ON with no level (Optional)"
						}
					input "global${itx.id}Lux", "number", required: false, multiple: false, range: "1..8000",submitOnChange: true,
						title: "${itx.label}<br />Lux On/Off point 1 to 8000. Leave blank to use Standard Lux settings (Optional)"
					input "global${itx.id}LuxFlagOn", "bool", required: false, defaultValue: false,
							title: "On/True: Light turns ON when current Lux <= set point<br />Off/False (Default): No automatic Lux on"
					input "global${itx.id}LuxFlagOff", "bool", required: false, defaultValue: false,
							title: "On/True: Light turns OFF when current Lux > set point<br />Off/False (Default): No automatic Lux off"
					input "global${itx.id}NightFlag", "bool", required: false, defaultValue: false,
							title: "On/True: Light turns OFF when at HSM armedNight status<br />Off/False (Default): No automatic armedNight off"
					input "global${itx.id}Motions", "capability.motionSensor", required: false, multiple:true, submitOnChange: true,
						title: "${itx.label}<br />Motion Sensors when active set light On, and used for Off decision (Optional)"
					if (settings."global${itx.id}Motions")
						{

						input "global${itx.id}MotionMinutes", "number", required: true, defaultValue: 10, range: "1..240",
							title: "${itx.label}<br />Minutes to remain On"
						input "global${itx.id}MotionFlagOn", "bool", required: false, defaultValue: false,
							title: "${itx.label}<br />On/True: Lux participates in motion On decision<br />Off/False (Default): Ignore lux, force light to On with motion<br />"
						input "global${itx.id}MotionFlagOff", "bool", required: false, defaultValue: false,
							title: "${itx.label}<br />On/True: Lux participates in motion Off decision<br />Off/False (Default): Ignore lux, force light Off when motion time expires<br />"
						input "global${itx.id}MotionHsmStatus", "enum", options: ["armedAway", "armedHome", "armedNight", "disarmed"], required: false, multiple: true,
							title: "Motion triggers only in these modes. When none selected, all status trigger action"
						if (itx.hasCommand('setLevel'))
							{
							input "global${itx.id}MotionDim", "number", required: false, multiple: false, range: "1..100",
								title: "${itx.label}<br />Motion Brightness Level 1 to 100 (Optional)"
							}
						}
					input "global${itx.id}Switchs", "capability.switch", required: false, multiple:true, submitOnChange: true,
						title: "${itx.label}<br />Switches status sets light On (Optional)"
					if (settings."global${itx.id}Switchs")
						{
						input "global${itx.id}SwitchMinutes", "number", required: true, defaultValue: 10, range: "1..240",
							title: "${itx.label}<br />Minutes to remain On"
						input "global${itx.id}SwitchFlagOn", "bool", required: false, defaultValue: false,
							title: "${itx.label}<br />On/True: Lux participates in switch On decision<br />Off/False (Default): Ignore lux, force light to On with switch<br />"
						input "global${itx.id}SwitchFlagOff", "bool", required: false, defaultValue: false,
							title: "${itx.label}<br />On/True: Lux participates in motion Off decision<br />Off/False (Default): Ignore lux, force light Off when switch time expires<br />"
						input "global${itx.id}SwitchHsmStatus", "enum", options: ["armedAway", "armedHome", "armedNight", "disarmed"], required: false, multiple: true,
							title: "Switch triggers only in these modes. When none selected, all status trigger action"
						if (itx.hasCommand('setLevel'))
							{
							input "global${itx.id}SwitchDim", "number", required: false, multiple: false, range: "1..100",
								title: "${itx.label}<br />Switch Brightness Level 1 to 100 (Optional)"
							}
						}
//					initially contact open On closed Off used mainly for closets, no timers for starters Jul 24, 2020
					input "global${itx.id}Contacts", "capability.contactSensor", required: false, multiple:true, submitOnChange: true,
						title: "${itx.label}<br />Contact that set light On (Optional)"
					if (settings."global${itx.id}Contacts")
						{
						input "global${itx.id}ContactHsmStatus", "enum", options: ["armedAway", "armedHome", "armedNight", "disarmed"], required: false, multiple: true,
							title: "Contact triggers only in these modes. When none selected, all status trigger action"
						if (itx.hasCommand('setLevel'))
							{
							input "global${itx.id}MotionDim", "number", required: false, multiple: false, range: "1..100",
								title: "${itx.label}<br />Contact Brightness Level 1 to 100 (Optional)"
							}
						}
					}
				}
			}
		}
	}

void installed() {
    log.info "Installed with settings: ${settings}"
	initialize()
}

void updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

void initialize()
	{
	def dvcXref = [:]
	def maxLux = new Integer("0")
	def minLux = new Integer("999999")
	if(settings.logDebugs)
		runIn(1800,debugOff)		//turns off debug logging after 30 min
	else
		unschedule(debugOff)
	unschedule(timeOffHandler)		//clear any pending scheduled events
	if (globalDisable)
		{}
	else
		{
//		Create two program defined globals for start and end Lux date
		if (globalDateLux)
			{
			Map months = [January: '01',
			February: '02',
			March: '03',
			April: '04',
			May: '05',
			June: '06',
			July: '07',
			August: '08',
			September: '09',
			October: '10',
			November: '11',
			December: '12']
			def beginMMDD=""
			def endMMDD=""
			if (globalFromDate < '10')
				beginMMDD = months[globalFromMonth] + '0' + globalFromDate as String
			else
				beginMMDD= months[globalFromMonth]+globalFromDate as String
			if (globalToDate < '10')
				endMMDD = months[globalToMonth] + '0' + globalToDate as String
			else
				endMMDD= months[globalToMonth]+globalToDate as String
			app.updateSetting("globalFromMMDD",[value: beginMMDD,type:"string"])
			app.updateSetting("globalToMMDD",[value: endMMDD, type:"string"])
			}

		subscribe(globalLuxSensors, "illuminance", luxHandler)
		subscribe(location, "hsmStatus", hsmStatusHandler)
		if (globalTimeOff)
			schedule(globalTimeOff, timeOffHandler)
		def i=-1
		globalLights.each
			{
			if (settings."global${it.id}Lux")
				{
				if (settings."global${it.id}Lux" < minLux)
					minLux=settings."global${it.id}Lux"
				if (settings."global${it.id}Lux" > maxLux)
					maxLux=settings."global${it.id}Lux"
				}
			i++
			settingMotions="global${it.id}Motions"
			if (settings."$settingMotions")
				{
				settings."global${it.id}Motions".each
					{it2 ->
					if (dvcXref[it2.id])
						dvcXref [it2.id] << i
					else
						dvcXref [it2.id] = [i]
					}
				subscribe (settings."$settingMotions", "motion.active", deviceHandler)
				}
			settingSwitchs="global${it.id}Switchs"
			if (settings."$settingSwitchs")
				{
				settings."global${it.id}Switchs".each
					{it2 ->
					if (dvcXref[it2.id])
						dvcXref [it2.id] << i
					else
						dvcXref [it2.id] = [i]
					}
				subscribe (settings."$settingSwitchs", "switch.on", deviceHandler)
				}
			if (settings."global${it.id}Contacts")
				{
				settings."global${it.id}Contacts".each
					{it2 ->
					if (dvcXref[it2.id])
						dvcXref [it2.id] << i
					else
						dvcXref [it2.id] = [i]
					}
				subscribe (settings."global${it.id}Contacts", "contact", contactHandler)
				}
			}
		state.maxLux=maxLux
		state.minLux=minLux
		if (state?.lastlux==null)
			state.lastLux=currLuxCalculate()
		log.info "minLux: $minLux maxLux: $maxLux lastLux: ${state.lastLux}"
		log.info "trigger device cross reference $dvcXref"
		state.dvcXref=dvcXref

//		state.dvcXref["1057"].each
//			{
//			log.debug globalLights[it].label
//			}
//		state.dvcXref["33"].each
//			{
//			log.debug globalLights[it].label
//			}
		}
//	log.debug "getevents returned " + getEvents('global321Switch','on')  		//used to test getEvents on Done
	}

void debugOff(){
//	stops debug logging
	log.info "deluxLighting: debug logging disabled"
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}

def currLuxCalculate()
	{
	if (settings.logDebugs) log.debug "currLuxCalculate entered"
	def total = new Integer("0")
	def currLux = new Integer("0")
	def luxCount = settings.globalLuxSensors.size()		//This is an integer field
	globalLuxSensors.each
		{
		total+=it.currentValue('illuminance').intValueExact()
		}
	if (luxCount>1)
		currLux = total.intdiv(luxCount)
	else
		currLux = total
	if (settings.logDebugs) log.debug "currLuxCalculate: $currLux ${currLux.class.name}"
	return currLux
	}

def appLuxCalculate()
	{
	if (settings.logDebugs) log.debug "appLuxCalculate entered"
	def appTestLux = globalTestLux  as Integer
	if (globalDateLux)
		{
		def mmdd = new Date().format( 'MMdd')	// mmdd is a text String, test accordingly
		if (settings.logDebugs) log.debug "luxHandler testing date $mmdd $globalFromMMDD and $globalToMMDD using globalDateLux: $globalDateLux"
//		log.debug "luxHandler ${mmdd.class.name} ${globalFromMMDD.class.name} ${globalToMMDD.class.name}"
		if (globalToMMDD >= globalFromMMDD)
			{
			if (mmdd >= globalFromMMDD && mmdd <= globalToMMDD)
				{
				if (settings.logDebugs) log.debug "luxHandler $mmdd is between $globalFromMMDD and $globalToMMDD using globalDateLux: $globalDateLux"
				appTestLux  = globalDateLux as Integer
				}
			}
		else
		if (mmdd >= globalToMMDD && mmdd <= globalFromMMDD)
			{}
		else
			{
			if (settings.logDebugs) log.debug "luxHandler $mmdd is between $globalFromMMDD and $globalToMMDD using globalDateLux: $globalDateLux"
			appTestLux = globalDateLux as Integer
			}
		}
	if (settings.logDebugs) log.debug "appLuxCalculate: $appTestLux ${appTestLux.class.name}"
	return appTestLux
	}

//	onlyLight deprecated in V015B
//  forceoff deprecated v019
void luxHandler(evt,forceOff=false,onlyLight=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting: luxHandler entered"
	def currLux=currLuxCalculate()
	def appTestLux=appLuxCalculate()
	def minLux = state.minLux
	def maxLux = state.maxLux
	def lastLux = state.lastLux
	state.lastLux=currLux
	if (appTestLux < minLux)
		minLux=appTestLux
	if (appTestLux > minLux)
		maxLux=appTestLux
	if (settings.logDebugs) log.debug "maxLux: $maxLux, minLux: $minLux, lastLux: $lastLux, currLux; $currLux"
	if (settings.logDebugs) log.debug "maxLux: ${maxLux.class.name}, minLux: ${minLux.class.name}, lastLux: ${lastLux.class.name}, currLux; ${currLux.class.name}"

	if (currLux > maxLux)
		{
		if (lastLux > maxLux)
			{
			if (settings.logDebugs) log.debug "luxHandler skipped already above maximum Lux threshhold"
			return
			}
		}
	else
	if (currLux <= minLux)
		{
		if (lastLux <= minLux)
			{
			if (settings.logDebugs) log.debug "luxHandler skipped already below lowest Lux threshhold"
			return
			}
		}


//	def sunRiseSet = getSunriseAndSunset(sunriseOffset: +45, sunsetOffset: -45)
//	if (settings.logDebugs) log.debug "sunRise+45 ${sunRiseSet.sunrise} sunSet-45 ${sunRiseSet.sunset} ${sunRiseSet.sunrise.class.name} now ${new Date()}"
//	if (!timeOfDayIsBetween(sunRiseSet.sunrise, sunRiseSet.sunset, new Date(), location.timeZone))
//		{
//		if (settings.logDebugs) log.debug "Not between sunrise+45 and sunset-45 due to sunset rule"
//		return
//		}

	def	settingDim=""
	def	settingLux=""
	def	settingMotion=""
	def	settingMotionMinutes=""
	def settingLuxFlagOn=""
	def settingLuxFlagOff=""
	globalLights.each
		{
		if (onlyLight && onlyLight != it.id) //dont process this light
			{
			if (settings.logDebugs) log.debug "luxHandler ${it.label} skipped ${it.id} $onlyLight"
			}
		else
			{
			if (onlyLight)
				if (settings.logDebugs) log.debug "luxHandler ${it.label} processing onlylight ${it.id}"
			settingDim="global${it.id}Dim"
			settingLux="global${it.id}Lux"
			settingLuxFlagOn="global${it.id}LuxFlagOn"
			settingLuxFlagOff="global${it.id}LuxFlagOff"
			settingMotion="global${it.id}Motions"
			settingMotionMinutes="global${it.id}MotionMinutes"
			if (settings."$settingLux")
				testLux=settings."$settingLux"
			else
				testLux = appTestLux
			if (settings.logDebugs) log.debug "${it.label} currLux: $currLux testLux: $testLux forceOff: ${forceOff} hsmStatus: $location.hsmStatus"
			if (it.currentValue('switch') == 'off')
				{
				if (location.hsmStatus == 'armedNight' || forceOff)
					{
					if (settings.logDebugs) log.debug "leaving ${it.label} Off"
					}
				else
				if (testLux >= currLux && settings."$settingLuxFlagOn")
					{
					if (atomicState?."Q${it.id}")
						{
						atomicState."Q${it.id}"=false		//V016 clear scheduled off jobs for this light
						unschedule("Q${it.id}")				//V016
						}
					if (settings."$settingDim")
						{
						if (settings.logDebugs) log.debug "doing setlevel ${it.label} ${it.id} ${settingDim}: " + settings."$settingDim"
						it.setLevel(settings."$settingDim", 5)
						}
					else
						{
						if (settings.logDebugs) log.debug "doing On ${it.label} ${it.id} ${settingDim} not found"
						it.on()
						}
					}
				}
			else
			if (testLux < currLux && settings."$settingLuxFlagOff" || location.hsmStatus == 'armedNight' || (forceOff && settings."global${it.id}TimerOffFlag")) //bulb is on if we get here
				{
				if (atomicState?."Q${it.id}")		//is a light off job scheduled?
					{
					if (settings.logDebugs) log.debug "leaving ${it.label} On, $settingMotion is active"
					}
				else
					{
					if (settings.logDebugs) log.debug "luxHandler doing off ${it.label} ${it.id}"
					it.off()
					}
				}
			else
				if (settings.logDebugs) log.debug 'Light skipped on and off'
			}
		}
/*		log.debug "exiting after looping through all lighting"
		used for timing during lux threshold development
*/
	}

void timeOffHandler()
	{
	if (settings.logDebugs) log.debug  "deluxLighting timeOffHandler"
	settings.globalLights.each
		{
		if (it.currentValue('switch') == 'on' && "global${it.id}TimerOffFlag")
			{
			if (atomicState?."Q${it.id}")		//is a light off job scheduled?
				{
				if (settings.logDebugs) log.debug "leaving ${it.label} On, $settingMotion is active"
				}
			else
				{
				if (settings.logDebugs) log.debug "doing off ${it.label} ${it.id}"
				it.off()
				}
			}
		}
	}

void hsmStatusHandler(evt)
	{
	if (settings.logDebugs) log.debug  "deluxLighting hsmStatusHandler $evt.value"
	if (evt.value.startsWith('arming'))
		{}
	else
	if (evt.value!='armedNight')
		luxHandler(evt)
	else
	{
		settings.globalLights.each
			{
			if (it.currentValue('switch') == 'on' && "global${it.id}NightFlag")
				{
				if (atomicState?."Q${it.id}")		//is a light off job queued?
					{
					if (settings.logDebugs) log.debug "leaving ${it.label} On, light off is queued"
					}
				else
					{
					if (settings.logDebugs) log.debug "doing off ${it.label} ${it.id}"
					it.off()
					}
				}
			}
		}
	}

void deviceHandler(evt)
	{
	if (settings.logDebugs) log.debug  "deviceHandler entered"
	def currLux = currLuxCalculate()
	def appTestLux = appLuxCalculate()
	def settingDevice=""
	def settingDim=""
	def settingDeviceDim=""
	def settingLux=""
	def settingDvcFlagOn=""
	def id=""
	def triggerSensor = evt.getDevice()
	def triggerId = evt.getDevice().id		//id of triggering device
	def triggerText='Switch'
	if (triggerSensor.hasCapability("MotionSensor"))
		triggerText='Motion'
	def triggerIndex=-1
	def qName=''
//	def dateTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss", globalTimeOff)
//	def millis = dateTime.getTime()
//	log.debug "$dateTime $millis ${now()}"
//	log.debug "${millis-now()}"
	def millisToTimeOff= Date.parse("yyyy-MM-dd'T'HH:mm:ss", globalTimeOff).getTime() - now()
	if (settings.logDebugs) log.debug "${settings.globalTimeOff} Millis: $millisToTimeOff"
	if (millisToTimeOff < 0)
		{
		millisToTimeOff += 86400000				//add 1 day of milliseconds if negative
		if (settings.logDebugs)log.debug "${settings.globalTimeOff} Millis: $millisToTimeOff adjusted for negative"
		}
	if (settings.logDebugs)log.debug  "deviceHandler processing: ${evt.getDevice().label} $triggerId triggerText $triggerText"
	def hsmStateValid=true

	state.dvcXref[triggerId].each
		{
		hsmStateValid=true
		id=globalLights[it].id
		if (settings."global${id}${triggerText}HsmStatus")
			{
			if (settings."global${id}${triggerText}HsmStatus".contains(location.hsmStatus))
				{}
			else
				hsmStateValid=false
			}

		if (settings.logDebugs) log.debug settings."global${id}${triggerText}HsmStatus"
		if (settings.logDebugs) log.debug "${id}" + ' '+ location.hsmStatus +' ' + hsmStateValid +' '+ triggerSensor.label
		qName="Q${id}"
		if (settings.logDebugs)log.debug "process ${globalLights[it].label} ids: $triggerId $id"
		settingDevice="global${id}${triggerText}s"				//name of motion or switch setting sensor controlling light
		settingDeviceMinutes="global${id}${triggerText}Minutes"
		settingDvcFlagOn="global${id}${triggerText}FlagOn"
		if (settings."${settingDevice}".size() == 1)
			triggerIndex=0
		else
			{
			triggerIndex=-1
			settings."${settingDevice}".find
				{ it2 ->
				triggerIndex++
				if (triggerId == it2.id)
					return true
				else
					return false
				}
			}
		minutes=settings."$settingDeviceMinutes"
		if (settings.logDebugs) log.debug "${globalLights[it].label} on minutes is ${minutes}"
		seconds=minutes * 60

		settingLux="global${id}Lux"
		if (settings."$settingLux")
			testLux=settings."$settingLux"
		else
			testLux = appTestLux

		if (!hsmStateValid)											//Ignore, invalid armed state
			{}
		else
		if (globalLights[it].currentValue('switch') == 'on')		//light is already On
			{
			if (settings.logDebugs) log.debug "deluxLighting deviceHandler device is on, testLux: $testLux currLux: $currLux and timeMillis: ${seconds * 1000}, OffMillis: $millisToTimeOff"
			if (atomicState."$qName")
				{
				if (settings.logDebugs) log.debug "deluxLighting deviceHandler extending timer light on runInQueue $qName exists"
				runInQueue(seconds,qName, it, id, settingDevice, triggerIndex, triggerId)
				}
			else
			if (testLux >= currLux && ((seconds * 1000) > millisToTimeOff))
				{
				if (settings.logDebugs) log.debug "deluxLighting deviceHandler creating queue, testLux $testLux >= currLux $currLux and ontime ${seconds * 1000} > $millisToTimeOff"
				runInQueue(seconds,qName, it, id, settingDevice, triggerIndex, triggerId)
				}
			}
		else
		if (!(settings."$settingDvcFlagOn") || testLux >= currLux)
			{
			runInQueue(seconds,qName, it, id, settingDevice, triggerIndex, triggerId)
			settingDim="global${id}Dim"
			settingDeviceDim="global${id}${triggerText}Dim"
			if (settings.logDebugs) log.debug "$settingDeviceDim:" + settings."$settingDeviceDim"
			if (settings."$settingDeviceDim")
				{
				if (settings.logDebugs) log.debug "deluxLighting deviceHandler doing setlevel ${globalLights[it].label} ${id} ${settingDeviceDim}: " + settings."$settingDeviceDim"
				globalLights[it].setLevel(settings."$settingDeviceDim", 3)
				}
			else
			if (settings."$settingDim")
				{
				if (settings.logDebugs) log.debug "deluxLighting deviceHandler doing setlevel ${globalLights[it].label} ${id} ${settingDim}: " + settings."$settingDim"
				globalLights[it].setLevel(settings."$settingDim", 3)
				}
			else
				{
				if (settings.logDebugs) log.debug "deluxLighting deviceHandler doing On ${globalLights[it].label} ${id} ${settingDim} not found"
				globalLights[it].on()
				}
			}
		}
	}

void contactHandler(evt)
	{
	def triggerSensor = evt.getDevice()
	def triggerId = evt.getDevice().id		//id of triggering device
	def settingDevice = ""
	def settingDeviceDim=""
	def allContactsClosed=true
	if (settings.logDebugs) log.debug  "contactHandler entered: ${evt.getDevice().label} ${evt.value} $triggerId"
	if (evt.value == 'open')
		{
		state.dvcXref[triggerId].each
			{
			settingDeviceDim="global${id}ContactDim"
			if (settings.logDebugs) log.debug "$settingDeviceDim:" + settings."$settingDeviceDim"
			if (settings."$settingDeviceDim")
				{
				if (settings.logDebugs) log.debug "deluxLighting deviceHandler doing setlevel ${globalLights[it].label} ${id} ${settingDeviceDim}: " + settings."$settingDeviceDim"
				globalLights[it].setLevel(settings."$settingDeviceDim", 3)
				}
			else
				{
				settings.globalLights[it].on()
//				log.debug "light on"
				}
			}
		}
	else
		{
		state.dvcXref[triggerId].each
			{
			settingDevice = "global${globalLights[it].id}Contacts"
			if (settings."$settingDevice".size()==1)
				settings.globalLights[it].off()
			else
				{
//				log.debug "checking all related contacts for $settingDevice"
				allContactsClosed=true
				settings."$settingDevice".find
					{it3 ->
					if (it3.currentValue('contact') == "open")
						{
//						log.debug "Open ${it3.label}"
						allContactsClosed=false
						return true
						}
					else
						{
//						log.debug "Not Open ${it3.label} ${it3.currentValue('contact')}"
						return false
						}
					}
				if (allContactsClosed)
					{
					settings.globalLights[it].off()
//					log.debug "light off"
					}
				}
			}
		}
	}

void runInQueue(seconds, qName, lightIndex, lightId, triggerSettingName, triggerIndex, triggerId)
	{
	try {
		if (settings.logDebugs) log.debug "deluxLighting runInQueue: seconds:$seconds, qName:$qName, lightIndex:$lightIndex,lightId:$lightId, triggerSettingName:$triggerSettingName, triggerIndex:$triggerIndex, triggerId:$triggerId"
		"${qName}"()									//When method exists queue it to the method
		runIn (seconds, "${qName}",
						[data: [lightIndex: lightIndex,
						lightId: lightId,
						triggerSettingName: triggerSettingName,
						triggerIndex: triggerIndex,
						triggerId: triggerId]])
		atomicState."${qName}"=true
		}
	catch (e)
		{
		log.warn "deluxLighting runInQueue: Using default runIn queue, please code $qName method"
		runIn (seconds, "qOffHandler",
						[data: [lightIndex: lightIndex,
						lightId: lightId,
						triggerSettingName: triggerSettingName,
						triggerIndex: triggerIndex,
						triggerId: triggerId], overwrite: false ])
		}
	}

/*	deprecated in V015B
def getEvents(settingDevice,deviceValue,minutes)
	{
	if (settings.logDebugs) log.debug "LuxLighing getEvents entered, Device: $settingDevice deviceValue: $deviceValue Minutes: $minutes"
	def	returnValue=false
	if (settings."$settingDevice")
		{
		if (settings.logDebugs) log.debug "getEvents entered ${settingDevice}"
		def events = settings."$settingDevice".events(max: 10)
		events.find
			{
			if (it.value == deviceValue)
				{
				millisec= (minutes * 60000) + 1
				if ((now() - it.date.getTime()) < millisec)		//less than time on minutes in milliseconds?
					{
					if (settings.logDebugs) log.debug "getEvents less than 10 minutes " + (now() - it.date.getTime())
					returnValue=true
					}
				else
					{
					if (settings.logDebugs) log.debug "getEvents more than 10 minutes " + (now() - it.date.getTime())
					}
				return true
				}
			else
				{
				if (settings.logDebugs) log.debug "getEvents not an active event ${it.value}"
				return false
				}
			}
		}
	if (settings.logDebugs) log.debug "getEvents return value is $returnValue"
	return returnValue
	}
*/
/* runIn queues eliminate overwrite: false reducing overhead for busy switch or motion sensor
 * The following Qnnnn routines are manually coded until I figure out how to generate them wih a Groovy Macro
 * They allow the device runIn to work with overwrite true, eliminating extra timeout processing
 * user should code one per globalLights defined device.id with a related switch or motion sensor
 */
void Q1288(mapData=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q1288 entered ${mapData}"
	if (mapData)
		{
		atomicState.Q1288=false
		qOffHandler(mapData)		//light may or may not be turned off based on lux and system satus
		}
	}

void Q1281(mapData=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q1281 entered ${mapData}"
	if (mapData)
		{
		atomicState.Q1281=false
		qOffHandler(mapData)		//light may or may not be turned off based on lux and system satus
		}
	}

void Q321(mapData=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q321 entered ${mapData}"
	if (mapData)
		{
		atomicState.Q321=false
		qOffHandler(mapData)		//light may or may not be turned off based on lux and system satus
		}
	}

void Q2(mapData=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q2 entered ${mapData}"
	if (mapData)
		{
		atomicState.Q2=false
		qOffHandler(mapData)		//light may or may not be turned off based on lux and system satus
		}
	}
void Q3(mapData=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q3 entered ${mapData}"
	if (mapData)
		{
		atomicState.Q3=false
		qOffHandler(mapData)		//light may or may not be turned off based on lux and system status
		}
	}

void qOffHandler(mapData)
	{
/*		runIn and map data formats
		runIn (seconds, "${qName}",
						data: ["lightIndex": lightIndex,
						"lightId": lightId,
						"triggerSettingName": triggerSettingName,
						"triggerIndex": triggerIndex,
						"triggerId": triggerId]]
		[lightIndex:4, lightId:1288, triggerSettingName:global1288Motions, triggerIndex:0, triggerId:1057]
*/

	if (settings.logDebugs) log.debug "qOffHandler entered: $mapData"

/*	keep these debugging statements just in case the logic breaks and intense debugging is needed
	log.debug settings.globalLights.id[mapData.lightIndex]+' '+settings.globalLights.id[mapData.lightIndex].class.name
	log.debug mapData.lightId +' '+ mapData.lightId.class.name
	log.debug settings."${mapData.triggerSettingName}".id[mapData.triggerIndex]+' '+settings."${mapData.triggerSettingName}".id[mapData.triggerIndex].class.name
	log.debug mapData.triggerId +' '+ mapData.triggerId.class.name
*/

//	Verify the mapData remains valid. It's a precaution against an interim settings save. Better to leave a light on, than creating an error

	if (settings.globalLights.id[mapData.lightIndex] == mapData.lightId &&
		settings."${mapData.triggerSettingName}".id[mapData.triggerIndex] == mapData.triggerId)
		{
		if (settings.globalLights[mapData.lightIndex].currentValue('switch') == 'on')
			{
			if (settings.logDebugs) log.debug "doing off " + ' '+ settings.globalLights[mapData.lightIndex].label +' '+ settings.globalLights.id[mapData.lightIndex]
			settings.globalLights[mapData.lightIndex].off()
			}
		else
			if (settings.logDebugs) log.debug 'deluxLighting qOffHandler: Light already Off'
		}
	else
		log.warn "deluxLighting qOffHandler: mismatched failure to process: $mapData"
	}