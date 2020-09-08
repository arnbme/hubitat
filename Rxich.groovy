
/**
 *  Ring Alarm Range Extender (1st and 2nd Generation)
 *
 *  Copyright 2019 Ben Rimmasch
 *
 *  https://shop.ring.com/products/alarm-range-extender
 *  https://products.z-wavealliance.org/products/2666
 *  https://products.z-wavealliance.org/products/3688
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
 *  Change Log:
 *  2020-01-01: Initial
 *  2020-04-10: Added a fingerprint for device 201
 *              Caught a weird event battery management event so the device driver won't tell the user it's the wrong device driver
 *  2020-04-29: Added support for 2nd gen extender (device 0301)
 *              Added brownout notification
 *  2020-09-08: Added initialize method to reset powerState at reboot. Arn Burkhoff
 *
 *
 *  Notes: For the 2nd gen repeater it seems that once smart pairing has started you may need to press the front button for about
 *         3 seconds to start classic pairing.
 *
 */

metadata {
  definition(
    name: "Ring Alarm Range Extender-Coda", namespace: "codahq-hubitat", author: "Ben Rimmasch",
    importUrl: "https://raw.githubusercontent.com/codahq/hubitat_codahq/master/devicestypes/ring-alarm-range-extender.groovy") {
    capability "Sensor"
    capability "Battery"
    capability "Configuration"
    capability "Refresh"
    capability "Health Check"
    capability "PowerSource"
	capability "Initialize"

    attribute "acStatus", "string"
    attribute "batteryStatus", "string"

    fingerprint mfr: "0346", prod: "0401", deviceId: "0101", deviceJoinName: "Ring Extender",
      inClusters: "0x5E,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x6C,0x71,0x70,0x80,0x7A"
    fingerprint mfr: "0346", prod: "0401", deviceId: "0201", deviceJoinName: "Ring Extender",
      inClusters: "0x5E,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x9F,0x80,0x71,0x6C,0x70,0x7A"
    fingerprint mfr: "0346", prod: "0401", deviceId: "0301", deviceJoinName: "Ring Extender 2nd Gen",
      inClusters: "0x5E,0x59,0x85,0x80,0x70,0x5A,0x7A,0x87,0x72,0x8E,0x71,0x73,0x9F,0x6C,0x55,0x86"
    //to add new fingerprints convert dec manufacturer to hex mfr, dec deviceType to hex prod, and dec deviceId to hex deviceId
  }

  preferences {

    input "batteryReportingInterval", "number", range: 4..70, title: "Battery Reporting Interval",
      description: "Battery reporting interval can be configured to report from 4 to 70 minutes",
      defaultValue: 70, required: false, displayDuringSetup: true
    if (getDataValue("deviceId") == "769") {
      input "ledIndications", "bool", title: "LED Indicator Enabled", defaultValue: true
    }
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
  }
}

private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

// Sets flag so that configuration is updated the next time it wakes up.
def updated() {
  logTrace "updated()"

  return configure()
}

// Initializes the device state when paired and updates the device's configuration.
def configure() {
  logTrace "configure()"
  def cmds = []

  //Set hub to the lifeline group
  cmds << zwave.associationV2.associationRemove(groupingIdentifier: 1, nodeId: [])
  cmds << zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: [zwaveHubNodeId])
  cmds << zwave.associationV2.associationGet(groupingIdentifier: 1)

  // Set and get current config parameter values
  def batteryInterval = batteryReportingInterval ? batteryReportingInterval.toInteger() : 70
  cmds << zwave.configurationV2.configurationSet(parameterNumber: 1, configurationValue: [batteryInterval], size: 1)
  cmds << zwave.configurationV2.configurationGet(parameterNumber: 1)
  cmds << zwave.batteryV1.batteryGet()

  if (getDataValue("deviceId") == "769" && ledIndications) {
    cmds << zwave.configurationV2.configurationSet(parameterNumber: 4, configurationValue: ledIndications ? [1] : [0], size: 1)
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 4)
  }

  cmds << zwave.versionV1.versionGet()
  cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()


  cmds = cmds.collect { secureCmd(it) }
  return delayBetween(cmds, 500)
}

