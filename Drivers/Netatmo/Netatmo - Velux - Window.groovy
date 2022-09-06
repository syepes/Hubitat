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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Netatmo - Velux - Window", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Velux%20-%20Window.groovy") {
    capability "Actuator"
    capability "WindowShade"
    command "stop"

    attribute "id", "string"
    attribute "type", "string"
    attribute "velux_type", "string"
    attribute "manufacturer", "string"
    attribute "bridge", "string"
    attribute "group_id", "number"
    attribute "homeName", "string"
    attribute "roomName", "string"

    attribute "reachable", "string"
    attribute "last_seen", "number"
    attribute "firmware_revision", "number"
    attribute "current_position", "number"
    attribute "target_position", "number"
    attribute "secure_position", "number"
    attribute "rain_position", "number"
    attribute "mode", "string"
    attribute "silent", "string"
  }
  preferences {
    section { // Gateway Encryption
      input name: "signKeyHash", title: "Hash Sign Key", description: "Gateway Encryption Key", type: "text", required: false
      input name: "signKeyId", title: "Sign Key Id", description: "Gateway Encryption Key ID", type: "text", required: false
    }
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: true, required: false
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

def uninstalled() {
  logger("debug", "uninstalled()")
  unschedule()
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }
  unschedule()
  initialize()
}

def initialize() {
  logger("debug", "initialize()")
}

def parse(value) {
  logger("debug", "parse() - value: ${value?.inspect()}")
  if (value) {
    sendEvent(value)
  }
}

def close() {
  logger("debug", "close()")
  sendEvent([name: "windowShade", value: "closing", displayed: true])
  setPosition(0)
}
def off() {
  logger("debug", "off()")
  close()
}

def open() {
  logger("debug", "open()")
  sendEvent(name: "windowShade", value: "opening", displayed: true)
  setPositionWithPin(100)
}
def on() {
  logger("debug", "on()")
  open()
}

def startPositionChange(value) {
  logger("debug", "startPositionChange(${value})")

  switch (value) {
    case "close":
      close()
      return
    case "open":
      open()
      return
    default:
      logger("error", "startPositionChange(${value}) - Unsupported state")
  }
}

