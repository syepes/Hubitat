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

@Field String VERSION = "1.0.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Heltun Touch Panel Switch - Quinto", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Heltun/Heltun%20Touch%20Panel%20Switch%20-%20Quinto.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "Temperature Measurement"
    capability "Relative Humidity Measurement"
    capability "Illuminance Measurement"
    capability "Power Meter"
    capability "Energy Meter"
    capability "Refresh"
    capability "Polling"
    capability "Configuration"
    capability "Initialize"

    command "clearState"
    command "reset"
    command "runCode"

    attribute "numberOfButtons", "number"
    attribute "lastPressed", "number"

    fingerprint mfr:"0344", prod:"0004"
    fingerprint deviceId: "0003", inClusters: "0x5E, 0x55, 0x98, 0x9F, 0x6C, 0x22", secureInClusters: "0x85, 0x59, 0x8E, 0x60, 0x86, 0x72, 0x5A, 0x73, 0x81, 0x87, 0x70, 0x31, 0x25, 0x5B, 0x32, 0x7A" // HE-TPS05
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 2, required: true
    }
    section { // Configuration
      input name: "param6", title: "Touch sensitivity", description: "", type: "enum", options:[[1:"1 (Lowest)"], [2:2], [3:3], [4:4], [5:5], [6:6], [7:7], [8:8], [9:9], [10:"10 (Highest)"]], defaultValue: 6, required: true
      input name: "param5", title: "Backlight brightness", description: "", type: "enum", options:[[0:"Automatic"], [1:"1 (Lowest)"], [2:2], [3:3], [4:4], [5:5], [6:6], [7:7], [8:8], [9:9], [10:"10 (Highest)"]], defaultValue: 0, required: true
      input name: "param30", title: "Backlight color", description: "Change the backlight active/inactive state color", type: "enum", options:[[0:"active = red / inactive = blue"], [1:"active = blue / inactive = red"]], defaultValue: 1, required: true

      input name: "param17", title: "Temperature Calibration", description: "°C", type: "enum", options:[["-50":"-5°C"], ["-40":"-4°C"], ["-30":"-3°C"], ["-20":"-2°C"], ["-10":"-1°C"], [0:"0°C"], [5:"0.5°C"], [10:"1°C"], [15:"1.5°C"], [20:"2°C"], [25:"2.5°C"], [30:"3°C"], [40:"4°C"], [50:"5°C"]], defaultValue: 0, required: true
      input name: "param18", title: "Time Correction", description: "Time and Day will be periodically polled and corrected from the gateway<br/>", type: "enum", options:[[0:"Disable"], [1:"Enable"]], defaultValue: 1, required: true
      input name: "param21", title: "Time Correction - Week Day", description: "Allows manual adjustment of the day of the week", type: "enum", options:[[1:"Monday"], [2:"Tuesday"], [3:"Wednesday"], [4:"Thursday"], [5:"Friday"], [6:"Saturday"], [7:"Sunday"]], defaultValue: 1, required: true
      input name: "param22", title: "Time Correction - Time", description: "Allows manual adjustment of Hour and Minutes following the format: HHMM", type: "number", range: "0..2359", defaultValue: 0, required: true

      input name: "param141", title: "Report Consumption Interval", description: "Energy Consumption Meter Consecutive Report Interval", type: "enum", options:[[1:"1m"], [2:"2m"], [3:"3m"], [4:"4m"], [5:"5m"],[10:"10m"], [15:"15m"], [30:"30m"], [60:"1h"], [120:"2h"]], defaultValue: 10, required: true
      input name: "param142", title: "Report Consumption on Change", description: "Control Energy Consumption Report", type: "enum", options:[[0:"Disabled"], [1:"Enabled"]], defaultValue: 1, required: true
      input name: "param143", title: "Report Sensors Interval", description: "Temperature, Humidity and Light sensors", type: "enum", options:[[1:"1m"], [2:"2m"], [3:"3m"], [4:"4m"], [5:"5m"],[10:"10m"], [15:"15m"], [30:"30m"], [60:"1h"], [120:"2h"]], defaultValue: 10, required: true
      input name: "param144", title: "Report Threshold Temperature", description: "Temperature change to report", type: "enum", options:[[0:"Disabled"], [1:"0.1°C"], [2:"0.2°C"], [3:"0.3°C"], [4:"0.4°C"], [5:"0.5°C"], [6:"0.6°C"], [7:"0.7°C"], [8:"0.8°C"], [9:"0.9°C"], [10:"1°C"], [20:"2°C"], [30:"3°C"], [40:"4°C"], [50:"5°C"], [60:"6°C"], [70:"7°C"], [80:"8°C"], [90:"9°C"], [100:"10°C"]], defaultValue: 2, required: true
      input name: "param145", title: "Report Threshold Humidity", description: "Humidity change to report", type: "enum", options:[[0:"Disabled"], [1:"1%"], [2:"2%"], [3:"3%"], [4:"4%"], [5:"5%"], [6:"6%"], [7:"8%"], [8:"8%"], [9:"9%"], [10:"10%"], [15:"15%"], [20:"20%"], [25:"25%"]], defaultValue: 2, required: true
      input name: "param146", title: "Report Threshold Light", description: "Light sensor change to report", type: "enum", options:[[0:"Disabled"], [10:"10%"], [15:"15%"], [20:"20%"], [25:"25%"], [30:"30%"], [35:"35%"], [40:"40%"], [45:"45%"], [50:"50%"], [55:"55%"], [60:"60%"], [65:"65%"], [70:"70%"], [75:"75%"], [80:"80%"], [85:"85%"], [90:"90%"], [95:"95%"]], defaultValue: 50, required: true

      input name: "param7", title: "Button 1 Relay mode", description: "Output NO (normal open) or NC (normal close)", type: "enum", options:[[0:"NO"], [1:"NC"]], defaultValue: 0, required: true
      input name: "param12", title: "Button 1 Relay power load", description: "Watt", type: "number", range: "0..1100", defaultValue: 0, required: true
      input name: "param31", title: "Button 1 Backlight control source", description: "", type: "enum", options:[[0:"Disabled (both color LEDs are turned off)"], [1:"Controlled by touch (reflects the button state)"], [2:"Controlled by gateway or associated device (the button state is ignored)"]], defaultValue: 1, required: true
      input name: "param41", title: "Button 1 Hold mode", description: "Buttons Hold (Long Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Operate like click"], [2:"Momentary"], [3:"Momentary Reversed"], [4:"Momentary Toggle"]], defaultValue: 2, required: true
      input name: "param51", title: "Button 1 Click mode", description: "Buttons Hold (Short Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Relay inverts based on relay state"], [2:"Relay inverts based on button backlight state"], [3:"Relay switches to ON"], [4:"Relay switches to OFF"], [5:"Timer On>Off"], [6:"Timer Off>On"]], defaultValue: 1, required: true
      input name: "param61", title: "Button 1 Relay control source", description: "Button Number for Relays Output Control", type: "enum", options:[[0:"Controlled by gateway or associated device"], [1:"N1 (Top Left)"], [2:"N2 (Top Right)"], [3:"N3 (Bottom Left)"], [4:"N4 (Bottom Right)"], [5:"N5 (Center)"]], defaultValue: 1, required: true
      input name: "param71", title: "Button 1 Timer duration", description: "Relay Timer mode duration in seconds", type: "number", range: "0..43200", defaultValue: 0, required: true

      input name: "param8", title: "Button 2 Relay mode", description: "Output NO (normal open) or NC (normal close)", type: "enum", options:[[0:"NO"], [1:"NC"]], defaultValue: 0, required: true
      input name: "param13", title: "Button 2 Relay power load", description: "Watt", type: "number", range: "0..1100", defaultValue: 0, required: true
      input name: "param32", title: "Button 2 Backlight control source", description: "", type: "enum", options:[[0:"Disabled (both color LEDs are turned off)"], [1:"Controlled by touch (reflects the button state)"], [2:"Controlled by gateway or associated device (the button state is ignored)"]], defaultValue: 1, required: true
      input name: "param42", title: "Button 2 Hold mode", description: "Buttons Hold (Long Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Operate like click"], [2:"Momentary"], [3:"Momentary Reversed"], [4:"Momentary Toggle"]], defaultValue: 2, required: true
      input name: "param52", title: "Button 2 Click mode", description: "Buttons Hold (Short Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Relay inverts based on relay state"], [2:"Relay inverts based on button backlight state"], [3:"Relay switches to ON"], [4:"Relay switches to OFF"], [5:"Timer On>Off"], [6:"Timer Off>On"]], defaultValue: 1, required: true
      input name: "param62", title: "Button 2 Relay control source", description: "Button Number for Relays Output Control", type: "enum", options:[[0:"Controlled by gateway or associated device"], [1:"N1 (Top Left)"], [2:"N2 (Top Right)"], [3:"N3 (Bottom Left)"], [4:"N4 (Bottom Right)"], [5:"N5 (Center)"]], defaultValue: 2, required: true
      input name: "param72", title: "Button 2 Timer duration", description: "Relay Timer mode duration in seconds", type: "number", range: "0..43200", defaultValue: 0, required: true

      input name: "param9", title: "Button 3 Relay mode", description: "Output NO (normal open) or NC (normal close)", type: "enum", options:[[0:"NO"], [1:"NC"]], defaultValue: 0, required: true
      input name: "param14", title: "Button 3 Relay power load", description: "Watt", type: "number", range: "0..1100", defaultValue: 0, required: true
      input name: "param33", title: "Button 3 Backlight control source", description: "", type: "enum", options:[[0:"Disabled (both color LEDs are turned off)"], [1:"Controlled by touch (reflects the button state)"], [2:"Controlled by gateway or associated device (the button state is ignored)"]], defaultValue: 1, required: true
      input name: "param43", title: "Button 3 Hold mode", description: "Buttons Hold (Long Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Operate like click"], [2:"Momentary"], [3:"Momentary Reversed"], [4:"Momentary Toggle"]], defaultValue: 2, required: true
      input name: "param53", title: "Button 3 Click mode", description: "Buttons Hold (Short Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Relay inverts based on relay state"], [2:"Relay inverts based on button backlight state"], [3:"Relay switches to ON"], [4:"Relay switches to OFF"], [5:"Timer On>Off"], [6:"Timer Off>On"]], defaultValue: 1, required: true
      input name: "param63", title: "Button 3 Relay control source", description: "Button Number for Relays Output Control", type: "enum", options:[[0:"Controlled by gateway or associated device"], [1:"N1 (Top Left)"], [2:"N2 (Top Right)"], [3:"N3 (Bottom Left)"], [4:"N4 (Bottom Right)"], [5:"N5 (Center)"]], defaultValue: 3, required: true
      input name: "param73", title: "Button 3 Timer duration", description: "Relay Timer mode duration in seconds", type: "number", range: "0..43200", defaultValue: 0, required: true

      input name: "param10", title: "Button 4 Relay mode", description: "Output NO (normal open) or NC (normal close)", type: "enum", options:[[0:"NO"], [1:"NC"]], defaultValue: 0, required: true
      input name: "param15", title: "Button 4 Relay power load", description: "Watt", type: "number", range: "0..1100", defaultValue: 0, required: true
      input name: "param34", title: "Button 4 Backlight control source", description: "", type: "enum", options:[[0:"Disabled (both color LEDs are turned off)"], [1:"Controlled by touch (reflects the button state)"], [2:"Controlled by gateway or associated device (the button state is ignored)"]], defaultValue: 1, required: true
      input name: "param44", title: "Button 4 Hold mode", description: "Buttons Hold (Long Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Operate like click"], [2:"Momentary"], [3:"Momentary Reversed"], [4:"Momentary Toggle"]], defaultValue: 2, required: true
      input name: "param54", title: "Button 4 Click mode", description: "Buttons Hold (Short Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Relay inverts based on relay state"], [2:"Relay inverts based on button backlight state"], [3:"Relay switches to ON"], [4:"Relay switches to OFF"], [5:"Timer On>Off"], [6:"Timer Off>On"]], defaultValue: 1, required: true
      input name: "param64", title: "Button 4 Relay control source", description: "Button Number for Relays Output Control", type: "enum", options:[[0:"Controlled by gateway or associated device"], [1:"N1 (Top Left)"], [2:"N2 (Top Right)"], [3:"N3 (Bottom Left)"], [4:"N4 (Bottom Right)"], [5:"N5 (Center)"]], defaultValue: 4, required: true
      input name: "param74", title: "Button 4 Timer duration", description: "Relay Timer mode duration in seconds", type: "number", range: "0..43200", defaultValue: 0, required: true

      input name: "param11", title: "Button 5 Relay mode", description: "Output NO (normal open) or NC (normal close)", type: "enum", options:[[0:"NO"], [1:"NC"]], defaultValue: 0, required: true
      input name: "param16", title: "Button 5 Relay power load", description: "Watt", type: "number", range: "0..1100", defaultValue: 0, required: true
      input name: "param35", title: "Button 5 Backlight control source", description: "", type: "enum", options:[[0:"Disabled (both color LEDs are turned off)"], [1:"Controlled by touch (reflects the button state)"], [2:"Controlled by gateway or associated device (the button state is ignored)"]], defaultValue: 1, required: true
      input name: "param45", title: "Button 5 Hold mode", description: "Buttons Hold (Long Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Operate like click"], [2:"Momentary"], [3:"Momentary Reversed"], [4:"Momentary Toggle"]], defaultValue: 2, required: true
      input name: "param55", title: "Button 5 Click mode", description: "Buttons Hold (Short Press) Control Mode", type: "enum", options:[[0:"Disabled"], [1:"Relay inverts based on relay state"], [2:"Relay inverts based on button backlight state"], [3:"Relay switches to ON"], [4:"Relay switches to OFF"], [5:"Timer On>Off"], [6:"Timer Off>On"]], defaultValue: 1, required: true
      input name: "param65", title: "Button 5 Relay control source", description: "Button Number for Relays Output Control", type: "enum", options:[[0:"Controlled by gateway or associated device"], [1:"N1 (Top Left)"], [2:"N2 (Top Right)"], [3:"N3 (Bottom Left)"], [4:"N4 (Bottom Right)"], [5:"N5 (Center)"]], defaultValue: 5, required: true
      input name: "param75", title: "Button 5 Timer duration", description: "Relay Timer mode duration in seconds", type: "number", range: "0..43200", defaultValue: 0, required: true
    }
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
    zwave.multiChannelV4.multiChannelEndPointGet(),
    zwave.versionV3.versionGet(),
    zwave.firmwareUpdateMdV5.firmwareMdGet(),
    zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
    zwave.sensorMultilevelV11.sensorMultilevelGet(sensorType: 1, scale: (location.temperatureScale=="F"?1:0)),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 3),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 5)
  ], 100)
}

