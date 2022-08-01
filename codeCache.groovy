/*
 *	Proof of concept Use Hub Variabvle for IR and RF code storage cache
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
			input "broadlinkDevices", "capability.actuator", title: "Controlled Broadlink IR/RF Devices", required: true, multiple: true
//			input "hubVariable", "capability.variable", title: "Hub Variable used to store code cache", required: true, multiple: false
//			if (broadlinkDevices && hubVariable
			if (broadlinkDevices)
				{
  				input(name: "appFunction", type: "enum", title: "Select a function", multiple: false, required: false,  submitOnChange: true,
					options: ["Import Codes From Devices", "Import Codes From String", "Delete Codes From Cache", "Delete Codes From Device","Clear Cache"])
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
						paragarph "Import Codes From Devices is a work in progress"
						}
					else
					if (appFunction == "Delete Codes From Cache")
						{
						paragarph "Delete Codes from Cache is a work in progress"
						}
					else
					if (appFunction == "Delete Codes From Device")
						{
						paragarph "Delete Codes From Device is a work in progress"
						}
					else
					if (appFunction == "Clear Cache")
						{
						if (codeString)
							input "buttonClearCache", "button", title: "Execute Clear Cache"
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
			importCodes(codeString)
			break
		case "buttonClearCache":
		    Map workCodes = [:]
			setGlobalVar("IrRfCache", "${workCodes}")
			break
		default:
			log.debug btn+" processing logic not found"
			break
		}
	}

void importCodes(codeInput)
	{
//	log.debug "importCodes entered"
    Map workCodes = [:]
	codeInput = codeInput?.replace('{','')?.replace('}','')
    codeInput?.split(',')?.each
    	{
        def entry = it.split('=')
        if(entry.size() == 2)
			{
			Map codeEntry = [(entry[0].toString().trim()) : (entry[1].toString().trim())]
			workCodes << codeEntry
			}
		}
//	log.debug workCodes
//	hubVariable.setVariable(workCodes)
//	hubVariable=workCodes
	setGlobalVar("IrRfCache", "${workCodes}")
    }
