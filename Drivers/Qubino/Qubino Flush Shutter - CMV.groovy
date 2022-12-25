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

@Field String VERSION = "1.0.3"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Qubino Flush Shutter - CMV", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Qubino/Qubino%20Flush%20Shutter%20-%20CMV.groovy") {
    capability "Actuator"
    capability "Switch"
    capability "Sensor"
    capability "Power Meter"
    capability "Energy Meter"
    capability "Temperature Measurement"
    capability "Refresh"
    capability "Polling"
    capability "Configuration"

    command "clearState"
    command "reset"
    command "Q1"
    command "Q2"
    attribute "mode", "string"

    fingerprint mfr:"0159", prod:"0003", model: "0001", deviceJoinName: "Qubino Flush Shutter"
    fingerprint deviceId:"0052", inClusters:"0x5E,0x5A,0x73,0x98,0x86,0x72,0x27,0x25,0x26,0x32,0x71,0x85,0x8E,0x59,0x70", outClusters:"0x26" // ZMNHCD1 868,4 MHz - EU)
    fingerprint deviceId:"0052", inClusters:"0x5E,0x5A,0x73,0x98" // ZMNHCD1 868,4 MHz - EU)
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 2, required: true
    }
    section { // Configuration
      input name: "param10", title: "Respond to switch all", description: "How does the switch respond to the 'Switch All' command", type: "enum", options:[[0:"ALL ON not active, ALL OFF not active"], [1:"ALL ON not active, ALL OFF active"], [2:"ALL ON active, ALL OFF not active"], [255:"ALL ON active, ALL OFF active"]], defaultValue: 255, required: true
      input name: "param42", title: "Power Reporting - Time interval", description: "Reporting in Watts by time interval for Q1 or Q2", type: "enum", options:[[0:"Disabled"], [60:"1min"], [120:"2min"], [300:"5min"], [600:"10min"], [90:"15min"], [1800: "30min"], [3600: "1h"], [14400: "4h"]], defaultValue: 300, required: true
      input name: "param110", title: "Temperature sensor offset", description: "Set value is added or subtracted to actual measured value by sensor<br/>32536 = 0.0C<br/>1 - 100 = 0.1 - 10.0 °C<br/>1001 - 1100 = -0.1 - -10.0°C", type: "number", range: "1..32536", defaultValue: 32536, required: true
      input name: "param120", title: "Temperature sensor reporting", description: "If digital temperature sensor is connected, module reports measured temperature on temperature change defined by this parameter<br/>0 = Reporting disabled<br/>1 - 127 = 0,1 - 12,7°C, step is 0,1°C", type: "number", range: "0..127", defaultValue: 0, required: true
    }
  }
}

def installed() {
  logger("debug", "installed(${VERSION})")

  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION]
  }

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  }

  initialize()
}

def initialize() {
  logger("debug", "initialize()")
  sendEvent(name: "switch", value: "off", descriptionText: "Q1 + Q2", displayed: true)
  sendEvent(name: "mode", value: "None", descriptionText: "Q2 + Q2 - Off", displayed: true)
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
    zwave.switchMultilevelV3.switchMultilevelGet(),
    zwave.meterV4.meterGet(scale: 0), // energy kWh
    zwave.meterV4.meterGet(scale: 2) // watts
  ], 100)
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")

  cmdSequence([
    zwave.powerlevelV1.powerlevelGet(),
    zwave.versionV2.versionGet(),
    zwave.firmwareUpdateMdV2.firmwareMdGet(),
    zwave.manufacturerSpecificV1.manufacturerSpecificGet()
  ], 200)
}

def on() {
  logger("debug", "on()")
}

def off() {
  logger("debug", "off()")
  sendEvent(name: "switch", value: "off", descriptionText: "Q1 + Q2", displayed: true)
  sendEvent(name: "mode", value: "None", descriptionText: "Q2 + Q2 - Off", displayed: true)

  cmd(zwave.switchMultilevelV3.switchMultilevelSet(value: 255, dimmingDuration: 0x00))
}

def Q1() {
  logger("debug", "Q1() - On")
  sendEvent(name: "switch", value: "on", descriptionText: "Q1", displayed: true)
  sendEvent(name: "mode", value: "Q1", descriptionText: "Q1 - On", displayed: true)

  cmd(zwave.switchMultilevelV3.switchMultilevelSet(value: 99, dimmingDuration: 0x00))
}

def Q2() {
  logger("debug", "Q2() - On")
  sendEvent(name: "switch", value: "on", descriptionText: "Q2", displayed: true)
  sendEvent(name: "mode", value: "Q2", descriptionText: "Q2 - On", displayed: true)

  cmd(zwave.switchMultilevelV3.switchMultilevelSet(value: 0, dimmingDuration: 0x00))
}


