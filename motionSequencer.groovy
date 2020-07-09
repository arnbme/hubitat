/**
 *  Import Url:   https://raw.githubusercontent.com/arnbme/hubitat/master/motionSequencer.groovy
 *
 *  motionSequencer App
 *  Functions:
 *
 *		1. Add some extra security during armedNight by checking normally unmonitored Motion Sensors
 *			using neighbor motions sensors and time
 *		2. Keep overhead very low by activating only during armedNight
 *		3. Triggers a virtual motion sensor that must be monitored by HSM during armedNight
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
 *  Jul 09, 2020 v0.0.0 Create
 */

definition(
    name: "motionSequencer",
    namespace: "arnbme",
    author: "Arn Burkhoff",
    description: "(${version()}) Motion Sensor Sequencer app",
	category: "Security",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/arnbme/hubitat/master/motionSequencer.groovy")

preferences {
    page(name: "mainPage")
	}

def version()
	{
	return "0.0.0";
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
			input "globalHallMotion", "capability.motionSensor", required: true, multiple: false,
				title: "Hallway Motion Sensor"
			input "globalLivingRoomMotion", "capability.motionSensor", required: true, multiple: false,
				title: "Living Room Motion Sensor"
			input "globalDiningRoomMotion", "capability.motionSensor", required: true, multiple: false,
				title: "Dining Room Motion Sensor"
			input "globalOfficeMotion", "capability.motionSensor", required: true, multiple: false,
				title: "Office Motion Sensor"
			input "globalVirtualMotionSensor", "capability.motionSensor", required: true, multiple: false,
				title: "Virtual Motion Sensor, must be monitored by HSM ArmedNight"
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
	if (settings.logDebugs) log.debug  "motionSequencer initialize ${location.hsmStatus}"
	if(settings.logDebugs)
		runIn(1800,logsOff)			//turns off debug logging after 30 min
	else
		unschedule(logsOff)
	if (globalDisable || location.hsmStatus != 'armedNight')
		{
		if (settings.logDebugs) log.debug "motionSequencer: initialize motion sensor subscribes disabled ${location.hsmStatus} $globalDisable"
		}
	else
		{
		subscribe(globalHallMotion, "motion.active", motionHallHandler)
		subscribe(globalLivingRoomMotion, "motion.active", motionLivingRoomHandler)
		subscribe(globalDiningRoomMotion, "motion.active", motionDiningRoomHandler)
		subscribe(globalOfficeMotion, "motion.active", motionOfficeHandler)
		}
	subscribe(location, "hsmStatus", hsmStatusHandler)
	if (!state?.hall) state.hall=now()-86400000
	if (!state?.living) state.living=now()-86400000
	if (!state?.dining) state.dining=now()-86400000
	if (!state?.office) state.office=now()-86400000
	}

void logsOff(){
//	stops debug logging
	log.info "luxLighting: debug logging disabled"
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}

void motionHallHandler(evt)
	{
	if (settings.logDebugs) log.debug "motionSequencer: motionHallHandler ${evt.value}"
	def unixTimeMillis = now()
	state.hall = unixTimeMillis
//	not checking this sensor gets trigerred when walking out of master bedroom on way to bathroom
	}

void motionLivingRoomHandler(evt)
	{
	if (settings.logDebugs) log.debug "motionSequencer: motionLivingRoomHandler ${evt.value}"
	def unixTimeMillis = now()
	state.living = unixTimeMillis
	checkNeighbors('Living Room', unixTimeMillis, state.hall, state.dining)
	}

void motionDiningRoomHandler(evt)
	{
	if (settings.logDebugs) log.debug "motionSequencer: motionDiningHandler ${evt.value}"
	def unixTimeMillis = now()
	state.dining = unixTimeMillis
	checkNeighbors('Dining Room', unixTimeMillis, state.living, state.office)
	}

void motionOfficeHandler(evt)
	{
	if (settings.logDebugs) log.debug "motionSequencer: motionOfficeHandler ${evt.value}"
	def unixTimeMillis = now()
	state.office = unixTimeMillis
	checkNeighbors('Office', unixTimeMillis, state.dining, false)
	}

void checkNeighbors(fromRoom, unixTimeMillis, neighbor1, neighbor2)
	{
//	a neighbor must be 15 minutes or less or trigger virtual motion sensor
	if (settings.logDebugs) log.debug "motionSequencer: checkNeighbors entered from $fromRoom $unixTimeMillis $neighbor1 $neighbor2"
//	log.debug "motionSequencer: ${unixTimeMillis.class.name} ${neighbor1.class.name} ${neighbor2.class.name}"
	if ((unixTimeMillis - neighbor1) > 900000)		//neighbor1 > 15 minutes, check neighbor2
		{
		if (neighbor2)
			{
			if ((unixTimeMillis - neighbor2) > 900000)		//neighbor1 > 15 minutes, neighbor2 > 15 minutes trigger
				{
				log.info "motionSequencer: checkNeighbors triggered alert from $fromRoom $unixTimeMillis $neighbor1 $neighbor2 millis1: ${unixTimeMillis - neighbor1} millis2: ${unixTimeMillis - neighbor2}"
				globalVirtualMotionSensor.inactive()
				globalVirtualMotionSensor.active()
				}
			}
		else
			{
			log.info "motionSequencer: checkNeighbors triggered alert from $fromRoom $unixTimeMillis $neighbor1 $neighbor2 millis1: ${unixTimeMillis - neighbor1}"
			globalVirtualMotionSensor.inactive()
			globalVirtualMotionSensor.active()
			}
		}
	}

void hsmStatusHandler(evt)
	{
	if (settings.logDebugs) log.debug  "motionSequencer hsmStatusHandler entered ${evt.value} ${location.hsmStatus}"
    unsubscribe()
    initialize()
    }

