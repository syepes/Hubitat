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

metadata {
  definition (name: "Qubino Flush Pilot Wire", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Qubino/Qubino%20Flush%20Pilot%20Wire.groovy") {
    capability "Actuator"
    capability "Refresh"
    capability "Sensor"
    capability "Polling"
    capability "Health Check"
    capability "Switch"
    capability "Switch Level"

    command "clearState"
    command "pilotMode", [[name:"mode",type:"ENUM", description:"Pilot mode", constraints: ["Stop","Anti Freeze","Eco","Comfort-2","Comfort-1","Comfort"]]]
    attribute "mode", "enum", ["Stop","Anti Freeze","Eco","Comfort-2","Comfort-1","Comfort"]

    fingerprint mfr: "0159", prod: "0004", model: "0001", deviceJoinName: "Qubino Flush Pilot Wire" // ZMNHJA2
    fingerprint mfr: "0159", prod: "0004", model: "0051", deviceJoinName: "Qubino Flush Pilot Wire" // ZMNHJD1 (868,4 MHz - EU)
  }

  preferences {
    input name: "logLevel", type: "enum", title: "Log Level", options:[[0:"Off"],[1:"Info"],[2:"Debug"],[3:"Trace"]], defaultValue: 0
  }
}


def updated() {
  logging "updated() - state: ${state.inspect()}", 2
  sendEvent(name: "checkInterval", value: 1 * 60 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def ping() {
  logging "ping()", 2
  refresh()
}

def poll() {
  logging "poll()", 2
  refresh()
}

def refresh() {
  logging "refresh() - state: ${state.inspect()}", 2

  def cmds = secureSequence([zwave.switchBinaryV1.switchBinaryGet(), zwave.switchMultilevelV1.switchMultilevelGet()], 100)
  if (getDataValue("MSR") == null) {
    cmds << secure(zwave.manufacturerSpecificV1.manufacturerSpecificGet())
  }

  cmds
}

def clearState() {
  logging "ClearStates() - Clearing device states", 2

  state.clear()
}

def on() {
  logging "on()", 2

  secureSequence([
    zwave.basicV1.basicSet(value: 0xFF),
    zwave.switchMultilevelV1.switchMultilevelGet()
  ], 3500)
}

def off() {
  logging "off()", 2

  secureSequence([
    zwave.basicV1.basicSet(value: 0x00),
    zwave.switchMultilevelV1.switchMultilevelGet()
  ], 3500)
}

def setLevel(value) {
  logging "setLevel(${value})", 2

  def valueaux = value as Integer
  def level = Math.max(Math.min(valueaux, 99), 0)
  secureSequence([
    zwave.basicV1.basicSet(value: level),
    zwave.switchMultilevelV1.switchMultilevelGet()
  ])
}

def setLevel(value, duration) {
  logging "setLevel(${value}, ${duration})", 2

  def valueaux = value as Integer
  def level = Math.max(Math.min(valueaux, 99), 0)
  def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
  secure(zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration))
}

def pilotMode(mode="Stop") {
  // Validate modes
  Integer mode_value = null
  LinkedHashMap mode_map = [0:"Stop",15:"Anti Freeze",25:"Eco",35:"Comfort-2",45:"Comfort-1",100:"Comfort"]
  mode_map.each { it->
    if (it.value == mode) { mode_value = it.key }
  }

  if (mode_value == null) {
    log.error "pilotMode(${mode}) - Mode is incorrect"
  } else {
    logging "pilotMode(${mode}) - Mode value = ${mode_value}", 1
    sendEvent(name: "mode", value: mode, displayed:true, isStateChange: true)
    setLevel(mode_value)
  }
}

def parse(String description) {
  logging "parse() - description: ${description.inspect()}", 2

  if (description.startsWith("Err 106")) {
    state.sec = 0
    createEvent(descriptionText: description, isStateChange: true)
  } else {
    def cmd = zwave.parse(description, [0x20: 1, 0x26: 3, 0x70: 1, 0x32:3, 0x98: 1])
    logging "parse() - description: ${description.inspect()} to cmd: ${cmd.inspect()}", 3

    if (cmd) {
      zwaveEvent(cmd)
    } else {
      log.error("parse() - Non-parsed event: '$description'")
      null
    }
  }
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  logging "zwaveEvent(ManufacturerSpecificReport) - cmd: ${cmd.inspect()}", 2
  logging "manufacturerId:   ${cmd.manufacturerId}\nmanufacturerName: ${cmd.manufacturerName}\nproductId:        ${cmd.productId}\nproductTypeId:    ${cmd.productTypeId}", 3

  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)
  updateDataValue("manufacturer", cmd.manufacturerName)
  createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  logging "zwaveEvent(SecurityMessageEncapsulation) - cmd: ${cmd.inspect()}", 2

  def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x26: 3, 0x32: 3])
  state.sec = 1
  if (encapsulatedCommand) {
    zwaveEvent(encapsulatedCommand)
  }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  logging "zwaveEvent(Command) - cmd: ${cmd.inspect()}", 2

  def linkText = device.label ?: device.name
  [linkText: linkText, descriptionText: "$linkText: $cmd", displayed: false]
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logging "zwaveEvent(switchmultilevelv1.BasicReport) - cmd: ${cmd.inspect()}", 2
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
  logging "zwaveEvent(switchmultilevelv1.SwitchMultilevelReport) - cmd: ${cmd.inspect()}", 2
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd) {
  logging "zwaveEvent(switchmultilevelv1.SwitchMultilevelReport) - cmd: ${cmd.inspect()}", 2
  setLevelEvent(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
  logging "zwaveEvent(switchmultilevelv3.SwitchMultilevelReport) - cmd: ${cmd.inspect()}", 2
  setLevelEvent(cmd)
}

private setLevelEvent(hubitat.zwave.Command cmd) {
  logging "setLevelEvent(Command) - cmd: ${cmd.inspect()}", 2

  def value = (cmd.value ? "on" : "off")
  def result = [createEvent(name: "switch", value: value, descriptionText: "$device.displayName was turned $value")]

  if (cmd.value) {
    result << createEvent(name: "level", value: cmd.value == 99 ? 100 : cmd.value , unit: "%")

    LinkedHashMap mode_map = [0:"Stop",15:"Anti Freeze",25:"Eco",35:"Comfort-2",45:"Comfort-1",99:"Comfort", 100:"Comfort"]
    result << createEvent(name: "mode", value: mode_map[cmd.value] == null ? 'Unknown' : mode_map[cmd.value] )
  }
  return result
}

private secure(hubitat.zwave.Command cmd) {
  logging "secure(Command) - cmd: ${cmd.inspect()}", 3

  if (state.sec) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private secureSequence(Collection commands, ...delayBetweenArgs=4200) {
  logging "secureSequence(Command) - commands: ${commands.inspect()}", 3
  delayBetween(commands.collect{ secure(it) }, *delayBetweenArgs)
}

private logging(msg,level=1) {
  logLevel = logLevel ?: 0

  if (logLevel > 2 && level == 3) {
    log.trace "${msg}"
  } else if (logLevel > 1 && level == 2) {
    log.debug "${msg}"
  } else if (logLevel > 0 && level == 1) {
    log.info "${msg}"
  }
}