def configure() {
  logger("debug", "configure()")
  def cmds = []
  def result = []

  if (stateCheckInterval.toInteger()) {
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
    zwave.associationV2.associationSet(groupingIdentifier:6, nodeId:zwaveHubNodeId),
    zwave.associationV2.associationSet(groupingIdentifier:7, nodeId:zwaveHubNodeId),
    zwave.associationV2.associationSet(groupingIdentifier:8, nodeId:zwaveHubNodeId),
    zwave.associationV2.associationSet(groupingIdentifier:9, nodeId:zwaveHubNodeId),

    zwave.configurationV1.configurationSet(parameterNumber: 10, size: 2, scaledConfigurationValue: param10.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 40, size: 1, scaledConfigurationValue: 0), // Power Reporting - Power change = Disabled
    zwave.configurationV1.configurationSet(parameterNumber: 42, size: 2, scaledConfigurationValue: param42.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 71, size: 1, scaledConfigurationValue: 0), // Operating mode = Shutter mode
    zwave.configurationV1.configurationSet(parameterNumber: 72, size: 2, scaledConfigurationValue: 0), // Slats tilting full turn time
    zwave.configurationV1.configurationSet(parameterNumber: 73, size: 1, scaledConfigurationValue: 1), // Slats position = Default
    zwave.configurationV1.configurationSet(parameterNumber: 74, size: 2, scaledConfigurationValue: 0), // Motor moving up/down time = Disabled
    zwave.configurationV1.configurationSet(parameterNumber: 76, size: 1, scaledConfigurationValue: 0), // Motor operation detection = Disabled
    zwave.configurationV1.configurationSet(parameterNumber: 78, size: 1, scaledConfigurationValue: 0), // Calibration = Normal Operation
    zwave.configurationV1.configurationSet(parameterNumber: 85, size: 1, scaledConfigurationValue: 0), // Power consumption max delay time = Time is set automatically
    zwave.configurationV1.configurationSet(parameterNumber: 90, size: 1, scaledConfigurationValue: 10), // Time delay for next motor movement = 3 Sec
    zwave.configurationV1.configurationSet(parameterNumber: 110, size: 2, scaledConfigurationValue: param110.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 120, size: 1, scaledConfigurationValue: param120.toInteger())
  ], 500)

  result = result + response(cmds)
  result
}

def reset() {
  logger("debug", "reset()")

  sendEvent(name: "power", value: "0", unit: "W", displayed: true)
  sendEvent(name: "energy", value: "0", unit: "kWh", displayed: true)
  sendEvent(name: "mode", value: "Unknown", descriptionText: "Q2 + Q2 - Unknown State", displayed: true)

  cmdSequence([
    zwave.meterV4.meterReset()
  ])
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
    zwave.powerlevelV1.powerlevelGet()
  ], 200)
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
  logger("warn", "Has reset itself")
  []
}