def poll() {
  logger("debug", "poll()")

  cmdSequence([
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: (location.temperatureScale=="F"?1:0)),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 3),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 5),
    zwave.meterV5.meterGet(scale: 0), // energy kWh
    zwave.meterV5.meterGet(scale: 2) // watts
  ])
}

def runCode() {
  logger("debug", "runCode()")

  cmdSequence([
      zwave.configurationV1.configurationGet(parameterNumber: 1),
      cmd(endpoint(zwave.basicV2.basicGet(), 1))
  ])
}

def configure() {
  logger("debug", "configure()")
  schedule("0 0 12 */7 * ?", updateCheck)

  if (stateCheckInterval.toInteger()) {
    if (['5', '10', '15', '30'].contains(stateCheckInterval) ) {
      schedule("0 */${stateCheckInterval} * ? * *", checkState)
    } else {
      schedule("0 0 */${stateCheckInterval} ? * *", checkState)
    }
  }

  def cmds = []
  cmds = cmds + cmdSequence([
    zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
    zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: param5.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: param6.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, scaledConfigurationValue: param7.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: param8.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: param9.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: param10.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: param11.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 12, size: 2, scaledConfigurationValue: param12.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 13, size: 2, scaledConfigurationValue: param13.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 14, size: 2, scaledConfigurationValue: param14.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 15, size: 2, scaledConfigurationValue: param15.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 16, size: 2, scaledConfigurationValue: param16.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 17, size: 1, scaledConfigurationValue: param17),
    zwave.configurationV1.configurationSet(parameterNumber: 18, size: 1, scaledConfigurationValue: param18.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 21, size: 1, scaledConfigurationValue: param21.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 22, size: 2, scaledConfigurationValue: param22),
    zwave.configurationV1.configurationSet(parameterNumber: 30, size: 1, scaledConfigurationValue: param30.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 31, size: 1, scaledConfigurationValue: param31.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 32, size: 1, scaledConfigurationValue: param32.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 33, size: 1, scaledConfigurationValue: param33.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 34, size: 1, scaledConfigurationValue: param34.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 35, size: 1, scaledConfigurationValue: param35.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 41, size: 1, scaledConfigurationValue: param41.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 42, size: 1, scaledConfigurationValue: param42.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 43, size: 1, scaledConfigurationValue: param43.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 44, size: 1, scaledConfigurationValue: param44.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 45, size: 1, scaledConfigurationValue: param45.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 51, size: 1, scaledConfigurationValue: param51.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 52, size: 1, scaledConfigurationValue: param52.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 53, size: 1, scaledConfigurationValue: param53.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 54, size: 1, scaledConfigurationValue: param54.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 55, size: 1, scaledConfigurationValue: param55.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 61, size: 1, scaledConfigurationValue: param61.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 62, size: 1, scaledConfigurationValue: param62.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 63, size: 1, scaledConfigurationValue: param63.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 64, size: 1, scaledConfigurationValue: param64.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 65, size: 1, scaledConfigurationValue: param65.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 71, size: 2, scaledConfigurationValue: param71.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 72, size: 2, scaledConfigurationValue: param72.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 73, size: 2, scaledConfigurationValue: param73.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 74, size: 2, scaledConfigurationValue: param74.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 75, size: 2, scaledConfigurationValue: param75.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 141, size: 1, scaledConfigurationValue: param141.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 142, size: 1, scaledConfigurationValue: param142.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 143, size: 1, scaledConfigurationValue: param143.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 144, size: 1, scaledConfigurationValue: param144.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 145, size: 1, scaledConfigurationValue: param145.toInteger()),
    zwave.configurationV1.configurationSet(parameterNumber: 146, size: 1, scaledConfigurationValue: param146.toInteger())
  ], 500)

  if (!getDataValue("MSR")) {
    cmds = cmds + cmdSequence([
      zwave.versionV3.versionGet(),
      zwave.firmwareUpdateMdV5.firmwareMdGet(),
      zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
      zwave.configurationV1.configurationGet(parameterNumber: 1),
      zwave.configurationV1.configurationGet(parameterNumber: 3),
      zwave.multiChannelV4.multiChannelEndPointGet()
    ], 100)
  }
}

