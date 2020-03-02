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
import hubitat.zwave.commands.usercodev1.*
import groovy.transform.Field

@Field String VERSION = "1.0.2"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Zipato Mini RFID Keypad", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Zipato/Zipato%20Mini%20RFID%20Keypad.groovy") {
    capability "Actuator"
    capability "Battery"
    capability "Sensor"
    capability "TamperAlert"
    capability "Switch"
    capability "PushableButton"
    capability "Refresh"
    capability "Configuration"
    capability "Initialize"

    command "rfidOperation", [[name:"mode",type:"ENUM", description:"RFID Operation", constraints: ["None","Capture","Register","UnRegister"]]]
    command "rfidSlot", ["number"]
    command "clearState"

    attribute "user", "String"
    attribute "mode", "enum", ["home","away"]

    attribute "rfid1", "enum", ["Used","Empty"]
    attribute "rfid2", "enum", ["Used","Empty"]
    attribute "rfid3", "enum", ["Used","Empty"]
    attribute "rfid4", "enum", ["Used","Empty"]
    attribute "rfid5", "enum", ["Used","Empty"]
    attribute "rfid6", "enum", ["Used","Empty"]
    attribute "rfid7", "enum", ["Used","Empty"]
    attribute "rfid8", "enum", ["Used","Empty"]
    attribute "rfid9", "enum", ["Used","Empty"]
    attribute "rfid10", "enum", ["Used","Empty"]
    attribute "rfidName1", "String"
    attribute "rfidName2", "String"
    attribute "rfidName3", "String"
    attribute "rfidName4", "String"
    attribute "rfidName5", "String"
    attribute "rfidName6", "String"
    attribute "rfidName7", "String"
    attribute "rfidName8", "String"
    attribute "rfidName9", "String"
    attribute "rfidName10", "String"

    fingerprint mfr: "0097", prod: "6131", model: "4501"
    fingerprint deviceId: "17665", inClusters: "0x85, 0x80, 0x84, 0x86, 0x72, 0x71, 0x70, 0x25, 0x63" // ZHD01

  }

  preferences {
    section { // Usage
      input title: "<b>Register (Code):</b>", description: "<ol> <li>Select the 'Capture' mode from the 'RFID Operation' and click the button</li> <li>From the device, press 'Home' and enter your code (Minimum 4 and Maximum 10 digits) and then press 'Enter'<br/>Now you can verify the captured code from the 'State Variables' section 'configCode.code' (Refresh the browser F5)</li> <li>Select the 'Register' mode from the 'RFID Operation' and click the button</li> <li>Enter an 'Empty' slot number on the 'RFID Slot' and click the button</li> <li>You can verify the operation and parameters that are going to be sent to the device in the 'State Variables' section 'configCode' (Refresh the browser F5)</li> <li>Wake up the device so the registration can be processed</li></ol>", type: "paragraph", element: "paragraph"
      input title: "<b>Register (RFID Tag):</b>", description: "<ol> <li>Select the 'Capture' mode from the 'RFID Operation' and click the button</li> <li>From the device, press 'Home' and wait until the LED is fully turned on, then approach the 'RFID Tag'<br/>Now you can verify the captured code from the 'State Variables' section 'configCode.code' (Refresh the browser F5)</li> <li>Select the 'Register' mode from the 'RFID Operation' and click the button</li> <li>Enter an 'Empty' slot number on the 'RFID Slot' and click the button</li> <li>You can verify the operation and parameters that are going to be sent to the device in the 'State Variables' section 'configCode' (Refresh the browser F5)</li> <li>Wake up the device so the registration can be processed</li></ol>", type: "paragraph", element: "paragraph"
      input title: "<b>UnRegister (Code or RFID Tag):</b>", description: "<ol> <li>Select the 'UnRegister' mode from the 'RFID Operation' and click the button</li> <li>Enter the 'Used' slot number on the 'RFID Slot' and click the button</li> <li>You can verify the operation and parameters that are going to be sent to the device in the 'State Variables' section 'configCode' (Refresh the browser F5)</li> <li>Wake up the device so the unregistration can be processed</li></ol><br/><b>Wake up device methods:</b><br/><ol> <li>Press 'Home', a random number and then press 'Enter'</li> <li>Press the 'Tamper' button and release it after 1 to 2 seconds (Recommended Methods)</li></ol>", type: "paragraph", element: "paragraph"
    }
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "batteryCheckInterval", title: "Device Battery Check Interval", description: "How aften (hours) should we check the battery level", type: "number", defaultValue: 24, required: true
      input name: "wakeUpInterval", title: "Device Wake Up Interval", description: "", type: "enum", options:[[2:"2m"],[5:"5m"], [15:"15m"], [30:"30m"], [60:"1h"], [120:"2h"], [180:"3h"], [240:"4h"], [480:"8h"], [720:"12h"], [1440: "24h"], [2880: "48h"]], defaultValue: 30, required: true
    }
    section { // Configuration
      input name: "param1", title: "Factory reset", description: "Set all configuration values to default values (factory settings)", type: "enum", options:[[85:"Configuration are altered"],[170:"Configuration is untouched"], [255:"Factory reset"]], defaultValue: 170, required: false
      input name: "param2", title: "Notification sound", description: "Number of seconds to emit feedback for in seconds<br/>-1 = Endless<br/>0 = Disabled", type: "number", range: "0..255", defaultValue: 10, required: true
      input name: "param4", title: "Notification sound beeps per second", description: "The number of beeps per second. Every beep is fixed at about 10ms", type: "number", range: "1..7", defaultValue: 1, required: true
      input name: "param3", title: "Away mode delay", description: "Number of seconds to wait until setting the Away mode (delay)<br/>0 = Disabled (immediate)", type: "number", range: "0..255", defaultValue: 30, required: true
      input name: "param5", title: "Operating mode", description: "The Sleep/WakeUp operating mode", type: "enum", options:[[1:"Sleep/WakeUp mode"],[3:"Always on mode (Battery killer)"]], defaultValue: 1, required: true
    }
    section { // User Slots
      input name: "who1", title: "Name for User 1", description: "1", type: "text", defaultValue: "User 1"
      input name: "who2", title: "Name for User 2", description: "2", type: "text", defaultValue: "User 2"
      input name: "who3", title: "Name for User 3", description: "3", type: "text", defaultValue: "User 3"
      input name: "who4", title: "Name for User 4", description: "4", type: "text", defaultValue: "User 4"
      input name: "who5", title: "Name for User 5", description: "5", type: "text", defaultValue: "User 5"
      input name: "who6", title: "Name for User 6", description: "6", type: "text", defaultValue: "User 6"
      input name: "who7", title: "Name for User 7", description: "7", type: "text", defaultValue: "User 7"
      input name: "who8", title: "Name for User 8", description: "8", type: "text", defaultValue: "User 8"
      input name: "who9", title: "Name for User 9", description: "9", type: "text", defaultValue: "User 9"
      input name: "who10", title: "Name for User 10", description: "10", type: "text", defaultValue: "User 10"
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
  sendEvent(name: "tamper", value: "clear", displayed: true)
  state.configCode = [task:'None', slot:'',code:'']
  state.driverInfo.configSynced = false
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }

  if (!getDataValue("MSR")) {
    refresh()
  }

  unschedule()
  configure()
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
  def cmds = []

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

  if (state?.configCode == null) {
    state.configCode = [task:'None', slot:'',code:'']
  } else {
    state.configCode.clear()
    state.configCode = [task:'None', slot:'',code:'']
  }

  if (state?.configSlots == null) {
    state.configSlots = [:]
  } else {
    state.configSlots.clear()
  }

  // Reset the slot state, this will be updated according to reports sent back from the device
  for(Integer i=0; i<=10; i++) {
    sendEvent(name: "rfid$i", value: "Empty")
    sendEvent(name: "rfidName$i", value: "User $i")
  }

  installed()
}

