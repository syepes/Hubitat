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
 * Note: This driver is specially made for controlling the two outputs connected with 1N4007 diodes, this allows the controlling of the thermostat heating systems four order modes.
 */

import groovy.json.*
import groovy.transform.Field

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "ShellyPlus Pilot Wire", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Shelly/ShellyPlus%20Pilot%20Wire.groovy") {
    capability "Actuator"
    capability "Switch"
    capability "Sensor"
    capability "PowerMeter"
    capability "CurrentMeter"
    capability "EnergyMeter"
    capability "VoltageMeasurement"
    capability "TemperatureMeasurement"
    capability "Refresh"
    capability "Polling"
    capability "Configuration"
    capability "Initialize"
    capability "SignalStrength"

    command "clearState"
    command "pilotMode", [[name:"mode",type:"ENUM", description:"Pilot mode", constraints: ["Stop","Anti Freeze","Eco","Comfort"]]]
    command "onTimer", [[name:"duration",type:"ENUM", description:"Pilot mode", constraints: ["5m","10m","15m","30m","1h","2h","3h","4h","5h","6h","7h","8h"]]]

    attribute "voltage0", "number"
    attribute "voltage1", "number"
    attribute "amperage0", "number"
    attribute "amperage1", "number"
    attribute "energy0", "number"
    attribute "energy1", "number"
    attribute "power0", "number"
    attribute "power1", "number"
    attribute "temperature0", "number"
    attribute "temperature1", "number"
    attribute "switch0", "string"
    attribute "switch1", "string"

    attribute "id", "string"
    attribute "type", "string"
    attribute "status", "string"
    attribute "WiFiSignal", "string"
    attribute "mode", "enum", ["Stop","Anti Freeze","Eco","Comfort"]
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [2:"2min"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"]], defaultValue: 2, required: true
    }
    section { // Configuration
      input name: "deviceAddress", title: "Device IP", type: "text", defaultValue: "", required: true
      input name: "deviceAuthUsr", title: "Device Auth Username (blank if none)", type: "text", defaultValue: "", required: false
      input name: "deviceAuthPwd", title: "Device Auth Password (blank if none)", type: "paaword", defaultValue: "", required: false
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
  state.devicePings = 0
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
  configure()
}

def configure() {
  logger("debug", "configure()")

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

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
  def di = deviceInfo()
  setDetails(di)
  def ds = deviceStatus()
  setStates(ds)
}

def poll() {
  logger("debug", "poll()")
  def ds = deviceStatus()
  setStates(ds)
}

def off() {
  logger("debug", "off()")
  pilotMode("Stop")
}

def on() {
  logger("debug", "on()")
  pilotMode("Comfort")
}

def pilotMode(mode="Stop") {
  // Validate modes
  List mode_list = ["Stop","Anti Freeze","Eco","Comfort"]

  if (mode_list.contains(mode)) {
    if (logDescText) {
      log.info "${device.displayName} Pilot Mode set to ${mode}"
    } else {
      logger("info", "Pilot Mode set to ${mode}")
    }

    switch (mode) {
      case "Stop":
        // reset
        setSwitch(0, false)
        setSwitch(1, false)
        pauseExecution(2000)
        // set mode
        setSwitch(1, true)
        sendEvent(name: "switch", value: "off", displayed: true)
      break
      case "Anti Freeze":
        // reset
        setSwitch(0, false)
        setSwitch(1, false)
        pauseExecution(2000)
        // set mode
        setSwitch(0, true)
        sendEvent(name: "switch", value: "on", displayed: true)
      break
      case "Eco":
        // reset
        setSwitch(0, false)
        setSwitch(1, false)
        pauseExecution(2000)
        // set mode
        setSwitch(0, true)
        setSwitch(1, true)
        sendEvent(name: "switch", value: "on", displayed: true)
      break
      case "Comfort":
        setSwitch(0, false)
        setSwitch(1, false)
        sendEvent(name: "switch", value: "on", displayed: true)
      break
    }
    sendEvent(name: "mode", value: mode, displayed:true)
    poll()
  } else {
    logger("error", "Pilot Mode (${mode}) is incorrect")
  }
}


