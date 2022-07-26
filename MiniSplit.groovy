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
 *  2. Create working IR code sets for cooling (multiple temperatures), heat (multiple temperatures) dry, off, and more as necessary
 *
 *  3. Must have a working Virtual Thermostat (or perhaps a real device).
 *		I use a Virtual Thermostat averaging temperatures from 3 devices using the HE Average Temperature app at
 *		https://github.com/hubitat/HubitatPublic/tree/master/example-apps
 *		Changed the app created child from a virtual temperature sensor device to a virtual thermostat.
 *
 *	4. A dashboard displaying the Virtual Thermostat device or some other method for control
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
 *      ACFanSwing - Mode: Fan, Temperature: n/a Swing: On (swing, louvers and other settings: whatever you want)
 *		AC Off - Mode: off
 *  7. Adjust IR code names in the code as necessary
 *
 *	Notes:
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
 *  Jul 19, 2022	v0.1.6 Change cooling to use newly added non-swing codes
 *  Aug 20, 2021 v0.1.6 Add support for Dry codes with temperatures using mode Dry and Dry+ 
 *									added codes ACDry(72|76|78)Swing used along with existing ACDry74Swing
 *  Aug 09, 2021 v0.1.5 When cool: change to use colder settings making  fan run faster in auto mode and perhaps blow colder 
 *  Aug 09, 2021 v0.1.5 add support for dry+ mode same as cool but only uses dry. Must set cool temp prior to setting dry+ no temps are shown
 *  Jul 22, 2021 v0.1.4 delay on dashboard due to additional 2 second delay. Change order of IR command and dashboard display update
 *  Jul 20, 2021 v0.1.4 in qHandler routine double send command due to occasional missed IR Blaster command sends, or mini-split device failing to respond
 *  Oct 15, 2020 v0.1.3 Add flag that stops this app from processing HSM Away and Night Mode changes when using Thermostat Scheduler. 
 *  Oct 14, 2020 v0.1.2 When outside temperature below setting value, do not use now inefficient MiniSplits for heating divert to baseboards, except on emergency heat.
 *							Fix issue where heat and cool would not occur.
 *  Oct 13, 2020 v0.1.2 Setup for individually controlling each miniSplit. Add Label to define app name. 
 *  Aug 15, 2020 v0.1.1 Add optional command queueing to buffer and remove many unneeded commands that may conflict
 *							due to commands entered from dashboard such as temperature down or up tapping on icon
 *							This is in addition to the Temperature Handler delay
 *						add optional input setting for number of milliseconds to delay command runin from 0 to 2000 default 1000
 *							This slows things down a little but hopefully stops a deluge of commands and IR triggering
 *  Jul 19, 2020 v0.1.0 Add optional user coolplus dry and fan offsets overriding hysterisis
 *						Display MyCool set points or Dry and Fan
 *  Jul 14, 2020 v0.0.9T Occasional error on transmission. Use a 125ms delay between all device commands vs issue all 5 at once
 *  Jul 05, 2020 v0.0.9 confirm all ccalculation fields are BigDecimal, adjust AC ir cooling codes
 *  Jun 30, 2020 v0.0.8 Fix BigDecimal problem calculating dryPoint and fanPoint
 *  Jun 30, 2020 v0.0.7 Add bool for standard cooling or extended cooling logic
 *							standard Cool mode directly controlled by thermostat
 *							add logic for heat and emergency heat
 *							Attempted setting mode 'mycool' but dashboard hides temperature controls so used input seting.
 *  Jun 28, 2020 v0.0.6 Set the cooling cycle as follows applies when mode is cool and globalMyCool is true
 *					cooling >=cooling set point
 *					drying	>=cooling set point - hysterisis
 *					fan		>=cooling set point - hysterisis*2
 *					off		< cooling set point - hysterisis*2
 *  Jun 28, 2020 v0.0.5 Process HSM arming Status
 *  Jun 28, 2020 v0.0.4	Change pauseExecution to a runIn
 *  Jun 27, 2020 v0.0.3	Support user defined thermostat mode: dry Turn on dry when idle and mode=dry regardless of temperature
 *							Used virtual thermostat command setSupportedThermostatModes adding dry
 *								with string [cool, off, dry, fan, heat, auto, emergency heat] also ajusting settings order on device
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
	return "0.1.6";
	}

