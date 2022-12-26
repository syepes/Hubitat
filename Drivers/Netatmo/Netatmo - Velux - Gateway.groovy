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
import groovy.json.JsonSlurper
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Field String VERSION = "1.0.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Netatmo - Velux - Gateway", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Velux%20-%20Gateway.groovy") {
    capability "Actuator"
    capability "Refresh"

    attribute "id", "string"
    attribute "type", "string"
    attribute "velux_type", "string"
    attribute "name", "string"
    attribute "homeName", "string"

    attribute "firmware_revision_netatmo", "number"
    attribute "firmware_revision_thirdparty", "string"
    attribute "is_raining", "string"
    attribute "busy", "string"
    attribute "calibrating", "string"
    attribute "outdated_weather_forecast", "string"
    attribute "locked", "string"
    attribute "locking", "string"
    attribute "pairing", "string"
    attribute "secure", "string"
    attribute "wifi_strength", "number"
    attribute "wifi_state", "string"
    attribute "last_seen", "number"
  }
  preferences {
    section { // Gateway Encryption
      input name: "signKeyHash", title: "Hash Sign Key", description: "Gateway Encryption Key", type: "text", required: false
      input name: "signKeyId", title: "Sign Key Id", description: "Gateway Encryption Key ID", type: "text", required: false
    }
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
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

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  return []
}

def setHome(homeID,homeName) {
  logger("debug", "setHome(${homeID?.inspect()}, ${homeName?.inspect()})")
  state.deviceInfo['homeID'] = homeID
  sendEvent(name: "homeName", value: homeName)
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  state.deviceInfo['velux_type'] = "gateway"
  state.deviceInfo['homeID'] = detail.homeID
  state.deviceInfo['roomID'] = detail.id
  sendEvent(name: "velux_type", value: "gateway")
  sendEvent(name: "homeName", value: detail.homeName)
}

def setStates(Map states) {
  logger("debug", "setStates(${states?.inspect()})")
  states?.each { k, v ->
    String cv = device.currentValue(k)
    boolean isStateChange = (cv?.toString() != v?.toString()) ? true : false
    if (isStateChange) {
      if (logDescText && k != "last_seen" && k != "wifi_strength") {
        log.info "${device.displayName} Value change: ${k} = ${cv} != ${v}"
      } else {
        logger("debug", "setStates() - Value change: ${k} = ${cv} != ${v}")
      }
    }
    sendEvent(name: "${k}", value: "${v}", displayed: true, isStateChange: isStateChange)
  }
}

def setScenario(String scenario_type) {
  logger("debug", "setScenario(${scenario_type})")

  try {
    def app = parent?.getParent()
    String auth = app.state.authToken

    Map params = [
      uri: "https://app.velux-active.com/syncapi/v1/setstate",
      headers: ["Authorization": "Bearer ${auth}"],
      body: ["app_type": "app_velux", "home":["id": state.deviceInfo.homeID, "modules":[["id": state.deviceInfo.roomID, "scenario": scenario_type]]]],
      timeout: 15
    ]

    logger("trace", "setScenario() - PARAMS: ${params.inspect()}")
    httpPostJson(params) { resp ->
      logger("trace", "setScenario() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      logger("debug", "setScenario() - respStatus: ${resp?.getStatus()}, respData: ${resp?.getData()}")
      if (resp && resp.getStatus() == 200 && resp?.getData()?.body?.errors == null) {
        if (logDescText) {
          log.info "${device.displayName} Setting Home Scenario = ${scenario_type}"
        } else {
          logger("info", "setScenario() - Setting Home Scenario = ${scenario_type}")
        }
      } else {
        logger("error", "setScenario() - Failed: ${resp?.getData()?.body?.errors}")
      }
    }
  } catch (Exception e) {
    logger("error", "setScenario() - Request Exception: ${e.inspect()}")
  }
}

def setScenarioWithPin(String scenario_type) {
  logger("debug", "setScenarioWithPin(${scenario_type})")

  try {
    def app = parent?.getParent()
    String auth = app.state.authToken

    Integer ts = (new Date().getTime()/1000) as int
    String sign_key_id = signKeyId.padLeft(32,"0").decodeHex().encodeBase64().toString().replaceAll('\\+','-').replaceAll('/','_')
    String hash = generateHash(signKeyHash, "scenario${scenario_type}", 0, ts, 0, state.deviceInfo.roomID)

    Map params = [
      uri: "https://app.velux-active.com/syncapi/v1/setstate",
      headers: ["Authorization": "Bearer ${auth}"],
      body: ["app_type": "app_velux", "home":["id": state.deviceInfo.homeID, "timezone": location?.timeZone?.ID, "modules":[["id": state.deviceInfo.roomID, "scenario": scenario_type, "nonce": 0, "sign_key_id": sign_key_id, "hash_scenario": hash, "timestamp": ts]]]],
      timeout: 15
    ]

    logger("trace", "setScenarioWithPin() - PARAMS: ${params.inspect()}")
    httpPostJson(params) { resp ->
      logger("trace", "setScenarioWithPin() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      logger("debug", "setScenarioWithPin() - respStatus: ${resp?.getStatus()}, respData: ${resp?.getData()}")
      if (resp && resp.getStatus() == 200 && resp?.getData()?.body?.errors == null) {
        if (logDescText) {
          log.info "${device.displayName} Setting Home Scenario = ${scenario_type}"
        } else {
          logger("info", "setScenarioWithPin() - Setting Home Scenario = ${scenario_type}")
        }
      } else {
        logger("error", "setScenarioWithPin() - Failed: ${resp?.getData()?.body?.errors}")
      }
    }
  } catch (Exception e) {
    logger("error", "setScenarioWithPin() - Request Exception: ${e.inspect()}")
  }
}

def setPositionWithPin(String deviceid, BigDecimal value) {
  logger("debug", "setPositionWithPin(${deviceid},${value})")

  if (signKeyHash == null || signKeyHash == "" || signKeyId == null || signKeyId == "") {
    logger("warn", "setPositionWithPin() - The Gateway encryption keys are required to open this device")
    return
  }

  try {
    def app = parent?.getParent()
    String auth = app.state.authToken
    Integer ts = (new Date().getTime()/1000) as int
    String sign_key_id = signKeyId.padLeft(32,"0").decodeHex().encodeBase64().toString().replaceAll('\\+','-').replaceAll('/','_')
    String hash = generateHash(signKeyHash, "target_position", value.toInteger(), ts, 0, deviceid)

    Map params = [
      uri: "https://app.velux-active.com/syncapi/v1/setstate",
      headers: ["Authorization": "Bearer ${auth}"],
      body: ["app_type": "app_velux", "home":["id": state.deviceInfo.homeID, "timezone": location?.timeZone?.ID, "modules":[["bridge": state.deviceInfo.roomID, "id": deviceid, "force": true, "target_position": value, "nonce": 0, "sign_key_id": sign_key_id, "hash_target_position": hash, "timestamp": ts]]]],
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

private String generateHash(String hashSignKey, String item_name, Integer level, Integer ts, Integer nonce, String deviceid) {
  logger("debug", "generateHash(${hashSignKey},${item_name},${level},${ts},${nonce},${deviceid})")

  String pre_hash
  if (level.toInteger() == 0) {
    pre_hash = "${item_name}${ts}${nonce}${deviceid}"
  } else {
    pre_hash = "${item_name}${level}${ts}${nonce}${deviceid}"
  }
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
