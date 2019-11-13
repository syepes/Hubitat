/**
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
 */

import groovy.transform.Field

@Field String VERSION = "1.0.0"

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
    attribute "mode", "enum", ["Stop","Anti Freeze","Eco","Comfort-2","Comfort-1","Comfort"]

    fingerprint mfr: "0159", prod: "0004", model: "0001", deviceJoinName: "Qubino Flush Pilot Wire" // ZMNHJA2
    fingerprint mfr: "0159", prod: "0004", model: "0051", deviceJoinName: "Qubino Flush Pilot Wire" // ZMNHJD1 (868,4 MHz - EU)
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL
      input type: " "
      input type: " "
    }
    section { // Configuration
      input name: "param1", title: "Input 1 (1)", description: "Switch type", type: "enum", options:[[0:"mono-stable switch type (push button)"],[1:"bi-stable switch type"]], defaultValue: 1, required: true
      input name: "param4", title: "Input 1 (4)", description: "Contact type", type: "enum", options:[[0:"NO (normally open) input type"],[1:"NC (normally close) input type"]], defaultValue: 0, required: true
      input name: "param11", title: "Input 1 (11)", description: "Operation mode", type: "enum", options:[[0:"Does not influence on selected mode"],[1:"Comfort"],[2:"Comfort-1"],[3:"Comfort-2"],[4:"Eco"],[5:"Anti Freeze"],[6:"Stop"]], defaultValue: 1, required: true

      input name: "param2", title: "Input 2 (2)", description: "Switch type", type: "enum", options:[[0:"mono-stable switch type (push button)"],[1:"bi-stable switch type"]], defaultValue: 1, required: true
      input name: "param5", title: "Input 2 (5)", description: "Contact type", type: "enum", options:[[0:"NO (normally open) input type"],[1:"NC (normally close) input type"]], defaultValue: 0, required: true
      input name: "param12", title: "Input 2 (12)", description: "Operation mode", type: "enum", options:[[0:"Does not influence on selected mode"],[1:"Comfort"],[2:"Comfort-1"],[3:"Comfort-2"],[4:"Eco"],[5:"Anti Freeze"],[6:"Stop"]], defaultValue: 4, required: true

      input name: "param3", title: "Input 3 (3)", description: "Switch type", type: "enum", options:[[0:"mono-stable switch type (push button)"],[1:"bi-stable switch type"]], defaultValue: 1, required: true
      input name: "param6", title: "Input 3 (6)", description: "Contact type", type: "enum", options:[[0:"NO (normally open) input type"],[1:"NC (normally close) input type"]], defaultValue: 0, required: true
      input name: "param13", title: "Input 3 (13)", description: "Operation mode", type: "enum", options:[[0:"Does not influence on selected mode"],[1:"Comfort"],[2:"Comfort-1"],[3:"Comfort-2"],[4:"Eco"],[5:"Anti Freeze"],[6:"Stop"]], defaultValue: 5, required: true
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

  sendEvent(name: "checkInterval", value: 1 * 60 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def updated() {
  logger("debug", "updated()")

  // Make sure installation has completed:
  if (!state.driverVer || state.driverVer != VERSION) {
    installed()
  }
}

def ping() {
  logger("debug", "ping()")

  refresh()
}

def poll() {
  logger("debug", "poll()")

  secureSequence([
    zwave.powerlevelV1.powerlevelGet(),
    zwave.basicV1.basicGet(),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.switchMultilevelV1.switchMultilevelGet(),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0)
  ], 100)
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")

  secureSequence([
    zwave.powerlevelV1.powerlevelGet(),
    zwave.versionV1.versionGet(),
    zwave.firmwareUpdateMdV2.firmwareMdGet(),
    zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
    zwave.basicV1.basicGet(),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.switchMultilevelV1.switchMultilevelGet(),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0)
  ], 100)
}

def on() {
  logger("debug", "on()")

  secureSequence([
    zwave.basicV1.basicSet(value: 0xFF),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.switchMultilevelV1.switchMultilevelGet()
  ], 3500)
}

def off() {
  logger("debug", "off()")

  secureSequence([
    zwave.basicV1.basicSet(value: 0x00),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.switchMultilevelV1.switchMultilevelGet()
  ], 3500)
}

def configure() {
  logger("debug", "configure()")

  def result = []

  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: new Integer("$param1"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: new Integer("$param4"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: new Integer("$param11"))))

  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: new Integer("$param2"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: new Integer("$param5"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: new Integer("$param12"))))

  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: new Integer("$param3"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: new Integer("$param6"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: new Integer("$param13"))))

  result
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

def setLevel(value) {
  logger("debug", "setLevel(${value})")

  Integer valueaux = value as Integer
  Integer level = Math.max(Math.min(valueaux, 99), 0)
  secureSequence([
    zwave.basicV1.basicSet(value: level),
    zwave.switchMultilevelV1.switchMultilevelGet()
  ])
}

