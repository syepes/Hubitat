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
  definition (name: "Aeotec MultiSensor 6", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Aeotec/Aeotec%20MultiSensor%206.groovy") {
    capability "Actuator"
    capability "Battery"
    capability "Power Source"
    capability "Sensor"
    capability "TamperAlert"
    capability "Motion Sensor"
    capability "Acceleration Sensor"
    capability "Ultraviolet Index"
    capability "Illuminance Measurement"
    capability "Temperature Measurement"
    capability "Relative Humidity Measurement"
    capability "Refresh"
    capability "Configuration"
    capability "Initialize"
    command "clearState"

    fingerprint mfr:"0086", prod:"0002"
    fingerprint deviceId: "100", inClusters: "0x5E, 0x86, 0x72, 0x59, 0x85, 0x73, 0x71, 0x84, 0x80, 0x30, 0x31, 0x70, 0x7A, 0x5A"
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
    }
    section { // Configuration
        input name: "batteryCheckInterval", title: "Device Battery Check Interval", description: "How aften (hours) should we check the battery level", type: "number", defaultValue: 24, required: true
        input name: "wakeUpInterval", title: "Device Wake Up Interval", description: "", type: "enum", options:[[5:"5m"], [15:"15m"], [30:"30m"], [60:"1h"], [120:"2h"], [180:"3h"], [240:"4h"], [480:"8h"], [720:"12h"], [1440: "24h"], [2880: "48h"]], defaultValue: 30, required: true
        input name: "param2", title: "Waking up", description: "Waking up for 10 minutes when re-power on (battery mode) the MultiSensor", type: "enum", options:[[0:"Disable"], [1:"Enable"]], defaultValue: 0, required: true
        input name: "param3", title: "PIR Motion Timeout", description: "Passive Infrared Sensor (PIR) will send BASIC SET CC (0x00) to the associated nodes if no motion is triggered again in X minutes (seconds)", type: "enum", options:[[10:"10s"], [15:"15s"], [20:"20s"], [30:"30s"], [60:"1m"], [120:"2m"], [180:"3m"], [240:"4m"], [300:"5m"], [480:"8m"], [600:"10m"], [900:"15m"], [1800: "30m"], [3600: "1h"], [7200: "2h"]], defaultValue: 60, required: true
        input name: "param4", title: "PIR Motion Sensitivity", description: "", type: "enum", options:[[0:"0 (minimum)"], [2:"2"], [3:"3"], [4:"4"], [5:"5 (maximum)"]], defaultValue: 5, required: true
        input name: "param5", title: "PIR Motion Triggered command", description: "Which command would be sent when the motion sensor is triggered", type: "enum", options:[[1:"Send Basic Set CC"], [2:"Send Sensor Binary Report CC"]], defaultValue: 1, required: true
        input name: "param8", title: "Awake timeout", description: "The timeout of awake after the Wake Up CC is sent out", type: "number", range: "10..30", defaultValue: 15, required: true
        input name: "param40", title: "Selective reporting", description: "Only when measurements reach a certain threshold or percentage. This is used to reduce network traffic. Note: If USB power, the Sensor will check the threshold every 10 seconds. If battery power, the Sensor will check the threshold when it is waken up", type: "enum", options:[[0:"Disable"], [1:"Enable"]], defaultValue: 0, required: true
        input name: "param41", title: "Selective reporting - Threshold Temperature", description: "Threshold change in Temperature to induce an automatic report", type: "number", range: "-128..127", defaultValue: 2, required: true
        input name: "param42", title: "Selective reporting - Threshold Humidity", description: "Threshold change in Humidity to induce an automatic report", type: "number", range: "1..100", defaultValue: 2, required: true
        input name: "param43", title: "Selective reporting - Threshold Luminance", description: "Threshold change in Luminance to induce an automatic report", type: "number", range: "1..30000", defaultValue: 2, required: true
        input name: "param44", title: "Selective reporting - Threshold Battery", description: "Threshold change in Battery to induce an automatic report", type: "number", range: "1..100", defaultValue: 5, required: true
        input name: "param45", title: "Selective reporting - Threshold Ultraviolet", description: "Threshold change in Ultraviolet to induce an automatic report", type: "number", range: "1..11", defaultValue: 2, required: true
        input name: "param81", title: "LED Mode", description: "When should the LED blink", type: "enum", options:[[0:"Enable LED blinking", 1:"Disable LED blinking only when the PIR is triggered", 2:"Completely disable LED for motion, wakeup, and sensor report"]], defaultValue: 0, required: true
        input name: "param39", title: "Report Low battery threshold", description: "When the current battery level is lower than this value, it will send out the low battery alarm", type: "number", range: "10..50", defaultValue: 20, required: true
        input name: "param64", title: "Report Temperature unit", description: "Default unit of the automatic temperature report", type: "enum", options:[[1:"Celsius"], [2:"Fahrenheit"]], defaultValue: 1, required: true
        input name: "param111", title: "Report interval group #1", description: "Time interval for sending reports", type: "enum", options:[[10:"10s"], [20:"20s"], [30:"30s"], [60:"1m"], [120:"2m"], [180:"3m"], [240:"4m"], [300:"5m"], [480:"8m"], [600:"10m"], [900:"15m"], [1800: "30m"], [3600: "1h"], [7200: "2h"]], defaultValue: 300, required: true
        input name: "param201", title: "Calibration Temperature", description: "", type: "number", range: "-128..127", defaultValue: 0, required: true
        input name: "param202", title: "Calibration Humidity", description: "", type: "number", range: "-50..50", defaultValue: 0, required: true
        input name: "param203", title: "Calibration Luminance", description: "", type: "number", range: "-1000..1000", defaultValue: 0, required: true
        input name: "param204", title: "Calibration Ultraviolet", description: "", type: "number", range: "-10..10", defaultValue: 0, required: true
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
  sendEvent(name: 'tamper', value: 'clear')
  sendEvent(name: 'motion', value: 'inactive')
  sendEvent(name: 'acceleration', value: 'inactive')
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
      if (cmd.configurationValue[0] == 0) {
        result << createEvent(name: "powerSource", value: "dc", descriptionText: "Connected to power via USB", displayed: true)
      } else if (cmd.configurationValue[0] == 1) {
        result << createEvent(name: "powerSource", value: "battery", descriptionText: "Running on battery power", displayed: true)
      }
    break;
    default:
      logger("warn", "zwaveEvent(ConfigurationReport) - Unhandled - cmd: ${cmd.inspect()}")
    break;
  }

  return result
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger("trace", "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}")
  logger("info", "Device woke up")
  def cmds = []
  def result = []

  cmds = cmds + cmdSequence([zwave.sensorBinaryV1.sensorBinaryGet()], 100)

  // Only send config if not synced
  if (!state?.driverInfo?.configSynced) {
    logger("info", "Synchronizing device config")
    cmds = cmds + cmdSequence([
      zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
      zwave.wakeUpV2.wakeUpIntervalSet(seconds:wakeUpInterval.toInteger() * 60, nodeid:zwaveHubNodeId),
      zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: param2.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 3, size: 2, scaledConfigurationValue: param3.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: param4.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: param5.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: param8.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 39, size: 1, scaledConfigurationValue: param39.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 40, size: 1, scaledConfigurationValue: param40.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 41, size: 4, configurationValue: [0, param41.toInteger(), param64.toInteger(), 0]),
      zwave.configurationV1.configurationSet(parameterNumber: 42, size: 1, scaledConfigurationValue: param42.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 43, size: 2, scaledConfigurationValue: param43.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 44, size: 1, scaledConfigurationValue: param44.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 45, size: 1, scaledConfigurationValue: param45.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 64, size: 1, scaledConfigurationValue: param64.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 81, size: 1, scaledConfigurationValue: param81.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 241), // All (1+16+32+64+128)
      zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 0), // None
      zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0), // None
      zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: param111.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 201, size: 2, configurationValue: [param201.toInteger(), param64.toInteger()]),
      zwave.configurationV1.configurationSet(parameterNumber: 202, size: 1, scaledConfigurationValue: param202.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 203, size: 2, scaledConfigurationValue: param203.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 204, size: 1, scaledConfigurationValue: param204.toInteger())
    ], 300)
    state.driverInfo.configSynced = true
  }

  // Refresh if MSR is not set
  if (!getDataValue("MSR")) {
    logger("info", "Refresing device info")

    cmds = cmds + cmdSequence([
      zwave.versionV1.versionGet(),
      zwave.firmwareUpdateMdV2.firmwareMdGet(),
      zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
      zwave.configurationV1.configurationGet(parameterNumber: 9) // Retrieve current power mode
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
    case 0x07:
      switch (cmd.event) {
        case 0: // Previous Events cleared
          logger("info", "Clear event")
          result << motionEvent(0)
          if (logDescText) { log.info "Tamper cleared" }
          result << createEvent(name: "tamper", value: "clear", descriptionText: "Tamper cleared", displayed: true)
          if (logDescText) { log.info "Acceleration is inactive" }
          result << createEvent(name: "acceleration", value: "inactive", descriptionText: "Acceleration is inactive", displayed: true)
        break
        case 3: // Tampering Product covering removed
          logger("info", "Tamper/Acceleration event")
          if (logDescText) { log.info "Tamper detected" }
          result << createEvent(name: "tamper", value: "detected", descriptionText: "Tamper detected", displayed: true)
          if (logDescText) { log.info "Acceleration is active" }
          result << createEvent(name: "acceleration", value: "active", descriptionText: "Acceleration is active", displayed: true)
        break
        case 8: // Motion Detection Unknown Location
          logger("info", "Motion event")
          if (logDescText) { log.info "Unknown motion detection" }
          result << motionEvent(1)
        break
        default:
          logger("warn", "zwaveEvent(NotificationReport) - Unhandled event - cmd: ${cmd.inspect()}")
        break;
      }
    break
    default:
      logger("warn", "zwaveEvent(NotificationReport) - Unhandled notificationType - cmd: ${cmd.inspect()}")
    break;
  }

  return result
}


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
  logger("trace", "zwaveEvent(BasicSet) - cmd: ${cmd.inspect()}")
  // Sensor sends value 0xFF on motion, 0x00 on no motion (after expiry interval)
  motionEvent(cmd.value)
}
def zwaveEvent(hubitat.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
  logger("trace", "zwaveEvent(SensorBinaryReport) - cmd: ${cmd.inspect()}")
  // Sensor sends value 0xFF on motion, 0x00 on no motion (after expiry interval)
  motionEvent(cmd.sensorValue)
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}")
  def result = []
  Map map = [:]

  switch (cmd.sensorType) {
    case 1:
      map.name = "temperature"
      map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmd.scale == 1 ? "f" : "c", cmd.precision)
      map.unit = "\u00b0" + getTemperatureScale()
      map.descriptionText = "Temperature is ${map.value} ${map.unit}"
    break
    case 3:
      map.name = "illuminance"
      map.value = cmd.scaledSensorValue
      map.unit = "lux"
      map.descriptionText = "Illuminance is ${map.value} ${map.unit}"
    break
    case 5:
      map.name = "humidity"
      map.value = cmd.scaledSensorValue
      map.unit = "%"
      map.descriptionText = "Humidity is ${map.value} ${map.unit}"
    break
    case 27:
      map.name = "ultravioletIndex"
      map.value = cmd.scaledSensorValue
      map.descriptionText = "Ultraviolet index is ${map.value}"
    break
    default:
      logger("warn", "zwaveEvent(SensorMultilevelReport) - Unknown sensorType - cmd: ${cmd.inspect()}")
    break;
  }

  if(map?.descriptionText) { logger("info", "${map.descriptionText}") }
  if(logDescText && map?.descriptionText) { log.info "${map.descriptionText}" }
  result << createEvent(map)
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

  String model = ""
  switch(cmd.productTypeId >> 8) {
    case 0:
      model = "EU"
    break
    case 1:
      model = "US"
    break
    case 2:
      model = "AU"
    break
    case 10:
      model = "JP"
    break
    case 29:
      model = "CN"
    break
    default:
      model = "Unknown"
  }
  state.deviceInfo['modelVersion'] = "${model}"

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

private motionEvent(value) {
  Map map = [name: "motion", displayed: true]
  if (value) {
    map.value = "active"
    map.descriptionText = "Motion is active"
  } else {
    map.value = "inactive"
    map.descriptionText = "Motion is inactive"
  }

  logger("info", "${map.descriptionText}")
  if(logDescText) { log.info "${map.descriptionText}" }
  createEvent(map)
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
          0x86: 1, // COMMAND_CLASS_VERSION (Insecure)
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x59: 1, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
          0x71: 3, // COMMAND_CLASS_ALARM (Secure)
          0x84: 2, // COMMAND_CLASS_WAKE_UP_V2
          0x80: 1, // COMMAND_CLASS_BATTERY
          0x30: 1, // COMMAND_CLASS_SENSOR_BINARY (Secure)
          0x31: 5, // COMMAND_CLASS_SENSOR_MULTILEVEL
          0x70: 1, // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
          0x7A: 2, // COMMAND_CLASS_FIRMWARE_UPDATE_MD (Insecure)
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Aeotec/Aeotec%20MultiSensor%206.groovy"]
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
