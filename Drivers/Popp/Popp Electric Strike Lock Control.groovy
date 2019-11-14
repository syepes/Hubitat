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

/**
  * zw:Ls type:4001 mfr:0154 prod:0005 model:0001 ver:1.05 zwv:4.38 lib:03 cc:5E,7A,73,5A,98,86,72 sec:30,71,70,59,85,62 role:05 ff:8300 ui:8300
  * Secure Features:
  *  30: Sensor Binary
  *  71: Alarm
  *  70: Configuration
  *  59: Association Grp Info
  *  85: Association
  *  62: Door Lock
  * Insecure Features:
  *  5E:
  *  7A: Firmware Update Md
  *  73: Powerlevel
  *  5A: Device Reset Locally
  *  98: Security
  *  86: Version
  *  72: Manufacturer Specific
  * Other:
  *  Zwaveplus Info
  *  Battery
  */

import hubitat.zwave.commands.doorlockv1.*
import hubitat.zwave.commands.usercodev1.*


public static String version() { return "v1.0.0" }

metadata {
  definition (name: "Popp Electric Strike Lock Control", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/Drivers/Popp/Popp Electric Strike Lock Control.groovy") {
    capability "Actuator"
    capability "Lock"
    capability "Sensor"
    capability "Battery"
    capability "Refresh"
    command "unlocktimer"
    fingerprint mfr: "0154", prod: "0005", model: "0001"
  }
  preferences {
    input name: "lockTimeout", type: "number", title: "Timeout", description: "Lock Timeout in Seconds", range: "1..59", defaultValue: 1, displayDuringSetup: true
    input name: "debugOutput", type: "bool", title: "Enable debug logging", description: "", defaultValue: false
  }
}



def updated() {
  if (debugOutput) log.debug "updated()"

  if (lockTimeout == null) lockTimeout = 0
  if (debugOutput == null) debugOutput = false

  // Set the configuration
  configure()

  // Device-Watch pings if no device events received for 1 hour (checkInterval)
  sendEvent(name: "checkInterval", value: 1 * 60 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def configure() {
  if (lockTimeout == null) lockTimeout = 0
  if (debugOutput == null) debugOutput = false

  def cmds = []
  cmds << zwave.doorLockV1.doorLockConfigurationSet(insideDoorHandlesState: 0, lockTimeoutMinutes: 0, lockTimeoutSeconds: lockTimeout.toInteger(), operationType: 2, outsideDoorHandlesState: 0).format()

  if (debugOutput) log.debug "configure() - cmds: ${cmds.inspect()}"
  delayBetween(cmds, 4200)
}

def parse(String description) {
  if (debugOutput) log.debug "parse() - description: ${description.inspect()}"
  def result = null

  if (description.startsWith("Err")) {
    if (state.sec) {
      result = createEvent(descriptionText:description, displayed:false)
    } else {
      result = createEvent(
        descriptionText: "This lock failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.",
        eventType: "ALERT",
        name: "secureInclusion",
        value: "failed",
        displayed: true,
      )
    }
  } else if (description == "updated") {
    return null
  } else {
    def cmd = zwave.parse(description, [ 0x98: 1, 0x72: 2, 0x85: 2, 0x86: 1 ])
    if (cmd) {
      result = zwaveEvent(cmd)
    }
  }
  if (debugOutput) log.debug "parse() - \"$description\" parsed to ${result.inspect()}"
  result
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  def encapsulatedCommand = cmd.encapsulatedCommand([0x62: 1, 0x71: 2, 0x80: 1, 0x85: 2, 0x63: 1, 0x98: 1, 0x86: 1])
  if (debugOutput) log.debug "zwaveEvent(SecurityMessageEncapsulation) - encapsulatedCommand: $encapsulatedCommand"
  if (encapsulatedCommand) {
    zwaveEvent(encapsulatedCommand)
  }
}

def zwaveEvent(DoorLockOperationReport cmd) {
  if (debugOutput) log.debug "zwaveEvent(DoorLockOperationReport) - cmd: ${cmd.inspect()}"

  def result = []
  unschedule("followupStateCheck")
  unschedule("stateCheck")

  def map = [ name: "lock" ]
  if (cmd.doorLockMode == 0) {
    if (debugOutput) log.debug "zwaveEvent(DoorLockOperationReport) - 0"
    log.info "unlocked - Strike Open (Permanently)"

    map.value = "unlocked"
    map.isStateChange = true
    map.displayed = true
    map.descriptionText = "Strike Open (Permanently)"
  } else if (cmd.doorLockMode == 1) {
    if (debugOutput) log.debug "zwaveEvent(DoorLockOperationReport) - 1"
    log.info "unlocked - Strike Open (Temporarily)"

    map.value = "unlocked"
    map.isStateChange = true
    map.displayed = true
    map.descriptionText = "Strike Open (Temporarily)"
  } else if (cmd.doorLockMode == 255) {
    if (debugOutput) log.debug "zwaveEvent(DoorLockOperationReport) - 255"
    log.info "locked - Strike Closed (Permanently)"

    map.value = "locked"
    map.isStateChange = true
    map.displayed = true
    map.descriptionText = "Strike Closed (Permanently)"
  } else {
    if (debugOutput) log.debug "zwaveEvent(DoorLockOperationReport) - ${cmd.doorLockMode}"
    log.warn "unknown - Strike Unknown (${cmd.doorLockMode})"

    map.value = "unknown"
    map.isStateChange = true
    if (state.assoc != zwaveHubNodeId) {
      if (debugOutput) log.debug "zwaveEvent(DoorLockOperationReport) - setting association"
      result << response(secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)))
      result << response(zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId))
      result << response(secure(zwave.associationV1.associationGet(groupingIdentifier:1)))
    }
  }

  result ? [createEvent(map), *result] : createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  if (debugOutput) log.debug "zwaveEvent(AssociationReport) - cmd: ${cmd.inspect()}"

  def result = []
  if (cmd.nodeId.any { it == zwaveHubNodeId }) {
    state.remove("associationQuery")
    if (debugOutput) log.debug "$device.displayName is associated to $zwaveHubNodeId"
    result << createEvent(descriptionText: "$device.displayName is associated")
    state.assoc = zwaveHubNodeId
    if (cmd.groupingIdentifier == 2) {
      result << response(zwave.associationV1.associationRemove(groupingIdentifier:1, nodeId:zwaveHubNodeId))
    }

  } else if (cmd.groupingIdentifier == 1) {
    result << response(secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)))

  } else if (cmd.groupingIdentifier == 2) {
    result << response(zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId))

  }
  result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
  if (debugOutput) log.debug "zwaveEvent(BasicSet) - cmd: ${cmd.inspect()}"

  def result = [ createEvent(name: "lock", value: cmd.value ? "unlocked" : "locked") ]
  def cmds = [ zwave.associationV1.associationRemove(groupingIdentifier:1, nodeId:zwaveHubNodeId).format(), "delay 1200", zwave.associationV1.associationGet(groupingIdentifier:2).format() ]

  [result, response(cmds)]
  if (debugOutput) log.debug "zwaveEvent(BasicSet) - result: ${result.inspect()}"
}

