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

import groovy.json.JsonSlurper
import groovy.transform.Field

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

/*
Example VD_JSON definition json string
{
  "Shade:Bedroom":{
    "close":"< B0 String that closes the Shade >",
    "open":"< B0 String that opens the Shade >",
    "stop":"< B0 String to stop the Shade >"
  },
  "Switch:Radio":{
    "on":"< B0 String turn on the Switch >",
    "off":"< B0 String turn off the Switch >"
  }
}
*/
@Field String VD_JSON = '{}'

metadata {
  definition (name: "Sonoff RF Bridge", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Sonoff/Sonoff%20RF%20Bridge.groovy") {
    capability "Actuator"
    capability "Refresh"
    capability "Initialize"
    capability "Configuration"

    command "clearState"
    command "cleanChild"
    attribute "status", "string"
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 5, required: true
    }
    section { // Configuration
      input name: "deviceAddress", title: "Device IP and PORT", type: "text", defaultValue: "ip:port", required: true
      input name: "deviceAuthUsr", title: "Device Auth Username", type: "text", defaultValue: "", required: false
      input name: "deviceAuthPwd", title: "Device Auth Password", type: "text", defaultValue: "", required: false
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
  sendEvent(name: "status", value: "unknown", descriptionText: "Is unknown", displayed: true)

  def slurper = new JsonSlurper()
  def vd_data = slurper.parseText(VD_JSON)

  // Create virtual devices
  vd_data?.each {
    logger("info", "configure() - Creating Virtual Device: ${it.key?.split(':')?.getAt(1)} (${it.key?.split(':')?.getAt(0)})")
    def vd = findOrCreateChild(it.key?.split(':')?.getAt(0), it.key?.split(':')?.getAt(1))
  }
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }

  if (!state.deviceInfo) {
    refresh()
  }

  unschedule()
  configure()
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
  getDeviceInfo()
}

def configure() {
  logger("debug", "configure()")

  state.devicePings = 0

  schedule("0 0 12 */7 * ?", updateCheck)

  if (stateCheckInterval) {
    if (['5', '10', '15', '30'].contains(stateCheckInterval) ) {
      schedule("0 */${stateCheckInterval} * ? * *", checkState)
    } else {
      schedule("0 0 */${stateCheckInterval} ? * *", checkState)
    }
  }
}

def cleanChild() {
  logger("debug", "cleanChild() - childDevices: ${childDevices?.size()}")
  childDevices?.each{ deleteChildDevice(it.deviceNetworkId) }
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
}

def checkState() {
  logger("debug", "checkState()")
  def cmds = []

  if (state?.devicePings >= 3) {
    if (device.currentValue('status') != 'offline') {
      sendEvent([ name: "status", value: 'offline', descriptionText: "Is offline", displayed: true])
    }
    logger("warn", "Device is offline")
  }

  state.devicePings = state.devicePings + 1

  cmds << getAction(getCommand("Status", 11))
  return cmds
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  def result = []

  def descMap = parseDescriptionAsMap(description)

  if (!descMap?.isEmpty()) {
    if (descMap["body"]?.containsKey("StatusSTS")) {
      if (descMap["body"].StatusSTS?.UptimeSec > 0) {
        deviceUpdate()
      } else {
        if (device.currentValue('status') != 'offline') {
          result << createEvent([ name: "status", value: 'offline', descriptionText: "Is offline", displayed: true])
        }
      }
    }

    if (descMap["body"]?.containsKey("StatusFWR")) {
      state.deviceInfo = descMap["body"].StatusFWR
    }
  }

  logger("debug", "parse() - descMap: ${descMap?.inspect()} with result: ${result?.inspect()}")
  result
}

def deviceUpdate() {
  // Sets the device status to online, but only if previously was offline
  Map deviceState = [ name: "status",
                      value: 'online',
                      descriptionText: "Is online",
                      displayed: true
                    ]

  state.devicePings = 0
  logger("info", "Device is online")

  sendEvent(deviceState)
}

def getDeviceInfo() {
  logger("debug", "getDeviceInfo()")
  def cmds = []

  cmds << getAction(getCommand("Status", 2))
  return cmds
}

