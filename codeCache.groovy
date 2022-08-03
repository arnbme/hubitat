/*
 *	Proof of concept for IR and RF code storage cache
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
    name: "Code Cache",
    namespace: "arnbme",
    author: "Arn B",
    description: "Broadlink Code Cache in Hub Variable",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def version()
	{
	return "0..0.0";
	}

def mainPage()
	{
	dynamicPage(name: "mainPage", title: "Broadlink Code Cache (${version()})", install: true, uninstall: true)
		{
		section
			{
			if (settings.logDebugs)
				input "buttonDebugOff", "button", title: "Stop Debug Logging"
			else
				input "buttonDebugOn", "button", title: "Debug For 30 minutes"
			input "broadlinkDevices", "capability.actuator", title: "Controlled Broadlink IR/RF Devices", required: true, multiple: true
			input "cacheDevice", "capability.actuator", title: "Broadlink IR/RF Device reserved for cache", required: true, multiple: false
			if (broadlinkDevices && cacheDevice)
				{
  				input(name: "appFunction", type: "enum", title: "Select a function", multiple: false, required: false,  submitOnChange: true,
					options: ["Import Codes From Devices", "Import Codes From String", "View Codes From Cache", "Delete Codes From Cache","Clear Cache"])
				if (appFunction)
					{
					if (appFunction == "Import Codes From String")
						{
						input "codeString", "text", title: "Code String", description: "Code string to import",required:true, submitOnChange: true
						if (codeString)
							input "buttonImport", "button", title: "Execute Import"
						}
					else
					if (appFunction == "Import Codes From Devices")
						{
						paragraph "Import Codes From Devices is a work in progress"
						}
					else
					if (appFunction == "View Codes From Cache")
						{
						cacheDevice.allKnownCodesKeys()		//refresh the codes list
						paragraph cacheDevice.getDataValue("codes")
						}
					else
					if (appFunction == "Delete Codes From Cache")
						{
						paragraph "Import Codes From Cache is a work in progress"
/*						cacheDevice.allKnownCodesKeys()		//refresh the codes list
						wkCodes = cacheDevice.getDataValue("codes")
						wkCodes = wkCodes.replace('[','')?.replace(']','')
						List wkList = []
					    wkCodes.split(',')?.each
						    {
						     wkList << it
						     }
						input name: "deleteNames", type: "enum", title: "Named Codes in cache", multiple: true, required: false,  submitOnChange: true, options: wkList
						if (deleteNames)
							input "buttonDeleteCacheCodes", "button", title: "Execute Delete"
*/						}
					else
					if (appFunction == "Clear Cache")
						{
						if (codeString)
							input "buttonClearCache", "button", title: "Execute Clear Cache", submitOnChange: true
						}
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
	{}

void uninstalled()
	{}

//	Process app buttons
void appButtonHandler(btn)
	{
	switch(btn)
		{
		case "buttonImport":
			cacheDevice.importCodes(codeString)
			break
		case "buttonClearCache":
			cacheDevice.clearSavedCodes()
			break
		case "buttonDeleteCacheCodes":
			deleteNames.each
				{
//				log.debug '*'+it+'*'
				cacheDevice.deleteSavedCode(it.trim())	//get rid of leading space or newline causing delete failure
				}
			break
		case "buttonDebugOff":
			debugOff()
			break
		case "buttonDebugOn":
			app.updateSetting("logDebugs",[value:"true",type:"bool"])
			runIn(1800,debugOff)		//turns off debug logging after 30 Minutes
			log.info "debug logging enabled"
			break
		default:
			log.debug btn+" processing logic not found"
			break
		}
	}

void debugOff(){
//	stops debug logging
	log.info "debug logging disabled"
	unschedule(debugOff)
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}
