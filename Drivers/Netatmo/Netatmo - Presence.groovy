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

@Field String VERSION = "1.0.3"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
  definition (name: "Netatmo - Presence", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Presence.groovy") {
    capability "Actuator"
    capability "Switch"
    capability "Motion Sensor"
    capability "Image Capture"
    capability "Refresh"
    capability "Initialize"

    command "light_mode", [[name:"light_mode",type:"ENUM", description:"Floodlight mode", constraints: ["auto","on","off"]]]
    command "light_intensity", [[name:"light_intensity",type:"NUMBER", description:"Floodlight intensity", range: 1..100]]
    command "motion"
    command "human"
    command "vehicle"
    command "animal"

    attribute "status", "string"
    attribute "sd_status", "string"
    attribute "alim_status", "string"
    attribute "switch_light", "string"
    attribute "homeName", "string"
    attribute "image_tag", "string"
    attribute "human", "string"
    attribute "vehicle", "string"
    attribute "animal", "string"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
    }
    section { // Snapshots
      input name: "cameraIP", title: "Camera Local IP", description: "The address of the camera in your local network", type: "text", required: true
      input name: "motionTimeout", title: "Motion timeout", description: "Motion, Human, Vehicle and Animal detection times out after how many seconds", type: "number", range: "0..3600", defaultValue: 60, required: true
      input name: "scheduledTake", title: "Take a snapshot every", type: "enum", options:[[0:"No snapshots"], [2:"2min"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 0, required: true
      input name: "motionHumans", title: "HumansAsMotion", description: "Humans detected count as motion", type: "bool", defaultValue: true, required: true
      input name: "motionVehicles", title: "VehiclesAsMotion", description: "Vehicles detected count as motion", type: "bool", defaultValue: true, required: true
      input name: "motionAnimals", title: "AnimalsAsMotion", description: "Animals detected count as motion", type: "bool", defaultValue: true, required: true
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

  sendEvent(name: "motion", value: "inactive")
  sendEvent(name: "human", value: "inactive")
  sendEvent(name: "vehicle", value: "inactive")
  sendEvent(name: "animal", value: "inactive")
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

  schedule("0 0 12 */7 * ?", updateCheck)

  if (scheduledTake.toInteger()) {
    if (['2', '5', '10', '15', '30'].contains(scheduledTake) ) {
      schedule("0 */${scheduledTake} * ? * *", take)
    } else {
      schedule("0 0 */${scheduledTake} ? * *", take)
    }
  }
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  return []
}

def on() {
  logger("debug", "on()")
  if(logDescText) {
    log.info "${device.displayName} Was turned on"
  } else {
    logger("info", "Was turned on")
  }
  sendEvent(name: "switch", value: "on")
}

def off() {
  logger("debug", "off()")
  if(logDescText) {
    log.info "${device.displayName} Was turned off"
  } else {
    logger("info", "Was turned off")
  }
  sendEvent(name: "switch", value: "off")
}

def setHome(homeID,homeName) {
  logger("debug", "setHome(${homeID?.inspect()}, ${homeName?.inspect()})")
  state.deviceInfo['homeID'] = homeID
  sendEvent(name: "homeName", value: homeName)
}

def setAKey(key) {
  logger("debug", "setAKey(${key?.inspect()})")
  state.deviceInfo['accessKey'] = key
}

def human(String snapshot_url=null) {
  logger("debug", "human(${snapshot_url})")
  if(logDescText) {
    log.info "${device.displayName} Has detected motion (Human)"
  } else {
    logger("info", "Has detected motion (Human)")
  }
  sendEvent(name: "human", value: "active", displayed: true)
  if (snapshot_url != null) {
    sendEvent(name: "image_tag", value: '<img src="'+ snapshot_url +'" width="240" height="190">', isStateChange: true, displayed: true)
  }

  if (motionHumans) {
    motion(snapshot_url)
  }
  if (motionTimeout) {
    startTimer(motionTimeout, cancelHuman)
  } else {
    logger("debug", "human() - Motion timeout has not been set in preferences, using 10 second default")
    startTimer(10, cancelHuman)
  }
}

def cancelHuman() {
  logger("debug", "cancelHuman()")
  sendEvent(name: "human", value: "inactive")
}

def vehicle(String snapshot_url=null) {
  logger("debug", "vehicle(${snapshot_url})")
  if(logDescText) {
    log.info "${device.displayName} Has detected motion (Vehicle)"
  } else {
    logger("info", "Has detected motion (Vehicle)")
  }
  sendEvent(name: "vehicle", value: "active", displayed: true)
  if (snapshot_url != null) {
    sendEvent(name: "image_tag", value: '<img src="'+ snapshot_url +'" width="240" height="190">', isStateChange: true, displayed: true)
  }

  if (motionVehicles) {
    motion(snapshot_url)
  }
  if (motionTimeout) {
    startTimer(motionTimeout, cancelVehicle)
  } else {
    logger("debug", "vehicle() - Motion timeout has not been set in preferences, using 10 second default")
    startTimer(10, cancelVehicle)
  }
}

def cancelVehicle() {
  logger("debug", "cancelVehicle()")
  sendEvent(name: "vehicle", value: "inactive")
}

def animal(String snapshot_url=null) {
  logger("debug", "animal(${snapshot_url})")
  if(logDescText) {
    log.info "${device.displayName} Has detected motion (Animal)"
  } else {
    logger("info", "Has detected motion (Animal)")
  }
  sendEvent(name: "animal", value: "active", displayed: true)
  if (snapshot_url != null) {
    sendEvent(name: "image_tag", value: '<img src="'+ snapshot_url +'" width="240" height="190">', isStateChange: true, displayed: true)
  }

  if (motionAnimals) {
    motion(snapshot_url)
  }
  if (motionTimeout) {
    startTimer(motionTimeout, cancelAnimal)
  } else {
    logger("debug", "animal() - Motion timeout has not been set in preferences, using 10 second default")
    startTimer(10, cancelAnimal)
  }
}

def cancelAnimal() {
  logger("debug", "cancelAnimal()")
  sendEvent(name: "animal", value: "inactive")
}

def motion(String snapshot_url=null) {
  logger("debug", "motion(${snapshot_url})")
  if(logDescText) {
    log.info "${device.displayName} Has detected motion"
  } else {
    logger("info", "Has detected motion")
  }
  sendEvent(name: "motion", value: "active", displayed: true)
  if (snapshot_url != null) {
    sendEvent(name: "image_tag", value: '<img src="'+ snapshot_url +'" width="240" height="190">', isStateChange: true, displayed: true)
  }

  if (motionTimeout) {
    startTimer(motionTimeout, cancelMotion)
  } else {
    logger("debug", "motion() - Motion timeout has not been set in preferences, using 10 second default")
    startTimer(10, cancelMotion)
  }
}

def cancelMotion() {
  logger("debug", "cancelMotion()")
  sendEvent(name: "motion", value: "inactive")
}

// TODO: Needs work to actually store the image somewhere
def take() {
  logger("debug", "take()")
  if (cameraIP == null || cameraIP == "") {
    logger("error", "take() - Please set camera local LAN IP")
    return
  }

  if (state.deviceInfo['accessKey'] == null || state.deviceInfo['accessKey'] == 'N/A') {
    logger("error", "take() - Please verify that the device access key is corect")
    return
  }

  def port = 80
  def path = "/${state.deviceInfo['accessKey']}/live/snapshot_720.jpg"
  def hostAddress = "$cameraIP:$port"

  sendEvent(name: "image", value: "http://"+ hostAddress + path, isStateChange: true, displayed: true)
  sendEvent(name: "image_tag", value: '<img src="http://'+ hostAddress + path +'" width="240" height="190">', isStateChange: true, displayed: true)
}

def light_mode(mode="auto") {
  logger("debug", "light_mode(${mode})")
  if (cameraIP == null || cameraIP == "") {
    logger("error", "light_mode() - Please set camera local LAN IP")
    return
  }

  if (state.deviceInfo['accessKey'] == null || state.deviceInfo['accessKey'] == 'N/A') {
    logger("error", "light_mode() - Please verify that the device access key is corect")
    return
  }

  if (!['auto','on','off'].contains(mode)) {
    logger("error", "light_mode(${mode}) - Floodlight Mode is incorrect")
  } else {
    if(logDescText) {
      log.info "${device.displayName} Floodlight Mode ${mode}"
    } else {
      logger("info", "Floodlight Mode ${mode}")
    }

    def port = 80
    def path = "/${state.deviceInfo['accessKey']}/command/floodlight_set_config?config=${ URLEncoder.encode("{\"mode\":\"${mode}\"}") }"
    def hostAddress = "$cameraIP:$port"
    def headers = ["HOST": hostAddress, "Accept": "application/json"]

    logger("debug", "light_mode() - hubAction - Request: ${hostAddress + path}")
    def hubAction = new hubitat.device.HubAction(
      method: "GET",
      path: path,
      headers: headers,
      null,
      [callback: cmdResponse]
    )

    logger("debug", "light_mode() - hubAction: ${hubAction?.inspect()}")
    sendHubCommand(hubAction)
  }
}

def light_intensity(intensity=100) {
  logger("debug", "light_intensity(${intensity})")
  if (cameraIP == null || cameraIP == "") {
    logger("error", "light_intensity() - Please set camera local LAN IP")
    return
  }

  if (state.deviceInfo['accessKey'] == null || state.deviceInfo['accessKey'] == 'N/A') {
    logger("error", "light_intensity() - Please verify that the device access key is corect")
    return
  }

  if (!(intensity >= 1 && val <= 100)) {
    logger("error", "light_intensity() - Invalid specified intensity number (${intensity})")
    return
  }

  if(logDescText) {
    log.info "${device.displayName} Floodlight intensity set to ${intensity}"
  } else {
    logger("info", "Floodlight intensity set to ${intensity}")
  }

  def port = 80
  def path = "/${state.deviceInfo['accessKey']}/command/floodlight_set_config?config=${ URLEncoder.encode("{\"intensity\":${intensity}}") }"
  def hostAddress = "$cameraIP:$port"
  def headers = ["HOST": hostAddress, "Accept": "application/json"]

  logger("debug", "light_intensity() - hubAction - Request: ${hostAddress + path}")
  def hubAction = new hubitat.device.HubAction(
    method: "GET",
    path: path,
    headers: headers,
    null,
    [callback: cmdResponse]
  )

  logger("debug", "light_intensity() - hubAction: ${hubAction?.inspect()}")
  sendHubCommand(hubAction)
}

def cmdResponse(hubitat.device.HubResponse resp) {
  logger("debug", "cmdResponse() - Status: ${resp?.getStatus()} / Data: ${resp?.getData()}")
  if (resp.getStatus() >= 300) {
    logger("error", "cmdResponse() - Status: ${resp?.getStatus()}, Unable to contact device")
  }
}

private startTimer(seconds, function) {
  def now = new Date()
  def runTime = new Date(now.getTime() + (seconds * 1000))
  runOnce(runTime, function) // runIn isn't reliable, use runOnce instead
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
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Presence.groovy"]
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