// Capability: Shade
private def childClose(String value) {
  logger("debug", "childClose(${value})")

  try {
    def slurper = new JsonSlurper()
    def vd_data = slurper.parseText(VD_JSON)

    def cd = getChildDevice(value)
    if (cd) {
        (vd_parent, vd_type, vd_name) = value?.split('-')
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("windowShade")
            logger("info", "childClose(${value}) - Shade: ${cv} -> closing")

            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.close
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              cd.parse([[name:"windowShade", value:"closed", descriptionText:"Was closed"]])
              cd.parse([[name:"switch", value:"off", descriptionText:"Was opened"]])
              if(logDescText) { log.info "Was closed" }
            }

        } else {
          logger("warn", "childClose(${value}) - Could not find the Virtual Device definition")
        }
    } else {
      logger("warn", "childClose(${value}) - Could not find the Virtual Device")
      configure()
    }
  } catch (e) {
    logger("error", "childClose(${value}) - ${e.inspect()}")
  }
}

// Capability: Shade
private def childOpen(String value) {
  logger("debug", "childOpen(${value})")

  try {
    def slurper = new JsonSlurper()
    def vd_data = slurper.parseText(VD_JSON)

    def cd = getChildDevice(value)
    if (cd) {
        (vd_parent, vd_type, vd_name) = value?.split('-')
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("windowShade")
            logger("info", "childOpen(${value}) - Shade: ${cv} -> opening")

            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.open
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              cd.parse([[name:"windowShade", value:"closed", descriptionText:"Was opened"]])
              cd.parse([[name:"switch", value:"on", descriptionText:"Was opened"]])
              if(logDescText) { log.info "Was opened" }
            }

        } else {
          logger("warn", "childOpen(${value}) - Could not find the Virtual Device definition")
        }
    } else {
      logger("warn", "childOpen(${value}) - Could not find the Virtual Device")
      configure()
    }
  } catch (e) {
    logger("error", "childOpen(${value}) - ${e.inspect()}")
  }
}

// Capability: Shade
private def childStop(String value) {
  logger("debug", "childStop(${value})")

  try {
    def slurper = new JsonSlurper()
    def vd_data = slurper.parseText(VD_JSON)

    def cd = getChildDevice(value)
    if (cd) {
        (vd_parent, vd_type, vd_name) = value?.split('-')
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("windowShade")
            logger("info", "childStop(${value}) - Shade: ${cv} -> stopping")

            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.stop
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              cd.parse([[name:"windowShade", value:"partially open", descriptionText:"Was stopped"]])
              if(logDescText) { log.info "Was stopped" }
            }

        } else {
          logger("warn", "childStop(${value}) - Could not find the Virtual Device definition")
        }
    } else {
      logger("warn", "childStop(${value}) - Could not find the Virtual Device")
      configure()
    }
  } catch (e) {
    logger("error", "childStop(${value}) - ${e.inspect()}")
  }
}

// Capability: Shade
private def childPosition(String value, BigDecimal position) {
  logger("debug", "childPosition(${value},${position})")
}


// Capability: Switch
private def childOn(String value) {
  logger("debug", "childOn(${value})")

  try {
    def slurper = new JsonSlurper()
    def vd_data = slurper.parseText(VD_JSON)

    def cd = getChildDevice(value)
    if (cd) {
        (vd_parent, vd_type, vd_name) = value?.split('-')
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("switch")
            logger("info", "childOn(${value}) - switch: ${cv} -> off")

            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.off
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              cd.parse([[name:"switch", value:"off", descriptionText:"Was turned off"]])
              if(logDescText) { log.info "Was turned off" }
            }

        } else {
          logger("warn", "childOn(${value}) - Could not find the Virtual Device definition")
        }
    } else {
      logger("warn", "childOn(${value}) - Could not find the Virtual Device")
      configure()
    }
  } catch (e) {
    logger("error", "childOn(${value}) - ${e.inspect()}")
  }
}

// Capability: Switch
private def childOff(String value) {
  logger("debug", "childOff(${value})")

  try {
    def slurper = new JsonSlurper()
    def vd_data = slurper.parseText(VD_JSON)

    def cd = getChildDevice(value)
    if (cd) {
        (vd_parent, vd_type, vd_name) = value?.split('-')
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("switch")
            logger("info", "childOff(${value}) - switch: ${cv} -> on")

            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.on
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              cd.parse([[name:"switch", value:"on", descriptionText:"Was turned on"]])
              if(logDescText) { log.info "Was turned on" }
            }

        } else {
          logger("warn", "childOff(${value}) - Could not find the Virtual Device definition")
        }
    } else {
      logger("warn", "childOff(${value}) - Could not find the Virtual Device")
      configure()
    }
  } catch (e) {
    logger("error", "childOff(${value}) - ${e.inspect()}")
  }
}

