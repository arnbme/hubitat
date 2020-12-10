/**
 *  Send XML for BrianP
 *
 */
import groovy.json.JsonSlurper
metadata
{
    definition (name: "BrianP", namespace: "BrrianP", author: "BrianP")
    {
		capability "Refresh"
		
/*		attribute "loadPercent", "string"
        attribute "model", "string"
		attribute "serial", "string"
		attribute "upsStatus", "string"
		attribute "batteryVoltage", "string"
		attribute "loadPercent", "string"
		attribute "timeOnBattery", "string"
		attribute "lifetimeOnBattery", "string"
		attribute "lowTransVolts", "string"
		attribute "highTransVolts", "string"
		attribute "nomPower", "string"
		attribute "sensitivity", "string"
		attribute "lastUpdate", "string"
		attribute "batteryDate", "string"
		attribute "lastPowerFail", "string"
		attribute "lastPowerRestore", "string"
		attribute "lastPowerFailReason", "string"
		attribute "batteryRuntime", "string"
		attribute "lastEvent", "string"
		attribute "windowsBatteryPercent", "number"
*/    
		command "sendXml"
	}

	preferences
	{	
		section("Target")
		{
			input("ip", "string", title:"IP Address", defaultValue: "192.168.nnn.nnn" ,required: true)		
			input("port", "number", title:"Port Number if not 80", defaultValue: 80, range: 1..65535, required: true)		
		}
		section ("TheMsg")
		{
			input "theXmlMsg", "string", required: true, title: "{XML message coded within brackets/}"		
		}
		section
		{
			input "enableDebug", "bool", title: "Enables debug logging for 30 minutes", defaultValue: false, required: false
		}
	}
}


def installed()
{
	log.info "BrianP Installed with settings: ${settings}"
	settingsInitialize()
	refresh()
}


def updated()
{
	log.info "BrianP Updated with settings: ${settings}"
	settingsInitialize()
	refresh()
}


def settingsInitialize()
{
    unschedule()

	if (enableDebug)
	{
		log.info "Verbose logging has been enabled for the next 30 minutes."
		runIn(1800, logsOff)
	}
}

//	runs when HUB boots, starting the device refresh cycle, if any (not used here)
void initialize()
	{
	log.info "BrianP hub restarting"
//	refresh()
	}

def reset()
{
	settingsInitialize()
}


def refresh()
{
	if (enableDebug) log.debug "Entered Refresh"
/*	if (settings.prefEventGhost)
		{
		def egCommand = java.net.URLEncoder.encode("HE.BrianP")
		def egHost = "${settings.ip}:${settings.port}" 
		if (enableDebug) log.debug "Sending Refresh to Windows EventGhost at $egHost"
		sendHubCommand(new hubitat.device.HubAction("""GET /?$egCommand HTTP/1.1\r\nHOST: $egHost\r\n\r\n""", hubitat.device.Protocol.LAN))
		if (prefRefreshMinutes && prefRefreshMinutes > 0)
			{
			if (enableDebug) log.debug "Refresh scheduled $prefRefreshMinutes ${prefRefreshMinutes * 60}"
			unschedule(refresh)
			runIn(prefRefreshMinutes*60, refresh) 

			}
		}
*/
}

def sendXml()
	{
	if (enableDebug) log.debug "sendXml command entered ${theXmlMsg}"
    def wktext=theXmlMsg.replaceAll('[{]','<')	
	wktext=wktext.replaceAll('[}]','>')	
	def egCommand = java.net.URLEncoder.encode(wktext)
	def egHost = "${settings.ip}:${settings.port}" 
	if (enableDebug) log.debug "sendXml data is ${egCommand}" //note wktext wont show in log without htmlencoding
//	sendHubCommand(new hubitat.device.HubAction("""POST /?$egCommand HTTP/1.1\r\nHOST: $egHost\r\n\r\n""", hubitat.device.Protocol.LAN))
	}


def parse(String description)
{
	def msg = parseLanMessage(description)
	if (enableDebug) log.debug "PARSED LAN EVENT Received: " + msg
	
	// Update DNI if changed
	if (msg?.mac && msg.mac != device.deviceNetworkId)
	{
		if (enableDebug) log.debug "Updating DNI to MAC ${msg.mac}..."
		device.deviceNetworkId = msg.mac
	}
	// Response to a push notification
	if ((msg?.headers?.Referer == "apcupsd" || msg?.headers?.VBReferer == "apcupsd") && msg?.body)
    {
    	if (enableDebug) log.trace "Processing LAN event notification..."
    	def body = msg?.body
		def sluper = new JsonSlurper();
		def json = sluper.parseText(body)

		// Response to an event notification
        if (json?.data?.event)
		{
			log.info "BrianP Push notification for UPS event [${json?.data?.event}] detected."
			
			// Update the child device if it's monitored
			updatePowerStatus(json.data.event)
		}
		// Response to a getUPSstatus call
		else if (json?.data?.device)
		{
			if (enableDebug) log.info "Device update received."
			if (json.data.device.status == 'ONLINE')
				updateDeviceStatus(json.data.device)
			else
				{
				sendEvent(name: "lastEvent", value: json.data.device.status)
				log.warn "BrianP ${json.data.device.upsname} driver is ${json.data.device.status}"
				}
		}
		else log.error "BrianP ABORTING DUE TO UNKNOWN EVENT"
	}
    else
        if (enableDebug) log.error "BrianP Unknown message received Referer:${msg?.headers?.Referer}  VBReferer:${msg?.headers?.VBReferer} body: ${msg?.body}" 
}


