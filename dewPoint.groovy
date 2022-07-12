/*
 *	This app was developed for Fujitsu minisplits or any minisplit with temperature controlled Cool and Dry modes. It's a work in progress.
 *  Note without some logic the Fujitsu mini-split remains on until shut off. The units have their own builit-in thermostat controlled logic
 *  and remain on fan mode when temperature is reached. 
 *  
 *	This app also attempts to mitigate the amount of time freezing air blows on room occupants. This is a demo concept app, eventually the logic
 *  will liklely be combined into my minisplit app, but for now it's seems to be working
 *
 *	To Do 						add Individual humidity controls to controlled thermostats
 *									perhaps add a DewPoint virtual thermostat to each controlled thermostat allowing data to show on dashboards 
 *
 *  Jul 10, 2022	v0.1.3	Make each controlStat thermostat work independently based on the room's dewPoint
 *										(need to purchase more humidity sensors?)
 *									Deprecate subscribe to HSM status. No longer needed with independent device control
 *									Lots of cleanup and tweaking
 *  Jul 10, 2022	v0.1.2	restore child device to virtual temperature device, don't need the therrmostat
 *  Jul 08, 2022	v0.1.2	Subscribe to HSM Status changes to catch and save any temperature changes
 *									Subscribe to controlStat cooling temperature change and update stored device restore value
 *									Need a better way to filter out changes when we change temp in dry mode
 *  Jul 08, 2022	v0.1.1	Calculate DewPoint for each individual device on settings 
 *									August-Roche-Magnus approximations from webpage http://bmcnoldy.rsmas.miami.edu/Humidity.html
 * 								RH: =100*(EXP((17.625*TD)/(243.04+TD))/EXP((17.625*T)/(243.04+T)))
 *									TD: =243.04*(LN(RH/100)+((17.625*T)/(243.04+T)))/(17.625-LN(RH/100)-((17.625*T)/(243.04+T)))
 *									T: =243.04*(((17.625*TD)/(243.04+TD))-LN(RH/100))/(17.625+LN(RH/100)-((17.625*TD)/(243.04+TD)))
 *  Jul 07, 2022	v0.1.0	Change child device to a virtual thermostat (may kill this) killed Jul 10, 2022 V0.1.2
 *									When off, set thermostat off versus raising temperature
 *									set target dewpoint based on HSM Status, add additional dewpoint inputs for Night and Away
 *  Jul 06, 2022	v0.0.9	Adjust logic for triggering dry mode with high dewpoint and cool temperatures that don't trigger cool operration on mini splits
 *  Jul 05, 2022	v0.0.8	Adjust logic for triggering and correctly resetting target device temperature and mode status, display child device name
 *  Jul 05, 2022	v0.0.7	Eliminate states for Temp and Humidity, delete child device on uninstall
 *  Jul 04, 2022	v0.0.6	Add logging on with auto off after 60 minutes
 *  Jul 04, 2022	v0.0.5	Cleanup for correct F Or C temperature control
 *  Jul 04, 2022	v0.0.4	First clean version with external Dew point controls
 *  Jun 30, 2022	v0.0.0	Create From John Rob version, Guffman's Virtual Dewpoint, and aaiyar's thoughts on the forum
 *
 *  Copyright 2022 Arn Burkhoff
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
 */

