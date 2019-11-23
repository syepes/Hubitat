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

import groovy.transform.Field

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Fibaro Smoke Sensor", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/Drivers/Fibaro/Fibaro%20Smoke%20Sensor.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "TamperAlert"
    capability "Smoke Detector"
    capability "Temperature Measurement"
    capability "Battery"
    capability "Refresh"
    capability "Configuration"
    capability "Initialize"

    command "clearState"

    attribute "heatAlarm", "enum", ["overheat", "inactive"]
    attribute "tamperStatus", "string"

    fingerprint mfr:"010F", prod:"0C02"
    fingerprint deviceId: "0x0701", inClusters: "0x5E, 0x86, 0x72, 0x5A, 0x59, 0x85, 0x73, 0x84, 0x80, 0x71, 0x56, 0x70, 0x31, 0x8E, 0x22, 0x9C, 0x98, 0x7A", outClusters: "0x20, 0x8B"
    fingerprint deviceId: "0x0701", inClusters: "0x5E, 0x86, 0x72, 0x5A, 0x59, 0x85, 0x73, 0x84, 0x80, 0x71, 0x56, 0x70, 0x31, 0x8E, 0x22, 0x9C, 0x98, 0x7A"
    fingerprint deviceId: "4099", inClusters: "0x5E, 0x20, 0x86, 0x72, 0x5A, 0x59, 0x85, 0x73, 0x84, 0x80, 0x71, 0x56, 0x70, 0x31, 0x8E, 0x22, 0x9C, 0x98,0 x7A" // FGSD-002 ZW5
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL
    }
    section { // Configuration
      input name: "wakeUpInterval", title: "Device Wake Up Interval", description: "", type: "enum", options:[[1:"1h"], [2:"2h"], [3:"3h"], [4:"4h"], [8:"8h"], [24:"12h"], [24: "24h"], [48: "48h"]], defaultValue: 1, required: true
      input name: "param1", title: "Smoke Sensor sensitivity", description: "", type: "enum", options:[[1:"High"],[2:"Medium"],[3:"Low"]], defaultValue: 2, required: true
      input name: "param2", title: "Z-Wave notifications", description: "Activate excess temperature and/or enclosure opening notifications sent to the main controller", type: "enum", options:[[0:"All notifications disabled"],[1:"Enclosure opening notification enabled"],[2:"Exceeding temperature threshold notification enabled"],[3:"All notifications enabled"]], defaultValue: 0, required: true
      input name: "param3", title: "Visual notifications", description: "Activate visual indications but does not apply to major alarms, such as FIRE, TROUBLE and LOW BATTERY ALARM", type: "enum", options:[[0:"All notifications disabled"],[1:"Enclosure opening notification enabled"],[2:"Exceeding temperature threshold notification enabled"],[4:"Lack of Z-Wave range notification"]], defaultValue: 0, required: true
      input name: "param4", title: "Sound notifications", description: "Activate sound signals but does not apply to major alarms, such as FIRE, TROUBLE and LOW BATTERY ALARM", type: "enum", options:[[0:"All notifications disabled"],[1:"Enclosure opening notification enabled"],[2:"Exceeding temperature threshold notification enabled"],[4:"Lack of Z-Wave range notification"]], defaultValue: 0, required: true

      input name: "param10", title: "Control frames in BASIC", description: "This parameter defines which frames will be sent in the 2-nd Association Group (FIRE ALARM). The values of BASIC ON and BASIC OFF frames may be defined as described in further parameters", type: "enum", options:[[0:"BASIC ON & BASIC OFF enabled"],[1:"BASIC ON enabled"],[2:"BASIC OFF enabled"]], defaultValue: 0, required: true
      input name: "param11", title: "BASIC ON frame value", description: "BASIC ON frame is sent in case of smoke presence detection and Fire Alarmtriggering. Its value is defined by the parameter", type: "number", range: "0..255",defaultValue: 255, required: true
      input name: "param12", title: "BASIC OFF frame value", description: "BASIC OFF frame is sent in case of Fire Alarm cancellation. Its value is defined by the parameter", type: "number", range: "0..255",defaultValue: 0, required: true
      input name: "param13", title: "Alarm broadcast", description: "A value other than 0 means that alarms are being sent in broadcast mode, i.e. to all devices within a FIBARO Smoke Sensor’s range", type: "enum", options:[[0:"Broadcast inactive"],[1:"FIRE ALARM broadcast (2nd & 4th Association Group) active; enclosure opening notification broadcast (3rd & 5th Association Group) inactive"],[2:"FIRE ALARM broadcast (2nd & 4th Association Group) inactive; enclosure opening notification broadcast (3rd & 5th Association Group) active"],[2:"FIRE ALARM broadcast (2nd & 4th Association Group) active; enclosure opening notification broadcast (3rd & 5th Association Group) active"]], defaultValue: 0, required: true

      input name: "param20", title: "Temperature report interval", description: "The temperature report will only be sent if there is a difference in temperature valuefrom the previous value reported", type: "enum", options:[[1:"10s"], [3:"30s"], [6:"60s"], [30:"300s"], [60:"600s"], [180:"1800s"], [360:"3600s"], [864: "8640s"]], defaultValue: 1, required: true
      input name: "param21", title: "Temperature reports threshold (Hysteresis)", description: "The temperature report will only be sent if there is a difference in temperature valuefrom the previous value reported", type: "enum", options:[[3:"0.3°C"], [5:"0.5°C"], [10:"1°C"], [15:"1.5°C"], [20:"2°C"], [25:"2.5°C"], [30:"3°C"]], defaultValue: 10, required: true
      input name: "param30", title: "Temperature threshold", description: "Temperature value measured by the built-in temperature sensor above which the excess temperature notification is sent", type: "enum", options:[[40:"100°F / 40°C"], [45:"110°F / 45°C"], [50:"120°F / 50°C"], [55:"130°F / 55°C"], [60:"140°F / 60°C"], [65:"150°F / 65°C"], [71:"160°F / 71°C"], [77:"170°F / 77°C"], [82:"180°F / 82°C"], [93:"200°F / 93°C"]], defaultValue: 55, required: true
      input name: "param31", title: "Temperature excess signaling interval", description: "Time interval of signaling (visual indication/sound) excess temperature level", type: "enum", options:[[1:"10s"], [3:"30s"], [6:"60s"], [30:"300s"], [60:"600s"], [180:"1800s"], [360:"3600s"], [8640: "8640s"]], defaultValue: 1, required: true

      input name: "param32", title: "Lack of Z-Wave range interval", description: "Time interval of signaling (visual indication/sound) lack of Z-Wave range", type: "enum", options:[[1:"10s"], [3:"30s"], [6:"60s"], [30:"300s"], [60:"600s"], [180:"1800s"], [360:"3600s"], [8640: "8640s"]], defaultValue: 180, required: true
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

  if (!state.driverVer || state.driverVer != VERSION) {
    installed()
  }

  unschedule()
  configure()
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
  updateDataValue("MSR", "")
}


