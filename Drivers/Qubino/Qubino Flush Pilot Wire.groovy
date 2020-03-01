/**
 *  Copyright (C) Sebastian YEPES
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.transform.Field

@Field String VERSION = "1.0.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Qubino Flush Pilot Wire", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Qubino/Qubino%20Flush%20Pilot%20Wire.groovy") {
    capability "Actuator"
    capability "Switch"
    capability "Switch Level"
    capability "Sensor"
    capability "Temperature Measurement"
    capability "Refresh"
    capability "Polling"
    capability "Configuration"
    capability "Initialize"

    command "clearState"
    command "pilotMode", [[name:"mode",type:"ENUM", description:"Pilot mode", constraints: ["Stop","Anti Freeze","Eco","Comfort-2","Comfort-1","Comfort"]]]
    command "onTimer", [[name:"duration",type:"ENUM", description:"Pilot mode", constraints: ["5m","10m","15m","30m","1h","2h","3h","4h","5h","6h","7h","8h"]]]
    attribute "mode", "enum", ["Stop","Anti Freeze","Eco","Comfort-2","Comfort-1","Comfort"]

    fingerprint mfr: "0159", prod: "0004", model: "0001", deviceJoinName: "Qubino Flush Pilot Wire" // ZMNHJA2
    fingerprint mfr: "0159", prod: "0004", model: "0051", deviceJoinName: "Qubino Flush Pilot Wire" // ZMNHJD1 (868,4 MHz - EU)
    fingerprint deviceId: "81", inClusters: "0x5E, 0x86, 0x72, 0x5A, 0x73, 0x20, 0x27, 0x25, 0x26, 0x31, 0x60, 0x85, 0x8E, 0x59, 0x70" // ZMNHJD1 (868,4 MHz - EU)
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 2, required: true
    }
    section { // Configuration
      input name: "param1", title: "Input 1 (1)", description: "Switch type", type: "enum", options:[[0:"mono-stable (Push button)"],[1:"bi-stable (Toggle switch)"]], defaultValue: 1, required: true
      input name: "param4", title: "Input 1 (4)", description: "Contact type", type: "enum", options:[[0:"NO (Normally open)"],[1:"NC (Normally close)"]], defaultValue: 0, required: true
      input name: "param11", title: "Input 1 (11)", description: "Operation mode", type: "enum", options:[[0:"No change"],[1:"Comfort"],[2:"Comfort-1"],[3:"Comfort-2"],[4:"Eco"],[5:"Anti Freeze"],[6:"Stop"]], defaultValue: 1, required: true

      input name: "param2", title: "Input 2 (2)", description: "Switch type", type: "enum", options:[[0:"mono-stable (Push button)"],[1:"bi-stable (Toggle switch)"]], defaultValue: 1, required: true
      input name: "param5", title: "Input 2 (5)", description: "Contact type", type: "enum", options:[[0:"NO (Normally open)"],[1:"NC (Normally close)"]], defaultValue: 0, required: true
      input name: "param12", title: "Input 2 (12)", description: "Operation mode", type: "enum", options:[[0:"No change"],[1:"Comfort"],[2:"Comfort-1"],[3:"Comfort-2"],[4:"Eco"],[5:"Anti Freeze"],[6:"Stop"]], defaultValue: 4, required: true

      input name: "param3", title: "Input 3 (3)", description: "Switch type", type: "enum", options:[[0:"mono-stable (Push button)"],[1:"bi-stable (Toggle switch)"]], defaultValue: 1, required: true
      input name: "param6", title: "Input 3 (6)", description: "Contact type", type: "enum", options:[[0:"NO (Normally open)"],[1:"NC (Normally close)"]], defaultValue: 0, required: true
      input name: "param13", title: "Input 3 (13)", description: "Operation mode", type: "enum", options:[[0:"No change"],[1:"Comfort"],[2:"Comfort-1"],[3:"Comfort-2"],[4:"Eco"],[5:"Anti Freeze"],[6:"Stop"]], defaultValue: 5, required: true
    }
  }
}

def installed() {
  logger("debug", "installed(${VERSION})")

  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION, status:'Current version']
  }

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  }

  initialize()
}

def initialize() {
  logger("debug", "initialize()")
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }

  unschedule()
  configure()
}

def poll() {
  logger("debug", "poll()")

  cmdSequence([
    zwave.powerlevelV1.powerlevelGet(),
    zwave.basicV1.basicGet(),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.switchMultilevelV3.switchMultilevelGet(),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0)
  ], 100)
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")

  cmdSequence([
    zwave.powerlevelV1.powerlevelGet(),
    zwave.versionV1.versionGet(),
    zwave.firmwareUpdateMdV2.firmwareMdGet(),
    zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
    zwave.basicV1.basicGet(),
    zwave.switchMultilevelV3.switchMultilevelGet(),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0)
  ], 100)
}

def on() {
  logger("debug", "on()")

  cmdSequence([
    zwave.basicV1.basicSet(value: 0xFF),
    zwave.basicV1.basicGet()
  ], 2000)
}

def off() {
  logger("debug", "off()")

  cmdSequence([
    zwave.basicV1.basicSet(value: 0x00),
    zwave.basicV1.basicGet()
  ], 2000)
}

def onTimer(String duration) {
  logger("debug", "onTimer(${duration})")

  // Validate modes
  Integer duration_value = null
  Map duration_map = [300:"5m", 600:"10m", 900:"15m", 1800:"30m", 3600:"1h", 7200:"2h", 10800:"3h", 14400:"4h", 18000:"5h", 21600:"6h", 25200:"7h", 28800:"8h"]
  duration_map.each { it->
    if (it.value == duration) { duration_value = it.key }
  }

  if (duration_value == null) {
    logger("error", "onTimer(${duration}) - Time value is incorrect")
  } else {
    logger("info", "onTimer(${duration}) - Pilot turned on for ${duration} (${duration_value})")
    if(logDescText) { log.info "Pilot turned on for ${duration} (${duration_value})" }

    startTimer(duration_value, off)

    cmdSequence([
      zwave.basicV1.basicSet(value: 0xFF),
      zwave.basicV1.basicGet()
    ], 2000)
  }
}

def configure() {
  logger("debug", "configure()")
  def cmds = []
  def result = []

  schedule("0 0 12 */7 * ?", updateCheck)

  if (stateCheckInterval) {
    if (['5', '10', '15', '30'].contains(stateCheckInterval) ) {
      schedule("0 */${stateCheckInterval} * ? * *", checkState)
    } else {
      schedule("0 0 */${stateCheckInterval} ? * *", checkState)
    }
  }

  cmds = cmds + cmdSequence([
    zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
    zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId),
    zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId),
    zwave.associationV2.associationSet(groupingIdentifier:4, nodeId:zwaveHubNodeId),
    zwave.associationV2.associationSet(groupingIdentifier:5, nodeId:zwaveHubNodeId),
    zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: param1.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: param4.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: param11.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: param2.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: param5.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: param12.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: param3.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: param6.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: param13.toInteger())
  ], 500)

  result = result + response(cmds)
  result
}

