/**
 *  Import Url:   https://raw.githubusercontent.com/arnbme/hubitat/master/MiniSplit.groovy
 *
 *  MiniSplit App
 *  Functions:
 *		Controls my dumb Fujitsu mini splits in conjunction with Broadlink IR blasters, and an HE Virtual Thermostat device
 *      Allows for using mini split Dry mode as part of cooling process, or stand alone.
 *
 *		I wrote this after attempting this with RM, it's just to slow and tedious for coding complex logic, IMHO.
 *		Also I could not get the HVAC manager app functional with my Fujitsu devices
 *
 *	Preinstallation Requirements
 *	1. Install IR Blasters, this app is designed to work with Broadlink devices using the now withdrawn Broadlink Mini Driver app at
 *		https://community.hubitat.com/t/withdrawn-native-broadlink-rm-rm-pro-rm-mini-sp-driver-rc-hvac-manager/31344
 *
 *  2. Create working IR code sets for cooling (multiple temperatures), dry, off, and more as necessary
 *
 *  3. Must have a working Virtual Thermostat (or perhaps a real device).
 *		I use a Virtual Thermostat averaging temperatures from 3 devices using the HE Average Temperature app at
 *		https://github.com/hubitat/HubitatPublic/tree/master/example-apps
 *		Changed the app created child from a virtual temperature sensor device to a virtual thermostat.
 *
 *	4. A dashboard displaying the Virtual Thermostat device or some other method for control
 *
 *	5. Logic for "heat" mode will likely happen in the future
 *
 *	How to store Mini Split IR codes for Fujitsu units. (Your remote key names and setup may vary)
 *	1. Set mini-split device power ON, pressing the remote's Stop/Start key. Mini-Split device powers on with last setting stored in the REMOTE device.
 *	2. Set device to exact temperature, mode, swing state, louver settings, and Fan (auto suggested) speed, and other settings wanted
 *  3. Press remote's Start/Stop key setting power to OFF.
 *  4. Set IR blaster into learning mode
 *  5. Aim remote at the blaster then press remote's Start/Stop key, setting power ON
 *			(the Off IR code is sent anytime power is On)
 *  6. Save and name the IR codes
 *		My IR code names and settings
 *		AC On2169 - Mode: Cool, Temperature 21C 69F, Fan: auto (swing, louvers and other settings: whatever you want)
 *		AC On2271 - Mode: Cool, Temperature 22C 71F, Fan: auto (swing, louvers and other settings: whatever you want)
 *		AC On2373 - Mode: Cool, Temperature 23C 73F, Fan: auto (swing, louvers and other settings: whatever you want)
 *		AC On2475 - Mode: Cool, Temperature 24C 75F, Fan: auto (swing, louvers and other settings: whatever you want)
 *		AC On2577 - Mode: Cool, Temperature 25C 77F, Fan: auto (swing, louvers and other settings: whatever you want) not currently used
 *      ACDry74Swing - Mode: Dry, Temperature: 74F 25C Swing: On (swing, louvers and other settings: whatever you want)
 *				Dry may or may not utilize temperature or fan speed setting depending upon the mini-split hardware,
 8				check the manufacturer's operating manual
 *		AC Off - Mode: off
 *  7. Adjust IR code names in the code as necessary
 *
 *	Notes:
 *	Mini-split devices operate in "cool" and "dry" modes only, allowing for manual control with the remote in other modes
 *	Most Mini-splits use Celcious for native temperature settings. When using Fahrenheit, temperature is appoximate.
 *	Most Mini-splits have a built in non accessable thermostat.
 *  IR communication is one way, from remote or IR blaster to mini-split
 *  When an Ir code is store with a blank in it's name, the Broadlink app replaces the space with a _ character.
 *		However when specifying the name in the sendStoredCode command, the _ character cannot be specified.
 *		Probably best not to use embedded blanks with IR command names. I learned the hard way.
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
 *  Jun 28, 2020 v0.0.5 Process HSM arming Status
 *  Jun 28, 2020 v0.0.4	Change pauseExecution to a runIn
 *  Jun 27, 2020 v0.0.3	Support user defined thermostat mode: dry Turn on dry when idle and mode=dry regardless of temperature
 *							Used virtual thermostat command setSupportedThermostatModes adding dry
 *								with string [cool, off, dry, heat, auto, emergency heat] also ajusting settings order on device
 *  						Subscribe to thermostatMode changes
 *							set Fan mode to dry, cool, or off giving clear visual operational indication
 *  Jun 26, 2020 v0.0.2	Delay temperature and coolSetPoint changes or 2 seconds allowing any virtual thermostat operating mode changes to complete
 *						set fan operating mode to dry when using dry mode, otherwise set fan mode to on
 *  Jun 25, 2020 v0.0.1	Use thermostatModeHandler for all IR processing, dont set operating mode to dry
 *  Jun 25, 2020 v0.0.0	Create
 */