def updatePowerStatus(status)
{
	def powerSource='unknown'
	switch (status)
		{
		case 'mainsback':
		case 'offbattery':
			powerSource = 'mains'
			break
		case 'onbattery':
		case 'failing':
		case 'doshutdown':
		case 'powerout':
			powerSource = 'battery'
			break
		}
	sendEvent(name: "lastEvent", value: status)
	sendEvent(name: "powerSource", value: powerSource)
    
    if (powerSource == "mains") sendEvent(name: "sessionStatus", value: "stopped", displayed: this.currentSessionStatus != sessionStatus ? true : false)
    else if (powerSource == "battery") sendEvent(name: "sessionStatus", value: "running", displayed: this.currentSessionStatus != sessionStatus ? true : false)

	if (status == 'commok' || status == 'reboot')			//communication restored or windows rebooted update device information
		refresh()
}


def updateDeviceStatus(data)
{
	def power = 0
	def winBattery = 100
	def timeLeft = Math.round(Float.parseFloat(data.timeleft.replace(" Minutes", "")))
	def battery = Math.round(Float.parseFloat(data.bcharge.replace(" Percent", "")))
	def voltage = Float.parseFloat(data.linev.replace(" Volts", ""))
	def batteryVoltage = Float.parseFloat(data.battv.replace(" Volts", ""))
	def lowTransVolts = Float.parseFloat(data.lotrans.replace(" Volts", ""))
	def highTransVolts = Float.parseFloat(data.hitrans.replace(" Volts", ""))
	def loadPercent = Float.parseFloat(data.loadpct.replace(" Percent", ""))
    def nomPower = Float.parseFloat(data.nompower.replace(" Watts", ""))
	if (data?.windowsbatterypercent)
	    winBattery = Math.round(Float.parseFloat(data.windowsbatterypercent))
	def powerSource =
    	data.status == "ONLINE" ? "mains" : 
        	data.status == "ONBATT" ? "battery" : 
            	"mains"

//	update lastEvent as necessary Killed in V004
/*	def lastEvent
	if (powerSource=='mains')
		lastEvent = 'offbattery'
	else
		lastEvent = 'onbattery'
	if (lastEvent != device.currentValue('lastEvent'))	
		sendEvent(name: "lastEvent", value: lastEvent)
*/
	// Calculate wattage as a percentage of nominal load
    power = ((loadPercent / 100) * nomPower)
    
	sendEvent(name: "powerSource", value: powerSource, displayed: this.currentPowerSource != powerSource ? true : false)
	sendEvent(name: "timeRemaining", value: timeLeft, displayed: false)  
	sendEvent(name: "upsStatus", value: data.status.toLowerCase(), displayed: this.currentUpsStatus != data.status ? true : false)
	sendEvent(name: "model", value: data.model, displayed: this.currentModel != data.model ? true : false)
	sendEvent(name: "serial", value: data.serialno, displayed: this.currentSerial != data.serialno ? true : false)
	sendEvent(name: "battery", value: battery, displayed: this.currentBattery != battery ? true : false)
	sendEvent(name: "batteryRuntime", value: data.timeleft, displayed: this.currentRunTimeRemain != data.timeleft ? true : false)
	sendEvent(name: "batteryVoltage", value: batteryVoltage, displayed: this.currentBatteryVoltage != batteryVoltage ? true : false)
	sendEvent(name: "voltage", value: voltage, descriptionText: "Line voltage is ${voltage} volts.", displayed: this.currentVoltage != voltage ? true : false)
	sendEvent(name: "loadPercent", value: loadPercent, displayed: this.currentLoadPercent != loadPercent ? true : false)
	sendEvent(name: "timeOnBattery", value: data.tonbatt, displayed: this.currentTimeOnBattery != data.tonbatt ? true : false)
	sendEvent(name: "lifetimeOnBattery", value: data.cumonbatt, displayed: this.currentLifetimeOnBattery != data.cumonbatt ? true : false)
	sendEvent(name: "lowTransVolts", value: lowTransVolts, displayed: this.currentLowTransVolts != lowTransVolts ? true : false)
	sendEvent(name: "highTransVolts", value: highTransVolts, displayed: this.currentHighTransVolts != highTransVolts ? true : false)
	sendEvent(name: "sensitivity", value: data.sense, displayed: this.currentSensitivity != data.sense ? true : false)
	sendEvent(name: "batteryDate", value: data.battdate, displayed: this.currentBatteryDate != data.battdate ? true : false)
	sendEvent(name: "lastUpdate", value: data.date, displayed: false)
	sendEvent(name: "nomPower", value: nomPower, displayed: this.currentNomPower != nomPower ? true : false)
	sendEvent(name: "power", value: power, displayed: this.currentPower != power ? true : false)
	sendEvent(name: "lastPowerFail", value: data.xonbatt, displayed: this.currentNomPower != data.xonbatt ? true : false)
	sendEvent(name: "lastPowerRestore", value: data.xoffbatt, displayed: this.currentPower != data.xoffbatt ? true : false)
	sendEvent(name: "lastPowerFailReason", value: data.lastxfer, displayed: this.currentLastPowerFailReason != data.lastxfer ? true : false)
	sendEvent(name: "windowsBatteryPercent", value: winBattery)
}


/*
	logsOff
    
	Disables debug logging.
*/
def logsOff()
{
    log.warn "debug logging disabled..."
	device.updateSetting("enableDebug", [value:"false",type:"bool"])
}