def clearState() {
  logger("debug", "ClearStates() - Clearing device states")
  state.clear()

  if (state?.driverInfo == null) {
    state.driverInfo = [:]
  } else {
    state.driverInfo.clear()
  }

  if (state?.deviceInfo == null) {
    state.deviceInfo = [:]
  } else {
    state.deviceInfo.clear()
  }
  installed()
}

def checkState() {
  logger("debug", "checkState()")

  cmdSequence([
    zwave.powerlevelV1.powerlevelGet(),
    zwave.basicV1.basicGet()
  ], 200)
}

def setLevel(BigDecimal value) {
  logger("debug", "setLevel(${value})")
  Integer level = Math.max(Math.min(value.toInteger(), 99), 0)
  cmdSequence([
    zwave.basicV1.basicSet(value: level),
    zwave.basicV1.basicGet()
  ])
}

def setLevel(BigDecimal value, duration) {
  logger("debug", "setLevel(${value}, ${duration})")
  setLevel(value)
}

def pilotMode(mode="Stop") {
  // Validate modes
  Integer mode_value = null
  Map mode_map = [0:"Stop",15:"Anti Freeze",25:"Eco",35:"Comfort-2",45:"Comfort-1",100:"Comfort"]
  mode_map.each { it->
    if (it.value == mode) { mode_value = it.key }
  }

  if (mode_value == null) {
    logger("error", "pilotMode(${mode}) - Pilot Mode is incorrect")
  } else {
    logger("info", "pilotMode(${mode}) - Pilot Mode value = ${mode_value}")
    if(logDescText) { log.info "Pilot Mode set to ${mode} (${mode_value})" }
    sendEvent(name: "mode", value: mode, displayed:true)
    setLevel(mode_value)
  }
}

