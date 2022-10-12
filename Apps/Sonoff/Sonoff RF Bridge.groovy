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

/*
Example json app parameter is defined as a json string
Note: the string must be defined as one single line (https://www.webtoolkitonline.com/json-minifier.html), see the below examples:
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

import groovy.transform.Field
import groovy.json.JsonSlurper
import com.hubitat.app.ChildDeviceWrapper

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

definition(
  name: "Sonoff RF Bridge",
  namespace: "syepes",
  author: "Sebastian YEPES",
  description: "Sonoff RF Bridge",
  category: "",
  oauth: false,
  singleInstance: true,
  iconUrl: "https://play-lh.googleusercontent.com/3RC_WggdYWlA7ZFjH8YKHkDmMrLayPAN72MleyhtmnAa7NRD94yKfaqoXkqLmblJiw=w200-h200",
  iconX2Url: "https://play-lh.googleusercontent.com/3RC_WggdYWlA7ZFjH8YKHkDmMrLayPAN72MleyhtmnAa7NRD94yKfaqoXkqLmblJiw=w400-h400"
)

preferences {
  page(name: "device", title: "Bridge device")
  page(name: "config", title: "Config")
  page(name: "check", title: "Check")
}

/* Preferences */
Map device() {
  logger("debug", "device()")

  return dynamicPage(name: "device", title: "Bridge device", nextPage:"config", install: false, uninstall: false) {
    section("Sonoff RF Bridge") {
      input name: "host_port", title: "Device IP and PORT", type: "text", defaultValue: "ip:port", required: true
      input name: "user", title: "User", type: "text", defaultValue: "", required: false
      input name: "password", title: "Password", type: "password", defaultValue: "", required: false
    }
  }
}

Map config() {
  logger("debug", "config()")

  return dynamicPage(name: "config", title: "Config", nextPage:"check", install: false, uninstall: false) {
    section("Devices") {
      input name: "json", title: "JSON Config", type: "text", required: true
    }
    section("Logging") {
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    }
  }
}

Map check() {
  logger("debug", "check()")
  try {
    JsonSlurper slurper = new JsonSlurper()
    def vd_data = slurper.parseText(json)
    logger("debug", "check() - Successful")
    return dynamicPage(name: "check", title: "Check Config", install: true, uninstall: true) {
      section() {
        paragraph "Successfully checked the the config. Click Next"
      }
    }
  } catch (e) {
    logger("error", "check() - Failed, ${e}")
    return dynamicPage(name: "check", title: "Check Config", nextPage: "config", install: false, uninstall: false) {
      section() {
        paragraph "Unable parse the JSON Config, double check your config. Click Next"
      }
    }
  }
}

def installed() {
  logger("debug", "installed(${VERSION})")
  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION]
  }
  initialize()
}

def uninstalled() {
  logger("debug", "uninstalled()")

  unschedule()
  removeChildDevices(getChildDevices())
}

def updated() {
  logger("debug", "updated() - settings: ${settings.inspect()} / InstallationState: ${app.getInstallationState()}")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    if (state.driverInfo == null || state.driverInfo.isEmpty()) {
      state.driverInfo = [ver:VERSION]
    }
  }

  initialize()
}

void initialize() {
  logger("debug", "initialize() - settings: ${settings.inspect()}")

  // Create virtual devices
  JsonSlurper slurper = new JsonSlurper()
  def vd_data = slurper.parseText(json)
  vd_data?.each {
    ChildDeviceWrapper vd = createDevices(it.key?.split(':')?.getAt(0), it.key?.split(':')?.getAt(1))
  }

  // Cleanup any other devices that need to go away
  unschedule()
  def hub = location.hubs[0]
  List allInstalled = getChildDevices().collect{ it.getChildDevices().collect{ it.deviceNetworkId?.replaceAll("${hub.id}-SonoffRFBridge-","").replaceFirst("-",":") } }.flatten()
  List device = vd_data.keySet().toList()
  List delete = allInstalled.findAll{ !device.contains(it) }

  getChildDevices()?.each { bridge ->
    bridge?.getChildDevices()?.each { dev ->
      String devID = dev?.deviceNetworkId?.replaceAll("${hub.id}-SonoffRFBridge-","").replaceFirst("-",":")
      if (delete.contains(devID)) {
        logger("info", "Removing Device: ${dev.deviceNetworkId}")
        bridge.deleteChildDevice(dev.deviceNetworkId)
      }
    }
  }
}

private ChildDeviceWrapper createDevices(String type, String name) {
  logger("debug", "createDevices(${type},${name})")
  try {
    def hub = location.hubs[0]
    def cd = getChildDevice("${hub.id}-SonoffRFBridge")
    if (cd) {
      cd.addDevice([name: name, type: type])

    } else {
      logger("info", "Creating Sonoff RF Bridge Device")
      cd = addChildDevice("syepes", "Sonoff RF Bridge", "${hub.id}-SonoffRFBridge", hub.id, [name: "Sonoff RF Bridge", label: "Sonoff RF Bridge", isComponent: true])
      if (cd) {
        logger("info", "Creating Sonoff RF Device: ${name} (${type})")
        cd.addDevice([name: name, type: type])
      }
    }

  } catch (e) {
    logger("error", "createDevices(${type},${name}) - e: ${e}")
  }
}

private removeChildDevices(delete) {
  logger("debug", "removeChildDevices() - Removing ${delete.size()} devices")
  delete.each { deleteChildDevice(it.deviceNetworkId) }
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
      log."${level}" "${app.name} ${msg}"
    }
  }
}
