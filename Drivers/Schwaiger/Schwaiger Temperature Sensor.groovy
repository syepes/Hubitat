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

@Field String VERSION = "1.0.2"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Schwaiger Temperature Sensor", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Schwaiger/Schwaiger%20Temperature%20Sensor.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "ThermostatHeatingSetpoint"
    capability "ThermostatSetpoint"
    capability "Temperature Measurement"
    capability "Battery"
    capability "Refresh"
    capability "Configuration"
    capability "Initialize"

    command "clearState"

    fingerprint mfr:"0002", prod:"0003"
    fingerprint deviceId: "32784", inClusters: "0x20, 0x72, 0x86, 0x80, 0x8F, 0x84, 0x75, 0x70, 0x31, 0x5B, 0x43, 0x53, 0x87" // ZHD01
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
    }
    section { // Configuration
      input name: "batteryCheckInterval", title: "Device Battery Check Interval", description: "How aften (hours) should we check the battery level", type: "number", defaultValue: 24, required: true
      input name: "wakeUpInterval", title: "Device Wake Up Interval", description: "", type: "enum", options:[[1:"1h"], [2:"2h"], [3:"3h"], [4:"4h"], [8:"8h"], [24:"12h"], [24: "24h"], [48: "48h"]], defaultValue: 3, required: true
      input name: "protect_local", title: "Local Protection", description: "Applies to physical switches", type: "enum", options:[[0:"No protection"], [2:"User interface locked"]], defaultValue: 0, required: true
      input name: "protect_remote", title: "Remote Protection", description: "Applies to Z-Wave commands sent from hub or other devices", type: "enum", options:[[0:"No protection"], [1:"No RF control"], [2:"No RF response"]], defaultValue: 0, required: true

      input name: "param1", title: "Temperature report threshold", description: "", type: "enum", options:[[1:"0.1°C"], [2:"0.2°C"], [3:"0.3°C"], [4:"0.4°C"], [5:"0.5°C"], [10:"1°C"], [15:"1.5°C"], [20:"2°C"], [25:"2.5°C"], [30:"3°C"], [50:"5°C"], [100:"10°C"]], defaultValue: 5, required: true
      input name: "param2", title: "Set-point resolution", description: "", type: "enum", options:[[1:"0.1°C"], [2:"0.2°C"], [3:"0.3°C"], [4:"0.4°C"], [5:"0.5°C"], [10:"1°C"], [15:"1.5°C"], [20:"2°C"], [25:"2.5°C"], [30:"3°C"], [50:"5°C"], [100:"10°C"]], defaultValue: 5, required: true
      input name: "param3", title: "Set-point and Override limit (Min)", description: "", type: "enum", options:[[0:"0°C"], [5:"5°C"], [10:"10°C"], [15:"15°C"], [16:"16°C"], [17:"17°C"], [18:"18°C"], [19:"19°C"], [20:"20°C"], [21:"21°C"], [22:"22°C"], [23:"23°C"], [24:"24°C"], [25:"25°C"], [26:"26°C"], [27:"27°C"], [28:"28°C"], [29:"29°C"], [30:"30°C"], [31:"31°C"], [32:"32°C"], [33:"33°C"], [34:"34°C"], [35:"35°C"], [36:"36°C"], [37:"38°C"], [39:"39°C"], [40:"40°C"]], defaultValue: 15, required: true
      input name: "param4", title: "Set-point and Override limit (Max)", description: "", type: "enum", options:[[0:"0°C"], [5:"5°C"], [10:"10°C"], [15:"15°C"], [16:"16°C"], [17:"17°C"], [18:"18°C"], [19:"19°C"], [20:"20°C"], [21:"21°C"], [22:"22°C"], [23:"23°C"], [24:"24°C"], [25:"25°C"], [26:"26°C"], [27:"27°C"], [28:"28°C"], [29:"29°C"], [30:"30°C"], [31:"31°C"], [32:"32°C"], [33:"33°C"], [34:"34°C"], [35:"35°C"], [36:"36°C"], [37:"38°C"], [39:"39°C"], [40:"40°C"]], defaultValue: 28, required: true

      input name: "param6", title: "Set-point control function", description: "", type: "enum", options:[[0:"Deactivated"], [1:"Activated"]], defaultValue: 1, required: true
      input name: "param7", title: "Temporarily override scheduler", description: "", type: "enum", options:[[0:"Deactivated"], [1:"Activated"]], defaultValue: 1, required: true
      input name: "param8", title: "Set-point in Thermostat_Setpoint_Reports", description: "", type: "enum", options:[[1:"Heating"], [2:"cooling"], [10:"Auto changeover"]], defaultValue: 1, required: true

      input name: "param9", title: "LED on time", description: "", type: "enum", options:[[1:"100 ms"], [2:"200 ms"], [3:"300 ms"], [4:"400 ms"], [5:"500 ms"]], defaultValue: 1, required: true
      input name: "param5", title: "LED Flash period", description: "", type: "enum", options:[[1:"1 sec"], [2:"2 sec"], [3:"3 sec"], [4:"4 sec"], [5:"5 sec"], [6:"6 sec"]], defaultValue: 1, required: true

      input name: "param10", title: "LED Number of flashes (duration)", description: "", type: "enum", options:[[1:"1"], [2:"2"], [3:"3"], [4:"4"], [5:"5"], [6:"6"], [7:"7"], [8:"8"], [9:"9"], [10:"10"]], defaultValue: 5, required: true
      input name: "param11", title: "LED Color", description: "", type: "enum", options:[[0x01:"Green"], [0x02:"Red"]], defaultValue: 0x01, required: true
    }
  }
}

