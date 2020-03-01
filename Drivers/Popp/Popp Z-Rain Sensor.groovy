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
  definition (name: "Popp Z-Rain Sensor", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Popp/Popp%20Z-Rain%20Sensor.groovy") {
    capability "Actuator"
    capability "Battery"
    capability "Power Source"
    capability "Sensor"
    capability "Refresh"
    capability "Configuration"
    capability "Initialize"
    command "clearState"

    attribute "rainMeter", "number"
    attribute "rainRate", "number"
    attribute "rainTotal", "number"

    attribute "rain", "enum", ["true","false"]
    attribute "rainHeavy", "enum", ["true","false"]

    fingerprint mfr:"0154", prod:"0004"
    fingerprint deviceId: "17", inClusters: "0x5E, 0x31, 0x70, 0x85, 0x80, 0x84, 0x32, 0x7A, 0x5A, 0x59, 0x73, 0x86, 0x72"
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
    }
    section { // Configuration
        input name: "batteryCheckInterval", title: "Device Battery Check Interval", description: "How aften (hours) should we check the battery level", type: "number", defaultValue: 24, required: true
        input name: "wakeUpInterval", title: "Device Wake Up Interval", description: "", type: "enum", options:[[15:"15m"], [30:"30m"], [60:"1h"], [120:"2h"], [180:"3h"], [240:"4h"], [480:"8h"], [720:"12h"], [1440: "24h"], [2880: "48h"]], defaultValue: 30, required: true
        input name: "param1", title: "Rain Counter", description: "Initial rain counter from the moment of inclusion in mm water level. By setting this value the counter will be reseted, it should be empty to leave the counter unchanged", type: "number", range: "0..32000", defaultValue: '', required: false
        input name: "param4", title: "Meter Multiplier", description: "This multiplier allows to adapt the display to certain controllers not being able to handle very low numbers", type: "enum", options:[[1:"1"], [10:"10"], [100:"100"], [1000:"1000"]], defaultValue: 1, required: true
        input name: "param6", title: "Heavy Rain Start Command", description: "This BASIC Set Command value is sent out into Association Group 3 when the device detects start of heavy rain", type: "number", range: "0..99", defaultValue: 99, required: true
        input name: "param7", title: "Heavy Rain Stop Command", description: "This BASIC Set Command value is sent out into Association Group 3 when the device detects stop of heavy rain", type: "number", range: "0..99", defaultValue: 1, required: true
        input name: "param5", title: "Heavy Rain", description: "This threshold defines when a heavy rain condition is hit. In most countries this is defined as > 15 mm rain per hour. The default value however is to turn this function off (256)", type: "number", range: "0..256", defaultValue: 25, required: true
        input name: "param2", title: "Rain Start Command", description: "This BASIC Set Command value is sent out into Association Group 2 when the device detects start of rain", type: "number", range: "0..99", defaultValue: 98, required: true
        input name: "param3", title: "Rain Stop Command", description: "This BASIC Set Command value is sent out into Association Group 2 when the device detects stop of rain", type: "number", range: "0..99", defaultValue: 0, required: true
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

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
  logger("trace", "zwaveEvent(ConfigurationReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  logger("trace", "zwaveEvent(DeviceResetLocallyNotification) - cmd: ${cmd.inspect()}")
  logger("warn", "zwaveEvent(DeviceResetLocallyNotification) - device has reset itself")
  []
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger("trace", "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}")
  logger("info", "Device woke up")
  def cmds = []
  def result = []

  cmds = cmds + cmdSequence([
    zwave.meterV4.meterGet(scale: 0), // Water meter
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0) // Rain rate
  ], 300)

  // Only send config if not synced
  if (!state?.driverInfo?.configSynced) {
    logger("info", "Synchronizing device config")
    cmds = cmds + cmdSequence([
      zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId),
      zwave.wakeUpV2.wakeUpIntervalSet(seconds:wakeUpInterval.toInteger() * 60, nodeid:zwaveHubNodeId),
      zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: param2.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: param3.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: param4.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: param5.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: param6.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, scaledConfigurationValue: param7.toInteger())
    ], 300)

    if (param1 != null && param1 != '') {
      logger("info", "Resetting Rain Counter to ${param1}")
      cmds = cmds + cmdSequence([
        zwave.configurationV1.configurationSet(parameterNumber: 1, size: 2, scaledConfigurationValue: param1.toInteger())
      ], 100)
    }
    state.driverInfo.configSynced = true
  }

  // Refresh if MSR is not set
  if (!getDataValue("MSR")) {
    logger("info", "Refresing device info")
    cmds = cmds + cmdSequence([
      zwave.versionV1.versionGet(),
      zwave.firmwareUpdateMdV2.firmwareMdGet(),
      zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
      zwave.powerlevelV1.powerlevelGet()
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

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
  logger("trace", "zwaveEvent(BasicSet) - cmd: ${cmd.inspect()}")
  def result = []
  Map map = [displayed: true]

  switch (cmd?.value) {
    case "${param2}":
      map.name = "rain"
      map.value = true
      map.descriptionText = "It has started raining (Normal)"
      if(logDescText) { log.info "${map.descriptionText}" }
    break
    case "${param3}":
      map.name = "rain"
      map.value = false
      map.descriptionText = "It has stopped raining (Normal)"
      if(logDescText) { log.info "${map.descriptionText}" }
    break
    case "${param6}":
      map.name = "rainHeavy"
      map.value = true
      map.descriptionText = "It has started raining (Heavy)"
      if(logDescText) { log.info "${map.descriptionText}" }
    break
    case "${param7}":
      map.name = "rainHeavy"
      map.value = false
      map.descriptionText = "It has stopped raining (Heavy)"
      if(logDescText) { log.info "${map.descriptionText}" }
    break
    default:
      logger("warn", "zwaveEvent(BasicSet) - Unknown value: ${cmd?.value}")
    break;
  }

  createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logger("trace", "zwaveEvent(BasicReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.meterv4.MeterReport cmd) {
  logger("trace", "zwaveEvent(MeterReport) - cmd: ${cmd.inspect()}")
  def result = []
  List meterTypes = ["Unknown", "Electric", "Gas", "Water"]
  List waterUnits = ["m^3", "ft^3", "gal"]

  if (cmd.meterType == 3) { // water
    logger("debug", "zwaveEvent(MeterReport) - deltaTime:${cmd.deltaTime} secs, meterType:${meterTypes[cmd.meterType]}, meterValue:${cmd.scaledMeterValue}, previousMeterValue:${cmd.scaledPreviousMeterValue}, scale:${cmd.scale}, unit: ${waterUnits[cmd.scale]}, precision:${cmd.precision}, rateType:${cmd.rateType}")
    def map = [name: "rainMeter", value: cmd.scaledMeterValue, unit: waterUnits[cmd.scale], displayed: true]
    result << createEvent(map)
    if(logDescText) { log.info "${meterTypes[cmd.meterType]} ${map.name} is ${map.value} ${map.unit}" }

  } else {
    logger("warn", "zwaveEvent(MeterReport) - Unknown meterType: ${cmd.meterType}")
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}")
  def cmds = []
  Map map = [sensorType: cmd.sensorType, scale: cmd.scale, displayed: true]

  switch (cmd.sensorType) {
    case 2: // General Purpose (V1)
      map.name = "rainTotal"
      map.unit = (cmd.scale == 1) ? "" : "%"
      map.precision = cmd.precision
      map.value = cmd.scaledSensorValue.toFloat() * cmd?.precision?.toInteger()
      if(logDescText) { log.info "Water ${map.name} is ${map.value} ${map.unit}" }
    break
    case 0xC: // 12 = Rain Rate (V2)
      map.name = "rainRate"
      map.unit = (cmd.scale == 1) ? "in/h" : "mm/h"
      map.precision = cmd.precision
      map.value = cmd.scaledSensorValue.toFloat() * cmd?.precision?.toInteger()
      if(logDescText) { log.info "Water ${map.name} is ${map.value} ${map.unit}" }
    break
    default:
      logger("warn", "zwaveEvent(SensorMultilevelReport) - Unknown sensorType: ${cmd.sensorType}")
    break;
  }

  createEvent(map)
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

private cmdSequence(Collection commands, Integer delayBetweenArgs=250) {
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
          0x31: 5, // COMMAND_CLASS_SENSOR_MULTILEVEL
          0x70: 1, // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x80: 1, // COMMAND_CLASS_BATTERY
          0x84: 2, // COMMAND_CLASS_WAKE_UP_V2
          0x32: 4, // COMMAND_CLASS_METER
          0x7A: 2, // COMMAND_CLASS_FIRMWARE_UPDATE_MD (Insecure)
          0x5A: 1, // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
          0x59: 1, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Popp/Popp%20Z-Rain%20Sensor.groovy"]
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