def setLevel(value, duration) {
  logger("debug", "setLevel(${value}, ${duration})")

  Integer valueaux = value as Integer
  Integer level = Math.max(Math.min(valueaux, 99), 0)
  Integer dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
  secure(zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration))
}

def pilotMode(mode="Stop") {
  // Validate modes
  Integer mode_value = null
  Map mode_map = [0:"Stop",15:"Anti Freeze",25:"Eco",35:"Comfort-2",45:"Comfort-1",100:"Comfort"]
  mode_map.each { it->
    if (it.value == mode) { mode_value = it.key }
  }

  if (mode_value == null) {
    logger("error", "pilotMode(${mode}) - Mode is incorrect")
  } else {
    logger("info", "pilotMode(${mode}) - Mode value = ${mode_value}")
    sendEvent(name: "mode", value: mode, displayed:true, isStateChange: true)
    setLevel(mode_value)
  }
}

def parse(String description) {
  logger("debug", "parse() - description: ${description.inspect()}")

  def result = null
  if (description.startsWith("Err 106")) {
    state.sec = 0
    createEvent(descriptionText: description, isStateChange: true)
  } else {
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

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logger("trace", "zwaveEvent(switchmultilevelv1.BasicReport) - cmd: ${cmd.inspect()}")
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  logger("trace", "zwaveEvent(SwitchBinaryReport) - cmd: ${cmd.inspect()}")

  String value = (cmd.value ? "on" : "off")
  createEvent(name: "switch", value: value, type: "digital", descriptionText: "$device.displayName was turned $value")
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
  logger("trace", "zwaveEvent(switchmultilevelv1.SwitchMultilevelReport) - cmd: ${cmd.inspect()}")
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd) {
  logger("trace", "zwaveEvent(switchmultilevelv1.SwitchMultilevelReport) - cmd: ${cmd.inspect()}")
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
  logger("trace", "zwaveEvent(switchmultilevelv3.SwitchMultilevelReport) - cmd: ${cmd.inspect()}")
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}")

  //The temperature sensor only measures the internal temperature of product (Circuit board)
  if (cmd.sensorType == hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport.SENSOR_TYPE_TEMPERATURE_VERSION_1) {
    createEvent(name: "temperature", value: cmd.scaledSensorValue, unit: cmd.scale ? "F" : "C", displayed: true )
  }
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  logger("trace", "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['applicationVersion'] = "${cmd.applicationVersion}"
  state.deviceInfo['applicationSubVersion'] = "${cmd.applicationSubVersion}"
  state.deviceInfo['zWaveLibraryType'] = "${cmd.zWaveLibraryType}"
  state.deviceInfo['zWaveProtocolVersion'] = "${cmd.zWaveProtocolVersion}"
  state.deviceInfo['zWaveProtocolSubVersion'] = "${cmd.zWaveProtocolSubVersion}"
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

def zwaveEvent(hubitat.zwave.commands.powerlevelv1.PowerlevelReport cmd) {
  logger("trace", "zwaveEvent(PowerlevelReport) - cmd: ${cmd.inspect()}")

  def power = (cmd.powerLevel > 0) ? "minus${cmd.powerLevel}dBm" : "NormalPower"
  logger("info", "Powerlevel Report: Power: ${power}, Timeout: ${cmd.timeout}")

  // state.powerlevel = power
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  logger("trace", "zwaveEvent(SecurityMessageEncapsulation) - cmd: ${cmd.inspect()}")

  def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
  state.sec = 1
  if (encapsulatedCommand) {
    zwaveEvent(encapsulatedCommand)
  }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  logger("trace", "zwaveEvent(MultiChannelCmdEncap) - cmd: ${cmd.inspect()}")

  def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions) // can specify command class versions here like in zwave.parse
  if (encapsulatedCommand) {
    return zwaveEvent(encapsulatedCommand)
  }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  logger("warn", "zwaveEvent(Command) - Unhandled - cmd: ${cmd.inspect()}")
  [:]
}

private setLevelEvent(hubitat.zwave.Command cmd) {
  logger("debug", "setLevelEvent(Command) - cmd: ${cmd.inspect()}")

  String value = (cmd.value ? "on" : "off")
  Map mode_map = [0:"Stop",15:"Anti Freeze",25:"Eco",35:"Comfort-2",45:"Comfort-1",99:"Comfort", 100:"Comfort"]

  def result = [createEvent(name: "switch", value: value, descriptionText: "$device.displayName was turned $value", isStateChange: true)]
  result << createEvent(name: "level", value: cmd.value == 99 ? 100 : cmd.value , unit: "%")
  result << createEvent(name: "mode", value: mode_map[cmd.value?.toInteger()], isStateChange: true)

  return result
}

private secure(hubitat.zwave.Command cmd) {
  logger("trace", "secure(Command) - cmd: ${cmd.inspect()}")

  if (state.sec) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private secureSequence(Collection commands, ...delayBetweenArgs=4200) {
  logger("trace", "secureSequence(Command) - commands: ${commands.inspect()}")
  delayBetween(commands.collect{ secure(it) }, *delayBetweenArgs)
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
