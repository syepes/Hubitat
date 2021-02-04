/**
 *  Aeon WallMote (with slide functionality)
 *
 *  Copyright 2019 Ben Rimmasch
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
 *  Aeon WallMote Dual/Quad
 *
 *  Author: Ben Rimmasch (codahq)
 *  Original Author: Eric Maycock (erocm123)
 *  Date: 2019-01-23
 *
 *  Change Log:
 *  2020-01-23: Initial
 *  2020-01-26: Fixed logging for those that switch to this driver and have never set a logging level yet
 *              Change to two pushed buttons instead of one button that is pushed and held
 *              Bug fixes for some events
 *              Handled the event sent when a user initiates a connectivity health test from the remote's button
 *              Fixed firmware reporting for current Hubitat command classes
 *
 *
 *  Previous Author's Change Log:
 *  2017-06-19: Added check to only send color change config for three wakeups. Editing preferences
 *              and hitting "done" will reset the counter. This is an attempt to prevent freezing
 *              caused by updating preferences.
  https://community.hubitat.com/t/beta-aeon-wallmote-with-slide-functionality/32941
  https://aeotec.freshdesk.com/support/solutions/articles/6000166184-wallmote-quad-technical-specifications-
  https://cdn.shopify.com/s/files/1/0066/8149/3559/files/24_wallmote_quad_-_es.pdf
  http://manuals-backend.z-wave.info/make.php?lang=en&sku=AEOEZW130
 */

metadata {
  definition (name: "Aeon WallMote Quad", namespace: "codahq-hubitat", author: "Ben Rimmasch",
      importUrl: "https://raw.githubusercontent.com/codahq/hubitat_codahq/master/devicestypes/aeon-wallmote.groovy") {
    capability "Actuator"
    capability "PushableButton"
    capability "HoldableButton"
    capability "ReleasableButton"
    capability "Configuration"
    capability "Sensor"
    capability "Battery"
    capability "Health Check"

    attribute "sequenceNumber", "number"
    attribute "needUpdate", "string"

    fingerprint mfr: "0086", prod: "0102", model: "0082", deviceJoinName: "Aeon WallMote"

    fingerprint deviceId: "0x1801", inClusters: "0x5E,0x73,0x98,0x86,0x85,0x59,0x8E,0x60,0x72,0x5A,0x84,0x5B,0x71,0x70,0x80,0x7A", outClusters: "0x25,0x26" // secure inclusion
    fingerprint deviceId: "0x1801", inClusters: "0x5E,0x85,0x59,0x8E,0x60,0x86,0x70,0x72,0x5A,0x73,0x84,0x80,0x5B,0x71,0x7A", outClusters: "0x25,0x26"

  }
  preferences {
    input description: "Once you change values on this page, the attribute value \"needUpdate\" will show \"YES\" until all configuration parameters are updated.", title: "Settings", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    generate_preferences(configuration_model())
  }

}