definition(
    name: "Dew Point Calculator",
    namespace: "hubitat",
    author: "Arn B",
    description: "Dew Point Calculator",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def version()
	{
	return "0.1.3";
	}

def mainPage() 
	{
	dynamicPage(name: "mainPage", title: "Dew Point Calculator (${version()})", install: true, uninstall: true)
		{
		section 
			{
			if (settings?.tempSensor && settings?.humidSensor)
				{
				paragraph "Dew Point: ${calcDew()}°${location.temperatureScale} Temp: ${tempSensor.currentTemperature}°${location.temperatureScale} Humidity ${humidSensor.currentHumidity}%"
				if (settings.dewOn) calcTemp('Home',settings.dewOn)
				if (settings.dewOnNight) calcTemp('Night',settings.dewOnNight)
				if (settings.dewOnAway) calcTemp('Away',settings.dewOnAway)
				}
		 	if (getChildDevice("DEWPoint_${app.id}"))
				{
				paragraph "Child Device is: DEWPoint_${app.id}"
				}
			if (settings.logDebugs)
				input "buttonDebugOff", "button", title: "Stop Debug Logging"
			else
				input "buttonDebugOn", "button", title: "Debug For 60 minutes"
			input "dewOn", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} On", defaultValue: 60.0, range: "*..*", width: 3, required: true
			input "dewOff", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "*..*", width: 3, required: true
			paragraph""
			input "dewOnNight", "decimal", title: "Night Dew Point °${location.temperatureScale} On", defaultValue: 60.0, range: "*..*", width: 3, required: true
			input "dewOffNight", "decimal", title: "Night Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "*..*", width: 3, required: true
			paragraph""
			input "dewOnAway", "decimal", title: "Away Dew Point °${location.temperatureScale} On", defaultValue: 60.0, range: "*..*", width: 3, required: true
			input "dewOffAway", "decimal", title: "Away Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "*..*", width: 3, required: true
			input "thisName", "text", title: "Name of this DEW Point Calculator", submitOnChange: true
			if(thisName) app.updateLabel("$thisName")
			input "tempSensor", "capability.temperatureMeasurement", title: "Select Temperature Sensor", submitOnChange: true, required: true, multiple: false
			input "humidSensor", "capability.relativeHumidityMeasurement", title: "Select Humidity Sensor", submitOnChange: true, required: true, multiple: false
			input "driverStat", "capability.temperatureMeasurement", title: "Select Mode Controlling Thermostat", required: true, multiple: false
			input "controlStats", "capability.temperatureMeasurement", title: "Select DewPoint Controlled Thermostats ", required: true, multiple: true, submitOnChange: true
			if (settings?.tempSensor && settings?.humidSensor && settings?.controlStats)
				{
				controlStats.each
					{
					paragraph "Dew Point: ${calcDew(false, it.currentTemperature)}°${location.temperatureScale} Temp: ${it.currentTemperature}°${location.temperatureScale} Humidity ${humidSensor.currentHumidity}% ${it.label}"
					}
				}
			}
		}
	}

void installed() {
	initialize()
}

void updated() {
	unsubscribe()
	initialize()
}

void initialize()
	{
 	def averageDev = getChildDevice("DEWPoint_${app.id}")
	if(!averageDev) 
		{
		averageDev = addChildDevice("hubitat", "Virtual Temperature Sensor", "DEWPoint_${app.id}", null, [label: "DEWPoint_${app.id}", name: "DEWPoint_${app.id}"])
		state.lastMode = driverStat.currentThermostatMode
		}
	subscribe(tempSensor, "temperature", handlerTEMP)
	subscribe(humidSensor, "humidity", handlerHUMID)
	subscribe(driverStat, "thermostatMode", handlerMode)
	calcDew()
	controlStats.each
		{
		subscribe(it, "coolingSetpoint", handlerCoolingTemp)
		subscribe(it, "temperature", handlerDeviceTemp)
		calcDewPointUpdate(it)
		}
	}	

void uninstalled()
	{
	if (getChildDevice("DEWPoint_${app.id}")) deleteChildDevice("DEWPoint_${app.id}")
	}
	
void calcTemp(status,Tx,RH=(humidSensor.currentHumidity < 55)? humidSensor.currentHumidity : 55) 
	{
	def TD=(location.temperatureScale == "F")? ((Tx - 32) * 5 / 9) : Tx
	def T =243.04*(((17.625*TD)/(243.04+TD))-Math.log(RH/100))/(17.625+Math.log(RH/100)-((17.625*TD)/(243.04+TD)))
	if (location.temperatureScale == "F") T = ((T * 1.8) + 32)
	T=Math.round(T * 10 ) / 10 
	paragraph "Target  Temp: ${T} RH: ${RH} ${status}"
	}

