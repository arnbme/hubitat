/*
 *	Adjust (virtual) thermostats based on Room or Whole house Dew Point
 *  Used to control my Fujitsu minisplits or any minisplit with temperature controlled Cool and Dry using Dew Point. 
 * 
 *	Please note you must already have some method of actually sending IR commands to the mini-splits. I'm using the (Withdrawn) Broadlink app with IR devices,
 *	along with my custom minisplit app. If your thermostats are real you have an easier task.
 *  
 *	Probably should have just made this a single device app an install in multiple times. Oh well this was a bit of a challenge 
 * Figure out how to make this work with thermostat schedular
 *
 *									add settings overrides as follows name=home|night|away; controledStat device id setting value as Td  on and off
 *									problem currently handled in code set target in master bedroom night to night -3F 
 *				
 *  Jul 17, 2022	v0.2.1	Calculate target cooling setpoint for cool.
 *									Modify when cooling setpoint is saved on controlStats. 
 *  Jul 16, 2022	v0.2.0	verify dewPoint off is less than dew point on in settings page, update settings descriptions
 *  Jul 15, 2022	v0.1.9	standardize numerical fields to BigDecimal
 *  Jul 15, 2022	v0.1.8	Create a Dew point Virtual temperature device for each controlStats, allows dew point data on Dashboards
 *  Jul 14, 2022	v0.1.7	Add missing logic for device humidity sensor subscribe and event
 *									When house humidity sensor changes only update devices that use it
 *  Jul 13, 2022	v0.1.6	The V01.5 delay did not always work add method anamalyKiller that runs 1 second after any cool or off
 *									that corrects any anomalies on all controlStats such as off with cooling.
 *									Done with runIn(1 so when multiples are issued, it only runs once. Yes this is Fugly!
 *  Jul 12, 2022	v0.1.5	Hubitat thermostat shows anomalous information mode=off operating state =cooling
 *									Cause: when device temperature changes multiple threads execute against the the virtual thermostat  
 *									Fix: When a thermostat temperature change occurs allow 300 millisseconds for Virtual thermostat to complete it's mission
 *											before issuing a the Off or Cool command. Dry command not an issue because thermostat does not do anything with it. 
 *  Jul 12, 2022	v0.1.4	Add optional individual humidity sensors for each controlled thermostat device
 *									Changed dvc.setThermostatMode("cool")  to dvc.cool()  dvc.setThermostatMode("off")  to dvc.off()
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
	return "0.2.1";
	}

def mainPage() 
	{
	dynamicPage(name: "mainPage", title: "Dew Point Calculator (${version()})", install: true, uninstall: true)
		{
		section 
			{
			if (settings?.tempSensor && settings?.humidSensor)
				{
				paragraph "Whole House Dew Point: ${calcDew("DEWPoint_${app.id}")}°${location.temperatureScale} Temp: ${tempSensor.currentTemperature}°${location.temperatureScale} Humidity ${humidSensor.currentHumidity}%"
				def dewOnTest=dewOn
				def dewOffTest=dewOff
				if (location.hsmStatus=='armedAway')
					{
					dewOnTest=dewOnAway
					dewOffTest=dewOffAway
					}
				else
				if (location.hsmStatus=='armedNight')
					{
					dewOnTest=dewOnNight
					dewOffTest=dewOffNight
					}
				paragraph "Dew On Temp: ${dewOnTest}°${location.temperatureScale} Dew Off Temp: ${dewOffTest}°${location.temperatureScale} HSM Status: ${location.hsmStatus}"
				}
		 	if (getChildDevice("DEWPoint_${app.id}"))
				{
				paragraph "Child Device is: DEWPoint_${app.id}"
				}
			if (settings.logDebugs)
				input "buttonDebugOff", "button", title: "Stop Debug Logging"
			else
				input "buttonDebugOn", "button", title: "Debug For 60 minutes"
			input "dewOn", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} On", defaultValue: 60.0, range: "0..100", width: 3, required: true,  submitOnChange: true
			if (dewOn)
				input "dewOff", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "0..${dewOn}", width: 3, required: true
			else
				input "dewOff", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "0..100", width: 3, required: true
			paragraph""
			input "dewOnNight", "decimal", title: "Night Dew Point °${location.temperatureScale} On", defaultValue: 60.0, range: "0..100", width: 3, required: true, submitOnChange: true
			if (dewOnNight)
				input "dewOffNight", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "0..${dewOnNight}", width: 3, required: true
			else
				input "dewOffNight", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "0..100", width: 3, required: true
			paragraph""
			input "dewOnAway", "decimal", title: "Away Dew Point °${location.temperatureScale} On", defaultValue: 60.0, range: "0..100", width: 3, required: true, submitOnChange: true
			if (dewOnAway)
				input "dewOffAway", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "0..${dewOnAway}", width: 3, required: true
			else
				input "dewOffAway", "decimal", title: "Home/Disarmed Dew Point °${location.temperatureScale} Off", defaultValue: 59.0, range: "0..100", width: 3, required: true
			input "thisName", "text", title: "Name of this DEW Point Calculator", submitOnChange: true
			if(thisName) app.updateLabel("$thisName")
			input "tempSensor", "capability.temperatureMeasurement", title: "Whole House Temperature Device", submitOnChange: true, required: true, multiple: false
			input "humidSensor", "capability.relativeHumidityMeasurement", title: "Whole House Humidity Sensor", submitOnChange: true, required: true, multiple: false
			input "driverStat", "capability.thermostat", title: "Dew Point Mode Controlling Thermostat. Usually a virtual device modified with to have a dewpt mode", required: true, multiple: false
			input "controlStats", "capability.thermostat", title: "Dew Point Controlled Thermostats", required: true, multiple: true, submitOnChange: true
			BigDecimal dewOnTest=dewOn
			BigDecimal dewOffTest=dewOff

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

			calcTemp(true, dewOnTest)
			if (settings?.tempSensor && settings?.humidSensor && settings?.controlStats)
				{
				controlStats.each
					{
					input "humidSensor${it.id}", "capability.relativeHumidityMeasurement", title: "${it.label} Humidity Sensor (Optional uses Whole House Humidity sensor when not defined)", required: false, multiple: false, submitOnChange: true
					RH = humidSensor.currentHumidity
					if (settings."humidSensor${it.id}")			//check if there is a defined humidity sensor
						{
						dvc=settings."humidSensor${it.id}"			//resolve name system cant handle more than one level of resolution (at least for me) 
						RH=dvc.currentHumidity
						}
					paragraph "Dew Point: ${calcDew("DewPt ${it.name} ${it.id}", it.currentTemperature)}°${location.temperatureScale} Temp: ${it.currentTemperature}°${location.temperatureScale} Humidity: ${RH}% Cool Pt: ${it.currentCoolingSetpoint} ${it.label}"
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
	calcDew("DEWPoint_${app.id}")
	controlStats.each
		{
		subscribe(it, "temperature", handlerDeviceTemp)
		if (settings."humidSensor${it.id}")
			{
//			objProperties(settings."humidSensor${it.id}")
			subscribe(settings."humidSensor${it.id}", "humidity", handlerDeviceHUMID)
			}
		def dvcNm="DewPt ${it.name} ${it.id}"		//Child Device Name
		def dvcObj=getChildDevice(dvcNm)				//Get the device object
		if (!dvcObj)												//create child device if it does not exist
			dvcObj=addChildDevice("hubitat", "Virtual Temperature Sensor", "${dvcNm}", null, [label: "${dvcNm}", name: "${dvcNm}"])
		calcDewUpdateDevice(it)
		}
	}	

void uninstalled()	{
    childDevices.each
    	{
    	deleteChildDevice(it.deviceNetworkId)
    	}
    }

//	Calculate Temperature for DewPoint and Humidity
//	Input 
//	Tx = Dew Point target
//	RH = current relative humidity
//	Output
//	T = cool point temperature
def calcTemp(issuePara=false,Tx,RH=humidSensor.currentHumidity) 
	{
	BigDecimal TD=(location.temperatureScale == "F")? ((Tx - 32) * 5 / 9) : Tx
	BigDecimal T =243.04*(((17.625*TD)/(243.04+TD))-Math.log(RH/100))/(17.625+Math.log(RH/100)-((17.625*TD)/(243.04+TD)))
	if (location.temperatureScale == "F") T = ((T * 1.8) + 32)
	T=Math.round(T * 10 ) / 10 
	log.debug "Target Temp: ${T} RH: ${RH} DewPoint: ${Tx}"
	if (issuePara) paragraph "Target  Temp: ${T} RH: ${RH} DewPoint: ${Tx}"
	return T
	}

/*
 *dvc: name of dew point virtual temperature device to update for example "DewPt ${it.name} ${it.id}"
*/ 
def calcDew(dvc=false,Tx=tempSensor.currentTemperature, RH= humidSensor.currentHumidity) 
	{
//	log.debug "calcdew entry ${dvc}"
	BigDecimal T = (location.temperatureScale == "F")? ((Tx - 32) * 5 / 9) : Tx
	BigDecimal dewPoint = 243.04 * (Math.log(RH/100)+((17.625*T)/(243.04+T)))/(17.625-Math.log(RH/100)-((17.625*T)/(243.04+T)))
	if (location.temperatureScale == "F")
		dewPoint = ((dewPoint * 1.8) + 32)
	dewPoint=Math.round(dewPoint * 10 ) / 10
	if (dvc)
		{
		def dvcObj=getChildDevice(dvc)							//Get the dew point temperature holding device
		if (dvcObj && dewPoint != dvcObj.currentTemperature)	//update target dvc when defined
			{
			if (settings.logDebugs) log.debug "update dewPoint: ${dewPoint} from ${dvc.currentTemperature} Humid: ${RH} Temp: ${tempSensor.currentTemperature}"
			dvcObj.setTemperature(dewPoint)
			}
		else 
			if (settings.logDebugs) log.debug "skipped update dewpoint for device: ${dvc}"
		}	
	return dewPoint
    }	
	