def parse(String description) {
  logDebug "parse(String description)"
  logTrace "description: $description"

  def results = []
  if (description.startsWith("Err")) {
    results = createEvent(descriptionText:description, displayed:true)
  }
  else {
    def cmd = zwave.parse(description, [0x2B: 1, 0x80: 1, 0x84: 1])
    if(cmd) results += zwaveEvent(cmd)
    if(!results) results = [ descriptionText: cmd, displayed: false ]
  }

  return results
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelSet cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelSet cmd)"
  logTrace "cmd: $cmd"
  //not needed. do nothing.
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd)"
  logTrace "cmd: $cmd"
  //not needed. do nothing.
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelStartLevelChange cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelStartLevelChange cmd)"
  logTrace "cmd: $cmd"

  def result = []

  //if not held buttons and button slide is enabled
  if (!getHeldButtons() && settings."3" == "1") {
    switch (cmd.upDown) {
      case false: // Up
        logTrace "Slide up"
        result << buttonEvent(device.currentValue("numberOfButtons") - 1, "pushed")
        break
      case true: // Down
        logTrace "Slide down"
        result << buttonEvent(device.currentValue("numberOfButtons"), "pushed")
        break
      default:
        logDebug "Unhandled SwitchMultilevelStartLevelChange: ${cmd}"
        break
    }
  }
  result
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd)"
  logTrace "cmd: $cmd"

  def result = []

  sendEvent(name: "sequenceNumber", value: cmd.sequenceNumber, displayed:false)
  switch (cmd.keyAttributes) {
    case 0:
      result << buttonEvent(cmd.sceneNumber, "pushed")
      break
    case 1: // released
      state."${cmd.sceneNumber}" = cmd.keyAttributes
      if (!settings.holdMode || settings.holdMode == "2") result << buttonEvent(cmd.sceneNumber, "held")
      result << buttonEvent(cmd.sceneNumber, "released")
      break
    case 2: // held
      state."${cmd.sceneNumber}" = cmd.keyAttributes
      if (settings.holdMode == "1") result << buttonEvent(cmd.sceneNumber, "held")
      break
    default:
      logDebug "Unhandled CentralSceneNotification: ${cmd}"
      break
  }
  result
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  def encapsulatedCommand = cmd.encapsulatedCommand([0x5B: 1, 0x20: 1, 0x31: 5, 0x30: 2, 0x84: 1, 0x70: 1])
  state.sec = 1
  if (encapsulatedCommand) {
    zwaveEvent(encapsulatedCommand)
  }
  else {
    log.warn "Unable to extract encapsulated cmd from $cmd"
    createEvent(descriptionText: cmd.toString())
  }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
  response(configure())
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalCapabilitiesReport cmd) {
  logger("trace", "zwaveEvent(WakeUpIntervalCapabilitiesReport) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalGet cmd) {
  logger("trace", "zwaveEvent(WakeUpIntervalGet) - cmd: ${cmd.inspect()}")
}

def zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpIntervalReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpIntervalReport cmd)"
  logTrace "cmd: $cmd"

  state.wakeInterval = cmd.seconds
}

def zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd)"
  logTrace "cmd: $cmd"

  logInfo "Device ${device.displayName} woke up"

  def request = update_needed_settings()

  request << zwave.versionV2.versionGet()

  if (!state.lastBatteryReport || (now() - state.lastBatteryReport) / 60000 >= 60 * 24) {
    logDebug "Over 24hr since last battery report. Requesting report"
    request << zwave.batteryV1.batteryGet()
  }

  state.wakeCount? (state.wakeCount = state.wakeCount + 1) : (state.wakeCount = 2)

  if (request != []) {
    response(commands(request) + ["delay 5000", zwave.wakeUpV1.wakeUpNoMoreInformation().format()])
  }
  else {
    logDebug "No commands to send"
    response([zwave.wakeUpV1.wakeUpNoMoreInformation().format()])
  }
}

def buttonEvent(button, name) {
  def msg = "$device.displayName button $button was $name"
  logInfo msg
  createEvent(name: name, value: button, descriptionText: msg, isStateChange: true)
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd)"
  logTrace "cmd: $cmd"

  def map = [ name: "battery", unit: "%" ]
  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "${device.displayName} battery is low"
    map.isStateChange = true
  }
  else {
    map.value = cmd.batteryLevel
    map.descriptionText = "Battery is ${cmd.batteryLevel} ${map.unit}"

  }
  state.lastBatteryReport = now()
  createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd)"
  logTrace "cmd: $cmd"
  state."association${cmd.groupingIdentifier}" = cmd.nodeId[0]
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  update_current_properties(cmd)
  logDebug "${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'"
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
  update_current_properties(cmd)
  logDebug "${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'"
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  logger("trace", "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}")

  if(cmd.applicationVersion != null && cmd.applicationSubVersion != null) {
    String firmwareVersion = "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}"
    Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
    updateDataValue("firmware", "${firmwareVersion}")
    state.deviceInfo['firmwareVersion'] = firmwareVersion

  } else if(cmd.firmware0Version != null && cmd.firmware0SubVersion != null) {
    def firmware = "${cmd.firmware0Version}.${cmd.firmware0SubVersion.toString().padLeft(2,'0')}"
    Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
    updateDataValue("firmware", "${firmwareVersion}")
    state.deviceInfo['firmwareVersion'] = firmwareVersion
    state.deviceInfo['protocolVersion'] = protocolVersion
  }
  []
}