def configure() {
  logger("debug", "configure()")
  logger("info", "Device configurations will be synchronized on the next device wakeUp")
  state.deviceConfigSynced = false
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

private alarmEventSmoke(value) {
  logger("debug", "alarmEventSmoke() - value: ${value}")

  Map map = [name: "smoke", isStateChange: true]

  if (value == 2) {
    logger("info", "Smoke alart detected")
    map.value = "detected"
    map.descriptionText = "Detected smoke"

  } else if (value == 0) {
    logger("info", "Smoke alart cleared")
    map.value = "clear"
    map.descriptionText = "Smoke alart is clear (no smoke)"

  } else if (value == 3) {
    logger("info", "Smoke alart test")
    map.value = "tested"
    map.descriptionText = "Smoke alarm test"

  } else {
    logger("warn", "Smoke alart unknown")
    map.value = "unknown"
    map.descriptionText = "Unknown event"

  }

  createEvent(map)
}

private alarmEventHeat(value) {
  logger("debug", "alarmEventHeat() - value: ${value}")

  Map map = [name: "heatAlarm", isStateChange: true]

  if (value == 2) {
    logger("info", "Heat alart detected")
    map.value = "overheat"
    map.descriptionText = "Overheat detected"

  } else if (value == 0) {
    logger("info", "Heat alart cleared")
    map.value = "inactive"
    map.descriptionText = "Heat alarm cleared (no overheat)"

  } else {
    logger("warn", "Heat alart unknown (${value})")
    map.value = "unknown"
    map.descriptionText = "Unknown event"

  }

  createEvent(map)
}

def parse(String description) {
  logger("debug", "parse() - description: ${description.inspect()}")

  def result = []
  if (description != "updated") {
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
      result = zwaveEvent(cmd)
      logger("debug", "parse() - description: ${description.inspect()} to cmd: ${cmd.inspect()} with result: ${result.inspect()}")

    } else {
      logger("error", "parse() - Non-parsed - description: ${description?.inspect()}")
    }
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger("trace", "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}")
  logger("info", "Device woke up")

  def cmds = []
  def results = [createEvent(descriptionText: "$device.displayName woke up", isStateChange: true)]

  cmds = cmds + secureSequence([
    zwave.batteryV1.batteryGet(),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0)
  ], 1000)

  // Only send config if not synced
  if (!state?.deviceConfigSynced) {
    logger("info", "Synchronizing device config")

    cmds = cmds + secureSequence([
        zwave.wakeUpV1.wakeUpIntervalSet(seconds:wakeUpInterval.toInteger() * 3600, nodeid:zwaveHubNodeId),
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
    state?.deviceConfigSynced = true
  }

  // Refresh if MSR is not set
  if (!getDataValue("MSR") ) {
    logger("info", "Refresing device info")

    cmds = cmds + secureSequence([
      zwave.powerlevelV1.powerlevelGet(),
      zwave.versionV1.versionGet(),
      zwave.firmwareUpdateMdV2.firmwareMdGet(),
      zwave.manufacturerSpecificV1.manufacturerSpecificGet()
    ], 100)
  }

  cmds << "delay " + (5000 + 15 * 1500)
  cmds << secure(zwave.wakeUpV1.wakeUpNoMoreInformation())

  results = results + response(cmds)
  logger("debug", "zwaveEvent(WakeUpNotification) - results: ${results.inspect()}")

  results
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logger("trace", "zwaveEvent(AssociationReport) - cmd: ${cmd.inspect()}")

  def result = []
  if (cmd.nodeId.any { it == zwaveHubNodeId }) {
    result << createEvent(descriptionText: "$device.displayName is associated in group ${cmd.groupingIdentifier}")
  } else if (cmd.groupingIdentifier == 1) {
    result << createEvent(descriptionText: "Associating $device.displayName in group ${cmd.groupingIdentifier}")
    result << response(zwave.associationV1.associationSet(groupingIdentifier:cmd.groupingIdentifier, nodeId:zwaveHubNodeId))
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
}

def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
  logger("trace", "zwaveEvent(ApplicationRejectedRequest) - cmd: ${cmd.inspect()}")
  logger("warn", "Rejected the last request")
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
        result << createEvent(name: "tamper", value: "clear", descriptionText: "${device.displayName} tamper cleared", displayed: true)
      break
      case 3:
        logger("warn", "Tamper detected")
        result << createEvent(name: "tamper", value: "detected", descriptionText: "${device.displayName} tamper detected", displayed: true)
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
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  logger("trace", "zwaveEvent(DeviceResetLocallyNotification) - cmd: ${cmd.inspect()}")
  logger("warn", "zwaveEvent(DeviceResetLocallyNotification) - device has reset itself")
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logger("trace", "zwaveEvent(BasicReport) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.sensoralarmv1.SensorAlarmReport cmd) {
  logger("trace", "zwaveEvent(SensorAlarmReport) - cmd: ${cmd.inspect()}")

  def map = [isStateChange:true]

  switch (cmd.sensorType) {
    case 1:
      map.name = "smoke"
      map.value = cmd.sensorState == 0xFF ? "detected" : "clear"
      map.descriptionText = cmd.sensorState == 0xFF ? "$device.displayName detected smoke" : "$device.displayName is clear (no smoke)"
    break
    case 4:
      map.name = "heatAlarm"
      map.value = cmd.sensorState == 0xFF ? "overheat" : "inactive"
      map.descriptionText = cmd.sensorState == 0xFF ? "$device.displayName overheat detected" : "$device.displayName heat alarm cleared (no overheat)"
    break
  }

  createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}")

  if (cmd.sensorType == hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport.SENSOR_TYPE_TEMPERATURE_VERSION_1) {
    createEvent(name: "temperature", value: cmd.scaledSensorValue, unit: cmd.scale ? "F" : "C", displayed: true )
  } else {
    logger("warn", "zwaveEvent(SensorMultilevelReport) - Unknown sensorType - cmd: ${cmd.inspect()}")
  }
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logger("trace", "zwaveEvent(BatteryReport) - cmd: ${cmd.inspect()}")

  Map map = [ name: "battery", unit: "%" ]
  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "${device.displayName} has a low battery"
    map.isStateChange = true
    logger("warn", map.descriptionText)

  } else {
    map.value = cmd.batteryLevel
    map.descriptionText = "${device.displayName} battery is ${cmd.batteryLevel}%"
    logger("info", map.descriptionText)

  }

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
