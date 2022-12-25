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
  definition (name: "Eurotronic Air Quality Sensor", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Eurotronic/Eurotronic%20Air%20Quality%20Sensor.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "Temperature Measurement"
    capability "Relative Humidity Measurement"
    capability "Carbon Dioxide Measurement"
    capability "Refresh"
    capability "Polling"
    capability "Configuration"
    capability "Initialize"
    command "clearState"

    attribute "DewPoint", "number"
    attribute "VOC", "number"
    attribute "VOC-Level", "enum", ["Outstanding","Good","Mediocre","Bad","Harmful","Risk"]
    attribute "carbonDioxide-Level", "enum", ["Good","Mediocre","Bad","Harmful","Risk"]
    attribute "HomeHealth", "enum", ["Good","Mediocre","Harmful","Risk"]

    fingerprint mfr:"0148", prod:"0005"
    fingerprint deviceId: "0001", inClusters: "0x5E, 0x85, 0x70, 0x59, 0x55, 0x31, 0x71, 0x86, 0x72, 0x5A, 0x73, 0x98, 0x9F, 0x6C, 0x7A"
    fingerprint deviceId: "0001", inClusters: "0x5E, 0x6C, 0x55, 0x98, 0x9F", secureInClusters: "0x86, 0x85, 0x70, 0x59, 0x72, 0x31, 0x71, 0x5A, 0x73, 0x7A" // 700088
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "forcePollInterval", title: "Force Poll", description: "Force Poll interval", type: "enum", options:[[0:"Disabled"], [1:"1min"], [3:"3min"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 1, required: true
    }
    section { // Configuration
      input name: "param8", title: "Report Air quality", description: "Air quality indicati-on via LED", type: "enum", options:[[0:"No air quality indication via LED"], [1:"IIndicate measured air quality via LED"]], defaultValue: 1, required: true
      input name: "param3", title: "Report Temperature Unit", description: "Reporting unit", type: "enum", options:[[0:"Celsius"], [1:"Fahrenheit"]], defaultValue: 0, required: true
      input name: "param4", title: "Report Temperature Resolution", description: "Reporting resolution", type: "enum", options:[[0:"22°C"], [1:"22.3°C"], [2:"22.35°C"]], defaultValue: 2, required: true
      input name: "param5", title: "Report Humidity Resolution", description: "Reporting resolution", type: "enum", options:[[0:"33%"], [1:"33.4%"], [2:"33.45%"]], defaultValue: 2, required: true
      input name: "param1", title: "Selective reporting - Threshold Temperature", description: "°C", type: "enum", options:[[0:"Disabled (only time-based reports)"], [1:"0.1°C"], [2:"0.2°C"], [3:"0.3°C"], [4:"0.4°C"], [5:"0.5°C"], [10:"1°C"], [15:"1.5°C"], [20:"2°C"], [25:"2.5°C"], [30:"3°C"], [40:"4°C"], [50:"5°C"]], defaultValue: 5, required: true
      input name: "param2", title: "Selective reporting - Threshold Humidity", description: "%", type: "enum", options:[[0:"Disabled (only time-based reports)"], [1:"1%"], [2:"2%"], [3:"3%"], [4:"4%"], [5:"5%"], [6:"6%"], [7:"7%"], [8:"8%"], [9:"9%"], [10:"10%"]], defaultValue: 2, required: true
      input name: "param6", title: "Selective reporting - Threshold VOC", description: "ppb (parts per billion) / Volatile organic compound (VOC)", type: "enum", options:[[0:"Disabled (only time-based reports)"], [1:"100ppb"], [2:"200ppb"], [3:"300ppb"], [4:"400ppb"], [5:"500ppb"], [6:"600ppb"], [7:"700ppb"], [8:"800ppb"], [9:"900ppb"], [10:"1000ppb"]], defaultValue: 5, required: true
      input name: "param7", title: "Selective reporting - Threshold CO2", description: "ppm (parts per million) / Carbon dioxide (CO2)", type: "enum", options:[[0:"Disabled (only time-based reports)"], [1:"100ppm"], [3:"200ppm"], [3:"300ppm"], [4:"400ppm"], [5:"500ppm"], [6:"600ppm"], [7:"700ppm"], [8:"800ppm"], [9:"900ppm"], [10:"1000ppm"]], defaultValue: 5, required: true
    }
  }
}