// Required for HealthCheck Capability, but doesn't actually do anything because this device sleeps.
def ping() {
  logDebug "ping()"
  secureCmd(zwave.batteryV1.batteryGet())
}

void initialize() 	{
/*	 current powerState returns the requested power management response. Failing powerState issues a x'00' */
  logDebug "initialize()"
  def cmds = []
  cmds << zwave.notificationV3.notificationGet(notificationType: 8, v1AlarmType: 0, event: 0x03)	//on mains?
  cmds << zwave.notificationV3.notificationGet(notificationType: 8, v1AlarmType: 0, event: 0x02)	//on battery?
  sendToDevice(cmds)
}

def refresh() {
  logDebug "refresh()"
  state.clear()

  def cmds = []
  cmds << zwave.batteryV1.batteryGet()
  cmds << zwave.notificationV3.notificationGet(notificationType: 8, v1AlarmType: 0)
  //can't seem to get the status of each individual event because the alarm value doesn't change
  //cmds << zwave.notificationV3.notificationGet(notificationType: 8, v1AlarmType: 0, event: 0x00)
  //cmds << zwave.notificationV3.notificationGet(notificationType: 8, v1AlarmType: 0, event: 0x02)
  //cmds << zwave.notificationV3.notificationGet(notificationType: 8, v1AlarmType: 0, event: 0x03)
  //cmds << zwave.notificationV3.notificationGet(notificationType: 8, v1AlarmType: 0, event: 0x0C)
  cmds = cmds.collect { secureCmd(it) }
  return delayBetween(cmds, 500)
}

// Processes messages received from device.
def parse(String description) {
  logDebug "parse(String description)"
  logTrace "Description: ${description}"
  def result = []

  def cmd = zwave.parse(description, commandClassVersions)
  if (cmd) {
    result += zwaveEvent(cmd)
  }
  else {
    log.warn "Unable to parse description: $description"
  }
  return result
}