def on(){
  logger("debug", "on()")
  if (logDescText) { log.info "Switching mode to Away (UI)" }

  sendEvent(name: "switch", value: "on", descriptionText: "Away by UI")
  sendEvent(name: "mode", value: "away", descriptionText: "Away by UI")
  sendEvent(name: "user", value: "UI", descriptionText: "Away by UI")
}

def off() {
  logger("debug", "off()")
  if (logDescText) { log.info "Switching mode to Home (UI)" }

  sendEvent(name: "switch", value: "off", descriptionText: "Home by UI")
  sendEvent(name: "mode", value: "home", descriptionText: "Home by UI")
  sendEvent(name: "user", value: "UI", descriptionText: "Home by UI")
}

def rfidOperation(String mode="None") {
  logger("debug", "rfidOperation() - mode: ${mode}")
  state.configCode.task = mode
}

def rfidSlot(BigDecimal slot) {
  if (slot == null) { return }
  logger("debug", "rfidSlot() - slot: ${slot}")
  state.configCode.slot = slot
}

private def rfidOperationCommands() {
  def cmds = []

  switch (state.configCode.task) {
    case 'Register':
      if(state.configCode.code != null && state.configCode.code != '') {
        String codeHex = code2Hex(state.configCode.code)
        BigInteger codeInt = new BigInteger(codeHex, 16)
        logger("debug", "rfidOperationCommands() - task: ${state.configCode.task}, slot: ${state.configCode.slot}, codeRaw: ${state.configCode.code}, codeHex: ${codeHex}, codeInt: ${codeInt}")
        if (logDescText) { log.info "Registering slot: ${state.configCode.slot} with code: ${state.configCode.code}" }

        def cmdRaw = [  0x63, // User Code Command Class
                        0x01, // User Code Set Command
                        state.configCode.slot.toInteger(), // User Slot
                        UserCodeSet.USER_ID_STATUS_OCCUPIED, // User Code Operation
                        codeInt
                      ]

        cmdRaw = cmdRaw.collect { String.format("%02x", it).toUpperCase() }.join('')
        cmds << cmdRaw
        cmds << 'delay 300'
        cmds << zwave.userCodeV1.userCodeGet(userIdentifier: state.configCode.slot.toInteger()).format()

        state.configCode.task = 'None'
        state.configCode.slot = ''
        state.configCode.code = ''
      } else {
        logger("warn", "rfidOperationCommands() - ${state.configCode.task} failed, No code has been captured")
      }
    break
    case 'UnRegister':
      if(state.configSlots[Integer.toString(state.configCode.slot)] != null) {
        logger("debug", "rfidOperationCommands() - task: ${state.configCode.task}, slot: ${state.configCode.slot}")
        if (logDescText) { log.info "UnRegistering slot: ${state.configCode.slot}" }

        cmds = cmds + cmdSequence([
          zwave.userCodeV1.userCodeSet(userIdentifier: state.configCode.slot.toInteger(), userIdStatus:UserCodeSet.USER_ID_STATUS_AVAILABLE_NOT_SET, userCode: 0),
          zwave.userCodeV1.userCodeGet(userIdentifier: state.configCode.slot.toInteger())
        ], 300)

        state.configCode.task = 'None'
        state.configCode.slot = ''
      } else {
        logger("warn", "rfidOperationCommands() - ${state.configCode.task} failed because tag number ${state.configCode.slot} is not in use")
      }
    break
    case 'Capture':
    case 'None':
    break
    default:
      logger("warn", "rfidOperationCommands() - Unhandled operation: ${state.configCode.task}")
    break
  }

  cmds
}