def zwaveEvent(hubitat.zwave.commands.powerlevelv1.PowerlevelTestNodeReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.powerlevelv1.PowerlevelTestNodeReport cmd)"
  logTrace "cmd: $cmd"
  logInfo "Connectivity testing performed by user at remote..."
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  log.warn "Unhandled zwaveEvent: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  logDebug "msr: $msr"
  updateDataValue("MSR", msr)
}

def installed() {
  log.info "...Aeon WallMote Installed..."
  configure()
}

/**
* Triggered when Done button is pushed on Preference Pane
*/
def updated() {
  logDebug "updated() is being called"
  state.wakeCount = 1
  def cmds = update_needed_settings()
  sendEvent(name: "checkInterval", value: 2 * 60 * 12 * 60 + 5 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
  sendEvent(name: "numberOfButtons", value: getNumButtons(), displayed: true)
  sendEvent(name:"needUpdate", value: device.currentValue("needUpdate"), displayed:false, isStateChange: true)
  if (cmds != []) response(commands(cmds))
}

def configure() {
  logDebug "Configuring Device For Use"
  def cmds = []
  cmds = update_needed_settings()
  sendEvent(name: "numberOfButtons", value: getNumButtons(), displayed: true)
  if (cmds != []) commands(cmds)
}

private getNumButtons() {
  return settings.buttons ? (settings."3" == "1" ? settings.buttons.toInteger() + 2 : settings.buttons) : (settings."3" ? 4 + 2 : 4)
}

def ping() {
  logDebug "ping()"
  log.warn "Battery Device - Not sending ping commands"
}

def generate_preferences(configuration_model) {
  def configuration = new XmlSlurper().parseText(configuration_model)

  configuration.Value.each {
    switch(it.@type) {
      case ["byte","short","four"]:
        input "${it.@index}", "number", title:"${it.@label}\n" + "${it.Help}", range: "${it.@min}..${it.@max}", defaultValue: "${it.@value}",
          displayDuringSetup: "${it.@displayDuringSetup}", required: "${it.@required}"
        break
      case "list":
        def items = []
        it.Item.each { items << ["${it.@value}": "${it.@label}"] }
        input "${it.@index}", "enum", title:"${it.@label}\n" + "${it.Help}", defaultValue: "${it.@value}", options: items,
          displayDuringSetup: "${it.@displayDuringSetup}", required: "${it.@required}"
        break
      case "decimal":
        input "${it.@index}", "decimal", title:"${it.@label}\n" + "${it.Help}", range: "${it.@min}..${it.@max}", defaultValue: "${it.@value}",
          displayDuringSetup: "${it.@displayDuringSetup}", required: "${it.@required}"
        break
      case "boolean":
        input "${it.@index}", "bool", title: it.@label != "" ? "${it.@label}\n" + "${it.Help}" : "" + "${it.Help}", defaultValue: "${it.@value}",
          displayDuringSetup: "${it.@displayDuringSetup}", required: "${it.@required}"
        break
    }
  }
}

def update_current_properties(cmd) {
  def currentProperties = state.currentProperties ?: [:]

  currentProperties."${cmd.parameterNumber}" = cmd.configurationValue

  if (settings."${cmd.parameterNumber}" != null) {
    if (convertParam(cmd.parameterNumber, settings."${cmd.parameterNumber}") == cmd2Integer(cmd.configurationValue)) {
      sendEvent(name:"needUpdate", value:"NO", displayed:false, isStateChange: true)
    }
    else {
      sendEvent(name:"needUpdate", value:"YES", displayed:false, isStateChange: true)
    }
  }

  state.currentProperties = currentProperties
}

def update_needed_settings() {
  def cmds = []
  def currentProperties = state.currentProperties ?: [:]

  def configuration = new XmlSlurper().parseText(configuration_model())
  def isUpdateNeeded = "NO"

  if (state.wakeInterval == null || state.wakeInterval != 86400) {
    logDebug "Setting Wake Interval to 86400"
    cmds << zwave.wakeUpV1.wakeUpIntervalSet(seconds: 86400, nodeid:zwaveHubNodeId)
    cmds << zwave.wakeUpV1.wakeUpIntervalGet()
  }

  if (settings."3" == "1") {
    if (!state.association3 || state.association3 == "" || state.association3 == "1") {
      logDebug "Setting association group 3"
      cmds << zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId)
      cmds << zwave.associationV2.associationGet(groupingIdentifier:3)
    }
    if (!state.association5 || state.association5 == "" || state.association5 == "1") {
      logDebug "Setting association group 5"
      cmds << zwave.associationV2.associationSet(groupingIdentifier:5, nodeId:zwaveHubNodeId)
      cmds << zwave.associationV2.associationGet(groupingIdentifier:5)
    }
    if (!state.association7 || state.association7 == "" || state.association7 == "1") {
      logDebug "Setting association group 7"
      cmds << zwave.associationV2.associationSet(groupingIdentifier:7, nodeId:zwaveHubNodeId)
      cmds << zwave.associationV2.associationGet(groupingIdentifier:7)
    }
    if (!state.association9 || state.association9 == "" || state.association9 == "1") {
      logDebug "Setting association group 9"
      cmds << zwave.associationV2.associationSet(groupingIdentifier:9, nodeId:zwaveHubNodeId)
      cmds << zwave.associationV2.associationGet(groupingIdentifier:9)
    }
  }

  if (state.MSR == null){
    logDebug "Getting Manufacturer Specific Info"
    cmds << zwave.manufacturerSpecificV2.manufacturerSpecificGet()
  }

  configuration.Value.each {
    if ("${it.@setting_type}" == "zwave") {
      if (currentProperties."${it.@index}" == null) {
        if (it.@setonly == "true"){
          if (it.@index == 5) {
            if (state.wakeCount <= 3) {
              logDebug "Parameter ${it.@index} will be updated to " + convertParam(it.@index.toInteger(), settings."${it.@index}"? settings."${it.@index}" : "${it.@value}")
              def convertedConfigurationValue = convertParam(it.@index.toInteger(), settings."${it.@index}"? settings."${it.@index}" : "${it.@value}")
              cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
              cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
            }
            else {
              logDebug "Parameter has already sent. Will not send again until updated() gets called"
            }
          }
          else {
            logDebug "Parameter ${it.@index} will be updated to " + convertParam(it.@index.toInteger(), settings."${it.@index}"? settings."${it.@index}" : "${it.@value}")
            def convertedConfigurationValue = convertParam(it.@index.toInteger(), settings."${it.@index}"? settings."${it.@index}" : "${it.@value}")
            cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
            cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
          }
        }
        else {
          isUpdateNeeded = "YES"
          logDebug "Current value of parameter ${it.@index} is unknown"
          cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
        }
      }
      else if (settings."${it.@index}" != null && cmd2Integer(currentProperties."${it.@index}") != convertParam(it.@index.toInteger(), settings."${it.@index}")) {
        isUpdateNeeded = "YES"

        if (it.@index == 5) {
          if (state.wakeCount <= 3) {
            logDebug "Parameter ${it.@index} will be updated to " + convertParam(it.@index.toInteger(), settings."${it.@index}")
            def convertedConfigurationValue = convertParam(it.@index.toInteger(), settings."${it.@index}")
            cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
            cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
          }
          else {
            logDebug "Parameter has already sent. Will not send again until updated() gets called"
          }
        }
        else {
          logDebug "Parameter ${it.@index} will be updated to " + convertParam(it.@index.toInteger(), settings."${it.@index}")
          def convertedConfigurationValue = convertParam(it.@index.toInteger(), settings."${it.@index}")
          cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
          cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
        }
      }
    }
  }

  sendEvent(name:"needUpdate", value: isUpdateNeeded, displayed:false, isStateChange: true)
  return cmds
}