def mainPage()
	{
	dynamicPage(name: "mainPage", title: "Mini Split Settings", install: true, uninstall: true)
		{
		section
			{
			if (settings.logDebugs)
				input "buttonDebugOff", "button", title: "Stop Debug Logging"
			else
				input "buttonDebugOn", "button", title: "Debug For 30 minutes"
			input "globalDisable", "bool", required: true, defaultValue: false,
				title: "Disable All Functions. Default: Off/False"
			input "thisName", "text", title: "Name of this MiniSplit controller zone. Eg: Zone1 Office", submitOnChange: true, required: true
			if(thisName) app.updateLabel("$thisName")
			input "globalThermostat", "capability.thermostat", required: true, multiple: false, submitOnChange: true,
				title: "A Thermostat that controls the Mini splits"
			input "globalMyCool", "bool", required: true, defaultValue: false,
				title: "ON: Uses app's extended cooling logic with Fan, Dry and Cool points.<br />OFF: Follows Thermostat's cooling and idle state. Default: Off/False"
 			input name: "globalDryOffset", type: "decimal", required: false, range: "0.1..2.0",  submitOnChange: true,
 					title: "MyCool Dry Offset from Cool Set Point, may be specified in tenths. Optional, when not defined thermostat hysteris is used"
 			input name: "globalFanOffset", type: "decimal", required: false, range: "0.1..2.0",  submitOnChange: true,
 					title: "MyCool Fan Offset from Dry point, may be specified in tenths. Optional, when not defined thermostat hysteris is used"
			if (settings.globalThermostat)
				{
				def coolSetPoint = globalThermostat.currentValue("coolingSetpoint")
				def hysteresis = globalThermostat.currentValue("hysteresis") as BigDecimal
				def dryPoint=coolSetPoint - hysteresis
				if (settings.globalDryOffset)
					dryPoint=coolSetPoint - settings.globalDryOffset
				def fanPoint=dryPoint - hysteresis
				if (settings.globalFanOffset)
					fanPoint=dryPoint - settings.globalFanOffset
				paragraph "MyCool settings Hysteresis:$hysteresis, coolSetpoint:$coolSetPoint, dryPoint:$dryPoint, fanPoint:$fanPoint"
				}
 			input name: "globalCommandDelay", type: "number", required: false, range: "0..2000",  submitOnChange: false, defaultValue: 1000,
 					title: "MilliSeconds of delay (Optional). Mitigates sending extraneos commands when changing temperature or mode settings. Default: 1000"
			input "globalIrBlasters", "capability.actuator", required: true, multiple: true,
				title: "One or More IR Blasters"
			input "globalThermostatApp", "bool", required: false, defaultValue: false,
				title: "Thermostat Scheduler app is controlling this device. When true: disables the app's Off function for Away and Night HSM status modes. Default: Off/False"
 			input name: "globalMinOutside", type: "number", required: false, range: "0..100",  submitOnChange: true,
 					title: "When outside temperature at or below, do not use Minisplits for heating (Optional)"
			if (globalMinOutside){
					input name: "globalTempOutside", type: "capability.temperatureMeasurement", required: true,
							title: "Use this device for outside Temperature"
				}			
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
	if (globalDisable)
		{}
	else
		{
		subscribe(globalThermostat, "thermostatOperatingState", temperatureHandler)
		subscribe(globalThermostat, "coolingSetpoint", temperatureHandler)
		subscribe(globalThermostat, "heatingSetpoint", temperatureHandler)
		subscribe(globalThermostat, "temperature", temperatureHandler)
		subscribe(globalThermostat, "thermostatMode", temperatureHandler)
		if (settings?.globalThermostatApp)
			{}
		else	
			subscribe(location, "hsmStatus", hsmStatusHandler)
		}
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
			runIn(1800,debugOff)		//turns off debug logging after 30 Minutes
			break
		}
	}

void debugOff(){
//	stops debug logging
	log.info "settings?.thisName: debug logging disabled"
	unschedule(debugOff)
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}

def temperatureHandler(evt)
	{
//	Temperature, Thermostat Mode, heatingSetPoint, or coolingSetPoint changed on Thermostat
	if (settings.logDebugs) log.debug  "temperatureHandler entered Value: ${evt.value}  mode: ${globalThermostat.currentValue("thermostatMode")}"
	runIn(2,thermostatModeHandler,[data: ["value":"${evt.value}"]])		//This overrwrites prior pending requests, eliminating them
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
		def offMode=state?.offMode		//get prior mode
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
			case 'dry+':
				globalThermostat.setThermostatMode('dry+')
				break
			case 'fan':
				globalThermostat.setThermostatMode('fan')
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
/*			default:
				forget about this, execute is not allowed in Hubitat
				def cmd = "globalThermostat.${offMode}()"
				log.debug "cmd: $cmd"
				cmd.execute()
				break
*/			}
		state.offMode='ignore'
		}
	else
	if (evt.value=='armedAway' || evt.value == 'armedNight')
		{
		state.offMode=globalThermostat.currentValue("thermostatMode")		//restore upon disarm
		globalThermostat.off()
		}
	else
		state.offMode='ignore'
	}