def setPosition(BigDecimal value) {
  logger("debug", "setPosition(${value})")

  try {
    def app = parent?.getParent()?.getParent()
    String auth = app.state.authToken

    Map params = [
      uri: "https://app.velux-active.com/syncapi/v1/setstate",
      headers: ["Authorization": "Bearer ${auth}"],
      body: ["home":["id": state.deviceInfo.homeID, "modules":[["bridge": state.deviceInfo.bridge, "id": state.deviceInfo.id, "target_position": value]]]],
      timeout: 15
    ]

    if (logDescText) {
      log.info "${device.displayName} Setting Position = ${value}"
    } else {
      logger("info", "setPosition() - Setting Position = ${value}")
    }

    logger("trace", "setPosition() - PARAMS: ${params.inspect()}")
    httpPostJson(params) { resp ->
      logger("trace", "setPosition() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      logger("debug", "setPosition() - respStatus: ${resp?.getStatus()}, respData: ${resp?.getData()}")
      if (resp && resp.getStatus() == 200 && resp?.getData()?.body?.errors == null) {
        if (logDescText) {
          log.info "${device.displayName} Setting Position = ${value}"
        } else {
          logger("info", "setPosition() - Setting Position = ${value}")
        }
      } else {
        logger("error", "setPosition() - Failed: ${resp?.getData()?.body?.errors}")
      }
    }
  } catch (Exception e) {
    logger("error", "setPosition() - Request Exception: ${e.inspect()}")
  }
}

def setPositionWithPin(BigDecimal value) {
  logger("debug", "setPositionWithPin(${value})")

  if (signKeyHash == null || signKeyHash == "" || signKeyId == null || signKeyId == "") {
    logger("warn", "setPositionWithPin() - The Gateway encryption keys are required to open this device")
    return
  }

  try {
    def app = parent?.getParent()?.getParent()
    String auth = app.state.authToken
    Integer ts = (new Date().getTime()/1000) as int
    String sign_key_id = signKeyId.padLeft(32,"0").decodeHex().encodeBase64().toString().replaceAll('\\+','-').replaceAll('/','_')
    String hash = generateHash(signKeyHash, "target_position", value.toInteger(), ts, 0, state.deviceInfo.id)

    Map params = [
      uri: "https://app.velux-active.com/syncapi/v1/setstate",
      headers: ["Authorization": "Bearer ${auth}"],
      body: ["home":["id": state.deviceInfo.homeID, "modules":[["bridge": state.deviceInfo.bridge, "id": state.deviceInfo.id, "target_position": value, "nonce": 0, "sign_key_id": sign_key_id, "hash_target_position": hash, "timestamp": ts]]]],
      timeout: 15
    ]

    logger("trace", "setPositionWithPin() - PARAMS: ${params.inspect()}")
    httpPostJson(params) { resp ->
      logger("trace", "setPositionWithPin() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      logger("debug", "setPositionWithPin() - respStatus: ${resp?.getStatus()}, respData: ${resp?.getData()}")
      if (resp && resp.getStatus() == 200 && resp?.getData()?.body?.errors == null) {
        if (logDescText) {
          log.info "${device.displayName} Setting Position = ${value}"
        } else {
          logger("info", "setPositionWithPin() - Setting Position = ${value}")
        }
      } else {
        logger("error", "setPositionWithPin() - Failed: ${resp?.getData()?.body?.errors}")
      }
    }
  } catch (Exception e) {
    logger("error", "setPositionWithPin() - Request Exception: ${e.inspect()}")
  }
}

def setLevel(BigDecimal value) {
  logger("debug", "setLevel(${value})")
  String cv = device.currentValue("position")
  if (cv && cv.toInteger() == 0) {
    setPositionWithPin(value)
  } else {
    setPosition(value)
  }
}

def stop() {
  logger("debug", "stop()")
  try {
    def app = parent?.getParent()?.getParent()
    String auth = app.state.authToken

    Map params = [
      uri: "https://app.velux-active.com/syncapi/v1/setstate",
      headers: ["Authorization": "Bearer ${auth}"],
      body: ["home":["id": state.deviceInfo.homeID, "modules":[["id": state.deviceInfo.bridge, "stop_movements": "all"]]]],
      timeout: 15
    ]

    logger("trace", "stop() - PARAMS: ${params.inspect()}")
    httpPostJson(params) { resp ->
      logger("trace", "stop() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      logger("debug", "stop() - respStatus: ${resp?.getStatus()}, respData: ${resp?.getData()}")
      if (resp && resp.getStatus() == 200 && resp?.getData()?.body?.errors == null) {
        if (logDescText) {
          log.info "${device.displayName} Stopping all movements"
        } else {
          logger("info", "stop() - Stopping all movements")
        }
      } else {
        logger("error", "stop() - Failed: ${resp?.getData()?.body?.errors}")
      }
    }
  } catch (Exception e) {
    logger("error", "stop() - Request Exception: ${e.inspect()}")
  }
}

def stopPositionChange() {
  logger("debug", "stopPositionChange()")
  stop()
}

String generateHash(String hashSignKey, String item_name, Integer level, Integer ts, Integer nonce, String deviceid) {
  logger("debug", "generateHash(${hashSignKey},${item_name}.${level},${ts},${nonce},${deviceid})")

  String pre_hash = "${item_name}${level}${ts}${nonce}${deviceid}"
  logger("trace", "generateHash() - pre_hash: ${pre_hash}")

  byte[] key
  try {
    key = hashSignKey.decodeHex()
  } catch (Exception e) {
    logger("error", "generateHash() - Invalid hashSignKey")
  }
  final SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA512");
  final Mac mac = Mac.getInstance("HmacSHA512");
  mac.init(keySpec);
  return mac.doFinal("${pre_hash}".getBytes("UTF-8")).encodeBase64().toString().replaceAll('\\+','-').replaceAll('/','_')
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  state.deviceInfo['homeID'] = detail.homeID
  if (detail?.roomID) { state.deviceInfo['roomID'] = detail.roomID }
  if (detail?.id) { state.deviceInfo['id'] = detail.id }
  state.deviceInfo['bridge'] = detail.bridge
  sendEvent(name: "homeName", value: detail.homeName)
  sendEvent(name: "bridge", value: detail.bridge)
}

def setStates(Map states) {
  logger("debug", "setStates(${states?.inspect()})")
  states?.each { k, v ->
    String cv = device.currentValue(k)
    boolean isStateChange = (cv?.toString() != v?.toString() ? true : false)
    if (isStateChange) {
      if (logDescText && k != "last_seen") {
        log.info "${device.displayName} Value change: ${k} = ${cv} != ${v}"
      } else {
        logger("debug", "setStates() - Value change: ${k} = ${cv} != ${v}")
      }
    }
    sendEvent(name: "${k}", value: "${v}", displayed: true, isStateChange: isStateChange)

    if (k == "current_position") {
      sendEvent(name: "position", value: v, displayed: true, isStateChange: isStateChange)
      if (v == 0) {
        sendEvent(name: "windowShade", value: "closed", displayed: true, isStateChange: isStateChange)
      } else if (v == 100 ) {
        sendEvent(name: "windowShade", value: "open", displayed: true, isStateChange: isStateChange)
      } else {
        sendEvent(name: "windowShade", value: "partially open", displayed: true, isStateChange: isStateChange)
      }
    }
    if (k == "reachable" && v == "false") {
      logger("warn", "Device is not reachable")
    }
  }
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