def convertParam(number, value) {
  long parValue
  switch (number) {
    case 5:
      switch (value) {
        case "1":
          parValue = 4278190080
          break
        case "2":
          parValue = 16711680
          break
        case "3":
          parValue = 65280
          break
        default:
          parValue = value
          break
      }
      break
    default:
      parValue = value.toLong()
      break
  }
  return parValue
}

private def getHeldButtons() {
  def heldButtons = (1..device.currentValue("numberOfButtons"))
  heldButtons = heldButtons.find { button ->
    state."${button}" == 2
  }
  logTrace "heldButtons $heldButtons"
  return heldButtons
}

private logInfo(msg) {
  if (settings.loggingLevel?.toInteger() >= 1) log.info msg
}

def logDebug(msg) {
  if (settings.loggingLevel?.toInteger() >= 2) log.debug msg
}

def logTrace(msg) {
  if (settings.loggingLevel?.toInteger() >= 3) log.trace msg
}

/**
* Convert 1 and 2 bytes values to integer
*/
def cmd2Integer(array) {
  long value
  if (array != [255, 0, 0, 0]){
    switch(array.size()) {
      case 1:
      value = array[0]
      break
      case 2:
      value = ((array[0] & 0xFF) << 8) | (array[1] & 0xFF)
      break
      case 3:
      value = ((array[0] & 0xFF) << 16) | ((array[1] & 0xFF) << 8) | (array[2] & 0xFF)
      break
      case 4:
      value = ((array[0] & 0xFF) << 24) | ((array[1] & 0xFF) << 16) | ((array[2] & 0xFF) << 8) | (array[3] & 0xFF)
      break
    }
  }
  else {
    value = 4278190080
  }
  return value
}