// Finds / Creates the child device
private def findOrCreateChild(String type, String name) {
  logger("debug", "findOrCreateChild(${type},${name})")
  try {
    String thisId = device.id
    def cd = getChildDevice("${thisId}-${type}-${name}")
    if (!cd) {
      switch (type) {
        case "Shade":
          cd = addChildDevice("Sonoff RF Bridge - ${type} Child Device", "${thisId}-${type}-${name}", [name: "${type} ${name}", label: "${type} ${name}", isComponent: true])
          cd.parse([[name:"windowShade", value:"closed"]])
        break
        case "Switch":
          cd = addChildDevice("Sonoff RF Bridge - ${type} Child Device", "${thisId}-${type}-${name}", [name: "${type} ${name}", label: "${type} ${name}", isComponent: true])
          cd.parse([[name:"switch", value:"off"]])
        break
        default :
          logger("error", "findOrCreateChild(${type},${name}) - Device type not found")
        break
      }
    }
    return cd
  } catch (e) {
    logger("error", "findOrCreateChild(${type},${name}) - e: ${e}")
  }
}

private parseDescriptionAsMap(description) {
  logger("trace", "parseDescriptionAsMap() - description: ${description.inspect()}")
  try {
    def descMap = description.split(",").inject([:]) { map, param ->
      def nameAndValue = param.split(":")
      if (nameAndValue.length == 2){
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
      } else {
        map += [(nameAndValue[0].trim()):""]
      }
    }

    def headers = new String(descMap["headers"]?.decodeBase64())
    def status_code = headers?.tokenize('\r\n')[0]
    headers = headers?.tokenize('\r\n')?.toList()[1..-1]?.collectEntries{
      it.split(":",2).with{ [ (it[0]): (it.size()<2) ? null : it[1] ?: null ] }
    }

    def body = new String(descMap["body"]?.decodeBase64())
    def body_json
    logger("trace", "parseDescriptionAsMap() - headers: ${headers.inspect()}, body: ${body.inspect()}")

    if (body && body != "") {
      if(body.startsWith("{") || body.startsWith("[")) {
        def slurper = new JsonSlurper()
        body_json = slurper.parseText(body)
        logger("trace", "parseDescriptionAsMap() - body_json: ${body_json}")
      }
    }

    return [desc: descMap.subMap(['mac','ip','port']), status_code: status_code, headers:headers, body:body_json]
  } catch (e) {
    logger("error", "parseDescriptionAsMap() - ${e.inspect()}")
    return [:]
  }
}

// Synchronous call
private getActionNow(uri) {
  logger("debug", "getActionNow() - uri: ${uri.inspect()}")

  try {
    httpGet(["uri": "http://${deviceAddress}" + uri, "contentType": "application/json; charset=utf-8"]) { resp ->
      logger("debug", "getActionNow() - respStatus: ${resp.getStatus()}, respHeaders: ${resp.getAllHeaders()?.inspect()}, respData: ${resp.getData()}")
      if (resp.success && resp?.getData()?.isEmpty()) {
        return true
      } else {
        logger("error", "getActionNow() - respStatus: ${resp.getStatus()}, respHeaders: ${resp.getAllHeaders()?.inspect()}, respData: ${resp.getData()}")
        return false
      }
    }
  } catch (Exception e) {
    logger("error", "getActionNow() - e: ${e.inspect()}")
  }
}

// Asynchronous call
private getAction(uri) {
  def headers = getHeader()
  def hubAction = new hubitat.device.HubAction(method: "GET", path: uri, headers: headers)
  return hubAction
}

private def getCommand(command, value=null) {
  String uri = "/cm?"
  if (deviceAuthUsr != null && deviceAuthPwd != null) {
    uri += "user=${deviceAuthUsr}&password=${deviceAuthPwd}&"
  }
  if (value) {
    uri += "cmnd=${command}%20${value}"
  } else {
    uri += "cmnd=${command}"
  }
  return uri
}

private String urlEscape(url) {
  return(URLEncoder.encode(url).replace("+", "%20"))
}

private getHeader() {
  def headers = [:]
  headers.put("Host", deviceAddress)
  headers.put("Content-Type", "application/x-www-form-urlencoded")

  if (deviceAuthUsr != null && deviceAuthPwd != null) {
    String auth = "Basic " + "${username}:${password}".bytes.encodeBase64().toString()
    headers.put("Authorization", auth)
  }

  return headers
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Sonoff/Sonoff%20RF%20Bridge.groovy"]
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