def installed() {
  logger("debug", "installed(${VERSION})")

  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION]
  }

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  }

  initialize()
}

def initialize() {
  logger("debug", "initialize()")
  sendEvent(name: "carbonDioxide", value: "0", displayed: true)
  sendEvent(name: "carbonDioxide-Level", value: "Good", displayed: true)
  sendEvent(name: "VOC", value: "0.0", displayed: true)
  sendEvent(name: "VOC-Level", value: "Outstanding", displayed: true)
  sendEvent(name: "DewPoint", value: "0.0", displayed: true)
  sendEvent(name: "HomeHealth", value: "Good", displayed: true)
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
    zwave.versionV2.versionGet(),
    zwave.firmwareUpdateMdV3.firmwareMdGet(),
    zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
    zwave.powerlevelV1.powerlevelGet(),
    zwave.sensorMultilevelV11.sensorMultilevelGet(sensorType: 1, scale: (location.temperatureScale=="F"?1:0)), // Temperature
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 5), // Humidity
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 11), // Dewpoint
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 17), // carbonDioxide (CO2)
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 39) // VOC
  ], 100)
}


def poll() {
  logger("debug", "poll()")

  cmdSequence([
    zwave.notificationV8.notificationGet(event:6, notificationType:13, v1AlarmType:0),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: (location.temperatureScale=="F"?1:0)), // Temperature
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 5), // Humidity
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 11), // Dewpoint
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 17), // carbonDioxide (CO2)
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 39) // VOC
  ])
}


def configure() {
  logger("debug", "configure()")

  if (forcePollInterval.toInteger()) {
    if (['1', '3', '5', '10', '15', '30'].contains(forcePollInterval) ) {
      schedule("0 */${forcePollInterval} * ? * *", poll)
    } else {
      schedule("0 0 */${forcePollInterval} ? * *", poll)
    }
  }

  def cmds = []
  cmds = cmds + cmdSequence([
    zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
    zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: param1.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: param2.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: param3.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: param4.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: param5.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: param6.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, scaledConfigurationValue: param7.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: param8.toInteger())
  ], 300)

  if (!getDataValue("MSR")) {
    cmds = cmds + cmdSequence([
      zwave.versionV3.versionGet(),
      zwave.firmwareUpdateMdV5.firmwareMdGet(),
      zwave.manufacturerSpecificV2.manufacturerSpecificGet()
    ], 100)
  }
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

  updateDataValue("MSR", "")
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

  return result
}

def zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd) {
  logger("trace", "zwaveEvent(NotificationReport) - cmd: ${cmd.inspect()}")
  def result = []

  switch(cmd.notificationType) {
    case 13:
      if (cmd.event == 6) {
        if (cmd.eventParametersLength != 0) {
          switch (cmd.eventParameter[0]) {
            case 0x01:
              sendEvent(name:"HomeHealth", value:"Good", displayed:true)
            break;
            case 0x02:
              sendEvent(name:"HomeHealth", value:"Mediocre", descriptionText:"Slightly polluted", displayed:true)
            break;
            case 0x03:
              sendEvent(name:"HomeHealth", value:"Harmful", descriptionText:"Moderately polluted", displayed:true)
            break;
            case 0x04:
              sendEvent(name:"HomeHealth", value:"Risk", descriptionText:"Highly polluted", displayed:true)
            break;
          }
        }
      }
    break;
    default:
      logger("warn", "zwaveEvent(NotificationReport) - Unhandled notificationType - cmd: ${cmd.inspect()}")
    break;
  }

  return result
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv10.SensorMultilevelReport cmd) {
  logger("trace", "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}")
  def result = []
  Map map = [:]

  switch (cmd.sensorType) {
    case 1:
      map.name = "temperature"
      map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmd.scale == 1 ? "f" : "c", cmd.precision)
      map.unit = "°" + getTemperatureScale()
      map.descriptionText = "Temperature is ${map.value} ${map.unit}"
    break
    case 5:
      map.name = "humidity"
      map.value = cmd.scaledSensorValue
      map.unit = "%"
      map.descriptionText = "Humidity is ${map.value} ${map.unit}"
    break
    case 11:
      map.name = "DewPoint"
      map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmd.scale == 1 ? "f" : "c", cmd.precision)
      map.unit = "°" + getTemperatureScale()
      map.descriptionText = "DewPoint is ${map.value} ${map.unit}"
    break
    case 17:
      map.name = "carbonDioxide"
      map.value = cmd.scaledSensorValue
      map.unit = "ppm"
      map.descriptionText = "CO2 is ${map.value} ${map.unit}"

      if (map.value < 800) {
        result << createEvent(name: "carbonDioxide-Level", value: "Good", displayed: true)
      } else if (map.value < 1400) {
        result << createEvent(name: "carbonDioxide-Level", value: "Mediocre", displayed: true)
      } else if (map.value < 2000) {
        result << createEvent(name: "carbonDioxide-Level", value: "Bad", displayed: true)
      } else if (map.value < 5000) {
        result << createEvent(name: "carbonDioxide-Level", value: "Harmful", displayed: true)
      } else {
        result << createEvent(name: "carbonDioxide-Level", value: "Risk", displayed: true)
      }
    break
    case 39:
      map.name = "VOC"
      map.value = (cmd.scaledSensorValue)
      map.unit = "ppb"
      map.descriptionText = "VOC is ${map.value} ${map.unit}"

      if (map.value < 0.065) {
        result << createEvent(name: "VOC-Level", value: "Outstanding", displayed: true)
      } else if (map.value < 0.220) {
        result << createEvent(name: "VOC-Level", value: "Good", displayed: true)
      } else if (map.value < 0.660) {
        result << createEvent(name: "VOC-Level", value: "Mediocre", displayed: true)
      } else if (map.value < 2.200) {
        result << createEvent(name: "VOC-Level", value: "Bad", displayed: true)
      } else if (map.value < 5.500) {
        result << createEvent(name: "VOC-Level", value: "Harmful", displayed: true)
      } else {
        result << createEvent(name: "VOC-Level", value: "Risk", displayed: true)
      }
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

def zwaveEvent(hubitat.zwave.commands.powerlevelv1.PowerlevelReport cmd) {
  logger("trace", "zwaveEvent(PowerlevelReport) - cmd: ${cmd.inspect()}")

  String power = (cmd.powerLevel > 0) ? "minus${cmd.powerLevel}dBm" : "NormalPower"
  logger("debug", "Powerlevel Report: Power: ${power}, Timeout: ${cmd.timeout}")
  []
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  logger("trace", "zwaveEvent(DeviceResetLocallyNotification) - cmd: ${cmd.inspect()}")
  logger("warn", "Has reset itself")
  []
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

void zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv3.FirmwareUpdateMdRequestReport cmd) {
  logger("trace", "zwaveEvent(FirmwareUpdateMdRequestReport) - cmd: ${cmd.inspect()}")
}
void zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareUpdateMdGet cmd) {
  logger("trace", "zwaveEvent(FirmwareUpdateMdGet) - cmd: ${cmd.inspect()}")
}

void zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareUpdateMdStatusReport cmd) {
  logger("trace", "zwaveEvent(FirmwareUpdateMdStatusReport) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) {
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

def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv3.FirmwareMdReport cmd) {
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
  logger("warn", "zwaveEvent(MultiChannelCmdEncap) - cmd: ${cmd.inspect()}")

  if (cmd.commandClass == 0x6C && cmd.parameter.size >= 4) { // Supervision encapsulated Message
    // Supervision header is 4 bytes long, two bytes dropped here are the latter two bytes of the supervision header
    cmd.parameter = cmd.parameter.drop(2)
    // Updated Command Class/Command now with the remaining bytes
    cmd.commandClass = cmd.parameter[0]
    cmd.command = cmd.parameter[1]
    cmd.parameter = cmd.parameter.drop(2)
    logger("warn", "zwaveEvent(MultiChannelCmdEncap) - cmd (0x6C): ${cmd.inspect()}")
	}

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

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd){
  logger("trace", "zwaveEvent(SupervisionGet) - cmd: ${cmd.inspect()}")

  hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
  if (encapCmd) {
    zwaveEvent(encapCmd)
  }
  sendHubCommand(new hubitat.device.HubAction(secure(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)), hubitat.device.Protocol.ZWAVE))
}

@Field static Map<String, Map<Short, String>> supervisedPackets = [:]
@Field static Map<String, Short> sessionIDs = [:]

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd) {
  logger("trace", "zwaveEvent(SupervisionReport) - cmd: ${cmd.inspect()}")
  logger("debug", "Supervision report for session: ${cmd.sessionID}")

  if (!supervisedPackets."${device.id}") { supervisedPackets."${device.id}" = [:] }
  if (supervisedPackets["${device.id}"][cmd.sessionID] != null) { supervisedPackets["${device.id}"].remove(cmd.sessionID) }
  unschedule(supervisionCheck)
}

void supervisionCheck() {
  // re-attempt once
  if (!supervisedPackets."${device.id}") { supervisedPackets."${device.id}" = [:] }
  supervisedPackets["${device.id}"].each { k, v ->
    logger("debug", "Supervision re-sending supervised session: ${k}")
    sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(v), hubitat.device.Protocol.ZWAVE))
    supervisedPackets["${device.id}"].remove(k)
  }
}

Short getSessionId() {
  Short sessId = 1
  if (!sessionIDs["${device.id}"]) {
    sessionIDs["${device.id}"] = sessId
    return sessId
  } else {
    sessId = sessId + sessionIDs["${device.id}"]
    if (sessId > 63) { sessId = 1 }
    sessionIDs["${device.id}"] = sessId
    return sessId
  }
}

hubitat.zwave.Command supervisedEncap(hubitat.zwave.Command cmd) {
    if (getDataValue("S2")?.toInteger() != null) {
        hubitat.zwave.commands.supervisionv1.SupervisionGet supervised = new hubitat.zwave.commands.supervisionv1.SupervisionGet()
        supervised.sessionID = getSessionId()
        if (logEnable) log.debug "new supervised packet for session: ${supervised.sessionID}"
        supervised.encapsulate(cmd)
        if (!supervisedPackets."${device.id}") { supervisedPackets."${device.id}" = [:] }
        supervisedPackets["${device.id}"][supervised.sessionID] = supervised.format()
        runIn(5, supervisionCheck)
        return supervised
    } else {
        return cmd
    }
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

private cmdSequence(Collection commands, Integer delayBetweenArgs=250) {
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
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x70: 1, // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
          0x59: 1, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x55: 2, // COMMAND_CLASS_TRANSPORT_SERVICE
          0x31: 10, // COMMAND_CLASS_SENSOR_MULTILEVEL
          0x71: 8, // COMMAND_CLASS_NOTIFICATION (Secure)
          0x86: 2, // COMMAND_CLASS_VERSION (Insecure)
          0x72: 1, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x5A: 1, // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
          0x98: 2, // COMMAND_CLASS_SECURITY (Secure)
          0x9F: 2, // COMMAND_CLASS_SECURITY_2 (Secure
          0x6C: 1, // COMMAND_CLASS_SUPERVISION
          0x7A: 3, // COMMAND_CLASS_FIRMWARE_UPDATE_MD (Insecure)
          0x73: 1 // COMMAND_CLASS_POWERLEVEL (Insecure)
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