def onTimer(String duration) {
  logger("debug", "onTimer(${duration})")

  // Validate modes
  Integer duration_value = null
  Map duration_map = [300:"5m", 600:"10m", 900:"15m", 1800:"30m", 3600:"1h", 7200:"2h", 10800:"3h", 14400:"4h", 18000:"5h", 21600:"6h", 25200:"7h", 28800:"8h"]
  duration_map.each { it->
    if (it.value == duration) { duration_value = it.key }
  }

  if (duration_value == null) {
    logger("error", "Time value(${duration}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} Pilot turned on for ${duration} (${duration_value})"
    } else {
      logger("info", "Pilot turned on for ${duration} (${duration_value})")
    }

    startTimer(duration_value, off)
    on()
  }
}

private startTimer(Integer seconds, function) {
  def now = new Date()
  def runTime = new Date(now.getTime() + (seconds * 1000))
  runOnce(runTime, function)
}

def checkState() {
  logger("debug", "checkState()")

  if (state?.devicePings >= 3) {
    if (device.currentValue('status') != 'offline') {
      sendEvent(name: "status", value: "offline", descriptionText: "Is offline", displayed: true, isStateChange: true)
    }
    logger("warn", "Device is offline")
  }
  state.devicePings = (state?.devicePings ?: 0) + 1

  def ds = deviceStatus()
  setStates(ds)
}

def deviceUpdate() {
  // Sets the device status to online, but only if previously was offline
  state.devicePings = 0
  if (device.currentValue('status') != 'online') {
    logger("info", "Device is online")
  }

  sendEvent(name: "status", value: "online", descriptionText: "Is online", displayed: true)
}

Map setSwitch(BigDecimal id=0, boolean status=false) {
  logger("debug", "deviceInfo()")
  apiGet("/rpc/Switch.Set",['id':id,'on':status]) { resp ->
    logger("trace", "deviceInfo() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp?.getStatus() == 200) {
      return resp?.getData()
    } else {
      return [:]
    }
  }
}

Map deviceInfo() {
  logger("debug", "deviceInfo()")
  apiGet("/rpc/Shelly.GetDeviceInfo",[:]) { resp ->
    logger("trace", "deviceInfo() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp?.getStatus() == 200) {
      return resp?.getData()
    } else {
      return [:]
    }
  }
}

Map deviceStatus() {
  logger("debug", "deviceStatus()")
  apiGet("/rpc/Shelly.GetStatus",[:]) { resp ->
    logger("trace", "deviceInfo() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp?.getStatus() == 200) {
      deviceUpdate()
      return resp?.getData()
    } else {
      return [:]
    }
  }
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  sendEvent(name: "id", value: detail.id)
  sendEvent(name: "type", value: detail.app +" Gen"+ detail.gen)

  detail?.each { k, v ->
    if (k =~ /^(id|auth_domain|auth_en|ver|fw_id)$/) { return }

    updateDataValue("manufacturer", "Shelly")
    if (k =~ /^(model)$/) {
      updateDataValue("model", v)
    }
    if (k =~ /^(app)$/) {
      updateDataValue("type", v)
    }
    if (k =~ /^(ver)$/) {
      updateDataValue("fw_ver", v)
    }
    if (k =~ /^(fw_id)$/) {
      updateDataValue("fw_id", v)
    }
    if (k =~ /^(mac)$/) {
      updateDataValue("mac", v)
    }
    state.deviceInfo[k] = v
  }
}

