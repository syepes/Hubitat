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

@Field String VERSION = "1.1.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Heatit Z-Temp2", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Heatit/Heatit%20Z-Temp2.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "Thermostat Operating State"
    capability "ThermostatSetpoint"
    capability "ThermostatHeatingSetpoint"
    capability "Temperature Measurement"
    capability "Relative Humidity Measurement"
    capability "Battery"
    capability "Refresh"
    capability "Configuration"
    capability "Initialize"

    command "clearState"
    command "childLock"
    command "childUnLock"
    command "setThermostatMode", [[name:"thermostatMode",type:"ENUM", description:"ThermostatMode", constraints: ["off","auto"]]]
    attribute "childLock", "enum", ["true","false"]
    attribute "thermostatMode", "enum", ["off","auto"]
    attribute "thermostatMode", "enum", ["off","auto"]

    fingerprint mfr:"019B", prod:"0004"
    fingerprint deviceId: "0204", inClusters: "0x5E, 0x55, 0x98, 0x9F, 0x6C, 0x22", secureInClusters: "0x86, 0x85, 0x8E, 0x59, 0x72, 0x5A, 0x87, 0x73, 0x80, 0x71, 0x7A, 0x40, 0x43, 0x42, 0x75, 0x70, 0x31" // 45 12 666 EU 868,4 MHz
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "batteryCheckInterval", title: "Battery Check", description: "Check interval of the battery state", type: "enum", options:[[0:"Disabled"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 12, required: true
    }
    section { // Configuration
      input name: "param1", title: "Temperature/Humidity report interval", description: "", type: "enum", options:[[30:"30s"], [60:"1m"], [120:"2m"], [180:"3m"], [240:"4m"], [300:"5m"],[600:"10m"], [900:"15m"], [1800:"30m"], [3600:"1h"], [7200:"2h"], [10800:"3h"], [14400:"4h"], [21600:"6h"], [28800:"8h"], [32400:"9h"]], defaultValue: 300, required: true
      input name: "param2", title: "Temperature delta value", description: "", type: "enum", options:[[5:"0.5°C"], [10:"1°C"], [20:"2°C"], [30:"3°C"], [40:"4°C"], [50:"5°C"]], defaultValue: 10, required: true
      input name: "param3", title: "Humidity delta value", description: "", type: "enum", options:[[5:"5%"], [10:"10%"], [20:"20%"], [30:"30%"], [40:"40%"], [50:"50%"]], defaultValue: 10, required: true
      input name: "param4", title: "Temperature offset", description: "Set value is added or subtracted to actual measured value by sensor<br/>-100 to 100°C<br/>65 535 – desired value + 1<br/>", type: "number", range: "-65535..100", defaultValue: 0, required: true
      input name: "param5", title: "Humidity offset", description: "Set value is added or subtracted to actual measured value by sensor<br/>-10 to 10%<br/>65 535 – desired value + 1<br/>", type: "number", range: "-65535..100", defaultValue: 0, required: true
      input name: "param7", title: "Proximity sensor", description: "", type: "enum", options:[[0:"Disable"], [1:"Enable"]], defaultValue: 1, required: true
      input name: "param6", title: "Proximity sensor retrigger interval", description: "", type: "enum", options:[[2:"2s"], [3:"3s"], [4:"4s"], [5:"5s"], [6:"6s"], [10:"10s"], [15:"15s"], [30:"30s"], [60:"1m"], [120:"2m"], [180:"3m"], [240:"4m"]], defaultValue: 10, required: true
      input name: "param8", title: "LED Brightness", description: "Adjust the backlight of LCD display", type: "enum", options:[[0:"0%"], [1:"1%"], [2:"2%"], [3:"3%"], [4:"4%"], [5:"5%"],[10:"10%"], [20:"20%"], [30:"30%"], [40:"40%"], [50:"50%"], [60:"60%"], [70:"70%"], [80:"80%"], [90:"90%"], [99:"100%"]], defaultValue: 50, required: true
      input name: "param9", title: "LED turned on before timeout", description: "Adjust the time from proximity sensor / display touched until display goes to sleep", type: "enum", options:[[3:"3s"], [4:"4s"], [5:"5s"], [6:"6s"], [10:"10s"], [15:"15s"], [30:"30s"], [60:"1m"], [120:"2m"], [180:"3m"], [240:"4m"], [300:"5m"]], defaultValue: 3, required: true
      input name: "param10", title: "Temperature Control Hysteresis", description: "Adjust the delta values for the thermostat to turn on off heating", type: "enum", options:[[3:"0.3°C"], [4:"0.4°C"], [5:"0.5°C"], [6:"0.6°C"], [7:"0.7°C"], [8:"0.8°C"], [9:"0.9°C"], [10:"1°C"], [15:"1.5°C"], [20:"2°C"], [30:"3°C"]], defaultValue: 5, required: true
      input name: "param11", title: "Temperature Limit Minimum", description: "Set the mimum temperature limit", type: "enum", options:[[50:"5°C"], [60:"6°C"], [70:"7°C"], [80:"8°C"], [90:"9°C"], [100:"10°C"], [150:"15°C"], [200:"20°C"], [210:"21°C"], [220:"22°C"], [230:"23°C"], [240:"24°C"], [250:"25°C"], [300:"30°C"], [310:"31°C"], [320:"32°C"], [330:"33°C"], [340:"34°C"], [350:"35°C"], [360:"36°C"], [370:"37°C"], [380:"38°C"], [390:"39°C"], [400:"40°C"]], defaultValue: 5, required: true
      input name: "param12", title: "Temperature Limit Maximum", description: "Set the maximum temperature limit", type: "enum", options:[[50:"5°C"], [60:"6°C"], [70:"7°C"], [80:"8°C"], [90:"9°C"], [100:"10°C"], [150:"15°C"], [200:"20°C"], [210:"21°C"], [220:"22°C"], [230:"23°C"], [240:"24°C"], [250:"25°C"], [300:"30°C"], [310:"31°C"], [320:"32°C"], [330:"33°C"], [340:"34°C"], [350:"35°C"], [360:"36°C"], [370:"37°C"], [380:"38°C"], [390:"39°C"], [400:"40°C"]], defaultValue: 400, required: true
      input name: "param13", title: "External Relay & Operating State update interval", description: "Set time on how often the devices sends Binary Switch Set and thermostat mode to gateway", type: "enum", options:[[0:"Sends only when changed"], [1:"1m"], [2:"2m"], [3:"3m"], [4:"4m"], [5:"5m"],[10:"10m"], [15:"15m"], [30:"30m"], [60:"1h"], [120:"2h"], [180:"3h"], [240:"4h"]], defaultValue: 0, required: true
      input name: "param14", title: "Report when presence is detected", description: "Decides if the thermostat sends temperature when presence is detected", type: "enum", options:[[0:"Disable (Do not report when presence is detected, only at interval)"], [1:"Enable (Send report when presence is detected)"]], defaultValue: 0, required: true
    }
  }
}


