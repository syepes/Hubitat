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
  definition (name: "Aeotec Water Sensor 6", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Aeotec/Aeotec%20Water%20Sensor%206.groovy") {
    capability "Actuator"
    capability "Battery"
    capability "Power Source"
    capability "Sensor"
    capability "Water Sensor"
    capability "Shock Sensor"
    capability "Temperature Measurement"
    capability "Refresh"
    capability "Configuration"
    capability "Initialize"
    command "clearState"
    attribute "temperatureStatus", "string"
    attribute "position", "string"
    attribute "probe", "string"

    fingerprint mfr:"0086", prod:"0002"
    fingerprint deviceId: "122", inClusters: "0x5E, 0x85, 0x59, 0x80, 0x70, 0x7A, 0x71, 0x73, 0x31, 0x86, 0x84, 0x60, 0x8E, 0x72, 0x5A" // ZW122
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
    }
    section { // Configuration
        input name: "motionTimeout", title: "Motion timeout", description: "Motion detection times out after how many seconds", type: "number", range: "0..3600", defaultValue: 15, required: true
        input name: "batteryCheckInterval", title: "Device Battery Check Interval", description: "How aften (hours) should we check the battery level", type: "number", defaultValue: 24, required: true
        input name: "wakeUpInterval", title: "Device Wake Up Interval", description: "", type: "enum", options:[[5:"5m"], [15:"15m"], [30:"30m"], [60:"1h"], [120:"2h"], [180:"3h"], [240:"4h"], [480:"8h"], [720:"12h"], [1440: "24h"], [2880: "48h"]], defaultValue: 30, required: true
        input name: "param2", title: "Waking up", description: "Waking up for 10 minutes when re-power on (battery mode) the MultiSensor", type: "enum", options:[[0:"Disable"], [1:"Enable"]], defaultValue: 1, required: true
        input name: "param8", title: "Awake timeout", description: "The timeout of awake after the Wake Up CC is sent out", type: "number", range: "0..127", defaultValue: 15, required: true
        input name: "param86", title: "Buzzer Status", description: "Enable/Disable the buzzer", type: "enum", options:[[0:"Disable"], [1:"Enable"]], defaultValue: 1, required: true
        input name: "param87", title: "Buzzer Alarm", description: "To set which sensor is triggered the buzzer will alarm", type: "enum", options:[[55:"All"], [1:"Water leak is triggered"], [2:"Vibration is triggered"], [4:"Tilt sensor is triggered"], [16:"Under heat is triggered"], [32:"Overheat is triggered"]], defaultValue: 55, required: true
        input name: "param48", title: "Sensor reports", description: "What types of reports should the sensor trigger", type: "enum", options:[[0:"Disable"], [55:"All"], [1:"Notification Report for Water Leak event"], [2:"Notification Report for Vibration event"], [4:"Configuration Report for Tilt sensor"], [16:"Notification Report for Under heat alarm"], [32:"Notification Report for Overheat alarm"]], defaultValue: 55, required: true
        input name: "param39", title: "Report Low battery threshold", description: "When the current battery level is lower than this value, it will send out the low battery alarm", type: "number", range: "10..50", defaultValue: 10, required: true
        input name: "param64", title: "Report Temperature unit", description: "Default unit of the automatic temperature report", type: "enum", options:[[0:"Celsius"], [1:"Fahrenheit"]], defaultValue: 0, required: true
        input name: "param88", title: "Probe 1 Value", description: "Basic Set will be sent to the associated nodes in association Group 3 when the Sensor probe 1 is triggered", type: "enum", options:[[0:"Send nothing"], [1:"Presence of water: 0xFF, Absence of water: 0x00"], [2:"Presence of water:0x00, Absence of water: 0xFF"]], defaultValue: 0, required: true
        input name: "param89", title: "Probe 2 Value", description: "Basic Set will be sent to the associated nodes in association Group 4 when the Sensor probe 2 is triggered", type: "enum", options:[[0:"Send nothing"], [1:"Presence of water: 0xFF, Absence of water: 0x00"], [2:"Presence of water:0x00, Absence of water: 0xFF"]], defaultValue: 0, required: true
        input name: "param94", title: "Report Power source", description: "To set which power source level is reported via the Battery CC", type: "enum", options:[[0:"USB power"], [1:"CR123A battery"]], defaultValue: 1, required: true
        input name: "param101", title: "Report unsolicited Lifeline", description: "To set what unsolicited report would be sent to the Lifeline group", type: "enum", options:[[0:"Send Nothing"], [1:"Battery Report is enabled"], [2:"Multilevel sensor report for temperature is enabled"], [3:"Battery Report and Multilevel sensor report for temperature are enabled"]], defaultValue: 3, required: true
        input name: "param111", title: "Report interval group #1", description: "Time interval for sending reports. Note: 1. The unit of interval time is second if USB power. 2. If battery power, the minimum interval time is equal to Wake Up interval set by the Wake Up CC", type: "enum", options:[[10:"10s"], [20:"20s"], [30:"30s"], [60:"1m"], [120:"2m"], [180:"3m"], [240:"4m"], [300:"5m"], [480:"8m"], [600:"10m"], [900:"15m"], [1800: "30m"], [3600: "1h"], [7200: "2h"]], defaultValue: 1800, required: true
        input name: "param201", title: "Calibration Temperature", description: "", type: "number", range: "-128..127", defaultValue: 0, required: true
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
  sendEvent(name: "probe", value: "dry", displayed: true)
  sendEvent(name: "water", value: "dry", displayed: true)
  sendEvent(name: "shock", value: "clear", displayed: true)
  sendEvent(name: "powerSource", value: "unknown", displayed: true)
  sendEvent(name: "position", value: "unknown", displayed: true)
  sendEvent(name: "temperatureStatus", value: "clear", displayed: true)
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
  def result = []

  switch (cmd.parameterNumber) {
    case 9:
      if (cmd.size == 2) {
        result << createEvent(name: "powerSource", value: "DC", descriptionText: "Connected to power via USB")
      } else {
        result << createEvent(name: "powerSource", value: "battery", descriptionText: "Running on battery power")
      }
    break;
    case 84:
      if (cmd.size == 0) {
        result << createEvent(name: "position", value: "horizontal", descriptionText: "Mounted horizontally")
      } else if (cmd.size == 1) {
        result << createEvent(name: "position", value: "vertical", descriptionText: "Mounted vertically")
      }
    break;
    case 136:
      if (cmd.size == 1) {
        result << createEvent(name: "probe", value: "1", descriptionText: "probe 1 detected water")
        if(logDescText) { log.info "Detected water on probe 1" }
      } else if (cmd.size == 2) {
        result << createEvent(name: "probe", value: "2", descriptionText: "probe 2 detected water")
        if(logDescText) { log.info "Detected water on probe 2" }
      } else if (cmd.size == 3) {
        result << createEvent(name: "probe", value: "3", descriptionText: "Both probes have detected water")
        if(logDescText) { log.info "Detected water on both probes" }
      }
    break;
    default:
      logger("warn", "zwaveEvent(ConfigurationReport) - Unhandled - cmd: ${cmd.inspect()}")
    break;
  }

  return result
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalGet cmd) {
  logger("trace", "zwaveEvent(WakeUpIntervalGet) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger("trace", "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}")
  logger("info", "Device woke up")
  def cmds = []
  def result = []

  cmds = cmds + cmdSequence([
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0)
  ], 300)

  // Only send config if not synced
  if (!state?.driverInfo?.configSynced) {
    logger("info", "Synchronizing device config")
    cmds = cmds + cmdSequence([
      zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:4, nodeId:zwaveHubNodeId),
      zwave.wakeUpV2.wakeUpIntervalSet(seconds:wakeUpInterval.toInteger() * 60, nodeid:zwaveHubNodeId),
      zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: param2.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: param8.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 10, size: 4, scaledConfigurationValue: 0|30|10|10),
      zwave.configurationV1.configurationSet(parameterNumber: 39, size: 1, scaledConfigurationValue: param39.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 48, size: 1, scaledConfigurationValue: param48.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 64, size: 1, scaledConfigurationValue: param64.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 86, size: 1, scaledConfigurationValue: param86.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 87, size: 1, scaledConfigurationValue: param87.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 88, size: 1, scaledConfigurationValue: param88.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 89, size: 1, scaledConfigurationValue: param89.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 94, size: 1, scaledConfigurationValue: param94.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 101, size: 1, scaledConfigurationValue: param101.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: param111.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 201, size: 2, configurationValue: [param201.toInteger(), param64.toInteger()]),
      zwave.configurationV1.configurationGet(parameterNumber: 9),
      zwave.configurationV1.configurationGet(parameterNumber: 84),
      zwave.configurationV1.configurationGet(parameterNumber: 136)
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

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
  logger("trace", "zwaveEvent(NotificationReport) - cmd: ${cmd.inspect()}")
  def result = []

  switch(cmd.notificationType) {
    case 0x05:
      if (cmd.event == 0x00) {
        result << createEvent(name: "water", value: "dry", descriptionText: "Sensor is dry")
        result << createEvent(name: "probe", value: "dry")
        if(logDescText) { log.info "Water cleared" }
      }
      if (cmd.event == 0x02) {
        result << createEvent(name: "water", value: "wet", descriptionText: "Sensor detected water")
        if(logDescText) { log.info "Water detected" }
        result << response(cmd(zwave.configurationV1.configurationGet(parameterNumber: 136)))
      }
    break
    case 0x04:
      if (cmd.event == 0x00) {
        result << createEvent(name: "temperatureStatus", value: "clear", descriptionText: "Temperature cleared")
        if(logDescText) { log.info "Temperature cleared" }
      } else if (cmd.event <= 0x02) {
        result << createEvent(name: "temperatureStatus", value: "overheat", descriptionText: "Detected overheat")
        if(logDescText) { log.info "Temperature overheat detected" }
      } else if (cmd.event == 0x06) {
        result << createEvent(name: "temperatureStatus", value: "low", descriptionText: "Detected low temperature")
        if(logDescText) { log.info "Temperature low detected" }
      }
    break
    case 0x07:
      if (cmd.event == 0x00) {
        result << createEvent(name: "shock", value: "clear", descriptionText: "Shock cleared")
        if(logDescText) { log.info "Shock cleared" }
      } else if (cmd.event == 0x03) {
        result << createEvent(name: "shock", value: "detected", descriptionText: "Shock detected")
        if(logDescText) { log.info "Shock detected" }
        startTimer(motionTimeout?.toInteger(), cancelMotion)
      }
    break
    default:
      logger("warn", "zwaveEvent(NotificationReport) - Unhandled - cmd: ${cmd.inspect()}")
    break;
  }

  return result
}