def parse(String description) {
  logger("debug", "parse() - description: ${description?.inspect()}")
  def result = []
  def cmd = zwave.parse(description, getCommandClassVersions())

  if (cmd) {
    if (cmd instanceof hubitat.zwave.commands.usercodev1.UserCodeReport) {
      logger("debug", "parse() - Enrich cmd with userCode")
      cmd.userCode = extractUserCode(description)
    }

    result = zwaveEvent(cmd)
    logger("debug", "parse() - parsed to cmd: ${cmd?.inspect()} with result: ${result?.inspect()}")
  } else {
    logger("error", "parse() - Non-parsed - description: ${description?.inspect()}")
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.usercodev1.UsersNumberReport cmd) {
  logger("trace", "zwaveEvent(UsersNumberReport) - cmd: ${cmd.inspect()}")
  state.deviceInfo['supportedUsers'] = cmd.supportedUsers
  []
}

def zwaveEvent(hubitat.zwave.commands.usercodev1.UserCodeReport cmd) {
  logger("trace", "zwaveEvent(UserCodeReport) - cmd: ${cmd.inspect()}")
  def result = []
  Integer slot = cmd.userIdentifier

  if (cmd.userIdStatus == UserCodeReport.USER_ID_STATUS_AVAILABLE_NOT_SET && slot == 0 ) {
    if (state.configCode.task == 'Capture') {
      logger("info", "Capturing code: ${cmd.userCode}")
      state.configCode.task = 'None'
      state.configCode.code = '' // Sync Bug
      state.configCode.code = cmd.userCode
    } else if (state.configCode.task == 'None') {
      logger("warn", "Unknown code: ${cmd.userCode}")
    }

  } else {
    if (cmd.userIdStatus == UserCodeReport.USER_ID_STATUS_OCCUPIED) {
      logger("info", "Remembering slot: ${slot} (${rfidName}) is set to ${cmd.userCode}")
      state.configSlots[Integer.toString(slot)] = cmd.userCode
      String rfidName = [who1, who2, who3, who4, who5, who6, who7, who8, who9, who10][slot - 1]
      result << createEvent(name: "rfid${slot}", value: "Used")
      result << createEvent(name: "rfidName${slot}", value: rfidName)

    } else if (cmd.userIdStatus == UserCodeReport.USER_ID_STATUS_AVAILABLE_NOT_SET) {
      logger("info", "Forgetting slot: ${slot}")
      state.configSlots[Integer.toString(slot)] = null
      result << createEvent(name: "rfid${slot}", value: "Empty")

    } else {
      logger("warn", "Unhandled Status: ${cmd.userIdStatus}")
    }
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
  logger("trace", "zwaveEvent(NotificationReport) - cmd: ${cmd.inspect()}")
  def result = []

  if (cmd.notificationType == 6) { // Known manual code or rfid is presented
    Integer slot = cmd.eventParameter.toString().replaceAll(/\[/, '').replaceAll(/\]/, '').toInteger()
    String rfidName = [who1, who2, who3, who4, who5, who6, who7, who8, who9, who10][slot - 1]
    if(rfidName == null) { rfidName = "User ${slot}" }

    switch (cmd.event) {
      case 5:
        // Recognised code or rfid pressed "Away"
        logger("info", "zwaveEvent(AlarmReport) - Away mode set by ${rfidName}")
        if (logDescText) { log.info "Away mode set by ${rfidName}" }

        if (param3.toInteger() == null || param3.toInteger() == 0) {
          result << createEvent(name: "switch", value: "on", descriptionText: "Away by ${rfidName}")
          result << createEvent(name: "mode", value: "away", descriptionText: "Away by ${rfidName}")
        }
        if (param3.toInteger() != null && param3.toInteger() > 0) {
          result << response(zwave.basicV1.basicSet(value: 0xFF))
        }

        result << createEvent(name: "user", value: rfidName, descriptionText: "Away by ${rfidName}")
        result << createEvent(name: "pushed", value: 2, descriptionText: "Away by ${rfidName}")
        startTimer(10, releaseButton)
      break
      case 6:
        // Recognised code or rfid pressed "Home"
        logger("info", "zwaveEvent(AlarmReport) - Home mode set by ${rfidName}")
        if (logDescText) { log.info "Home mode set by ${rfidName}" }

        result << createEvent(name: "switch", value: "off", descriptionText: "Home by ${rfidName}")
        result << createEvent(name: "mode", value: "home", descriptionText: "Home by ${rfidName}")
        result << createEvent(name: "user", value: rfidName, descriptionText: "Home by ${rfidName}")
        result << createEvent(name: "pushed", value: 1, descriptionText: "Home by ${rfidName}")
        startTimer(10, releaseButton)
      break
      default:
        logger("warn", "zwaveEvent(NotificationReport) - Unhandled cmd: ${cmd.inspect()}")
      break
    }

  } else if (cmd.notificationType == 7) {
    switch (cmd.event) {
      case 3:
        if (logDescText) { log.info "Tamper detected" }
        result << createEvent(name: "tamper", value: "detected", descriptionText: "Tamper detected", displayed: true)
        startTimer(30, clearTamper)
      break
      default:
        logger("error", "System hardware failure")
      break
    }

  } else {
    logger("warn", "zwaveEvent(NotificationReport) - Unhandled - cmd: ${cmd.inspect()}")
  }

  result << response(zwave.wakeUpV2.wakeUpNoMoreInformation())
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

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  logger("trace", "zwaveEvent(SwitchBinaryReport) - cmd: ${cmd.inspect()}")
  def result = []

  String value_name = (cmd.value ? "home" : "away")
  result << createEvent(name: "mode", value: "armed ${value_name}", descriptionText: "${value_name.capitalize()} by UI")

  String value = (cmd.value ? "off" : "on") // (home=off, away=on)
  result << createEvent(name: "switch", value: value, descriptionText: "Was turned ${value}")

  result
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger("trace", "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}")
  logger("info", "Device woke up")
  def cmds = []
  def result = []

  // Only send config if not synced
  if (!state?.driverInfo?.configSynced) {
    logger("info", "Synchronizing device config")
    cmds = cmds + cmdSequence([
      zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
      zwave.wakeUpV2.wakeUpIntervalSet(seconds:wakeUpInterval.toInteger() * 60, nodeid:zwaveHubNodeId),
      zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: param1.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: param2.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: param3.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: param4.toInteger()),
      zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: param5.toInteger())
    ], 300)
    state.driverInfo.configSynced = true
  }

  // Refresh if MSR is not set
  if (!getDataValue("MSR")) {
    logger("info", "Refresing device info")

    cmds = cmds + cmdSequence([
      zwave.versionV1.versionGet(),
      zwave.versionV1.versionCommandClassGet(),
      zwave.firmwareUpdateMdV2.firmwareMdGet(),
      zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
      zwave.switchBinaryV1.switchBinaryGet(),
      zwave.userCodeV1.usersNumberGet(),
      zwave.userCodeV1.userCodeGet(userIdentifier: 1),
      zwave.userCodeV1.userCodeGet(userIdentifier: 2),
      zwave.userCodeV1.userCodeGet(userIdentifier: 3),
      zwave.userCodeV1.userCodeGet(userIdentifier: 4),
      zwave.userCodeV1.userCodeGet(userIdentifier: 5),
      zwave.userCodeV1.userCodeGet(userIdentifier: 6),
      zwave.userCodeV1.userCodeGet(userIdentifier: 7),
      zwave.userCodeV1.userCodeGet(userIdentifier: 8),
      zwave.userCodeV1.userCodeGet(userIdentifier: 9),
      zwave.userCodeV1.userCodeGet(userIdentifier: 10)
    ], 300)
  }

  // Check battery level only once every Xh
  if (!state?.deviceInfo?.lastbatt || now() - state.deviceInfo.lastbatt >= batteryCheckInterval?.toInteger() *60*60*1000) {
    cmds = cmds + cmdSequence([zwave.batteryV1.batteryGet()], 100)
  }

  cmds = cmds + rfidOperationCommands()
  cmds = cmds + cmdSequence([zwave.wakeUpV2.wakeUpNoMoreInformation()], 500)
  result = result + response(cmds)

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
  state.deviceInfo['manufacturerName'] = "${cmd.manufacturerName}"
  state.deviceInfo['productId'] = "${cmd.productId}"
  state.deviceInfo['productTypeId'] = "${cmd.productTypeId}"

  String msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr) // Sync Bug
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
  return [0x85: 2, // COMMAND_CLASS_ASSOCIATION_V2 (Secure)
          0x80: 1, // COMMAND_CLASS_BATTERY
          0x84: 2, // COMMAND_CLASS_WAKE_UP_V2
          0x86: 1, // COMMAND_CLASS_VERSION (Insecure)
          0x72: 2, // COMMAND_CLASS_MANUFACTURER_SPECIFIC (Insecure)
          0x71: 3, // COMMAND_CLASS_ALARM (Secure)
          0x70: 2, // COMMAND_CLASS_CONFIGURATION_V2 (Secure)
          0x25: 1, // COMMAND_CLASS_SWITCH_BINARY
          0x63: 1  // COMMAND_CLASS_USER_CODE
  ]
}

