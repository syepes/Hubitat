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
import com.hubitat.app.ChildDeviceWrapper

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Sonoff RF Bridge", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Sonoff/Sonoff%20RF%20Bridge.groovy") {
    capability "Actuator"
    capability "Refresh"
    capability "Initialize"
    capability "Configuration"

    command "clearState"
    attribute "status", "string"
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: true
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [2:"2min"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 2, required: true
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
  sendEvent(name: "status", value: "unknown", descriptionText: "Is unknown", displayed: true)
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
  checkState()
}

def configure() {
  logger("debug", "configure()")

  state.devicePings = 0
  if (stateCheckInterval.toInteger()) {
    if (['2' ,'5', '10', '15', '30'].contains(stateCheckInterval) ) {
      schedule("0 */${stateCheckInterval} * ? * *", checkState)
    } else {
      schedule("0 0 */${stateCheckInterval} ? * *", checkState)
    }
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
}

def checkState() {
  logger("debug", "checkState()")
  def cmds = []

  if (state?.devicePings >= 4) {
    if (device.currentValue('status') != 'offline') {
      sendEvent([ name: "status", value: 'offline', descriptionText: "Is offline", displayed: true])
    }
    logger("warn", "Device is offline")
  }

  state.devicePings = (state?.devicePings ?: 0) + 1

  cmds << getAction(getCommand("Status", 11))
  return cmds
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  def result = []

  Map descMap = parseDescriptionAsMap(description)

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
    JsonSlurper slurper = new JsonSlurper()
    def vd_data = slurper.parseText(parent?.json)

    ChildDeviceWrapper cd = getChildDevice(value)
    if (cd) {
        (vd_hub, vd_parent, vd_type, vd_name) = value?.split('-', 4)
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("windowShade")
            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.close
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              logger("debug", "childClose(${value}) - Shade: ${cv} -> closed")
              cd.parse([[name:"windowShade", value:"closed", descriptionText:"Was closed"]])
              cd.parse([[name:"switch", value:"off", descriptionText:"Was opened"]])
              if (logDescText) {
                log.info "${cd.displayName} Was closed"
              } else {
                logger("info", "${cd.displayName} Was closed")
              }
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
    JsonSlurper slurper = new JsonSlurper()
    def vd_data = slurper.parseText(parent?.json)

    ChildDeviceWrapper cd = getChildDevice(value)
    if (cd) {
        (vd_hub, vd_parent, vd_type, vd_name) = value?.split('-', 4)
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("windowShade")
            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.open
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              logger("debug", "childOpen(${value}) - Shade: ${cv} -> open")
              cd.parse([[name:"windowShade", value:"open", descriptionText:"Was opened"]])
              cd.parse([[name:"switch", value:"on", descriptionText:"Was opened"]])
              if (logDescText) {
                log.info "${cd.displayName} Was opened"
              } else {
                logger("info", "${cd.displayName} Was opened")
              }
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
    JsonSlurper slurper = new JsonSlurper()
    def vd_data = slurper.parseText(parent?.json)

    ChildDeviceWrapper cd = getChildDevice(value)
    if (cd) {
        (vd_hub, vd_parent, vd_type, vd_name) = value?.split('-', 4)
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("windowShade")
            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.stop
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              logger("debug", "childStop(${value}) - Shade: ${cv} -> partially open")
              cd.parse([[name:"windowShade", value:"partially open", descriptionText:"Was stopped"]])
              if (logDescText) {
                log.info "${cd.displayName} Was stopped"
              } else {
                logger("info", "${cd.displayName} Was stopped")
              }
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
    JsonSlurper slurper = new JsonSlurper()
    def vd_data = slurper.parseText(parent?.json)

    ChildDeviceWrapper cd = getChildDevice(value)
    if (cd) {
        (vd_hub, vd_parent, vd_type, vd_name) = value?.split('-', 4)
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("switch")
            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.off
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              logger("debug", "childOn(${value}) - switch: ${cv} -> off")
              cd.parse([[name:"switch", value:"off", descriptionText:"Was turned off"]])
              if (logDescText) {
                log.info "${cd.displayName} Was turned off"
              } else {
                logger("info", "${cd.displayName} Was turned off")
              }
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
    JsonSlurper slurper = new JsonSlurper()
    def vd_data = slurper.parseText(parent?.json)

    ChildDeviceWrapper cd = getChildDevice(value)
    if (cd) {
        (vd_hub, vd_parent, vd_type, vd_name) = value?.split('-', 4)
        if (vd_data?.containsKey(vd_type +':'+ vd_name)) {
            String cv = cd.currentValue("switch")
            String rf_cmd = vd_data[vd_type +':'+ vd_name]?.on
            if ( getActionNow(getCommand("Backlog", urlEscape("RfRaw ${rf_cmd}; RfRaw 0"))) ) {
              logger("debug", "childOff(${value}) - switch: ${cv} -> on")
              cd.parse([[name:"switch", value:"on", descriptionText:"Was turned on"]])
              if (logDescText) {
                log.info "${cd.displayName} Was turned on"
              } else {
                logger("info", "${cd.displayName} Was turned on")
              }
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

ChildDeviceWrapper addDevice(Map detail) {
  logger("debug", "addDevice(${detail?.inspect()})")
  logger("info", "Creating Device: ${detail?.name} (${detail?.type})")

  try {
    ChildDeviceWrapper cd = getChildDevice("${device.deviceNetworkId}-${detail?.type}-${detail?.name}")
    if(!cd) {
      logger("debug", "addDevice() - Creating Device (${detail.inspect()}")
      ChildDeviceWrapper cdm = addChildDevice("syepes", "Sonoff RF Bridge - ${detail?.type}", "${device.deviceNetworkId}-${detail?.type}-${detail?.name}", [name: "${detail?.type} ${detail?.name}", label: "${detail?.type} ${detail?.name}", isComponent: true])
      switch (detail?.type) {
        case "Shade":
          cdm.parse([[name:"windowShade", value:"closed"]])
        break
        case "Switch":
          cdm.parse([[name:"switch", value:"off"]])
        break
        default :
          logger("error", "addDevice(${detail?.inspect()}) - Device type not found")
        break
      }

      return cdm
    } else {
      logger("debug", "addDevice() - Device: ${cd.name} (${detail.type}) (${device.deviceNetworkId}-${detail?.type}-${detail?.name}) already exists")
      return cd
    }
  } catch (e) {
    logger("error", "addDevice() - Room creation Exception: ${e.inspect()}")
    return null
  }
}

private Map parseDescriptionAsMap(description) {
  logger("trace", "parseDescriptionAsMap() - description: ${description.inspect()}")
  try {
    Map descMap = description.split(",").inject([:]) { map, param ->
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
        JsonSlurper slurper = new JsonSlurper()
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
private boolean getActionNow(uri) {
  logger("debug", "getActionNow(${uri})")

  try {
    String host_port = parent?.host_port

    httpGet(["uri": "http://${host_port}" + uri, "contentType": "application/json; charset=utf-8"]) { resp ->
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

private String getCommand(command, value=null) {
  logger("debug", "getCommand(command=${command}, value=${value})")
  String uri = "/cm?"
  String user = parent?.user
  String password = parent?.password

  if (user != null && password != null) {
    uri += "user=${user}&password=${password}&"
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

private Map getHeader() {
  String host_port = parent?.host_port
  String user = parent?.user
  String password = parent?.password

  Map headers = [:]
  headers.put("Host", host_port)
  headers.put("Content-Type", "application/x-www-form-urlencoded")

  if (user != null && password != null) {
    String auth = "Basic " + "${user}:${password}".bytes.encodeBase64().toString()
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
      log."${level}" "${device.displayName} ${msg}"
    }
  }
}