def setStates(Map states) {
  logger("debug", "setStates(${states?.inspect()})")
  String temperatureFormat = location.getTemperatureScale()

  states?.each { k, v ->
    if (k =~ /^(wifi)$/) {
      v?.each { sk, sv ->
        String cv = device.currentValue(sk)
        boolean isStateChange = (cv?.toString() != sv?.toString()) ? true : false

        switch (sk) {
          case ~/rssi/:
            // Last measured Wifi Signal strength
            sendEvent(name: "${sk}", value: "${sv}", displayed: true, isStateChange: isStateChange)

            cv = device.currentValue("WiFiSignal")
            if (sv <= 0 && sv >= -70) {
              isStateChange = (cv?.toString() != "Excellent") ? true : false
              sendEvent(name: "WiFiSignal", value: "Excellent", isStateChange: isStateChange)
            } else if (sv < -70 && sv >= -80) {
              isStateChange = (cv?.toString() != "Good") ? true : false
              sendEvent(name: "WiFiSignal", value: "Good", isStateChange: isStateChange)
            } else if (sv < -80 && sv >= -90) {
              isStateChange = (cv?.toString() != "Poor") ? true : false
              sendEvent(name: "WiFiSignal", value: "Poor", isStateChange: isStateChange)
            } else if (sv < -90 && sv >= -100) {
              isStateChange = (cv?.toString() != "Weak") ? true : false
              sendEvent(name: "WiFiSignal", value: "Weak", isStateChange: isStateChange)
            }
          break
          default:
          break
        }
      }
    }

    if ((n = k =~ /^switch:(\d+)$/)) {
      String switchNum = n.group(1)
      logger("debug", "Checking switch #${switchNum}")

      v?.each { sk, sv ->
        String cv = device.currentValue(sk)
        boolean isStateChange = (cv?.toString() != sv?.toString()) ? true : false

        switch (sk) {
          case ~/output/:
            cv = device.currentValue("switch${switchNum}")
            String value = (sv?.toString() == "true" ? "on" : "off")
            isStateChange = (cv?.toString() != value?.toString()) ? true : false
            logger("debug", "Was turned ${value} (switch${switchNum})")

            // true if the output channel is currently on, false otherwise
            sendEvent(name: "switch${switchNum}", value: "${value}", displayed: true, isStateChange: isStateChange)
          break
          case ~/voltage/:
            // Last measured voltage in Volts
            sendEvent(name: "${sk}${switchNum}", value: "${sv}", displayed: true, isStateChange: isStateChange, unit: "V")
          break
          case ~/current/:
            cv = device.currentValue("amperage${switchNum}")
            isStateChange = (cv?.toString() != sv?.toString()) ? true : false
            // Last measured current in Amperes
            sendEvent(name: "amperage${switchNum}", value: "${sv}", displayed: true, isStateChange: isStateChange, unit: "A")
          break
          case ~/apower/:
            cv = device.currentValue("power${switchNum}")
            isStateChange = (cv?.toString() != sv?.toString()) ? true : false
            // Last measured instantaneous active power (in Watts) delivered to the attached load (shown if applicable)
            sendEvent(name: "power${switchNum}", value: "${sv}", displayed: true, isStateChange: isStateChange, unit: "W")
          break
          case ~/aenergy/:
            cv = device.currentValue("energy${switchNum}")
            isStateChange = (cv?.toString() != sv.total?.toString()) ? true : false
            // Information about the active energy counter, Total energy consumed in Watt-hours
            sendEvent(name: "energy${switchNum}", value: "${sv.total}", displayed: true, isStateChange: isStateChange, unit:"kWh")
          break
          case ~/temperature/:
            def value
            if (temperatureFormat == "C") {
              value = sv.tC
            } else {
              value = v.tF
            }
            isStateChange = (cv?.toString() != value) ? true : false
            sendEvent(name: "${sk}${switchNum}", value: "${value}", displayed: true, isStateChange: isStateChange, unit: "°${temperatureFormat}")
          break
          default:
          break
        }
      }
    }
  }
  setStatesAvg(states)
}

private setStatesAvg(Map states) {
  logger("debug", "setStatesAvg()")

  List attributes = ["voltage","amperage","power","energy","temperature"]
  List switchNum = []
  states?.each { k, v ->
    if ((n = k =~ /^switch:(\d+)$/)) {
      switchNum.push(n.group(1))
    }
  }

  attributes?.each { a ->
    List<BigDecimal> vals = []
    switchNum?.each { s ->
      vals.push(device.currentValue("${a}${s}"))
    }
    try {
      String valAvg = vals.sum() / vals.size()
      sendEvent(name: "${a}", value: "${valAvg}", displayed: true)
    } catch (Exception e) { }
  }
}

private getApiUrl() { "http://${deviceAddress}" }

private apiGet(String path, Closure callback) {
  apiGet(path, [:], callback);
}

private apiGet(String path, Map query, Closure callback) {
  logger("debug", "apiGet()")

  Map params = [
    uri: getApiUrl(),
    path: path,
    requestContentType: 'application/json; charset=utf-8',
    contentType: 'application/json; charset=utf-8',
    'query': query,
    timeout: 30
  ]
  if (deviceAuthUsr != null && deviceAuthPwd != null) {
    String auth = "Basic "+ "${deviceAuthUsr}:${deviceAuthPwd}".bytes.encodeBase64().toString()
    params['headers'] << ['Authorization':auth]
  }

  try {
    logger("trace", "apiGet() - URL: ${getApiUrl() + path}, PARAMS: ${params.inspect()}")
    logger("trace", "apiGet() - Request: ${getApiUrl() + path}")
    httpGet(params) { resp ->
      logger("trace", "apiGet() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      callback.call(resp)
    }
  } catch (Exception e) {
    logger("error", "apiGet() - Request Failed: ${e.getMessage()}")
    logger("trace", "apiGet() - Request Exception: ${e.inspect()}")
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