def setThermostatMode(mode="auto") {
  // Validate modes
  Integer mode_value = null
  Map mode_map = [0:"off", 1:"auto"]
  mode_map.each { it->
    if (it.value == mode) { mode_value = it.key }
  }

  if (mode_value == null) {
    logger("error", "Mode (${mode}) is incorrect")
  } else {
    logger("info", "Mode (${mode}) value = ${mode_value}")

    cmdSequence([
      zwave.thermostatModeV2.thermostatModeSet(mode: mode_value),
      zwave.thermostatModeV2.thermostatModeGet()
    ], 300)
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
  def cmds = []
  cmds = cmds + cmdSequence([
    zwave.versionV3.versionGet(),
    zwave.firmwareUpdateMdV5.firmwareMdGet(),
    zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
    zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: (location.temperatureScale=="F"?1:0)),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 5),
    zwave.thermostatModeV2.thermostatModeGet(),
    zwave.thermostatOperatingStateV1.thermostatOperatingStateGet(),
    zwave.protectionV1.protectionGet(),
    zwave.batteryV1.batteryGet()
  ], 100)
}

def configure() {
  logger("debug", "configure()")
  schedule("0 0 12 */7 * ?", updateCheck)

  if (batteryCheckInterval.toInteger()){
    if (['5', '10', '15', '30'].contains(batteryCheckInterval) ) {
      schedule("0 */${batteryCheckInterval} * ? * *", checkBattery)
    } else {
      schedule("0 0 */${batteryCheckInterval} ? * *", checkBattery)
    }
  }

  def cmds = []
  cmds = cmds + cmdSequence([
    zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
    zwave.configurationV1.configurationSet(parameterNumber: 1, size: 2, scaledConfigurationValue: param1.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 2, size: 2, scaledConfigurationValue: param2.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 3, size: 2, scaledConfigurationValue: param3.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: param4.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 5, size: 2, scaledConfigurationValue: param5.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 6, size: 2, scaledConfigurationValue: param6.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 7, size: 2, scaledConfigurationValue: param7.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 8, size: 2, scaledConfigurationValue: param8.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 9, size: 2, scaledConfigurationValue: param9.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 10, size: 2, scaledConfigurationValue: param10.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 11, size: 2, scaledConfigurationValue: param11.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 12, size: 2, scaledConfigurationValue: param12.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 13, size: 2, scaledConfigurationValue: param13.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 14, size: 2, scaledConfigurationValue: param14.toInteger())
  ], 300)

  if (!getDataValue("MSR")) {
    cmds = cmds + cmdSequence([
      zwave.versionV3.versionGet(),
      zwave.firmwareUpdateMdV5.firmwareMdGet(),
      zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
    ], 100)
  }
}

