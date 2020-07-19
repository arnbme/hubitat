/**
	Work in progress!
	ADD CODE FOR MULTIPLE MOTION AND DEVICE SENSORS CONTROLLING A LIGHT
	ADD LOGIC FOR LIGHT OFF NOT USING LUXhANLER OR NOT
 *  Import Url:   https://raw.githubusercontent.com/arnbme/hubitat/master/deluxLighting.groovy
 *
 *  deluxLighting App
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
	return "0.1.4";
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
			input "globalTimeOff", "time", title: "Optional: Turn off lighting devices at this time daily. Leave blank to ignore", required: false
			input "globalLights", "capability.switch", required: true, multiple: true, submitOnChange: true,
				title: "One or more Bulbs, Leds or Switches"

//			for each globalLights get a brightness and optional motion and switch sensors if active leave light on
			globalLights.each
				{
				if (it.hasCommand('setLevel'))
					{
					input "global${it.id}Dim", "number", required: false, multiple: false, range: "1..100",
						title: "${it.name}<br />Brightness Level 1 to 100, leave blank for ON with no level (Optional)"
					}
				input "global${it.id}Lux", "number", required: false, multiple: false, range: "1..8000",submitOnChange: true,
					title: "${it.name}<br />Lux On/Off point 1 to 8000. Leave blank to use Standard Lux settings (Optional)"
				input "global${it.id}LuxFlag", "bool", required: false, defaultValue: false,
						title: "On/True: Light turns on and off following Lux set point<br />Off/False (Default): No automatic Lux on/off"
				input "global${it.id}Motion", "capability.motionSensor", required: false, multiple:false, submitOnChange: true,
					title: "${it.name}<br />Motion Sensors when active set light On, and used for Off decision (Optional)"
				settingMotion="global${it.id}Motion"
				if (settings."$settingMotion")
					{
					input "global${it.id}MotionMinutes", "number", required: true, defaultValue: 10, range: "1..240",
						title: "${it.name}<br />Minutes to remain On"
					input "global${it.id}MotionFlagOn", "bool", required: false, defaultValue: false,
						title: "${it.name}<br />On/True: Lux participates in motion On decision<br />Off/False (Default): Ignore lux, force light to On<br />"
					input "global${it.id}MotionFlagOff", "bool", required: false, defaultValue: false,
						title: "${it.name}<br />On/True: Lux participates in motion Off decision<br />Off/False (Default): Ignore lux, force light Off<br />"
					}
				input "global${it.id}Switch", "capability.switch", required: false, multiple:false, submitOnChange: true,
					title: "${it.name}<br />Switches status sets light On (Optional)"
				settingSwitch="global${it.id}Switch"
				if (settings."$settingSwitch")
					{
					input "global${it.id}SwitchMinutes", "number", required: true, defaultValue: 10, range: "1..240",
						title: "${it.name}<br />Minutes to remain On"
					input "global${it.id}SwitchFlagOn", "bool", required: false, defaultValue: false,
						title: "${it.name}<br />On/True: Lux participates in switch On decision<br />Off/False (Default): Ignore lux, force light to On<br />"
					input "global${it.id}SwitchFlagOff", "bool", required: false, defaultValue: false,
						title: "${it.name}<br />On/True: Lux participates in motion Off decision<br />Off/False (Default): Ignore lux, force light Off<br />"
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
		subscribe(location, "hsmStatus", luxHandler)
		if (globalTimeOff)
			schedule(globalTimeOff, timeOffHandler)
		globalLights.each
			{
			settingMotion="global${it.id}Motion"
			if (settings."$settingMotion")
				{
				subscribe (settings."$settingMotion", "motion.active", deviceHandler)
				}
			settingSwitch="global${it.id}Switch"
			if (settings."$settingSwitch")
				{
				subscribe (settings."$settingSwitch", "switch.on", deviceHandler)
				}

//			This code may be used to test the Qnames tto verify they work and exist
/*			if ((settings."$settingMotion" ) || (settings."$settingSwitch"))
				{
				qName="Q${it.id}"
				try {
					log.debug "trying $qName"
					"${qName}"()
					}
				catch (e)
					{
					log.debug "Execution failed ${e}"
					}
				}
*/			}
		}