def calcDew(adjustDev=true,Tx=tempSensor.currentTemperature, RH= humidSensor.currentHumidity) 
	{
	def T = (location.temperatureScale == "F")? ((Tx - 32) * 5 / 9) : Tx
	def dewPoint = 243.04 * (Math.log(RH/100)+((17.625*T)/(243.04+T)))/(17.625-Math.log(RH/100)-((17.625*T)/(243.04+T)))
	if (location.temperatureScale == "F")
		dewPoint = ((dewPoint * 1.8) + 32)
	dewPoint=Math.round(dewPoint * 10 ) / 10
//	log.debug "${dewPoint} ${averageDev.currentTemperature}"
	if (adjustDev)
		{
	 	def averageDev = getChildDevice("DEWPoint_${app.id}")
		if (averageDev && dewPoint != averageDev.currentTemperature)
			{
 			if (settings.logDebugs) log.debug "update dewPoint: ${dewPoint} from ${averageDev.currentTemperature} Humid: ${RH} Temp: ${tempSensor.currentTemperature}"
			averageDev.setTemperature(dewPoint)
			}
//		else 
//			if (settings.logDebugs) log.debug "skipped update dewpoint did not change or device not defined"
		}
	return dewPoint
    }	
	
void calcDewUpdateDevice(dvc)			//dvc must be a thermostat device 
	{
//	log.debug "entered calcDewUpdateDevice ${dvc.id} ${dvc.name}"
	if (driverStat.currentThermostatMode == 'dewpt')
		{
		def temperature=dvc.currentTemperature
		def id=dvc.id
		dewPoint=calcDew(false,temperature)
		def thermostatMode = dvc.currentThermostatMode		
		def hsmStatus=location.hsmStatus
		def dewOnTest=dewOn
		def dewOffTest=dewOff
		
		if (hsmStatus=='armedAway')
			{
			dewOnTest=dewOnAway
			dewOffTest=dewOffAway
			}
		else
		if (hsmStatus=='armedNight')
			{
			dewOnTest=dewOnNight
			dewOffTest=dewOffNight
			}
		if (settings.logDebugs) log.debug "calcDewUpdateDevice ${dvc.id} ${dvc.name} ${dewPoint} On: ${dewOnTest} Off: ${dewOffTest} ${temperature} "

		if (dewPoint >= dewOnTest)
			{
			if (dewPoint >= (dewOnTest + 1.5) && temperature < dvc.currentCoolingSetpoint)
				{
//				High humidity with low temperature, kick in the dehumidifier if availabe. With Mini splits dry mode kind of works
				if (thermostatMode != 'dry')
					{
					if (settings.logDebugs) log.debug ("On dry dewpoint: ${dvc.id} ${dvc.name} ${dewPoint}") 
					dvc.setThermostatMode("dry")
					(location.temperatureScale == "F")? dvc.setCoolingSetpoint(76) : dvc.setCoolingSetpoint(24) // with minisplits temp must be lowered for dry to actually work 
					}
				}
			else
				{
				if (thermostatMode != 'cool')
					{
					if (settings.logDebugs) log.debug ("On cool dewpoint: ${dvc.id} ${dvc.name} ${dewPoint}")
					if (thermostatMode=='dry')
						dvc.setCoolingSetpoint(state."Temp${id}")
					dvc.setThermostatMode("cool")
					}
				}
			}	
		else
		if (thermostatMode=='off')
			{
			if (settings.logDebugs) log.debug ("Already Off ${dvc.id} ${dvc.name} ${dewPoint} On: ${dewOnTest} Off: ${dewOffTest}")
//			if (dewPoint > dewOffTest)
//				{
//				if (settings.logDebugs) log.debug ("Off setting cool dewpoint: ${dvc.id} ${dvc.name} ${dewPoint}") 
//				dvc.setThermostatMode("cool")
//				}
			}	
		else
		if (dewPoint < dewOffTest || (dewPoint < (dewOffTest + 1.5) && thermostatMode=='dry'))
			{
			if (settings.logDebugs) log.debug ("Off dewpoint: ${dvc.id} ${dvc.name} ${dewPoint} On: ${dewOnTest} Off: ${dewOffTest}") 
			if (thermostatMode=='dry')			//restore original temperature from dry
				dvc.setCoolingSetpoint(state."Temp${id}")
			dvc.setThermostatMode("off")
			}
		}
	}

