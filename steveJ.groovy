/**
 *  get device states SteveJ
 *
 *  Dec 11, 2020 v0.0.0 Create 

 */
definition(
    name: "steveJ",
    namespace: "steveJ",
    author: "Arn Burkhoff",
    description: "Test UriQ",
    category: "My Apps",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
    singleInstance: true)
    
def version()
	{
	return "0.0.0";
	}
	
	
preferences {
	page(name: "pageOne")
	}

def pageOne()
	{
	dynamicPage(name: "pageOne", title: "UriQ Tester", install: true, uninstall: true)
		{
		section
			{
			input "theDevices", "capability.refresh", required: true, multiple: true,
				title: "Speaker Devices to test?"
//			input "scanSeconds", "number", title:"Scan Queue seconds", defaultValue: 240, range: 60..600, required: true	
			if (settings.QScanStopped)
				input "buttonQOff", "button", title: "Restart Inactive Queue Scan"
			else
				input "buttonQOn", "button", title: "Queue Scan Active"
			if (settings.logDebugs)
				input "buttonDebugOff", "button", title: "Stop Debug Logging"
			else
				input "buttonDebugOn", "button", title: "Debug For 30 minutes"
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
	unschedule()
	checkQ()		//run now then every scan seconds

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
		case "buttonQOff":
			app.updateSetting("QScanStopped",[value:"false",type:"bool"])
//			unschedule(checkQ)
//			checkQ()
			break
		case "buttonQOn":
			app.updateSetting("QScanStopped",[value:"true",type:"bool"])
//			unschedule(checkQ)
			break
		default:
		log.debug "appButtonHandler: Unknown case ${btn}"
		}
	}

void debugOff(){
//	stops debug logging
	log.info "settings?.thisName: debug logging disabled"
	unschedule(debugOff)
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}

void checkQ()
{
//	check if there is a uniq state in each device
	if (settings.logDebugs) log.debug  "checkQ entered"
	def devicesStates
	theDevices.each
		{
		log.debug "${it.name} ${it.state.delayExpire}"
//		deviceStates=it.getCurrentStates()
//		log.debug "${it.name} $deviceStates"
		}
//	runIn(240,checkQ)		//Check Q every 4 Minutes
}