//	log.debug "getevents returned " + getEvents('global1288Motion','active')  	//used to test getEvents on Done
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

void luxHandler(evt,forceOff=false,onlyLight=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting: luxHandler entered"
	def currLux=currLuxCalculate()
	def appTestLux=appLuxCalculate()

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
	def settingLuxFlag=""
	globalLights.each
		{
		if (onlyLight && onlyLight != it.id) //dont process this light
			{
			if (settings.logDebugs) log.debug "luxHandler ${it.name} skipped ${it.id} $onlyLight"
			}
		else
			{
			if (onlyLight)
				if (settings.logDebugs) log.debug "luxHandler ${it.name} processing onlylight ${it.id}"
			settingDim="global${it.id}Dim"
			settingLux="global${it.id}Lux"
			settingLuxFlag="global${it.id}LuxFlag"
			settingMotion="global${it.id}Motion"
			settingMotionMinutes="global${it.id}MotionMinutes"
			if (settings."$settingLux")
				testLux=settings."$settingLux"
			else
				testLux = appTestLux
			if (settings.logDebugs) log.debug "${it.name} currLux: $currLux testLux: $testLux forceOff: ${forceOff} hsmStatus: $location.hsmStatus"
			if (it.currentValue('switch') == 'off')
				{
				if (location.hsmStatus == 'armedNight' || forceOff)
					{
					if (settings.logDebugs) log.debug "leaving ${it.name} Off"
					}
				else
				if (testLux >= currLux && settings."$settingLuxFlag")
					{
					if (settings."$settingDim")
						{
						if (settings.logDebugs) log.debug "doing setlevel ${it.name} ${it.id} ${settingDim}: " + settings."$settingDim"
						it.setLevel(settings."$settingDim", 5)
						}
					else
						{
						if (settings.logDebugs) log.debug "doing On ${it.name} ${it.id} ${settingDim} not found"
						it.on()
						}
					}
				}
			else
			if (testLux < currLux || settings."$settingLuxFlag" != true || location.hsmStatus == 'armedNight' || forceOff ) //bulb is on if we get here
				{
				def minutes = new Integer ("0")
				if (settings."$settingMotionMinutes")
					minutes=settings."$settingMotionMinutes"
				if (settings."$settingMotion" &&
					(settings."$settingMotion".currentValue('motion') == 'active' ||
					(minutes > 0 && getEvents(settingMotion,'active', minutes))))
					{
					if (settings.logDebugs) log.debug "leaving ${it.name} On, $settingMotion is active"
					}
				else
					{
					if (settings.logDebugs) log.debug "doing off ${it.name} ${it.id}"
					it.off()
					}
				}
			else
				if (settings.logDebugs) log.debug 'Light skipped on and off'
			}
		}
	}

void timeOffHandler()
	{
	if (settings.logDebugs) log.debug  "deluxLighting timeOffHandler"
	luxHandler(true,true)
	}