def handleSensorValue(String value) {
  String valueName = (value ? "wet" : "dry")
  createEvent(name: "water", value: valueName, descriptionText: "Was ${valueName}")
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logger("trace", "zwaveEvent(BasicReport) - cmd: ${cmd.inspect()}")
  handleSensorValue(cmd.value)
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
  logger("trace", "zwaveEvent(BasicSet) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  logger("trace", "zwaveEvent(SwitchBinaryReport) - cmd: ${cmd.inspect()}")
  handleSensorValue(cmd.value)
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.sensorType == 1) {
    result << createEvent(name: "temperature", value: cmd.scaledSensorValue, unit: cmd.scale ? "\u00b0F" : "\u00b0C")
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

def cancelMotion() {
  logger("debug", "cancelMotion()")
  if(logDescText) { log.info "Shock cleared" }
  sendEvent(name: "shock", value: "clear")
}

private startTimer(Integer seconds, function) {
  def now = new Date()
  def runTime = new Date(now.getTime() + (seconds * 1000))
  runOnce(runTime, function)
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
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x59: 1, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x80: 1, // COMMAND_CLASS_BATTERY
          0x70: 1, // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
          0x7A: 2, // COMMAND_CLASS_FIRMWARE_UPDATE_MD (Insecure)
          0x71: 3, // COMMAND_CLASS_ALARM (Secure)
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
          0x31: 5, // COMMAND_CLASS_SENSOR_MULTILEVEL
          0x86: 1, // COMMAND_CLASS_VERSION (Insecure)
          0x84: 2, // COMMAND_CLASS_WAKE_UP_V2
          0x60: 1, // COMMAND_CLASS_MULTI_INSTANCE
          0x8E: 2, // COMMAND_CLASS_MULTI_INSTANCE_ASSOCIATION
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x5A: 1 // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Aeotec/Aeotec%20Water%20Sensor%206.groovy"]
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