def integer2Cmd(value, size) {
  switch(size) {
    case 1:
      [value.toInteger()]
      break
    case 2:
      def short value1   = value & 0xFF
      def short value2 = (value >> 8) & 0xFF
      [value2.toInteger(), value1.toInteger()]
      break
    case 3:
      def short value1   = value & 0xFF
      def short value2 = (value >> 8) & 0xFF
      def short value3 = (value >> 16) & 0xFF
      [value3.toInteger(), value2.toInteger(), value1.toInteger()]
      break
    case 4:
      def short value1 = value & 0xFF
      def short value2 = (value >> 8) & 0xFF
      def short value3 = (value >> 16) & 0xFF
      def short value4 = (value >> 24) & 0xFF
      [value4.toInteger(), value3.toInteger(), value2.toInteger(), value1.toInteger()]
      break
  }
}

private command(hubitat.zwave.Command cmd) {
  if (state.sec) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  }
  else {
    cmd.format()
  }
}

private commands(commands, delay=1000) {
  delayBetween(commands.collect{ command(it) }, delay)
}

def configuration_model() {
'''
<configuration>
  <Value type="list" byteSize="1" index="buttons" label="WallMote Model" min="2" max="4" value="" setting_type="preference" fw="" displayDuringSetup="true">
    <Help>
Which model of WallMote is this?
    </Help>
    <Item label="Dual" value="2" />
    <Item label="Quad" value="4" />
  </Value>
  <Value type="list" byteSize="1" index="1" label="Touch Sound" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
Enable/disable the touch sound.
Default: Enable
    </Help>
    <Item label="Disable" value="0" />
    <Item label="Enable" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="2" label="Touch Vibration" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
Enable/disable the touch vibration.
Default: Enable
    </Help>
    <Item label="Disable" value="0" />
    <Item label="Enable" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="3" label="Button Slide" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
Enable/disable the function of button slide.
Default: Enable
    </Help>
    <Item label="Disable" value="0" />
    <Item label="Enable" value="1" />
  </Value>
  <Value type="list" byteSize="4" index="5" label="Color" min="1" max="3" value="3" setting_type="zwave" fw="" setonly="true">
    <Help>
To configure which color will be displayed when the button is pressed.
Default: Blue
    </Help>
    <Item label="Red" value="1" />
    <Item label="Green" value="2" />
    <Item label="Blue" value="3" />
  </Value>
  <Value type="list" byteSize="4" index="holdMode" label="Hold Mode" min="1" max="2" value="2" setting_type="preference" fw="">
    <Help>
Multiple "held" events on botton hold? With this option, the controller will send a "held" event about every second while holding down a button. If set to No it will send a "held" event a single time when the button is released.
Default: No
    </Help>
    <Item label="No" value="2" />
    <Item label="Yes" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="loggingLevel" label="Logging Level?" min="0" max="3" value="0" setting_type="preference" fw="" required="true">
    <Help>
Set the verbosity of the logs.
    </Help>
    <Item label="No Logging" value="0" />
    <Item label="Descriptive Text" value="1" />
    <Item label="Debug" value="2" />
    <Item label="Trace" value="3" />
  </Value>
</configuration>
'''
}