private ArrayList extractUserCode(String description) {
  logger("trace", "extractUserCode() - description: ${description?.inspect()}")
  List code = []

  try {
    Integer payloadIndex = description.indexOf('payload')
    Integer payloadEnd = description.indexOf('isMulticast') - 4
    String payload = description[(payloadIndex + 9)..(payloadEnd)]

    if (payload != null && payload != '') {
      Integer codeIdx = 0
      for (int i = 6; i < 36; i+=3) {
        String payloadByte = (payload[i..(i+1)])
        Integer payloadInt = Integer.parseInt(payloadByte,16)
        code[codeIdx] = payloadInt
        codeIdx ++
      }
      logger("debug", "extractUserCode() - code: ${code} (${new String(code as byte[])})")

    } else {
      logger("warn", "extractUserCode() - Failed to extract payload")
    }

  } catch (e) {
    logger("error", "extractUserCode() - User code extraction failed: ${e.inspect()}")
  }

  code
}

// Convert ByteArray String to Hex String
// [143, 181, 75, 30, 123, 10, 1, 4, 0, 0] => 8fb54b1e7b0a01040000
private String code2Hex(String code) {
  logger("trace", "code2Hex() - code: ${code}")

  String codeHex = ""
  Integer indexStart = 0
  Integer indexEnd = code.indexOf(',')
  for (int i = 0; i < 10; i++) {
    Integer byteDec = code[indexStart+1..indexEnd-1] as int
    String codeByte = Integer.toHexString(byteDec)
    if (byteDec < 15) { codeHex = "${codeHex}0" }
    codeHex += codeByte

    indexStart = indexEnd + 1
    indexEnd = code.indexOf(',',indexEnd +1)
  }

  logger("debug", "code2Hex() - codeRaw: ${code}, codeHex: ${codeHex}")
  codeHex
}

def releaseButton() {
  sendEvent(name: "pushed", value: 0)
}

def clearTamper() {
  if (logDescText) { log.info "Tamper cleared" }
  sendEvent(name: "tamper", value: "clear")
}

private startTimer(seconds, function) {
  def now = new Date()
  def runTime = new Date(now.getTime() + (seconds * 1000))
  runOnce(runTime, function)
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Zipato/Zipato%20Mini%20RFID%20Keypad.groovy"]
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
