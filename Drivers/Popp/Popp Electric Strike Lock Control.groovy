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

import hubitat.zwave.commands.doorlockv1.*
import groovy.transform.Field

@Field String VERSION = "1.0.2"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Popp Electric Strike Lock Control", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Popp/Popp%20Electric%20Strike%20Lock%20Control.groovy") {
    capability "Actuator"
    capability "Lock"
    capability "DoorControl"
    capability "Sensor"
    capability "Battery"
    capability "Refresh"
    capability "Polling"
    capability "Configuration"
    capability "Initialize"

    command "clearState"

    fingerprint mfr: "0154", prod: "0005", model: "0001"
    fingerprint deviceId: "1", inClusters: "0x5E, 0x30, 0x71, 0x70, 0x85, 0x80, 0x7A, 0x5A, 0x59, 0x73, 0x98, 0x62, 0x86, 0x72"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 2, required: true
    }
    section { // Configuration
      input name: "lockTimeout", title: "Automated Close after Opening", description: "Number of seconds for automatic closure", type: "number", range: "1..59", defaultValue: 5, required: true
      input name: "param1", title: "Value of Off-Command (1)", description: "", type: "number", range: "0..99", defaultValue: 0, required: true
      input name: "param2", title: "Value of On-Command (2)", description: "", type: "number", range: "0..99", defaultValue: 99, required: true
      input name: "param5", title: "Force FliRS Mode (5)", description: "", type: "enum", options:[[0:"Depends on Power Status in Inclusion Moment"], [1:"Force FLiRS Mode"]], defaultValue: 1, required: true
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
  sendEvent(name: "lock", value: "unknown", displayed: true)
  sendEvent(name: "door", value: "unknown", displayed: true)
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }

  if (!state.MSR) {
    refresh()
  }

  unschedule()
  configure()
}

def poll() {
  logger("debug", "poll()")
  def cmds = []

  cmdSequence([
    zwave.batteryV1.batteryGet(),
    zwave.doorLockV1.doorLockOperationGet()
  ])
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")

  cmdSequence([
    zwave.powerlevelV1.powerlevelGet(),
    zwave.versionV1.versionGet(),
    zwave.firmwareUpdateMdV2.firmwareMdGet(),
    zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
    zwave.batteryV1.batteryGet(),
    zwave.doorLockV1.doorLockOperationGet(),
    zwave.doorLockV1.doorLockConfigurationGet(),
    zwave.configurationV1.configurationGet(parameterNumber: 1),
    zwave.configurationV1.configurationGet(parameterNumber: 2),
    zwave.configurationV1.configurationGet(parameterNumber: 5)
  ])
}

def configure() {
  logger("debug", "configure()")
  def cmds = []

  schedule("0 0 12 */7 * ?", updateCheck)

  if (stateCheckInterval) {
    if (['5', '10', '15', '30'].contains(stateCheckInterval) ) {
      schedule("0 */${stateCheckInterval} * ? * *", checkState)
    } else {
      schedule("0 0 */${stateCheckInterval} ? * *", checkState)
    }
  }

  cmds = cmdSequence([
    zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
    zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId),
    zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: lockTimeout.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: param1.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: param2.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: param5.toInteger()),
    zwave.doorLockV1.doorLockConfigurationSet(insideDoorHandlesState: 0, lockTimeoutMinutes: 0, lockTimeoutSeconds: lockTimeout.toInteger(), operationType: 2, outsideDoorHandlesState: 0)
  ], 500)
  cmds
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
    zwave.batteryV1.batteryGet(),
    zwave.doorLockV1.doorLockOperationGet()
  ], 200)
}

def parse(String description) {
  logger("debug", "parse() - description: ${description?.inspect()}")
  def result = []
  def cmd = zwave.parse(description, getCommandClassVersions())

  if (cmd) {
    result = zwaveEvent(cmd)
    logger("debug", "parse() - parsed to cmd: ${cmd?.inspect()} with result: ${result?.inspect()}")

  } else {
    logger("error", "parse() - Non-parsed - description: ${description?.inspect()}")
  }

  result
}

def open() {
  logger("debug", "open()")
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_UNSECURED_WITH_TIMEOUT)
}

def close() {
  logger("debug", "close()")
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_SECURED)
}

def lock() {
  logger("debug", "lock()")
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_SECURED)
}

def unlock() {
  logger("debug", "unlock()")
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_UNSECURED_WITH_TIMEOUT)
}