void deviceHandler(evt)
	{
	def currLux = currLuxCalculate()
	def appTestLux = appLuxCalculate()
	def settingDevice=""
	def settingDim=""
	def settingLux=""
	def settingDvcFlagOn=""
	def triggerSensor = evt.getDevice()
	def triggerId = evt.getDevice().id		//id of triggering device
	def triggerText='Switch'
	def qName=''
	if (triggerSensor.hasCapability("MotionSensor"))
		triggerText='Motion'
	if (settings.logDebugs) log.debug  "deviceHandler entered: ${evt.getDevice().name} $triggerId triggerText $triggerText"
	globalLights.each
		{
		qName="Q${it.id}"
		settingDevice="global${it.id}${triggerText}"		//name of motion or switch setting sensor controlling light
		settingLux="global${it.id}Lux"
		settingDvcFlagOn="global${it.id}${triggerText}FlagOn"
		if (settings.logDebugs) log.debug "searching for ${settingDevice} triggerid:$triggerId ${settings."${settingLuxFlagOn}"} $triggerText occurred for light device ${it.name}"
		if (settings."${settingDevice}" && triggerId == settings."${settingDevice}".id)
			{
			if (settings.logDebugs) log.debug "found ${settingDevice} ids $triggerId ${settings."${settingDevice}".id}"
			settingDeviceMinutes="global${it.id}${triggerText}Minutes"
			minutes=settings."$settingDeviceMinutes"
			if (settings.logDebugs)log.debug "it.name on minutes is ${minutes}"
			seconds=minutes * 60

			if (settings."$settingLux")
				testLux=settings."$settingLux"
			else
				testLux = appTestLux

			if (it.currentValue('switch') == 'on')					//already On update off time
				runInQueue(seconds,qName,it.id)
			else
			if (!(settings."$settingDvcFlagOn") || testLux >= currLux)
				{
				runInQueue(seconds,qName,it.id)
				settingDim="global${it.id}Dim"
				if (settings."$settingDim")
					{
					if (settings.logDebugs) log.debug "deluxLighting deviceHandler doing setlevel ${it.name} ${it.id} ${settingDim}: " + settings."$settingDim"
					it.setLevel(settings."$settingDim", 5)
					}
				else
					{
					if (settings.logDebugs) log.debug "deluxLighting deviceHandler doing On ${it.name} ${it.id} ${settingDim} not found"
					it.on()
					}
				}
			}
		}
	}

void runInQueue(seconds, qName, dvcId)
	{
	try {
		if (settings.logDebugs) log.debug "deluxLighting runInQueue: test if $qName exists as a method seconds:$seconds, Id:$dvcId"
		"${qName}"()									//When method exists queue it to the method
		runIn (seconds, "${qName}", [data: dvcId])		//turn off in setting minutes from last activity, pass the device Id
		}
	catch (e)
		{
		log.warn "deluxLighting runInQueue: Using default runIn queue, please code $qName method"
		runIn (seconds, "lightOff", [data: [it.id], overwrite: false])		//turn off in setting minutes from last activity, pass the device id
		}
	}

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

/* standard runIn queue when device Queue method not coded
void lightOff(dvcId)
	{
	if (settings.logDebugs) log.debug "deluxLighting lightOff entered ${dvcId.id} ${dvcId.name}"
	luxHandler(false,false,dvcId)		//light may or may not be turned off based on lux and system satus
	}

/*
 * runIn queues eliminate overwrite: false reducing overhead for busy switch or motion sensor
 * The following Qnnnn routines are manually coded until I figure out how to generate them wih a Groovy Macro
 * They purpose is to allow the device runIn to work with overwrite true eliminating extra timeout processing
 * user should code one per globalLights defined device.id with a related switch or motion sensor
 */
void Q1288(dvcIdj=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q1288 entered ${dvcId}"
	if (dvcId)
		luxHandler(false,false,dvcId)		//light may or may not be turned off based on lux and system satus
	}

void Q1281(dvcId=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q1281 entered ${dvcId}"
	if (dvcId)
		luxHandler(false,false,dvcId)		//light may or may not be turned off based on lux and system satus
	}

void Q321(dvcId=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q321 entered ${dvcId}"
	if (dvcId)
		luxHandler(false,false,dvcId)		//light may or may not be turned off based on lux and system satus
	}

void Q2(dvcId=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q2 entered ${dvcId}"
	if (dvcId)
		luxHandler(false,false,dvcId)		//light may or may not be turned off based on lux and system satus
	}
void Q3(dvcId=false)
	{
	if (settings.logDebugs) log.debug "deluxLighting Q3 entered ${dvcId}"
	if (dvcId)
		luxHandler(false,false,dvcId)		//light may or may not be turned off based on lux and system satus
	}