def clearState() {
  logger("debug", "ClearState() - Clearing device states")
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

  updateDataValue("MSR", "")
  installed()
}

def checkBattery() {
  logger("debug", "checkBattery()")
  cmd(zwave.batteryV1.batteryGet())
}

def childLock() {
  logger("debug", "childLock()")
  cmdSequence([
    zwave.protectionV1.protectionSet(protectionState: 1),
    zwave.protectionV1.protectionGet()
  ], 300)
}

def childUnLock() {
  logger("debug", "childUnLock()")
  cmdSequence([
    zwave.protectionV1.protectionSet(protectionState: 0),
    zwave.protectionV1.protectionGet()
  ], 300)
}


def setHeatingSetpoint(Double degrees) {
  logger("debug", "setHeatingSetpoint() - degrees: ${degrees}")
  cmdSequence([
    zwave.thermostatSetpointV2.thermostatSetpointSet(setpointType: hubitat.zwave.commands.thermostatsetpointv1.ThermostatSetpointSet.SETPOINT_TYPE_HEATING_1, scale: (location.temperatureScale=="F"?1:0), precision: 0, scaledValue: degrees),
    zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: hubitat.zwave.commands.thermostatsetpointv1.ThermostatSetpointSet.SETPOINT_TYPE_HEATING_1)
  ], 300)
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

def zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorReport cmd) {
  logger("trace", "zwaveEvent(IndicatorReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.protectionv1.ProtectionReport cmd) {
  logger("trace", "zwaveEvent(ProtectionReport) - cmd: ${cmd.inspect()}")
  def result = []
  Map map = [displayed: true]

  switch (cmd?.protectionState) {
    case "0":
      map.name = "childLock"
      map.value = false
      map.descriptionText = "The touch buttons are UnLocked"
    break
    case "1":
      map.name = "childLock"
      map.value = true
      map.descriptionText = "The touch buttons are Locked"
    break
    default:
      logger("warn", "zwaveEvent(ProtectionReport) - Unknown value: ${cmd?.protectionState}")
    break;
  }

  if(logDescText && map?.descriptionText) {
    log.info "${device.displayName} ${map.descriptionText}"
  } else if(map?.descriptionText) {
    logger("info", "${map.descriptionText}")
  }

  sendEvent(map)
  result
}

def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) {
  logger("trace", "zwaveEvent(ThermostatSetpointReport) - cmd: ${cmd.inspect()}")

  switch (cmd.setpointType) {
    case 1:
      sendEvent(name: "thermostatSetpoint", value: cmd.scaledValue, unit: cmd.scale ? "F" : "C")
      sendEvent(name: "heatingSetpoint", value: cmd.scaledValue, unit: cmd.scale ? "F" : "C")
      if(logDescText) {
        log.info "${device.displayName} Thermostat Setpoint: ${cmd.scaledValue} \u00b0${getTemperatureScale()}"
      } else {
        logger("info", "Thermostat Setpoint: ${cmd.scaledValue} \u00b0${getTemperatureScale()}")
      }
    break;
    default:
      logger("warn", "Unknown setpointType ${cmd.setpointType}")
  }
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

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  logger("trace", "zwaveEvent(ConfigurationReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  logger("trace", "zwaveEvent(DeviceResetLocallyNotification) - cmd: ${cmd.inspect()}")
  logger("warn", "Has reset itself")
  []
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
      map.displayed = true
    break
    case 5:
      map.name = "humidity"
      map.value = cmd.scaledSensorValue
      map.unit = "%"
      map.descriptionText = "Humidity is ${map.value} ${map.unit}"
      map.displayed = true
    break
    default:
      logger("warn", "zwaveEvent(SensorMultilevelReport) - Unknown sensorType - cmd: ${cmd.inspect()}")
    break;
  }

  if(logDescText && map?.descriptionText) {
    log.info "${device.displayName} ${map.descriptionText}"
  } else if(map?.descriptionText) {
    logger("info", "${map.descriptionText}")
  }
  result << createEvent(map)
  result
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
  logger("trace", "zwaveEvent(NotificationReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.notificationType == 8) {
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

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
  logger("trace", "zwaveEvent(ThermostatModeReport) - cmd: ${cmd.inspect()}")
  def result = []
  Map map = [:]

  switch (cmd?.mode) {
    case 0:
      map.name = "thermostatMode"
      map.value = "off"
      map.descriptionText = "Thermostat regulation is deactivated"
      map.displayed = true
    break
    case 1:
      map.name = "thermostatMode"
      map.value = "auto"
      map.descriptionText = "Thermostat regulation is active"
      map.displayed = true
    break
    default:
      logger("warn", "zwaveEvent(ThermostatModeReport) - Unknown mode - cmd: ${cmd.inspect()}")
    break;
  }

  if(logDescText && map?.descriptionText) {
    log.info "${device.displayName} ${map.descriptionText}"
  } else if(map?.descriptionText) {
    logger("info", "${map.descriptionText}")
  }
  result << createEvent(map)
  result
}


def zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd) {
  logger("trace", "zwaveEvent(ThermostatOperatingStateReport) - cmd: ${cmd.inspect()}")

  def map = [name: "thermostatOperatingState"]
  switch (cmd.operatingState) {
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_IDLE:
      map.value = "idle"
    break
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_HEATING:
      map.value = "heating"
    break
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_COOLING:
      map.value = "cooling"
    break
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_FAN_ONLY:
      map.value = "fan only"
    break
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_HEAT:
      map.value = "pending heat"
    break
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_COOL:
      map.value = "pending cool"
    break
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_VENT_ECONOMIZER:
      map.value = "vent economizer"
    break
  }

  logger("info", "is ${map.value}")
  sendEvent(map)

  []
}