def thermostatModeHandler(evt)
	{
//	Thermostat operating state changed, blast IR code to mini-splits
//	Note this ignores the thermostat device operatingMode
	def acMode = globalThermostat.currentValue("thermostatMode")
	if (settings.logDebugs) log.debug  "thermostatModeHandler entered Value: ${evt.value} acMode: $acMode"
	def irCode='AC Off'
	switch (acMode)
		{
		case 'cool':
			def coolSetPoint = globalThermostat.currentValue("coolingSetpoint")
			def hysteresis = globalThermostat.currentValue("hysteresis") as BigDecimal
			def temperature=globalThermostat.currentValue("temperature") as BigDecimal
			if (settings.globalMyCool)
				{
//				all fields should be BigDecimal
				def dryPoint=coolSetPoint - hysteresis
				if (settings.globalDryOffset)
					dryPoint=coolSetPoint - settings.globalDryOffset
				def fanPoint=dryPoint - hysteresis
				if (settings.globalFanOffset)
					fanPoint=dryPoint - settings.globalFanOffset
				if (settings.logDebugs) log.debug "coolSetPoint: $coolSetPoint ${coolSetPoint.class.name} hysteresis: $hysteresis ${hysteresis.class.name} dryPoint: $dryPoint fanPoint: $fanPoint ${dryPoint.class.name} temperature: $temperature ${temperature.class.name}"
				if (temperature>=coolSetPoint)
					{
					if (coolSetPoint < 72) irCode='AC On2169'
					else
					if (coolSetPoint < 74) irCode='AC On2271'
					else
					if (coolSetPoint < 76) irCode='AC On2373'
					else
					if (coolSetPoint < 78) irCode='AC On2475'
					else
					if (coolSetPoint < 80) irCode='AC On2577'
					else
						irCode='AC On2579'
					}
				else
				if (temperature>=dryPoint)
					irCode='ACDry74Swing'
				else
				if (temperature>=fanPoint)
					irCode='ACFanSwing'
				else
					irCode='AC Off'
				}
			else
				{
				if (globalThermostat.currentValue("thermostatOperatingState") =='cooling' || coolSetPoint < temperature-hysteresis)
					{
					if (coolSetPoint < 72) irCode='AC2170NS'
//					if (coolSetPoint < 72) irCode='AC On2169'
					else
					if (coolSetPoint < 74) irCode='AC2272NS'
//					if (coolSetPoint < 74) irCode='AC On2271'
//					if (coolSetPoint < 74) irCode='AC On2169'
					else
					if (coolSetPoint < 76) irCode='AC2374NS'
//					if (coolSetPoint < 76) irCode='AC On2373'
//					if (coolSetPoint < 76) irCode='AC On2271'
					else
					if (coolSetPoint < 78) irCode='AC2476NS'
//					if (coolSetPoint < 78) irCode='AC On2475'
//					if (coolSetPoint < 78) irCode='AC On2373'
					else
					if (coolSetPoint < 80) irCode='AC2578NS'
//					if (coolSetPoint < 80) irCode='AC On2577'
//					if (coolSetPoint < 80) irCode='AC On2475'
					else
						irCode='AC2680NS'
//						irCode='AC On2579'
					}
				else
					irCode='AC Off'
				}
			break
		case 'heat':
//			MiniSplis are not efficient below a certain temperature		
			if (settings?.globalMinOutside && settings.globalTempOutside.currentValue("temperature") <= settings.globalMinOutside)
				{
				irCode='AC Off'
				globalThermostat.setThermostatOperatingState('idle')
				break
				}
		case 'emergency heat':		
			def heatSetPoint = globalThermostat.currentValue("heatingSetpoint")
			def hysteresis = globalThermostat.currentValue("hysteresis") as BigDecimal
			def temperature=globalThermostat.currentValue("temperature") as BigDecimal
			if (globalThermostat.currentValue("thermostatOperatingState") =='heating' || heatSetPoint > temperature+hysteresis)
				{
				if (heatSetPoint < 68) irCode='ACHeat2068'
				else
				if (heatSetPoint < 70) irCode='ACHeat2170'
				else
				if (heatSetPoint < 72) irCode='ACHeat2272'
				else
				if (heatSetPoint < 74) irCode='ACHeat2374'
				else
				if (heatSetPoint < 76) irCode='ACHeat2476'
				else
					irCode='ACHeat2578'
				}
			else
				irCode='AC Off'

			if (settings.logDebugs) log.debug "thermostatModeHandler mode:$acMode irCode:$irCode HeatSetPoint:$heatSetPoint Hysteresis:$hysteresis Temperature:$temperature ${globalThermostat.currentValue('thermostatOperatingState')}"
			break

		case 'off':
			irCode='AC Off'
			break

		case 'dry':
			def coolSetPoint = globalThermostat.currentValue("coolingSetpoint")
			if (coolSetPoint <= 72) irCode='ACDry70Swing'
			else
			if (coolSetPoint <= 74) irCode='ACDry72Swing'
			else
			if (coolSetPoint <= 76) irCode='ACDry74Swing'
			else
			if (coolSetPoint <= 78) irCode='ACDry76Swing'
			else
				irCode='ACDry78Swing'
			break

		case 'dry+':			//dry with thermostat control
			def coolSetPoint = globalThermostat.currentValue("coolingSetpoint")
			def hysteresis = globalThermostat.currentValue("hysteresis") as BigDecimal
			def temperature=globalThermostat.currentValue("temperature") as BigDecimal
			if (coolSetPoint < temperature-hysteresis)
				{
				if (coolSetPoint <= 72) irCode='ACDry70Swing'
				else
				if (coolSetPoint <= 74) irCode='ACDry72Swing'
				else
				if (coolSetPoint <= 76) irCode='ACDry74Swing'
				else
				if (coolSetPoint <= 78) irCode='ACDry76Swing'
				else
					irCode='ACDry78Swing'
				}
			else
				irCode='AC Off'
			break

		case 'fan':
			irCode='ACFanSwing'
			break
		}
	if (settings.logDebugs) log.debug  "thermostatModeHandler irCode: $irCode Prior irCode: ${state.priorIrCode}"
	if (settings.globalCommandDelay && settings.globalCommandDelay > 0)
		{
		if (settings.logDebugs) log.debug  "thermostatModeHandler queueing for ${settings.globalCommandDelay} milliseconds command: irCode: $irCode Prior irCode: ${state.priorIrCode}"
		runInMillis(settings.globalCommandDelay,"qHandler",[data: irCode])
		}
	else
		{
		if (settings.logDebugs) log.debug  "thermostatModeHandler not queueing command: irCode: $irCode Prior irCode: ${state.priorIrCode}"
		qHandler(irCode)
		}
	}


void qHandler(irCode)				//process commands
	{
	if (settings.logDebugs) log.debug  "qHandler irCode: $irCode Prior irCode: ${state.priorIrCode}"
	if (irCode != state?.priorIrCode)
		{
		state.priorIrCode=irCode
		if (irCode.startsWith('ACDry'))
//			globalThermostat.setThermostatFanMode('Dry')
			globalThermostat.setThermostatFanMode(irCode)
		else
		if (irCode=='AC Off')
			globalThermostat.setThermostatFanMode('Off')
		else
		if (irCode=='ACFanSwing')
			globalThermostat.setThermostatFanMode('Fan Only')
		else
		if (irCode.startsWith('ACHeat'))
			globalThermostat.setThermostatFanMode('Heat')
		else
		if (settings.globalMyCool)
			globalThermostat.setThermostatFanMode('myCool')
		else
			globalThermostat.setThermostatFanMode('Cool')
		globalIrBlasters.each
			{
			it.SendStoredCode(irCode)
			pauseExecution(2000)			//July 20, 2021 reissue command compensating for lost or ignored commands
			it.SendStoredCode(irCode)
			
			if (globalIrBlasters.size()> 1)
				pauseExecution(125)
			}
		}
	}