def lockAndCheck(doorLockMode) {
  logger("debug", "lockAndCheck() - doorLockMode: ${doorLockMode}")
  cmdSequence([ zwave.doorLockV1.doorLockOperationSet(doorLockMode: doorLockMode), zwave.doorLockV1.doorLockOperationGet() ], 2000)
}

def zwaveEvent(DoorLockOperationReport cmd) {
  logger("debug", "zwaveEvent(DoorLockOperationReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.doorLockMode == 0xFF) {
    logger("info", "Locked")
    result << createEvent(name: "lock", value: "locked", descriptionText: "Strike Closed (Permanently)", displayed: true)
    if(logDescText) { log.info "Strike Closed (Permanently)" }

  } else if (cmd.doorLockMode >= 0x40) {
    logger("info", "Unknown")
    result << createEvent(name: "lock", value: "unknown", descriptionText: "Strike in Unknown state", displayed: true)
    if(logDescText) { log.info "Strike in Unknown state" }

  } else if (cmd.doorLockMode & 1) {
    logger("info", "Unlocked with timeout")
    result << createEvent(name: "lock", value: "unlocked", descriptionText: "Strike Open (Temporarily)", displayed: true)
    if(logDescText) { log.info "Strike Open (Temporarily)" }

  } else {
    logger("info", "Unlocked")
    result << createEvent(name: "lock", value: "unlocked", descriptionText: "Strike Open (Permanently)", displayed: true)
    if(logDescText) { log.info "Strike Open (Permanently)" }
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.doorlockv1.DoorLockConfigurationReport cmd) {
  logger("trace", "zwaveEvent(DoorLockConfigurationReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.alarmv2.AlarmReport cmd) {
  logger("trace", "zwaveEvent(AlarmReport) - cmd: ${cmd.inspect()}")

  if (cmd.alarmType == 0) {
    if (cmd.zwaveAlarmEvent == 22) {
      logger("info", "zwaveEvent(AlarmReport) - Dry Input Open")

    }
    if (cmd.zwaveAlarmEvent == 23) {
      logger("info", "zwaveEvent(AlarmReport) - Dry Input Closed")
    }
  }
  []
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
  logger("trace", "zwaveEvent(SensorBinaryReport) - cmd: ${cmd.inspect()}")
  logger("info", "zwaveEvent(SensorBinaryReport) - Dry Input is ${cmd.sensorValue ? "open" : "closed"}")
  if(logDescText) { log.info "Dry Input is ${cmd.sensorValue ? "open" : "closed"}" }

  createEvent(name: "door", value: cmd.sensorValue ? "open" : "closed")
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
  logger("trace", "zwaveEvent(DeviceResetLocallyNotification) - cmd: ${cmd.inspect()}")
  logger("warn", "zwaveEvent(DeviceResetLocallyNotification) - device has reset itself")
  []
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logger("trace", "zwaveEvent(BatteryReport) - cmd: ${cmd.inspect()}")
  Map map = [name: "battery", unit: "%"]

  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "Has a low battery"
    map.isStateChange = true
    logger("warn", map.descriptionText)
  } else {
    map.value = cmd.batteryLevel
    map.descriptionText = "Battery is ${cmd.batteryLevel} ${map.unit}"
    logger("info", map.descriptionText)
  }

  createEvent(map)
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

private Boolean secondsPast(timestamp, seconds) {
  if (!(timestamp instanceof Number)) {
    if (timestamp instanceof Date) {
      timestamp = timestamp.time
    } else if ((timestamp instanceof String) && timestamp.isNumber()) {
      timestamp = timestamp.toLong()
    } else {
      return true
    }
  }
  return (new Date().time - timestamp) > (seconds * 1000)
}

private getCommandClassVersions() {
  return [0x5E: 1, // COMMAND_CLASS_ZWAVEPLUS_INFO (Insecure)
          0x30: 2, // COMMAND_CLASS_SENSOR_BINARY (Secure)
          0x71: 2, // COMMAND_CLASS_ALARM (Secure)
          0x70: 2, // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x80: 1, // COMMAND_CLASS_BATTERY (Insecure)
          0x7A: 4, // COMMAND_CLASS_FIRMWARE_UPDATE_MD (Insecure)
          0x5A: 1, // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
          0x59: 1, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
          0x98: 1, // COMMAND_CLASS_SECURITY (Secure)
          0x62: 1, // COMMAND_CLASS_DOOR_LOCK (Secure)
          0x86: 1, // COMMAND_CLASS_VERSION (Insecure)
          0x72: 2  // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
  ]
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Popp/Popp%20Electric%20Strike%20Lock%20Control.groovy"]
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
