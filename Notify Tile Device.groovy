 /*
 * Notify Tile Device
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2021-01-06  thebearmay	 Original version 0.1.0
 *    2021-01-07  thebearmay     Fix condition causing a loss notifications if they come in rapidly
 *    2021-01-07  thebearmay     Add alternative date format
 *    2021-01-07  thebearmay     Add last5H for horizontal display
 *    2021-01-07  thebearmay     Add leading date option
 *    2011-03-10  thebearmay     Lost span tag with class=last5
 *    2021-11-14  ArnB              Add capability Momentary an routine Push allowing a Dashboard switch to clear all messages. 	
 *    2021-11-15  ArnB              Revise logic minimizing attributes and sendevents. Allow for 5 to 20 messages in tile. Insure tile is less than 1024 	
 */
import java.text.SimpleDateFormat
static String version()	{  return '2.0.0'  }

metadata {
    definition (
        		name: "Notify Tile Device", 
		namespace: "thebearmay", 
		description: "Simple driver to act as a destination for notifications, and provide an attribute to display the last 5 on a tile.",
		author: "Jean P. May, Jr.",
	    	importUrl:"https://raw.githubusercontent.com/thebearmay/hubitat/main/notifyTile.groovy"
	) {
		capability "Notification"
		capability "Momentary"
		attribute "last5", "STRING"
		command "configure"
		}   
	}

preferences {
	input("debugEnable", "bool", title: "Enable debug logging?")
    input("dfEU", "bool", title: "Use Date Format dd/MM/yyyy")
    input("leadingDate", "bool", title:"Use leading date instead of trailing")
    input("msgLimit", "number", title:"Number of messages from 5 to 20",defaultValue:5, range:5..20)
}

def installed() {
//	log.trace "installed()"
    state.lastLimit=5
    configure()
}

def updated(){
//	log.trace "updated()"
	if(debugEnable) runIn(1800,logsOff)
	if (state.lastLimit>settings.msgLimit)
		configure()
	state.lastLimit=settings.msgLimit	
}

def configure() {
//	log.trace "configure()"
    sendEvent(name:"last5", value:'<span class="last5"></span>')
    state.msgCount=1
}

def deviceNotification(notification){
	if (debugEnable) log.debug "deviceNotification entered: ${notification}" 
    dateNow = new Date()
    if (dfEU)
        sdf= new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    else
        sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
    if (leadingDate)
        notification = sdf.format(dateNow) + " " + notification
    else
        notification += " " + sdf.format(dateNow)

//	insert new message at beginning	of last5 string
	wkTile=device.currentValue("last5").replace('<span class="last5">','<span class="last5">' + notification + '<br/>')

//	when msg count exceeds limit, purge last message
	msgFilled = state.msgCount
	if (debugEnable) log.debug "deviceNotification2 msgFilled: ${msgFilled} msgLimit: ${settings.msgLimit}" 
	if (msgFilled < settings.msgLimit)
		{
		msgFilled++
		state.msgCount = msgFilled
		}
	else	
		{
		int i = wkTile.lastIndexOf('<br/>');
		if (i != -1) 
    		wkTile = wkTile.substring(0, i) + '</span>';
		}

//	Insure tile length is less than 1024 and hopefully stop loops
	int wkLen=wkTile.length()	
	int loop=0
	while (wkLen > 1024 && loop < msgFilled)
		{
		loop++
		if (debugEnable) log.debug "wkTile length ${wkLen}> 1024 truncating loop: ${loop}"
		int i = wkTile.lastIndexOf('<br/>');
		if (i != -1) 
    		{
    		wkTile = wkTile.substring(0, i) + '</span>';
    		msgFilled = state.msgCount
    		msgFilled--
    		state.msgCount=msgFilled
    		}
    	else
    		{
    		wkTile='<span class="last5"></span>'
    		state.msgCount=1
    		}
		wkLen=wkTile.length()
		if (debugEnable) log.debug "Truncated wkTile length ${wkLen}, loops: ${loop}"
		}	
	sendEvent(name:"last5", value: wkTile)
}    

void logsOff(){
     device.updateSetting("debugEnable",[value:"false",type:"bool"])
}

def push() {
    configure()
}