definition(
    name: "MiniSplit",
    namespace: "arnbme",
    author: "Arn Burkhoff",
    description: "(${version()}) Mini Split control app",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/arnbme/hubitat/master/MiniSplit.groovy")

preferences {
    page(name: "mainPage")
	}

def version()
	{
	return "0.0.5";
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
			input "globalThermostat", "capability.thermostat", required: true, multiple: false,
				title: "A Thermostat that controls the Mini splits"
			input "globalIrBlasters", "capability.actuator", required: true, multiple: true,
				title: "One or More IR Blasters"
			}
		}
	}

def installed() {
    log.info "Installed with settings: ${settings}"
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
		subscribe(globalThermostat, "thermostatOperatingState", thermostatModeHandler)
		subscribe(globalThermostat, "coolingSetpoint", temperatureHandler)
		subscribe(globalThermostat, "temperature", temperatureHandler)
		subscribe(globalThermostat, "thermostatMode", temperatureHandler)
		subscribe(location, "hsmStatus", hsmStatusHandler)
		}
	}

void logsOff(){
//	stops debug logging
	log.info "MiniSplit: debug logging disabled"
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}

def temperatureHandler(evt)
	{
//	Temperature or cooling setPoint changed on Thermostat
	if (settings.logDebugs) log.debug  "temperatureHandler entered Value: ${evt.value}  mode: ${globalThermostat.currentValue("thermostatMode")}"
//	pauseExecution(2000)				//allow any operating state change from the virtual thermostat device to complete, it seems delayed, dont know why
//	thermostatModeHandler(evt)
//	change above to a runin and unschedule in thermostatModeHandler v004
	runIn(2,thermostatModeHandler,[data: ["value":"${evt.value}"]])		//This overrwrites prior pending requests
	}

void hsmStatusHandler(evt)
	{
//	HSM arming status changed
	if (settings.logDebugs) log.debug  "hsmStatusHandler entered Value: ${evt.value}"
	if (evt.value.startsWith('arming'))
		{}
	else
	if (evt.value=='disarmed')
		{
		def offMode=state?.offMode
		switch (offMode)
			{
			case 'ignore':
				break
			case 'off':
				globalThermostat.off()
				break
			case 'cool':
				globalThermostat.cool()
				break
			case 'dry':
				globalThermostat.setThermostatMode('dry')
				break
			case 'auto':
				globalThermostat.auto()
				break
			case 'heat':
				globalThermostat.heat()
				break
			case 'emergency heat':
				globalThermostat.emergencyHeat()
				break
			}
		state.offMode='ignore'
		}
	else
	if (evt.value=='armedAway')
		{
		state.offMode=globalThermostat.currentValue("thermostatMode")		//restore upon disarm
		globalThermostat.off()
		}
	else
		state.offMode='ignore'
	}

def thermostatModeHandler(evt)
	{
//	Thermostat Operating Mode changed, blast IR code to mini-splits
	def acMode = globalThermostat.currentValue("thermostatOperatingState")
	if (settings.logDebugs) log.debug  "thermostatModeHandler entered Value: ${evt.value} acMode: $acMode"
	def irCode='nada'
	def coolSetPoint = globalThermostat.currentValue("coolingSetpoint")
	def coolSetPointBD = coolSetPoint as BigDecimal
	def dryPoint = (((coolSetPointBD * 10) - 3)/10)
	switch (acMode)
		{
		case 'cooling':
			if (location.hsmStatus=='armedAway'|| globalThermostat.currentValue('thermostatMode') != 'cool')
				irCode='AC Off'
			else
			if (globalThermostat.currentValue("temperature") < coolSetPointBD)
				irCode='ACDry74Swing'
			else
			if (coolSetPoint < 72) irCode='AC On2169'
			else
			if (coolSetPoint < 74) irCode='AC On2169'
			else
			if (coolSetPoint < 76) irCode='AC On2271'
			else
			if (coolSetPoint < 78) irCode='AC On2373'
			else
				irCode='AC On2475'
			break
		default:
			irCode='AC Off'
			if (location.hsmStatus=='armedAway')
				{}
			else
			if (globalThermostat.currentValue("thermostatMode") == 'dry')
				irCode='ACDry74Swing'
			else
			if (acMode=='idle' && globalThermostat.currentValue("thermostatMode") == 'cool' && globalThermostat.currentValue("temperature") >= coolSetPointBD)
				irCode='ACDry74Swing'
		}
	if (settings.logDebugs) log.debug  "thermostatModeHandler irCode: $irCode Prior irCode: ${state.priorIrCode}"
	if (irCode != state?.priorIrCode)
		{
		globalIrBlasters.SendStoredCode(irCode)
		state.priorIrCode=irCode
		if (irCode=='ACDry74Swing')
			globalThermostat.setThermostatFanMode('dry')
		else
		if (irCode=='AC Off')
			globalThermostat.setThermostatFanMode('off')
		else
			globalThermostat.setThermostatFanMode('cool')
		}
	}