private getCommandClassVersions() {
  //synchronized from https://products.z-wavealliance.org/products/3688/classes
  [
    0x59: 1,  // AssociationGrpInfo
    0x85: 2,  // Association V2
    0x80: 1,  // Battery
    0x70: 1,  // Configuration
    0x5A: 1,  // DeviceResetLocally
    0x7A: 4,  // Firmware Update MD
    0x72: 2,  // ManufacturerSpecific V2
    0x71: 8,  // Notification V8
    0x73: 1,  // Powerlevel
    0x98: 1,  // Security
    // Security 2 not implemented in Hubitat
    0x6C: 1,  // Supervision
    0x55: 2,  // Transport Service V2
    0x86: 1,  // Version (V2)
    0x5E: 2,  // ZwaveplusInfo V2
  ]
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd)"
  logTrace "BatteryReport: $cmd"

  def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)
  if (val > 100) {
    val = 100
  }
  else if (val <= 0) {
    val = 1
  }
  state.lastBatteryReport = convertToLocalTimeString(new Date())
  logInfo "Battery: ${val}%"
  return createEvent([name: "battery", value: val, descriptionText: "battery ${val}%", unit: "%"])
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.associationv1.AssociationReport cmd)"
  logTrace "cmd: $cmd"

  logInfo "${device.label}'s group ${cmd.groupingIdentifier} is associated to ${cmd.nodeId} (max ${cmd.maxNodesSupported})"
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd)"
  logTrace "cmd: $cmd"

  def firmware = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
  updateDataValue("firmware", firmware)
  logDebug "${device.displayName} is running firmware version: $firmware, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd)"
  logTrace "cmd: $cmd"

  logDebug "manufacturerId:   ${cmd.manufacturerId}"
  logDebug "manufacturerName: ${cmd.manufacturerName}"
  logDebug "productId:        ${cmd.productId}"
  logDebug "productTypeId:    ${cmd.productTypeId}"
  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)
  updateDataValue("manufacturer", cmd.manufacturerId.toString())
  updateDataValue("manufacturerName", cmd.manufacturerName ? cmd.manufacturerName : "Ring")
  sendEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd)"
  logTrace "NotificationReport3: $cmd"

  def result = []
  switch (cmd.notificationType) {
  //Power management
    case 8:
      logTrace "Power management"
      switch (cmd.event) {
        case 0x00:
          def msg = "${device.label}'s batteryStatus is ok (not charging)"
          logInfo msg
          result << createEvent([name: "batteryStatus", value: "ok", descriptionText: msg])
          break
        case 0x02:
          def msg = "${device.label}'s acStatus is disconnected"
          logInfo msg
          result << createEvent([name: "acStatus", value: "disconnected", descriptionText: msg])
          result << createEvent([name: "powerSource", value: "battery"])
          break
        case 0x03:
          def msg = "${device.label}'s acStatus is connected"
          logInfo msg
          result << createEvent([name: "acStatus", value: "connected", descriptionText: msg])
          result << createEvent([name: "powerSource", value: "mains"])
          break
        case 0x05:
          def msg = "${device.label}'s acStatus is brownout"
          logInfo msg
          result << createEvent([name: "acStatus", value: "brownout", descriptionText: msg])
          result << createEvent([name: "powerSource", value: "mains"])
          break
        case 0x0C:
          def msg = "${device.label}'s batteryStatus is charging"
          logInfo msg
          result << createEvent([name: "batteryStatus", value: "charging", descriptionText: msg])
          break
        case 0xFE:
          def msg = "${device.label}'s batteryStatus is unknown"
          logInfo msg
          result << createEvent([name: "batteryStatus", value: "unknown", descriptionText: msg])
          break
        default:
          log.warn "Unhandled event ${cmd.event} for ${cmd.notificationType} notification type!"
          break
      }
      break
    case 9:
      logTrace "System"
      switch (cmd.event) {
        case 0x05:
          def msg = "${device.label}'s button was pressed"
          logInfo msg
          result << createEvent([name: "pushed", value: 1, descriptionText: msg, isStateChange: true])
          break
        default:
          log.warn "Unhandled event ${cmd.event} for ${cmd.notificationType} notification type!"
          break
      }
      break
    default:
      log.warn "Unhandled notification type! ${cmd.notificationType}"
      break
  }

  if (result == []) {
    logIncompatible(cmd)
  }
  return result
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd)"
  logTrace "cmd: $cmd"

  result = []
  switch (cmd.parameterNumber) {
    case 1:
      def msg = "Battery Reporting Interval is ${cmd.scaledConfigurationValue} minutes"
      logInfo msg
      result << createEvent([name: "Battery Reporting Interval", value: "${cmd.scaledConfigurationValue} minutes", displayed: false, descriptionText: msg])
      break
    case 4:
      def msg = "LED Indications are ${cmd.scaledConfigurationValue ? 'on' : 'off'}"
      logInfo msg
      result << createEvent([name: "LED Indications", value: "${cmd.scaledConfigurationValue}", displayed: false, descriptionText: msg])
      break
    default:
      log.warn "Unhandled parameter ${cmd.parameterNumber} from configuration report!"
      break
  }
  return result
}

// Logs unexpected events from the device.
def zwaveEvent(hubitat.zwave.Command cmd) {
  logDebug "zwaveEvent(hubitat.zwave.Command cmd)"
  logTrace "Command: $cmd"
  logIncompatible(cmd)
  return []
}

private secureCmd(cmd) {
  if (getDataValue("zwaveSecurePairingComplete")) {
    return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  }
  else {
    return cmd.format()
  }
}

private convertToLocalTimeString(dt) {
  def timeZoneId = location?.timeZone?.ID
  if (timeZoneId) {
    return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
  }
  else {
    return "$dt"
  }
}

private logIncompatible(cmd) {
  log.error "This is probably not the correct device driver for this device!"
  log.warn "cmd: ${cmd}"
}