def checkState() {
  logger("debug", "checkState()")

  Integer endPoints = state?.deviceInfo?.containsKey('endPoints') ? state.deviceInfo['endPoints'] : null
  if ( endPoints && endPoints > 0 ) {
    def cmds = []
    logger("info", "Checking State of child buttons ${endPoints/2}")
    (1..(endPoints/2)).each() {
      cmds << cmd(endpoint(zwave.basicV2.basicGet(), it))
    }
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
  }

  return cmds
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

  childDevices?.each{ deleteChildDevice(it.deviceNetworkId) }
  updateDataValue("MSR", "")
  installed()
}

def reset() {
  logger("debug", "reset()")

  sendEvent(name: "energy", value: "0", displayed: true, unit: "kWh")
  sendEvent(name: "power", value: "0", displayed: true, unit: "W")

  cmdSequence([
    zwave.meterV5.meterReset(),
    zwave.meterV5.meterGet(scale: 0),
    zwave.meterV5.meterGet(scale: 2)
  ])
}

def parse(String description) {
  logger("debug", "parse() - description: ${description.inspect()}")
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

void componentRefresh(cd){
  logger("debug", "componentRefresh() - cd: ${cd.inspect()}")

  def buttonNum = cd?.deviceNetworkId?.split('-')[1]
  if (buttonNum != null && buttonNum > 0) {
    def cmds = []
    cmds << cmd(endpoint(zwave.basicV2.basicGet(), buttonNum.toInteger()))
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
  }
}

void componentOn(cd){
  logger("debug", "componentOn() - cd: ${cd.inspect()}")

  def buttonNum = cd?.deviceNetworkId?.split('-')[1]
  if (buttonNum != null && buttonNum > 0) {
    def cmds = []
    cmds = cmds +cmdSequence([
      endpoint(zwave.basicV2.basicSet(value: 0xFF), buttonNum.toInteger()),
      endpoint(zwave.basicV2.basicGet(), buttonNum.toInteger())
    ], 500)
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
  }
}

void componentOff(cd){
  logger("debug", "componentOff() - cd: ${cd.inspect()}")

  def buttonNum = cd?.deviceNetworkId?.split('-')[1]
  if (buttonNum != null && buttonNum > 0) {
    def cmds = []
    cmds = cmds +cmdSequence([
      endpoint(zwave.basicV2.basicSet(value: 0x00), buttonNum.toInteger()),
      endpoint(zwave.basicV2.basicGet(), buttonNum.toInteger())
    ], 500)
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
  }
}

void componentPush(cd){
  logger("debug", "componentPush() - cd: ${cd.inspect()}")

  def buttonNum = cd?.deviceNetworkId?.split('-')[1]
  def curState = getChildDevice(cd.deviceNetworkId).currentValue('switch')
  Map stateInvert = ['on':0x00, 'off':0xFF]
  def newState = stateInvert[curState]

  if (buttonNum != null && buttonNum > 0 && newState != null) {
    if(logDescText) {
      log.info "${device.displayName} Button ${buttonNum} Was pushed"
    } else if(map?.descriptionText) {
      logger("info", "Button ${buttonNum} Was turned pushed")
    }

    def cmds = []
    cmds = cmds +cmdSequence([
      endpoint(zwave.basicV2.basicSet(value: newState), buttonNum.toInteger()),
      endpoint(zwave.basicV2.basicGet(), buttonNum.toInteger())
    ], 500)
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
  }
}

def zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorReport cmd) {
  logger("trace", "zwaveEvent(IndicatorReport) - cmd: ${cmd.inspect()}")
  []
}

def zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneConfigurationReport cmd) {
  logger("trace", "zwaveEvent(CentralSceneConfigurationReport) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {
  logger("trace", "zwaveEvent(CentralSceneNotification) - cmd: ${cmd.inspect()}")
  def result = []

  result << createEvent(name: "lastPressed", value: cmd.sceneNumber, isStateChange: true)

  if (cmd.keyAttributes == 0) {
    getChildDevice("${device.deviceNetworkId}-${cmd.sceneNumber}").push(cmd.sceneNumber, 'physical')
  }

  if (cmd.keyAttributes == 1) {
    getChildDevice("${device.deviceNetworkId}-${cmd.sceneNumber}").refresh()
  }

  if (cmd.keyAttributes == 2) {
    getChildDevice("${device.deviceNetworkId}-${cmd.sceneNumber}").hold(cmd.sceneNumber, 'physical')
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

  switch(cmd.parameterNumber) {
    case 1:
      List frequencyRegions = ["EU", "US", "ANZ", "HK", "IN", "IL", "RU", "CN", "JP", "KR"]
      state.deviceInfo['frequencyRegion'] = frequencyRegions?.getAt(cmd?.configurationValue?.getAt(0)) ?: 'unknown'
    break;
    default:
      logger("warn", "ConfigurationReport() - Unknown parameter: ${cmd.parameterNumber}, cmd: ${cmd.inspect()}")
    break;
  }

  []
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  logger("trace", "zwaveEvent(DeviceResetLocallyNotification) - cmd: ${cmd.inspect()}")
  logger("warn", "Has reset itself")
  []
}

def zwaveEvent(hubitat.zwave.commands.meterv5.MeterReport cmd) {
  logger("trace", "zwaveEvent(MeterReport) - cmd: ${cmd.inspect()}")

  def result = []
  List meterTypes = ["Unknown", "Electric", "Gas", "Water"]
  List electricNames = ["energy", "energy", "power", "count", "voltage", "current", "powerFactor", "unknown"]
  List electricUnits = ["kWh", "kVAh", "W", "pulses", "V", "A", "Power Factor", ""]

  if (cmd.meterType == 1) { // electric
    logger("trace", "handleMeterReport() - deltaTime:${cmd.deltaTime} secs, meterType:${meterTypes[cmd.meterType]}, meterValue:${cmd.scaledMeterValue}, previousMeterValue:${cmd?.scaledPreviousMeterValue}, scale:${electricNames[cmd.scale]}(${cmd.scale}), unit: ${electricUnits[cmd.scale]}, precision:${cmd.precision}, rateType:${cmd.rateType}")

    def map = [ name: electricNames[cmd.scale] ?: "electric", unit: electricUnits[cmd.scale], displayed: true]
    switch(cmd.scale) {
      case 0: //kWh
        map.value = cmd.scaledMeterValue
        map.descriptionText = "${meterTypes[cmd.meterType]} ${map.name} is ${map?.value} ${map?.unit}"
      break;
      case 2: //Watts
        map.value = Math.round(cmd.scaledMeterValue)
        map.descriptionText = "${meterTypes[cmd.meterType]} ${map.name} is ${map?.value} ${map?.unit}"
      break;
      default:
        logger("warn", "handleMeterReport() - Unknown type: ${cmd.scale}")
      break;
    }

    if (device.currentValue(map.name) != map.value) {
      if(logDescText) {
        log.info "${device.displayName} ${map.descriptionText}"
      } else if(map?.descriptionText) {
        logger("info", "${map.descriptionText}")
      }
    }
  }

  result << createEvent(map)
  return result
}

def zwaveEvent(hubitat.zwave.commands.basicv2.BasicReport cmd, Integer endPoint=null) {
  logger("trace", "zwaveEvent(BasicReport) - cmd: ${cmd.inspect()}, endPoint: ${endPoint}")
  def result = []

  if (endPoint >0) {
    String value = (cmd.value ? "on" : "off")
    if(logDescText) {
      log.info "${device.displayName} Button ${endPoint} Was turned ${value}"
    } else if(map?.descriptionText) {
      logger("info", "Button ${endPoint} Was turned ${value}")
    }
    result << createEvent(name: "lastPressed", value: endPoint, isStateChange: true)

    getChildDevice("${device.deviceNetworkId}-${endPoint}").parse([[name:"switch", value:value, descriptionText:"Button ${endPoint} was turned ${value}"]])
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd, Integer endPoint=null) {
  logger("trace", "zwaveEvent(SwitchBinaryReport) - cmd: ${cmd.inspect()}, endPoint: ${endPoint}")
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

def zwaveEvent(hubitat.zwave.commands.powerlevelv1.PowerlevelReport cmd) {
  logger("trace", "zwaveEvent(PowerlevelReport) - cmd: ${cmd.inspect()}")

  String power = (cmd.powerLevel > 0) ? "-${cmd.powerLevel}dBm" : "NormalPower"
  logger("debug", "Powerlevel Report: Power: ${power}, Timeout: ${cmd.timeout}")
  []
}

def zwaveEvent(hubitat.zwave.commands.clockv1.ClockReport cmd) {
  logger("trace", "zwaveEvent(ClockReport) - cmd: ${cmd.inspect()}")
  // hour:0, minute:7, weekday:1
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

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
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

def zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd) {
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

def zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelEndPointReport cmd) {
  logger("trace", "zwaveEvent(MultiChannelEndPointReport) - cmd: ${cmd.inspect()}")
  state.deviceInfo['endPoints'] = cmd.endPoints
  sendEvent(name: "numberOfButtons", value: cmd.endPoints/2, displayed: true)

  if ( !childDevices && cmd.endPoints > 1 ) {
    logger("info", "Creating child buttons ${cmd.endPoints/2}")
    (1..(cmd.endPoints/2)).each() {
      addChildDevice("syepes", "Heltun Touch Panel Switch - Button", "${device.deviceNetworkId}-${it}", [name: "Button ${it} (${device.displayName})", label: "Button ${it} (${device.displayName})", isComponent: true])
    }
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

private endpoint(hubitat.zwave.Command cmd, Integer endpoint) {
  zwave.multiChannelV4.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(cmd)
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
          0x85: 2, // COMMAND_CLASS_ASSOCIATION (Secure)
          0x59: 3, // COMMAND_CLASS_ASSOCIATION_GRP_INFO (Secure)
          0x8E: 3, // COMMAND_CLASS_MULTI_CHANNEL_ASSOCIATION (Secure)
          0x60: 4, // COMMAND_CLASS_MULTI_CHANNEL
          0x86: 3, // COMMAND_CLASS_VERSION (Insecure)
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x5A: 1, // COMMAND_CLASS_DEVICE_RESET_LOCALLY (Insecure)
          0x73: 1, // COMMAND_CLASS_POWERLEVEL (Insecure)
          0x81: 1, // COMMAND_CLASS_CLOCK
          0x87: 3, // COMMAND_CLASS_INDICATOR
          0x70: 4, // COMMAND_CLASS_CONFIGURATION (Secure)
          0x31: 11, // COMMAND_CLASS_SENSOR_MULTILEVEL
          0x25: 2, // COMMAND_CLASS_SWITCH_BINARY
          0x5B: 3, // COMMAND_CLASS_CENTRAL_SCENE
          0x32: 5, // COMMAND_CLASS_METER
          0x7A: 5 // COMMAND_CLASS_FIRMWARE_UPDATE_MD (Insecure)
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Heltun/Heltun%20Touch%20Panel%20Switch%20-%20Quinto.groovy"]
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
