/**
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
import hubitat.zwave.commands.usercodev1.*

import groovy.transform.Field

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Popp Electric Strike Lock Control", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/Drivers/Popp/Popp Electric Strike Lock Control.groovy") {
    capability "Actuator"
    capability "Lock"
    capability "Sensor"
    capability "Battery"
    capability "Refresh"
    capability "Polling"
    capability "Configuration"
    capability "Initialize"

    command "clearState"
    command "unlocktimer"

    fingerprint mfr: "0154", prod: "0005", model: "0001"
    fingerprint deviceId: "1", inClusters: "0x5E, 0x30, 0x71, 0x70, 0x85, 0x80, 0x7A, 0x5A, 0x59, 0x73, 0x98, 0x62, 0x86, 0x72"
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL
      input type: " "
      input type: " "
    }
    section { // Configuration
      input name: "wakeUpInterval", title: "Device Wake Up Interval", description: "", type: "enum", options:[[1:"1h"], [2:"2h"], [3:"3h"], [4:"4h"], [8:"8h"], [24:"12h"], [24: "24h"], [48: "48h"]], defaultValue: 1, required: true
      input name: "lockTimeout", title: "Timeout", description: "Lock Timeout in Seconds", type: "number", range: "1..59", defaultValue: 1, displayDuringSetup: true
    }
  }
}

def installed() {
  logger("debug", "installed(${VERSION})")

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  }
  state.driverVer = VERSION

  initialize()
}

def initialize() {
  logger("debug", "initialize()")

}

def updated() {
  logger("debug", "updated()")

  if (lockTimeout == null) lockTimeout = 0

  if (!state.driverVer || state.driverVer != VERSION) {
    installed()
  }

  unschedule()
  configure()
}

def poll() {
  logger("debug", "poll()")

  def cmds = []

  // Only check lock state if it changed recently or we haven't had an update in an hour
  def latest = device.currentState("lock")?.date?.time

  if (!latest || !secondsPast(latest, 6 * 60) || secondsPast(state.lastPoll, 55 * 60)) {
    logger("info", "poll() - Lock state")

    cmds << response(secure(zwave.doorLockV1.doorLockOperationGet()))
    state.lastPoll = now()

  } else if (!state.lastbatt || now() - state.lastbatt > 8*60*60*1000) {
    // Only check battery level if it has not been check in the past 8h
    logger("info", "poll() - Checking Battery")

    cmds << response(secure(zwave.batteryV1.batteryGet()))
    state.lastbatt = now()
  }

  if (cmds) {
    cmds
  } else {
    logger("info", "poll() - Skipping poll")

    // workaround to keep polling from stopping due to lack of activity
    sendEvent(descriptionText: "skipping poll", isStateChange: true, displayed: false)
    null
  }
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")

  secureSequence([
    zwave.powerlevelV1.powerlevelGet(),
    zwave.versionV1.versionGet(),
    zwave.firmwareUpdateMdV2.firmwareMdGet(),
    zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
    zwave.batteryV1.batteryGet(),
    zwave.basicV1.basicGet(),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.doorLockV1.doorLockOperationGet()
  ])
}

def clearState() {
  logger("debug", "ClearStates() - Clearing device states")

  state.clear()

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  } else {
    state.deviceInfo.clear()
  }
}

def configure() {
  logger("debug", "configure()")

  def cmds = []
  def results = []

  schedule("0 0 0/12 * * ?", poll)

  cmds = cmds + secureSequence([
    zwave.wakeUpV1.wakeUpIntervalSet(seconds:wakeUpInterval.toInteger() * 3600, nodeid:zwaveHubNodeId),
    zwave.doorLockV1.doorLockConfigurationSet(insideDoorHandlesState: 0, lockTimeoutMinutes: 0, lockTimeoutSeconds: lockTimeout.toInteger(), operationType: 2, outsideDoorHandlesState: 0)
  ], 500)

  results = results + response(cmds)
  logger("debug", "configure() - results: ${results.inspect()}")

  results
}

def lock() {
  logger("debug", "lock()")

  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_SECURED)
}

def unlock() {
  logger("debug", "unlock()")
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_UNSECURED)
}

def unlocktimer() {
  logger("debug", "unlocktimer()")
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_UNSECURED_WITH_TIMEOUT)
}

def lockAndCheck(doorLockMode) {
  logger("debug", "lockAndCheck() - doorLockMode: ${doorLockMode}")
  secureSequence([ zwave.doorLockV1.doorLockOperationSet(doorLockMode: doorLockMode), zwave.doorLockV1.doorLockOperationGet() ], 2000)
}

def parse(String description) {
  logger("debug", "parse() - description: ${description.inspect()}")

  def result = null
  if (description != "updated") {
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
      result = zwaveEvent(cmd)
      logger("debug", "parse() - description: ${description.inspect()} to cmd: ${cmd.inspect()} with result: ${result.inspect()}")

    } else {
      logger("error", "parse() - Non-parsed - description: ${description?.inspect()}")
      result = null
    }
  }

  result
}

def zwaveEvent(DoorLockOperationReport cmd) {
  logger("debug", "zwaveEvent(DoorLockOperationReport) - cmd: ${cmd.inspect()}")

  def result = []
  Map map = [ name: "lock", isStateChange: true, displayed: true ]

  if (cmd.doorLockMode == 0xFF) {
    logger("info", "Locked")
    map.value = "locked"
    map.descriptionText = "Strike Closed (Permanently)"
  } else if (cmd.doorLockMode >= 0x40) {
    logger("info", "Unknown")
    map.value = "unknown"
    map.descriptionText = "Strike in Unknown state"
  } else if (cmd.doorLockMode & 1) {
    logger("info", "Unlocked with timeout")
    map.value = "unlocked"
    map.descriptionText = "Strike Open (Temporarily)"
  } else {
    logger("info", "Unlocked")
    map.value = "unlocked"
    map.descriptionText = "Strike Open (Permanently)"
  }

  result ? [createEvent(map), *result] : createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logger("trace", "zwaveEvent(AssociationReport) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
  logger("trace", "zwaveEvent(BasicSet) - cmd: ${cmd.inspect()}")

  createEvent(name: "lock", value: cmd.value ? "unlocked" : "locked")
}

/*
  Battery powered devices can be configured to periodically wake up and check in.
  They send this command and stay awake long enough to receive commands, or until they get a WakeUpNoMoreInformation command
  that instructs them that there are no more commands to receive and they can stop listening.
*/
def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger("trace", "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}")
  logger("info", "Device woke up")

  Map result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]

  // Only ask for battery if we haven't had a BatteryReport in a while
  if (!state.lastbatt || (new Date().time) - state.lastbatt > 24*60*60*1000) {
    result << response(zwave.batteryV1.batteryGet())
    result << response("delay 1200") // leave time for device to respond to batteryGet
  }
  result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
  result
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  logger("trace", "zwaveEvent(ConfigurationReport) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  logger("trace", "zwaveEvent(DeviceResetLocallyNotification) - cmd: ${cmd.inspect()}")
  logger("warn", "zwaveEvent(DeviceResetLocallyNotification) - device has reset itself")
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logger("trace", "zwaveEvent(BatteryReport) - cmd: ${cmd.inspect()}")

  Map map = [name: "battery", unit: "%"]
  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "${device.displayName} has a low battery"
    map.isStateChange = true
    logger("warn", map.descriptionText)

  } else {
    map.value = cmd.batteryLevel
    map.descriptionText = "$device.displayName battery is ${cmd.batteryLevel}%"
    logger("info", map.descriptionText)

  }

  state.lastbatt = now()
  createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.powerlevelv1.PowerlevelReport cmd) {
  logger("trace", "zwaveEvent(PowerlevelReport) - cmd: ${cmd.inspect()}")

  def power = (cmd.powerLevel > 0) ? "minus${cmd.powerLevel}dBm" : "NormalPower"
  logger("info", "Powerlevel Report: Power: ${power}, Timeout: ${cmd.timeout}")
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  logger("trace", "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['applicationVersion'] = "${cmd.applicationVersion}"
  state.deviceInfo['applicationSubVersion'] = "${cmd.applicationSubVersion}"
  state.deviceInfo['zWaveLibraryType'] = "${cmd.zWaveLibraryType}"
  state.deviceInfo['zWaveProtocolVersion'] = "${cmd.zWaveProtocolVersion}"
  state.deviceInfo['zWaveProtocolSubVersion'] = "${cmd.zWaveProtocolSubVersion}"

  updateDataValue("firmware", "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
  logger("trace", "zwaveEvent(DeviceSpecificReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['deviceIdData'] = "${cmd.deviceIdData}"
  state.deviceInfo['deviceIdDataFormat'] = "${cmd.deviceIdDataFormat}"
  state.deviceInfo['deviceIdDataLengthIndicator'] = "l${cmd.deviceIdDataLengthIndicator}"
  state.deviceInfo['deviceIdType'] = "${cmd.deviceIdType}"
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

  createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
}

def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
  logger("trace", "zwaveEvent(FirmwareMdReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['firmwareChecksum'] = "${cmd.checksum}"
  state.deviceInfo['firmwareId'] = "${cmd.firmwareId}"
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  logger("trace", "zwaveEvent(SecurityMessageEncapsulation) - cmd: ${cmd.inspect()}")

  setSecured()
  def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
  if (encapsulatedCommand) {
    logger("trace", "zwaveEvent(SecurityMessageEncapsulation) - encapsulatedCommand: ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand)

  } else {
    logger("warn", "zwaveEvent(SecurityMessageEncapsulation) - Unable to extract Secure command from: ${cmd.inspect()}")
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
  }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  logger("trace", "zwaveEvent(MultiChannelCmdEncap) - cmd: ${cmd.inspect()}")

  def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
  if (encapsulatedCommand) {
    logger("trace", "zwaveEvent(MultiChannelCmdEncap) - encapsulatedCommand: ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
  } else {
    logger("warn", "zwaveEvent(MultiChannelCmdEncap) - Unable to extract MultiChannel command from: ${cmd.inspect()}")
  }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecuritySchemeReport cmd) {
  logger("trace", "zwaveEvent(SecuritySchemeReport) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
  logger("trace", "zwaveEvent(SecurityCommandsSupportedReport) - cmd: ${cmd.inspect()}")
  setSecured()
}

def zwaveEvent(hubitat.zwave.commands.securityv1.NetworkKeyVerify cmd) {
  logger("trace", "zwaveEvent(NetworkKeyVerify) - cmd: ${cmd.inspect()}")
  logger("info", "Secure inclusion was successful")
  setSecured()
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  logger("warn", "zwaveEvent(Command) - Unhandled - cmd: ${cmd.inspect()}")
  [:]
}

private secure(hubitat.zwave.Command cmd) {
  logger("trace", "secure(Command) - cmd: ${cmd.inspect()} isSecured(): ${isSecured()}")

  if (isSecured()) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private secureSequence(Collection commands, Integer delayBetweenArgs=4200) {
  logger("trace", "secureSequence(Command) - commands: ${commands.inspect()} delayBetweenArgs: ${delayBetweenArgs}")
  delayBetween(commands.collect{ secure(it) }, delayBetweenArgs)
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