// Battery powered devices can be configured to periodically wake up and
// check in. They send this command and stay awake long enough to receive
// commands, or until they get a WakeUpNoMoreInformation command that
// instructs them that there are no more commands to receive and they can
// stop listening.
def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  if (debugOutput) log.debug "zwaveEvent(WakeUpNotification) - cmd: ${cmd.inspect()}"
  log.info "${device.displayName} woke up"

  def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]

  // Only ask for battery if we haven't had a BatteryReport in a while
  if (!state.lastbatt || (new Date().time) - state.lastbatt > 24*60*60*1000) {
    result << response(zwave.batteryV1.batteryGet())
    result << response("delay 1200") // leave time for device to respond to batteryGet
  }
  result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
  result
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  if (debugOutput) log.debug "zwaveEvent(BatteryReport) - cmd: ${cmd.inspect()}"

  def map = [ name: "battery", unit: "%" ]
  if (cmd.batteryLevel == 0xFF) {
    log.warn "$device.displayName has a low battery"
    map.value = 1
    map.descriptionText = "$device.displayName has a low battery"

  } else {
    log.info "$device.displayName battery is ${cmd.batteryLevel}%"
    map.value = cmd.batteryLevel
    map.descriptionText = "$device.displayName battery is ${cmd.batteryLevel}%"

  }
  state.lastbatt = now()
  createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  if (debugOutput) log.debug "zwaveEvent(ManufacturerSpecificReport) - cmd: ${cmd.inspect()}"

  def result = []
  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)

  updateDataValue("MSR", msr)

  result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)

  if (debugOutput) log.debug "zwaveEvent(ManufacturerSpecificReport) - result: ${result.inspect()}"
  result
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  if (debugOutput) log.debug "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}"

  def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
  updateDataValue("firmware", fw)
  if (state.MSR == "003B-6341-5044") {
    updateDataValue("ver", "${cmd.applicationVersion >> 4}.${cmd.applicationVersion & 0xF}")
  }
  def text = "$device.displayName: firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
  createEvent(descriptionText: text, isStateChange: false)
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  if (debugOutput) log.debug "zwaveEvent(Command) - cmd: ${cmd.inspect()}"

  createEvent(displayed: false, descriptionText: "$device.displayName: $cmd")
}