void saveControlsData(saveMode=false)
	{
	controlStats.each
		{
//		log.debug (Saving "${it.label} Id:${it.id} ${it.currentCoolingSetpoint} ${it.currentThermostatMode}")
		state."Temp${it.id}"= it.currentCoolingSetpoint
		if (saveMode)
			state."Mode${it.id}"= it.currentThermostatMode
		}
	}	

void restoreControlsData(resetMode=false)
	{
	controlStats.each
		{
//		log.debug (Restoring "${it.label} Id:${it.id} ${state.'Temp${it.id}'} ${state.'Mode${it.id}'}")	//Warning This fails
//		log.debug 'Restoring '+it.label+' '+it.id+' '+state."Temp${it.id}"+' '+state."Mode${it.id}"		//This works
		if (state."Temp${it.id}")
			it.setCoolingSetpoint(state."Temp${it.id}")
		if (resetMode && state."Mode${it.id}")
			it.setThermostatMode(state."Mode${it.id}")
		}
	}	

//	Trigger: device cooling setpoint is changed
//	Set: save restore temperature for device, when device mode is not dry
//  DOES NOT IMPACT DEW RUNNING CRITERIA, simply update saved state temperature
void handlerCoolingTemp(evt) 
	{
	if (settings.logDebugs)
		log.debug "handlerCoolingTemp ${evt.getDevice()} ${evt.getDeviceId()} ${evt.value} ${evt.source} ${evt.installedAppId} ${evt.device.currentThermostatMode}"

	if (evt.device.currentThermostatMode  != 'dry')	// actually needs to be when we did not change temp when setting dry future logic
		{
//		state."Temp${evt.getDeviceId()}"= evt.value
		state."Temp${evt.getDeviceId()}"= evt.numberValue
		}
//	evt.properties.each{ log.info it}			//keep this here just in case I need to get more info
//	evt.properties.each							//gets around error on null values, testing for null fails with an error
//		{ k,v -> 
//		if (v?.class)	
//			log.debug  "${k}: ${v} ${v.class.name}"
//		else	
//			log.debug  "${k}: ${v}"
//		}
//	log.debug evt.value.class.name
//	log.debug state."Temp${evt.getDeviceId()}".class.name
	}
void handlerHUMID(evt) {
	if (settings.logDebugs)	log.debug "Humidity = ${evt.value}"
	calcDew()								//get new system dew point
	controlStats.each
		{
		calcDewUpdateDevice(it)		//update each contolled thermostat
		}
	}

//	System temperatue change does not impact conntolled devices
void handlerTEMP(evt) {
	if (settings.logDebugs) log.debug "Average House Temperature = ${evt.value}"
	calcDew()
}

//	Update specific controlled thermostat when temperatue changes
void handlerDeviceTemp(evt) {
	if (settings.logDebugs) 	log.debug "Device Temperature = ${evt.value} ${evt.device.name}"
	calcDewUpdateDevice(evt.device)
}

void handlerMode(evt) 
	{
	if (settings.logDebugs) 
		log.debug "Mode = ${evt.value}"
	if (evt.value=='dewpt')
		{
//		save all target devices temperature and running mode, set cool running mode
		saveControlsData(true)
		state.lastMode='dewpt'
		controlStats.each
			{
			calcDewUpdateDevice(it)		//update each contolled thermostat
			}
		}
	else
	if (state.lastMode=='dewpt')
		restoreControlsData(false)		   //RM is propogating thermostat mode from driverStat on my system, so don't restore device mode here 
	state.lastMode=evt.value
	}

//	Process Debug buttons
void appButtonHandler(btn)
	{
	switch(btn)
		{
		case "buttonDebugOff":
			debugOff()
			break
		case "buttonDebugOn":
			app.updateSetting("logDebugs",[value:"true",type:"bool"])
			runIn(3600,debugOff)		//turns off debug logging after 60 Minutes
			log.info "debug logging enabled"
			break
		}
	}

void debugOff(){
//	stops debug logging
	log.info "debug logging disabled"
	unschedule(debugOff)
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}
	