def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logger("trace", "zwaveEvent(BatteryReport) - cmd: ${cmd.inspect()}")
  Map map = [ name: "battery", unit: "%" ]

  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "Has a low battery"
    map.isStateChange = true
    logger("warn", "${map.descriptionText}")
  } else {
    map.value = cmd.batteryLevel
    map.descriptionText = "Battery is ${cmd.batteryLevel} ${map.unit}"
    logger("info", "${map.descriptionText}")
  }

  state.deviceInfo.lastbatt = now()
  createEvent(map)
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

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
  logger("trace", "zwaveEvent(VersionCommandClassReport) - cmd: ${cmd.inspect()}")

  state.deviceInfo['commandClassVersion'] = "${cmd.commandClassVersion}"
  state.deviceInfo['requestedCommandClass'] = "${cmd.requestedCommandClass}"
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
  return [0x5E: 2, // COMMAND_CLASS_ZWAVEPLUS_INFO (Insecure)
          0x55: 2, // COMMAND_CLASS_TRANSPORT_SERVICE
          0x98: 1, // COMMAND_CLASS_SECURITY (Secure)
          0x9F: 1, // COMMAND_CLASS_SECURITY_2 (Secure)
          0x6C: 1, // COMMAND_CLASS_SUPERVISION
          0x22: 1, // COMMAND_CLASS_APPLICATION_STATUS
          0x86: 3, // COMMAND_CLASS_VERSION (Insecure)
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x8E: 2, // COMMAND_CLASS_MULTI_CHANNEL_ASSOCIATION (Secure)
          0x59: 3, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x5A: 1, // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
          0x87: 3, // COMMAND_CLASS_INDICATOR
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
          0x80: 1, // COMMAND_CLASS_BATTERY
          0x71: 8, // COMMAND_CLASS_NOTIFICATION (Secure)
          0x7A: 5, // COMMAND_CLASS_FIRMWARE_UPDATE_MD (Insecure)
          0x40: 3, // COMMAND_CLASS_THERMOSTAT_MODE (x)
          0x43: 3, // COMMAND_CLASS_THERMOSTAT_SETPOINT
          0x42: 1, // COMMAND_CLASS_THERMOSTAT_OPERATING_STATE
          0x75: 1, // COMMAND_CLASS_PROTECTION
          0x70: 4, // COMMAND_CLASS_CONFIGURATION (Secure)
          0x31: 5 // COMMAND_CLASS_SENSOR_MULTILEVEL
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
      log."${level}" "${device.displayName} ${msg}"
    }
  }
}

def updateCheck() {
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Heatit/Heatit%20Z-Temp2.groovy"]
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