def installed() {
  logger("debug", "installed(${VERSION})")

  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION, status:'Current version']
    state.driverInfo.configSynced = false
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

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
  state.deviceInfo.lastbatt = now()
  updateDataValue("MSR", "")
}

def configure() {
  logger("debug", "configure()")
  schedule("0 0 12 */7 * ?", updateCheck)

  logger("info", "Device configurations will be synchronized on the next device wakeUp")
  state.driverInfo.configSynced = false
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

def setHeatingSetpoint(Double degrees) {
  logger("debug", "setHeatingSetpoint() - degrees: ${degrees}")
}

def parse(String description) {
  logger("debug", "parse() - description: ${description.inspect()}")
  def result = []

  // Not implemented CC
  if (description.contains("command: ")) {
    def index = description.indexOf("command: ") + 9
    def commandClassId = description.substring(index, index +2)
    if (commandClassId == "53") { // COMMAND_CLASS_SCHEDULE
      return result
    }
  }

  def cmd = zwave.parse(description, getCommandClassVersions())
  if (cmd) {
    result = zwaveEvent(cmd)
    logger("debug", "parse() - parsed to cmd: ${cmd?.inspect()} with result: ${result?.inspect()}")

  } else {
    logger("error", "parse() - Non-parsed - description: ${description?.inspect()}")
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.indicatorv1.IndicatorReport cmd) {
  logger("trace", "zwaveEvent(IndicatorReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionReport cmd) {
  logger("trace", "zwaveEvent(ProtectionReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
  logger("trace", "zwaveEvent(CentralSceneNotification) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.keyAttributes == 0) {
    result << createEvent(name: "pushed", value: 1, data: [buttonNumber: 1], descriptionText: "Button was pushed")
    logger("info", "Button Pushed")
  }

  if (cmd.keyAttributes == 1) {
    result << createEvent(name: "released", value: 1, data: [buttonNumber: 1], descriptionText: "Button was released")
    logger("info", "Button Released")
  }

  if (cmd.keyAttributes == 2) {
    result << createEvent(name: "held", value: 1, data: [buttonNumber: 1], descriptionText: "Button was held")
    logger("info", "Button Held")
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) {
  logger("trace", "zwaveEvent(ThermostatSetpointReport) - cmd: ${cmd.inspect()}")
  def result = []

  switch (cmd.setpointType) {
    case 1:
      result << createEvent(name: "thermostatSetpoint", value: cmd.scaledValue, unit: cmd.scale ? "F" : "C")
      result << createEvent(name: "heatingSetpoint", value: cmd.scaledValue, unit: cmd.scale ? "F" : "C")
      break;
    default:
      log.debug "unknown setpointType $cmd.setpointType"
      return
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger("trace", "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}")
  logger("info", "Device woke up")
  def cmds = []
  def result = []

  // zwave.scheduleV1.commandScheduleGet(), // Not Implemented in HE
  // zwave.scheduleV1.scheduleStateReport(), // Not Implemented in HE
  cmds = cmds + cmdSequence([
    zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0)
  ], 300)

  // Only send config if not synced
  if (!state?.driverInfo?.configSynced) {
    logger("info", "Synchronizing device config")

    cmds = cmds + cmdSequence([
      zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
      zwave.wakeUpV2.wakeUpIntervalSet(seconds:wakeUpInterval.toInteger() * 3600, nodeid:zwaveHubNodeId),
      zwave.indicatorV1.indicatorSet(value: 255),
      zwave.configurationV1.configurationSet(parameterNumber: 1, size: 2, scaledConfigurationValue: param1.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 2, size: 2, scaledConfigurationValue: param2.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 3, size: 2, scaledConfigurationValue: param3.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: param4.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 5, size: 2, scaledConfigurationValue: param5.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: param6.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, scaledConfigurationValue: param7.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: param8.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: param9.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: param10.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: param11),
      zwave.protectionV2.protectionSet(localProtectionState : protect_local.toInteger(), rfProtectionState: protect_remote.toInteger()),
      zwave.indicatorV1.indicatorGet(),
      zwave.protectionV2.protectionGet()
    ], 300)
    state.driverInfo.configSynced = true
  }

  // Refresh if MSR is not set
  if (!getDataValue("MSR")) {
    logger("info", "Refresing device info")

    cmds = cmds + cmdSequence([
      zwave.versionV1.versionGet(),
      zwave.firmwareUpdateMdV2.firmwareMdGet(),
      zwave.manufacturerSpecificV1.manufacturerSpecificGet()
    ], 100)
  }

  // Check battery level only once every Xh
  if (!state?.deviceInfo?.lastbatt || now() - state.deviceInfo.lastbatt >= batteryCheckInterval?.toInteger() *60*60*1000) {
    cmds = cmds + cmdSequence([zwave.batteryV1.batteryGet()], 100)
  }

  cmds = cmds + cmdSequence([zwave.wakeUpV2.wakeUpNoMoreInformation()], 500)
  result = result + response(cmds)

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

def zwaveEvent(hubitat.zwave.commands.timeparametersv1.TimeParametersGet cmd) {
  logger("trace", "zwaveEvent(TimeParametersGet) - cmd: ${cmd.inspect()}")

  //Time Parameters are requested by an un-encapsulated frame
  def nowCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
  response(zwave.timeParametersV1.timeParametersReport(year: nowCal.get(Calendar.YEAR), month: (nowCal.get(Calendar.MONTH) + 1), day: nowCal.get(Calendar.DAY_OF_MONTH), hourUtc: nowCal.get(Calendar.HOUR_OF_DAY), minuteUtc: nowCal.get(Calendar.MINUTE), secondUtc: nowCal.get(Calendar.SECOND)).format())
}

def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationBusy cmd) {
  logger("trace", "zwaveEvent(ApplicationBusy) - cmd: ${cmd.inspect()}")
  logger("warn", "Is busy")
  []
}

def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
  logger("trace", "zwaveEvent(ApplicationRejectedRequest) - cmd: ${cmd.inspect()}")
  logger("warn", "Rejected the last request")
  []
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

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logger("trace", "zwaveEvent(BatteryReport) - cmd: ${cmd.inspect()}")
  Map map = [ name: "battery", unit: "%" ]

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

  state.deviceInfo.lastbatt = now()
  createEvent(map)
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

private getCommandClassVersions() {
  return [0x20: 1, // COMMAND_CLASS_BASIC
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x86: 1, // COMMAND_CLASS_VERSION (Insecure)
          0x80: 1, // COMMAND_CLASS_BATTERY
          0x8F: 1, // COMMAND_CLASS_MULTI_CMD
          0x84: 2, // COMMAND_CLASS_WAKE_UP_V2
          0x75: 2, // COMMAND_CLASS_PROTECTION_V2
          0x70: 2, // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
          0x31: 5, // COMMAND_CLASS_SENSOR_MULTILEVEL
          0x5B: 1, // COMMAND_CLASS_CENTRAL_SCENE
          0x43: 2, // COMMAND_CLASS_THERMOSTAT_SETPOINT
          0x53: 1, // COMMAND_CLASS_SCHEDULE
          0x87: 1  // COMMAND_CLASS_INDICATOR
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Schwaiger/Schwaiger%20Temperature%20Sensor.groovy"]
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
