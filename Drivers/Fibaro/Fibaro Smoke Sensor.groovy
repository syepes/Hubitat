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
  definition (name: "Fibaro Smoke Sensor", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Fibaro/Fibaro%20Smoke%20Sensor.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "TamperAlert"
    capability "SmokeDetector"
    capability "TemperatureMeasurement"
    capability "Battery"
    capability "Refresh"
    capability "Configuration"
    capability "Initialize"

    command "clearState"

    attribute "heatAlarm", "enum", ["overheat", "inactive"]

    fingerprint mfr:"010F", prod:"0C02"
    fingerprint deviceId: "0x0701", inClusters: "0x5E, 0x86, 0x72, 0x5A, 0x59, 0x85, 0x73, 0x84, 0x80, 0x71, 0x56, 0x70, 0x31, 0x8E, 0x22, 0x9C, 0x98, 0x7A", outClusters: "0x20, 0x8B"
    fingerprint deviceId: "0x0701", inClusters: "0x5E, 0x86, 0x72, 0x5A, 0x59, 0x85, 0x73, 0x84, 0x80, 0x71, 0x56, 0x70, 0x31, 0x8E, 0x22, 0x9C, 0x98, 0x7A"
    fingerprint deviceId: "4099", inClusters: "0x5E, 0x20, 0x86, 0x72, 0x5A, 0x59, 0x85, 0x73, 0x84, 0x80, 0x71, 0x56, 0x70, 0x31, 0x8E, 0x22, 0x9C, 0x98,0 x7A" // FGSD-002 ZW5
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
    }
    section { // Configuration
      input name: "batteryCheckInterval", title: "Device Battery Check Interval", description: "How aften (hours) should we check the battery level", type: "number", defaultValue: 24, required: true
      input name: "wakeUpInterval", title: "Device Wake Up Interval", description: "", type: "enum", options:[[1:"1h"], [2:"2h"], [3:"3h"], [4:"4h"], [8:"8h"], [24:"12h"], [24: "24h"], [48: "48h"]], defaultValue: 1, required: true
      input name: "param1", title: "Smoke Sensor sensitivity", description: "", type: "enum", options:[[1:"High"],[2:"Medium"],[3:"Low"]], defaultValue: 2, required: true
      input name: "param2", title: "Z-Wave notifications", description: "Activate excess temperature and/or enclosure opening notifications sent to the main controller", type: "enum", options:[[0:"All notifications disabled"],[1:"Enclosure opening notification enabled"],[2:"Exceeding temperature threshold notification enabled"],[3:"All notifications enabled"]], defaultValue: 0, required: true
      input name: "param3", title: "Visual notifications", description: "Activate visual indications but does not apply to major alarms, such as FIRE, TROUBLE and LOW BATTERY ALARM", type: "enum", options:[[0:"All notifications disabled"],[1:"Enclosure opening notification enabled"],[2:"Exceeding temperature threshold notification enabled"],[4:"Lack of Z-Wave range notification"]], defaultValue: 0, required: true
      input name: "param4", title: "Sound notifications", description: "Activate sound signals but does not apply to major alarms, such as FIRE, TROUBLE and LOW BATTERY ALARM", type: "enum", options:[[0:"All notifications disabled"],[1:"Enclosure opening notification enabled"],[2:"Exceeding temperature threshold notification enabled"],[4:"Lack of Z-Wave range notification"]], defaultValue: 0, required: true
      input name: "param13", title: "Alarm broadcast", description: "A value other than 0 means that alarms are being sent in broadcast mode, i.e. to all devices within a FIBARO Smoke Sensor’s range", type: "enum", options:[[0:"Broadcast inactive"],[1:"FIRE ALARM broadcast (2nd & 4th Association Group) active; enclosure opening notification broadcast (3rd & 5th Association Group) inactive"],[2:"FIRE ALARM broadcast (2nd & 4th Association Group) inactive; enclosure opening notification broadcast (3rd & 5th Association Group) active"],[3:"FIRE ALARM broadcast (2nd & 4th Association Group) active; enclosure opening notification broadcast (3rd & 5th Association Group) active"]], defaultValue: 0, required: true
      input name: "param10", title: "Report BASIC command", description: "This parameter defines which frames will be sent in the 2-nd Association Group (FIRE ALARM). The values of BASIC ON and BASIC OFF frames may be defined as described in further parameters", type: "enum", options:[[0:"BASIC ON & BASIC OFF enabled"],[1:"BASIC ON enabled"],[2:"BASIC OFF enabled"]], defaultValue: 0, required: true
      input name: "param11", title: "BASIC ON value", description: "BASIC ON frame is sent in case of smoke presence detection and Fire Alarmtriggering. Its value is defined by the parameter", type: "number", range: "0..255",defaultValue: 255, required: true
      input name: "param12", title: "BASIC OFF value", description: "BASIC OFF frame is sent in case of Fire Alarm cancellation. Its value is defined by the parameter", type: "number", range: "0..255",defaultValue: 0, required: true
      input name: "param20", title: "Temperature report interval", description: "The temperature report will only be sent if there is a difference in temperature valuefrom the previous value reported", type: "enum", options:[[1:"10s"], [3:"30s"], [6:"60s"], [30:"300s"], [60:"600s"], [180:"1800s"], [360:"3600s"], [864: "8640s"]], defaultValue: 3, required: true
      input name: "param21", title: "Temperature reports threshold (Hysteresis)", description: "The temperature report will only be sent if there is a difference in temperature valuefrom the previous value reported", type: "enum", options:[[2:"0.2°C"], [3:"0.3°C"], [5:"0.5°C"], [10:"1°C"], [15:"1.5°C"], [20:"2°C"], [25:"2.5°C"], [30:"3°C"]], defaultValue: 5, required: true
      input name: "param30", title: "Temperature threshold", description: "Temperature value measured by the built-in temperature sensor above which the excess temperature notification is sent", type: "enum", options:[[40:"100°F / 40°C"], [45:"110°F / 45°C"], [50:"120°F / 50°C"], [55:"130°F / 55°C"], [60:"140°F / 60°C"], [65:"150°F / 65°C"], [71:"160°F / 71°C"], [77:"170°F / 77°C"], [82:"180°F / 82°C"], [93:"200°F / 93°C"]], defaultValue: 55, required: true
      input name: "param31", title: "Temperature excess signaling interval", description: "Time interval of signaling (visual indication/sound) excess temperature level", type: "enum", options:[[1:"10s"], [3:"30s"], [6:"60s"], [30:"300s"], [60:"600s"], [180:"1800s"], [360:"3600s"], [8640: "8640s"]], defaultValue: 1, required: true
      input name: "param32", title: "Lack of Z-Wave range interval", description: "Time interval of signaling (visual indication/sound) lack of Z-Wave range", type: "enum", options:[[1:"10s"], [3:"30s"], [6:"60s"], [30:"300s"], [60:"600s"], [180:"1800s"], [360:"3600s"], [8640: "8640s"]], defaultValue: 180, required: true
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
  sendEvent(name: "smoke", value: "clear", displayed: true)
  sendEvent(name: "heatAlarm", value: "clear", displayed: true)
  sendEvent(name: "tamper", value: "clear", displayed: true)
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

private alarmEventSmoke(value) {
  logger("debug", "alarmEventSmoke() - value: ${value}")
  def result = []
  Map map = [name: "smoke", isStateChange: true]

  if (value == 2) {
    logger("info", "Smoke alart detected")
    map.value = "detected"
    map.descriptionText = "Smoke alarm is Active"

  } else if (value == 0) {
    logger("info", "Smoke alart cleared")
    map.value = "clear"
    map.descriptionText = "Smoke alarm is Cleared (no smoke)"

  } else if (value == 3) {
    logger("info", "Smoke alart test")
    map.value = "tested"
    map.descriptionText = "Smoke alarm is Test"

  } else {
    logger("warn", "Smoke alart unknown (${value})")
    map.value = "unknown"
    map.descriptionText = "Smoke alarm Unknown (${value})"
  }

  result << createEvent(map)
  if(logDescText) { log.info "${map.descriptionText}" }

  result
}

private alarmEventHeat(value) {
  logger("debug", "alarmEventHeat() - value: ${value}")
  def result = []
  Map map = [name: "heatAlarm", isStateChange: true]

  if (value == 2) {
    logger("info", "Heat alart detected")
    map.value = "detected"
    map.descriptionText = "Heat alarm is Active"

  } else if (value == 0) {
    logger("info", "Heat alart cleared")
    map.value = "clear"
    map.descriptionText = "Heat alarm is Cleared (no overheat)"

  } else {
    logger("warn", "Heat alart unknown (${value})")
    map.value = "unknown"
    map.descriptionText = "Heat alarm is Unknown (${value})"
  }

  result << createEvent(map)
  if(logDescText) { log.info "${map.descriptionText}" }

  result
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

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger("trace", "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}")
  logger("info", "Device woke up")
  def cmds = []
  def result = []

  cmds = cmds + cmdSequence([
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0)
  ], 1000)

  // Only send config if not synced
  if (!state?.driverInfo?.configSynced) {
    logger("info", "Synchronizing device config")

    cmds = cmds + cmdSequence([
      zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:4, nodeId:zwaveHubNodeId),
      zwave.associationV2.associationSet(groupingIdentifier:5, nodeId:zwaveHubNodeId),
      zwave.wakeUpV2.wakeUpIntervalSet(seconds:wakeUpInterval.toInteger() * 3600, nodeid:zwaveHubNodeId),
      zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: param1.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: param2.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: param3.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: param4.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: param10.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 11, size: 2, scaledConfigurationValue: param11.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 12, size: 2, scaledConfigurationValue: param12.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: param13.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 20, size: 2, scaledConfigurationValue: param20.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 21, size: 1, scaledConfigurationValue: param21.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 30, size: 1, scaledConfigurationValue: param30.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 31, size: 2, scaledConfigurationValue: param31.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 32, size: 2, scaledConfigurationValue: param32.toInteger())
    ], 500)
    state.driverInfo.configSynced = false
  }

  // Refresh if MSR is not set
  if (!getDataValue("MSR")) {
    logger("info", "Refresing device info")

    cmds = cmds + cmdSequence([
      zwave.powerlevelV1.powerlevelGet(),
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

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
  logger("trace", "zwaveEvent(NotificationReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.notificationType == 1) { // Smoke Alarm (V2)
    logger("info", "Smoke Alarm")
    result << alarmEventSmoke(cmd.event)

  } else if (cmd.notificationType == 4) { // Heat Alarm (V2)
    logger("info", "Heat Alarm")
    result << alarmEventHeat(cmd.event)

  } else if (cmd.notificationType == 7) {
    switch (cmd.event) {
      case 0:
        logger("info", "Tamper cleared")
        result << createEvent(name: "tamper", value: "clear", descriptionText: "Tamper cleared", displayed: true)
      break
      case 3:
        logger("warn", "Tamper detected")
        result << createEvent(name: "tamper", value: "detected", descriptionText: "Tamper detected", displayed: true)
      break
    }

  } else if (cmd.notificationType == 8) {
    if (cmd.event == 0x0A) {
      Map map = [name:"battery", value:1, unit:"%", displayed:true]
      result << createEvent(map)
    }

  } else if (cmd.notificationType == 9) {
    if (cmd.event == 0x01) {
      logger("warn", "System hardware failure")
    }

  } else {
    logger("warn", "zwaveEvent(NotificationReport) - Unhandled - cmd: ${cmd.inspect()}")
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


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logger("trace", "zwaveEvent(BasicReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
  logger("trace", "zwaveEvent(BasicSet) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.sensoralarmv1.SensorAlarmReport cmd) {
  logger("trace", "zwaveEvent(SensorAlarmReport) - cmd: ${cmd.inspect()}")
  Map map = [isStateChange:true]

  switch (cmd.sensorType) {
    case 1:
      map.name = "smoke"
      map.value = cmd.sensorState == 0xFF ? "detected" : "clear"
      map.descriptionText = cmd.sensorState == 0xFF ? "Detected smoke" : "Smoke is clear (no smoke)"
    break
    case 4:
      map.name = "heatAlarm"
      map.value = cmd.sensorState == 0xFF ? "overheat" : "inactive"
      map.descriptionText = cmd.sensorState == 0xFF ? "Overheat detected" : "Heat alarm cleared (no overheat)"
    break
  }

  createEvent(map)
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

private getCommandClassVersions() {
  return [0x5E: 1, // COMMAND_CLASS_ZWAVEPLUS_INFO (Insecure)
          0x20: 1, // COMMAND_CLASS_BASIC
          0x86: 1, // COMMAND_CLASS_VERSION (Insecure)
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x5A: 1, // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
          0x59: 1, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
          0x84: 2, // COMMAND_CLASS_WAKE_UP_V2
          0x80: 1, // COMMAND_CLASS_BATTERY
          0x71: 3, // COMMAND_CLASS_ALARM (Secure)
          0x56: 1, // COMMAND_CLASS_CRC_16_ENCAP
          0x70: 2, // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
          0x31: 5, // COMMAND_CLASS_SENSOR_MULTILEVEL
          0x8E: 2, // COMMAND_CLASS_MULTI_INSTANCE_ASSOCIATION
          0x22: 1, // COMMAND_CLASS_APPLICATION_STATUS
          0x9C: 1, // OMMAND_CLASS_SENSOR_ALARM
          0x98: 1, // COMMAND_CLASS_SECURITY (Secure)
          0x7A: 2  // COMMAND_CLASS_FIRMWARE_UPDATE_MD (Insecure)
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

public updateCheck() {
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Fibaro/Fibaro%20Smoke%20Sensor.groovy"]
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