def zwaveEvent(hubitat.zwave.commands.meterv4.MeterReport cmd) {
  logger("trace", "zwaveEvent(MeterReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.meterType == 1) { // electric
    switch(cmd.scale){
      case 0:
        result << createEvent(name:"energy", value: cmd.scaledMeterValue, unit:"kWh", displayed: true)
      break;
      case 2:
        result << createEvent(name:"power", value: Math.round(cmd.scaledMeterValue), unit:"W", displayed: true)
      break;
      default:
        logger("warn", "zwaveEvent(MeterReport) - Unknown type: ${cmd.scale}")
      break;
    }
  }

  return result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logger("trace", "zwaveEvent(BasicReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.switchallv1.SwitchAllReport cmd) {
  logger("trace", "zwaveEvent(SwitchAllReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  logger("trace", "zwaveEvent(SwitchBinaryReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SwitchMultilevelReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd, Integer endPoint=null) {
  logger("trace", "zwaveEvent(SensorBinaryReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.sensorType == 1) {
    result << createEvent(name: "temperature", value: cmd.scaledSensorValue, unit: cmd.scale ? "°F" : "°C", displayed: true )
    if (logDescText) { log.info "Temperature is ${cmd.scaledSensorValue} ${cmd.scale ? "°F" : "°C"}" }
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

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  logger("trace", "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}")

  if(cmd.applicationVersion != null && cmd.applicationSubVersion != null) {
    String firmwareVersion = "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}"
    Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
    updateDataValue("firmware", "${firmwareVersion}")
    state.deviceInfo['firmwareVersion'] = firmwareVersion

  } else if(cmd.firmware0Version != null && cmd.firmware0SubVersion != null) {
    def firmware = "${cmd.firmware0Version}.${cmd.firmware0SubVersion.toString().padLeft(2,'0')}"
    Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
    updateDataValue("firmware", "${firmwareVersion}")
    state.deviceInfo['firmwareVersion'] = firmwareVersion
    state.deviceInfo['protocolVersion'] = protocolVersion
  }
  []
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
  logger("trace", "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}")

  Double firmware0Version = cmd.firmware0Version + (cmd.firmware0SubVersion / 100)
  Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
  updateDataValue("firmware", "${firmware0Version}")
  state.deviceInfo['firmwareVersion'] = firmware0Version
  state.deviceInfo['protocolVersion'] = protocolVersion
  state.deviceInfo['hardwareVersion'] = cmd.hardwareVersion

  if (cmd.firmwareTargets > 0) {
    cmd.targetVersions.each { target ->
      Double targetVersion = target.version + (target.subVersion / 100)
      state.deviceInfo["firmware${target.target}Version"] = targetVersion
    }
  }
  []
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
  logger("trace", "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}")

  Double firmware0Version = cmd.firmware0Version + (cmd.firmware0SubVersion / 100)
  Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
  updateDataValue("firmware", "${firmware0Version}")
  state.deviceInfo['firmwareVersion'] = firmware0Version
  state.deviceInfo['protocolVersion'] = protocolVersion
  state.deviceInfo['hardwareVersion'] = cmd.hardwareVersion

  if (cmd.firmwareTargets > 0) {
    cmd.targetVersions.each { target ->
      Double targetVersion = target.version + (target.subVersion / 100)
      state.deviceInfo["firmware${target.target}Version"] = targetVersion
    }
  }
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
  state.deviceInfo['productId'] = "${cmd.productId}"
  state.deviceInfo['productTypeId'] = "${cmd.productTypeId}"

  String msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)
  if (cmd?.manufacturerName && cmd?.manufacturerName != "") {
    updateDataValue("manufacturer", cmd.manufacturerName)
    state.deviceInfo['manufacturerName'] = "${cmd.manufacturerName}"
  } else if (cmd?.manufacturerId != "") {
    updateDataValue("manufacturer", cmd?.manufacturerId?.toString())
  }
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

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd){
  logger("trace", "zwaveEvent(SupervisionGet) - cmd: ${cmd.inspect()}")

  hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
  if (encapCmd) {
    zwaveEvent(encapCmd)
  }
  sendHubCommand(new hubitat.device.HubAction(secure(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)), hubitat.device.Protocol.ZWAVE))
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  logger("warn", "zwaveEvent(Command) - Unhandled - cmd: ${cmd.inspect()}")
  []
}

private cmd(hubitat.zwave.Command cmd) {
  logger("trace", "cmd(Command) - cmd: ${cmd.inspect()} isSecured(): ${isSecured()} S2: ${getDataValue("S2")}")

  if (getDataValue("zwaveSecurePairingComplete") == "true" && getDataValue("S2") == null) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else if (getDataValue("zwaveSecurePairingComplete") == "true") {
    zwaveSecureEncap(cmd)
  } else {
    cmd.format()
  }
}

String secure(String cmd) {
  logger("trace", "secure(String) - cmd: ${cmd.inspect()}")
  return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd) {
  logger("trace", "secure(Command) - cmd: ${cmd.inspect()}")
  return zwaveSecureEncap(cmd)
}

private cmdSequence(Collection commands, Integer delayBetweenArgs=4200) {
  logger("trace", "cmdSequence(Command) - commands: ${commands.inspect()} delayBetweenArgs: ${delayBetweenArgs}")
  delayBetween(commands.collect{ cmd(it) }, delayBetweenArgs)
}

private setSecured() {
  updateDataValue("zwaveSecurePairingComplete", "true")
}
private isSecured() {
  getDataValue("zwaveSecurePairingComplete") == "true"
}

private getCommandClassVersions() {
  return [0x5E: 1, // COMMAND_CLASS_ZWAVEPLUS_INFO (Insecure)
          0x5A: 1, // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
          0x98: 1, // COMMAND_CLASS_SECURITY (Secure)
          0x86: 2, // COMMAND_CLASS_VERSION (Insecure)
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x27: 1, // COMMAND_CLASS_SWITCH_ALL
          0x25: 1, // COMMAND_CLASS_SWITCH_BINARY
          0x26: 3, // COMMAND_CLASS_SWITCH_MULTILEVEL_V2
          0x32: 4, // COMMAND_CLASS_METER
          0x71: 3, // COMMAND_CLASS_ALARM (Secure)
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