/*
 * dvc: Must be a controlStats Temperature Device Object
 * dew point is calculated all the time for use on the dashboards
*/ 
void calcDewUpdateDevice(dvc,commandDelay=false)			//dvc must be a  thermostat device 
	{
//	log.debug "entered calcDewUpdateDevice ${dvc.id} ${dvc.name}"
	def temperature=dvc.currentTemperature
	def id=dvc.id
	if (settings.logDebugs && settings."humidSensor${id}")
		log.debug "calcDewUpdateDevice optional humidity sensor found ${dvc.label} ${settings."humidSensor${id}".currentHumidity}"

	def nameDewPt="DewPt ${dvc.name} ${dvc.id}"
	dewPoint = (settings."humidSensor${id}")? calcDew(nameDewPt,temperature, settings."humidSensor${id}".currentHumidity,) : calcDew(nameDewPt,temperature)

	if (driverStat.currentThermostatMode == 'dewpt')
		{
		def thermostatMode = dvc.currentThermostatMode		
		def hsmStatus=location.hsmStatus
		BigDecimal dewOnTest=dewOn
		BigDecimal dewOffTest=dewOff
		
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
					state."Temp${dvc.id}"= dvc.currentCoolingSetpoint		//v021 Jul 17, 2022	save cooling setpoint 
					dvc.setThermostatMode("dry")
					(location.temperatureScale == "F")? dvc.setCoolingSetpoint(76) : dvc.setCoolingSetpoint(24) // with minisplits temp must be lowered for dry to actually work 
					}
				}
			else
				{
				if (thermostatMode != 'cool')
					{
					if (settings.logDebugs) log.debug ("On cool dewpoint: ${dvc.id} ${dvc.name} ${dewPoint}")
					state."Temp${dvc.id}"= dvc.currentCoolingSetpoint		//v021 Jul 17, 2022	save cooling setpoint 
					if (hsmStatus=='armedNight' && dvc.name=='Zone4 MasterBedRoom')		//adjust night Td for Master bedroom
						dewOnTest = dewOnTest - 3
					dvc.setCoolingSetpoint(calcTemp(false, dewOnTest))		//set cool point target tempereature
					dvc.cool()
					runIn(1,anomalyKiller)												
					}
				}
			}	
		else
		if (thermostatMode=='off')
			{
			if (settings.logDebugs) log.debug ("Already Off ${dvc.id} ${dvc.name} ${dewPoint} On: ${dewOnTest} Off: ${dewOffTest}")
			}	
		else
		if (dewPoint < dewOffTest || (dewPoint < (dewOffTest + 1.5) && thermostatMode=='dry'))
			{
			if (settings.logDebugs) log.debug ("Off dewpoint: ${dvc.id} ${dvc.name} ${dewPoint} On: ${dewOnTest} Off: ${dewOffTest}") 
			dvc.off()
			runIn(1,anomalyKiller)
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

void handlerHUMID(evt) {
	if (settings.logDebugs)	log.debug "Whole House Humidity = ${evt.value}"
	calcDew("DEWPoint_${app.id}")								//get new system dew point
	controlStats.each
		{
		if (!settings."humidSensor${it.id}")
			{
			calcDewUpdateDevice(it)		//update each house humidity contolled thermostat
//			log.debug 'execute calcDewUpdateDevice ${it.name} using whole house humidistat'
			}
		}
	}

void handlerDeviceHUMID(evt) {
	if (settings.logDebugs)	
		log.debug "Device Humidity = ${evt.value} ${evt.deviceId}"
	controlStats.each
		{
		if (settings."humidSensor${it.id}")			//check if using a defined humidity sensor
			{
			dvc=settings."humidSensor${it.id}"			//resolve name system cant handle more than one level of resolution (at least for me) 
//			log.debug dvc.id+' '+evt.deviceId 
//			log.debug "a humid sensor found ${it.name}"
//			log.debug dvc.id+' '+dvc.id.class.name+' '+evt.deviceId+' '+evt.deviceId.class.name
			if (dvc.id == evt.deviceId as String)		//dvc.id = string evt.deviceId = Long	oy vay wont match without changing field type
				{
				calcDewUpdateDevice(it)		//update associated thermostat
//				log.debug "executed calcDewUpdateDevice for ${it.name} event humidistat ${dvc.name}"
				}
			else
				log.debug "did not match"
			}
		}
	}

//	System temperatue change does not impact conntolled devices
void handlerTEMP(evt) {
	if (settings.logDebugs) log.debug "Average House Temperature = ${evt.value}"
	calcDew("DEWPoint_${app.id}")
}

//	Update specific controlled thermostat when temperatue changes
void handlerDeviceTemp(evt) {
	if (settings.logDebugs) 	log.debug "Device Temperature = ${evt.value} ${evt.device.name}"
		calcDewUpdateDevice(evt.device, true)		//V0.1.5 command delay when calDewUpdateDevice issues on or cool command
}

void handlerMode(evt) 
	{
	if (settings.logDebugs) 
		log.debug "Mode = ${evt.value}"
	if (evt.value=='dewpt')
		{
//		save all target devices temperature and running mode, set cool running mode
//		saveControlsData(true)				//deprecate V021 Jul 17, 2022
		state.lastMode='dewpt'
		controlStats.each
			{
			calcDewUpdateDevice(it)		//update each contolled thermostat
			}
		}
//	else											//deprecate V021 Jul 17, 2022
//	if (state.lastMode=='dewpt')
//		restoreControlsData(false)		   //RM is propogating thermostat mode from driverStat on my system, so don't restore device mode here 
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

//	Purpose fix weird anomalies likely from Virtual Thermostat mistakenly resetting or failing to set thermostatOperatingState
void anomalyKiller()
	{
	if (settings.logDebugs) log.debug 'anomalyKiller entered'
	controlStats.each
		{
		if (it.currentThermostatMode == 'off' && it.currentThermostatOperatingState == 'cooling')
			{
			if (settings.logDebugs) log.debug "anomaly dry with cooling found for ${it.name}"
			it.setThermostatOperatingState('idle') 			//if this does not work issue the off()
//			it.off()				
			}
		else
		if (it.currentThermostatMode == 'cool' && it.currentThermostatOperatingState == 'idle' && 
			it.currentCoolingSetpoint < (it.currentTemperature-it.currentHysteresis))
			{
			if (settings.logDebugs) log.debug "anomaly cool with idle found for ${it.name} Cool Pt: ${it.currentCoolingSetpoint} Temperatue: ${it.currentTemperature} Hysteresis: ${it.currentHysteresis}"
			it.setThermostatOperatingState('cooling') 		//if this does not work issue the cool()
//			it.cool()				
			}
		}
	}	
	
//	put all properties to debug log	
	void objProperties(obj)
		{
		obj.properties.each							//gets around error on null values, testing for null fails with an error
			{ k,v -> 
			if (v?.class)	
				log.debug  "${k}: ${v} ${v.class.name}"
			else	
				log.debug  "${k}: ${v}"
			}
		}