def lock() {
  if (debugOutput) log.debug "lock()"
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_SECURED)
}

def unlock() {
  if (debugOutput) log.debug "unlock()"
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_UNSECURED)
}

def unlocktimer() {
  if (debugOutput) log.debug "unlocktimer()"
  lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_UNSECURED_WITH_TIMEOUT)
}

def lockAndCheck(doorLockMode) {
  if (debugOutput) log.debug "lockAndCheck() - doorLockMode: ${doorLockMode}"
  secureSequence([ zwave.doorLockV1.doorLockOperationSet(doorLockMode: doorLockMode), zwave.doorLockV1.doorLockOperationGet() ], 4200)
}

def stateCheck() {
  if (debugOutput) log.debug "stateCheck()"
  sendHubCommand(new hubitat.device.HubAction(secure(zwave.doorLockV1.doorLockOperationGet())))
}


def refresh() {
  def cmds = [secure(zwave.doorLockV1.doorLockOperationGet()), secure(zwave.versionV1.versionGet())]
  if (debugOutput) log.debug "refresh() - cmds: ${cmds.inspect()}"

  if (state.assoc == zwaveHubNodeId) {
    if (debugOutput) log.debug "refresh() - $device.displayName is associated to ${state.assoc}"
  } else if (!state.associationQuery) {
    if (debugOutput) log.debug "refresh() - checking association"
    cmds << "delay 4200"
    cmds << zwave.associationV1.associationGet(groupingIdentifier:2).format() // old Schlage locks use group 2 and don't secure the Association CC
    cmds << secure(zwave.associationV1.associationGet(groupingIdentifier:1))
    state.associationQuery = now()
  } else if (secondsPast(state.associationQuery, 9)) {
    cmds << "delay 6000"
    cmds << zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
    cmds << secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId))
    cmds << zwave.associationV1.associationGet(groupingIdentifier:2).format()
    cmds << secure(zwave.associationV1.associationGet(groupingIdentifier:1))
    state.associationQuery = now()
  }
  cmds << zwave.batteryV1.batteryGet().format()
  cmds << secure(zwave.doorLockV1.doorLockConfigurationSet(insideDoorHandlesState: 0, lockTimeoutMinutes: 0, lockTimeoutSeconds: lockTimeout.toInteger(), operationType: 2, outsideDoorHandlesState: 0))
  if (debugOutput) log.debug "refresh() - sending cmds: ${cmds.inspect()}"
  cmds
}

def poll() {
  if (debugOutput) log.debug "poll()"

  def cmds = []
  // Only check lock state if it changed recently or we haven't had an update in an hour
  def latest = device.currentState("lock")?.date?.time

  if (!latest || !secondsPast(latest, 6 * 60) || secondsPast(state.lastPoll, 55 * 60)) {
    cmds << secure(zwave.doorLockV1.doorLockOperationGet())
    state.lastPoll = now()
  } else if (!state.lastbatt || now() - state.lastbatt > 53*60*60*1000) {
    cmds << secure(zwave.batteryV1.batteryGet())
    state.lastbatt = now() //inside-214
  }
  if (cmds) {
    if (debugOutput) log.debug "poll() - cmds: ${cmds.inspect()}"
    cmds
  } else {
    // workaround to keep polling from stopping due to lack of activity
    sendEvent(descriptionText: "skipping poll", isStateChange: true, displayed: false)
    null
  }
}

private secure(hubitat.zwave.Command cmd) {
  if (debugOutput) log.debug "secure(Command) - cmd: ${cmd.inspect()}"
  zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}


private secureSequence(Collection commands, ...delayBetweenArgs=4200) {
  if (debugOutput) log.debug "secureSequence(Command) - commands: ${commands.inspect()}"
  delayBetween(commands.collect{ secure(it) }, *delayBetweenArgs)
}

private Boolean secondsPast(timestamp, seconds) {
  if (!(timestamp instanceof Number)) {
    if (timestamp instanceof Date) {
      timestamp = timestamp.time
    } else if ((timestamp instanceof String) && timestamp.isNumber()) {
      timestamp = timestamp.toLong()
    } else {
      return true
    }
  }
  return (new Date().time - timestamp) > (seconds * 1000)
}