def parse(String description) {
  logger("debug", "parse() - description: ${description.inspect()}")
  def result = []
  def cmd = zwave.parse(description, getCommandClassVersions())

  if (cmd) {
    result = zwaveEvent(cmd)
    logger("debug", "parse() - parsed to cmd: ${cmd.inspect()} with result: ${result?.inspect()}")

  } else {
    logger("error", "parse() - Non-parsed - description: ${description?.inspect()}")
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logger("trace", "zwaveEvent(AssociationReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.nodeId.any { it == zwaveHubNodeId }) {
    logger("info", "Is associated in group ${cmd.groupingIdentifier}")
  } else if (cmd.groupingIdentifier == 1) {
    logger("info", "Associating in group ${cmd.groupingIdentifier}")
    result << response(zwave.associationV2.associationSet(groupingIdentifier:cmd.groupingIdentifier, nodeId:zwaveHubNodeId))
  }
  result
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  logger("trace", "zwaveEvent(ConfigurationReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  logger("trace", "zwaveEvent(DeviceResetLocallyNotification) - cmd: ${cmd?.inspect()}")
  logger("warn", "zwaveEvent(DeviceResetLocallyNotification) - device has reset itself")
  []
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logger("trace", "zwaveEvent(BasicReport) - cmd: ${cmd.inspect()}")
  if(logDescText) { log.info "Was turned ${cmd.value ? "on" : "off"}" }
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  logger("trace", "zwaveEvent(SwitchBinaryReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SwitchMultilevelReport) - cmd: ${cmd.inspect()}")
  if(logDescText) { log.info "Was turned ${cmd.value ? "on" : "off"}" }
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
  logger("trace", "zwaveEvent(SensorBinaryReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.sensorType == 1) {
    result << createEvent(name: "temperature", value: cmd.scaledSensorValue, unit: cmd.scale ? "\u00b0F" : "\u00b0C", displayed: true )
    if(logDescText) { log.info "Temperature is ${cmd.scaledSensorValue} ${cmd.scale ? "\u00b0F" : "\u00b0C"}" }
  } else {
    logger("warn", "zwaveEvent(SensorMultilevelReport) - Unknown sensorType - cmd: ${cmd.inspect()}")
  }
  result
}

def zwaveEvent(hubitat.zwave.commands.powerlevelv1.PowerlevelReport cmd) {
  logger("trace", "zwaveEvent(PowerlevelReport) - cmd: ${cmd.inspect()}")

  String power = (cmd.powerLevel > 0) ? "minus${cmd.powerLevel}dBm" : "NormalPower"
  logger("debug", "Powerlevel Report: Power: ${power}, Timeout: ${cmd.timeout}")
  []
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  logger("trace", "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['applicationVersion'] = "${cmd.applicationVersion}"
  state.deviceInfo['applicationSubVersion'] = "${cmd.applicationSubVersion}"
  state.deviceInfo['zWaveLibraryType'] = "${cmd.zWaveLibraryType}"
  state.deviceInfo['zWaveProtocolVersion'] = "${cmd.zWaveProtocolVersion}"
  state.deviceInfo['zWaveProtocolSubVersion'] = "${cmd.zWaveProtocolSubVersion}"

  updateDataValue("firmware", "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
  []
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
  logger("trace", "zwaveEvent(DeviceSpecificReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['deviceIdData'] = "${cmd.deviceIdData}"
  state.deviceInfo['deviceIdDataFormat'] = "${cmd.deviceIdDataFormat}"
  state.deviceInfo['deviceIdDataLengthIndicator'] = "l${cmd.deviceIdDataLengthIndicator}"
  state.deviceInfo['deviceIdType'] = "${cmd.deviceIdType}"
  []
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  logger("trace", "zwaveEvent(ManufacturerSpecificReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['manufacturerId'] = "${cmd.manufacturerId}"
  state.deviceInfo['manufacturerName'] = "${cmd.manufacturerName}"
  state.deviceInfo['productId'] = "${cmd.productId}"
  state.deviceInfo['productTypeId'] = "${cmd.productTypeId}"

  String msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)
  updateDataValue("manufacturer", cmd.manufacturerName)
  []
}

def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
  logger("trace", "zwaveEvent(FirmwareMdReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['firmwareChecksum'] = "${cmd.checksum}"
  state.deviceInfo['firmwareId'] = "${cmd.firmwareId}"
  []
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  logger("trace", "zwaveEvent(SecurityMessageEncapsulation) - cmd: ${cmd.inspect()}")

  setSecured()
  def encapsulatedCommand = cmd.encapsulatedCommand(getCommandClassVersions())
  if (encapsulatedCommand) {
    logger("trace", "zwaveEvent(SecurityMessageEncapsulation) - encapsulatedCommand: ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand)
  } else {
    logger("warn", "zwaveEvent(SecurityMessageEncapsulation) - Unable to extract Secure command from: ${cmd.inspect()}")
    []
  }
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
  logger("trace", "zwaveEvent(Crc16Encap) - cmd: ${cmd.inspect()}")

  def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data)
  if (encapsulatedCommand) {
    logger("trace", "zwaveEvent(Crc16Encap) - encapsulatedCommand: ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand)
  } else {
    logger("warn", "zwaveEvent(Crc16Encap) - Unable to extract CRC16 command from: ${cmd.inspect()}")
    []
  }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  logger("trace", "zwaveEvent(MultiChannelCmdEncap) - cmd: ${cmd.inspect()}")

  def encapsulatedCommand = cmd.encapsulatedCommand(getCommandClassVersions())
  if (encapsulatedCommand) {
    logger("trace", "zwaveEvent(MultiChannelCmdEncap) - encapsulatedCommand: ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
  } else {
    logger("warn", "zwaveEvent(MultiChannelCmdEncap) - Unable to extract MultiChannel command from: ${cmd.inspect()}")
    []
  }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecuritySchemeReport cmd) {
  logger("trace", "zwaveEvent(SecuritySchemeReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
  logger("trace", "zwaveEvent(SecurityCommandsSupportedReport) - cmd: ${cmd.inspect()}")
  setSecured()
  []
}

def zwaveEvent(hubitat.zwave.commands.securityv1.NetworkKeyVerify cmd) {
  logger("trace", "zwaveEvent(NetworkKeyVerify) - cmd: ${cmd.inspect()}")
  logger("info", "Secure inclusion was successful")
  setSecured()
  []
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  logger("warn", "zwaveEvent(Command) - Unhandled - cmd: ${cmd.inspect()}")
  []
}

private setLevelEvent(hubitat.zwave.Command cmd) {
  logger("debug", "setLevelEvent(Command) - cmd: ${cmd.inspect()}")
  def result = []

  String value = (cmd.value ? "on" : "off")
  Map mode_map = [0:"Stop",15:"Anti Freeze",25:"Eco",35:"Comfort-2",45:"Comfort-1",99:"Comfort", 100:"Comfort"]

  result << createEvent(name: "switch", value: value, descriptionText: "Was turned $value")
  result << createEvent(name: "level", value: cmd.value == 99 ? 100 : cmd.value , unit: "%")
  result << createEvent(name: "mode", value: mode_map[cmd.value?.toInteger()])

  result
}

private cmd(hubitat.zwave.Command cmd) {
  logger("trace", "cmd(Command) - cmd: ${cmd.inspect()} isSecured(): ${isSecured()}")

  if (isSecured()) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private cmdSequence(Collection commands, Integer delayBetweenArgs=4200) {
  logger("trace", "cmdSequence(Command) - commands: ${commands.inspect()} delayBetweenArgs: ${delayBetweenArgs}")
  delayBetween(commands.collect{ cmd(it) }, delayBetweenArgs)
}

private setSecured() {
  updateDataValue("secured", "true")
}
private isSecured() {
  getDataValue("secured") == "true"
}

private getCommandClassVersions() {
  return [0x5E: 1, // COMMAND_CLASS_ZWAVEPLUS_INFO (Insecure)
          0x86: 1, // COMMAND_CLASS_VERSION (Insecure)
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x5A: 1, // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
          0x20: 1, // COMMAND_CLASS_BASIC
          0x27: 1, // COMMAND_CLASS_SWITCH_ALL
          0x25: 1, // COMMAND_CLASS_SWITCH_BINARY
          0x26: 3, // COMMAND_CLASS_SWITCH_MULTILEVEL_V2
          0x31: 5, // COMMAND_CLASS_SENSOR_MULTILEVEL
          0x60: 1, // COMMAND_CLASS_MULTI_INSTANCE
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x8E: 2, // COMMAND_CLASS_MULTI_INSTANCE_ASSOCIATION
          0x59: 1, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x70: 2  // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
  ]
}

private startTimer(Integer seconds, function) {
  def now = new Date()
  def runTime = new Date(now.getTime() + (seconds * 1000))
  runOnce(runTime, function)
}

/**
 * @param level Level to log at, see LOG_LEVELS for options
 * @param msg Message to log
 */
private logger(level, msg) {
  if (level && msg) {
    Integer levelIdx = LOG_LEVELS.indexOf(level)
    Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel)
    if (setLevelIdx < 0) {
      setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
    }
    if (levelIdx <= setLevelIdx) {
      log."${level}" "${msg}"
    }
  }
}

def updateCheck() {
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Qubino/Qubino%20Flush%20Pilot%20Wire.groovy"]
  asynchttpGet("updateCheckHandler", params)
}

private updateCheckHandler(resp, data) {
  if (resp?.getStatus() == 200) {
    Integer ver_online = (resp?.getData() =~ /(?m).*String VERSION = "(\S*)".*/).with { hasGroup() ? it[0][1]?.replaceAll('[vV]', '')?.replaceAll('\\.', '').toInteger() : null }
    if (ver_online == null) { logger("error", "updateCheck() - Unable to extract version from source file") }

    Integer ver_cur = state.driverInfo?.ver?.replaceAll('[vV]', '')?.replaceAll('\\.', '').toInteger()

    if (ver_online > ver_cur) {
      logger("info", "New version(${ver_online})")
      state.driverInfo.status = "New version (${ver_online})"
    } else if (ver_online == ver_cur) {
      logger("info", "Current version")
      state.driverInfo.status = 'Current version'
    }

  } else {
    logger("error", "updateCheck() - Unable to download source file")
  }
}
