/**
 *  Copyright (C) Sebastian YEPES
 *  Original Authors: Sam Lalor, Andrew Stanley-Jones
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

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

definition (
  name: "LG WebOS TV Discovery",
  namespace: "syepes",
  author: "Sebastian YEPES",
  description: "Discovers an LG WebOS TV",
  category: "user",
  iconUrl: "",
  iconX2Url: "",
  iconX3Url: ""
)

preferences {
  page(name:"settings", title:"Settings", content:"settings")
  page(name:"televisionDiscovery", title:"LG TV Setup", content:"televisionDiscovery", refreshTimeout:5)
  page(name:"televisionAuthenticate", title:"LG TV Pairing", content:"televisionAuthenticate", refreshTimeout:5)
}

def settings() {
  logger("debug", "settings()")

  return dynamicPage(name:"settings", title:"Settings", nextPage:"televisionDiscovery", refreshInterval:refreshInterval, uninstall: true) {
    section("Logging") {
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "stateCheckInterval", title: "State Check Interval", description: "Check interval of the current state", type: "enum", options:[[5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 15, required: true
    }
  }
}

def televisionDiscovery() {
  logger("debug", "televisionDiscovery()")

  int tvRefreshCount = !state.bridgeRefreshCount ? 0 : state.bridgeRefreshCount as int
  state.bridgeRefreshCount = tvRefreshCount + 1
  def refreshInterval = 10

  def options = televisionsDiscovered() ?: []
  def numFound = options.size() ?: 0

  if(!state.subscribe) {
    subscribe(location, null, deviceLocationHandler, [filterEvents:false])
    state.subscribe = true
  }

  // Television discovery request every 15 seconds
  if((tvRefreshCount % 5) == 0) {
    findTv()
  }

  return dynamicPage(name:"televisionDiscovery", title:"LG TV Search Started!", nextPage:"televisionAuthenticate", refreshInterval:refreshInterval, uninstall: true){
    section("Please wait while we discover your LG TV. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
      input "selectedTv", "enum", required:false, title:"Select LG TV (${numFound} found)", multiple:false, options:options
    }
  }
}

def televisionAuthenticate() {
  logger("debug", "televisionAuthenticate()")

  if (!selectedTv?.trim()) {
    logger("warn", "televisionAuthenticate() - No TV selected")
    return televisionDiscovery()
  }

  def settingsInfo = selectedTv.split("!")
  if (settingsInfo[3] == "NETCAST") {
    tvRequestPairingKey()

    return dynamicPage(name:"televisionAuthenticate", title:"LG TV Pairing Started!", nextPage:"", install:true) {
      section("We sent a pairing request to your TV. Please enter the pairing key and click Done.") {
        input "pairingKey", "string", defaultValue:"DDTYGF", required:true, title:"Pairing Key", multiple:false
      }
    }
  } else {
    return dynamicPage(name:"televisionAuthenticate", title:"LG TV Search Started!", nextPage:"", install:true) {
      section("WebOS TVs can not be paired from the application. The driver will attempt to pair with the TV when it is initialized. Please authorize the pairing from the TV using the TV remote control. Please click Done."){}
    }
  }
}

Map televisionsDiscovered() {
  logger("debug", "televisionsDiscovered()")

  def map = [:]
  def vbridges = getLGTvs()
  vbridges.each {
    logger("debug", "televisionsDiscovered() - Discovered List: ${it}")

    def value = "$it"
    def key = it.value

    if (key.contains("!")) {
      def settingsInfo = key.split("!")
      def deviceIp = convertHexToIP(settingsInfo[1])
      value = "LG TV (${deviceIp} - ${settingsInfo[3]})"
    }

    map["${key}"] = value
  }
  map
}

def installed() {
  logger("debug", "installed()")
  initialize()
}

def uninstalled() {
  logger("debug", "uninstalled()")
  removeChildDevices(getChildDevices())
}

def updated() {
  logger("debug", "updated()")
  initialize()
}

def initialize() {
  logger("debug", "initialize()")

  // Remove UPNP Subscription
  unsubscribe()
  state.subscribe = false

  addDevice()

  logger("debug", "initialize() - Selected TV: ${selectedTv}")
}

def addDevice() {
  logger("debug", "addDevice()")

  def deviceSettings = selectedTv.split("!")
  def macAddress = deviceSettings[0]
  def ipAddressHex = deviceSettings[1]
  def ipAddress = convertHexToIP(ipAddressHex)
  def pairKey = "$pairingKey"
  if (pairKey == null) { pairKey = "x"}
  def tvType = deviceSettings[3]

  def dni = "${ipAddressHex}_${macAddress}_${tvType}_${convertPortToHex(8080)}"
  if (tvType == "WEBOS") { dni = "${ipAddressHex}_${macAddress}_${tvType}_${convertPortToHex(3000)}" }

  logger("debug", "addDevice() - ip: ${ipAddress}, mac: ${macAddress}, type: ${tvType}, pairKey: ${pairKey}, dni: ${dni}, Hub: ${location.hubs[0].id}")

  def d = getChildDevice(dni)
  if(!d) {
    addChildDevice("syepes", "LG WebOS TV", dni, null, [name: "LG WebOS TV", isComponent: true, label: "LG WebOS TV - $ipAddress"])
    d = getChildDevice(dni)
    d.updateSetting("televisionIp",[type:"text", value:ipAddress])
    d.updateSetting("televisionMac",[type:"text", value:macAddress])
    d.updateSetting("televisionType",[type:"text", value:tvType])
    if (tvType == "NETCAST") {
      d.updateSetting("pairingKey",[type:"text", value:"${pairKey}"])
    }
    // d.setParameters(ipAddress,macAddress,tvType,pairkey)

    logger("debug", "addDevice() - Created Device: ${d.displayName} with id ${d.deviceNetworkId}")
  } else {
    logger("debug", "addDevice() - Device: ${d.displayName} (${dni}) with id ${d.deviceNetworkId}already created - Updating")
    d.updateSetting("televisionIp",[type:"text", value:ipAddress])
    d.updateSetting("televisionMac",[type:"text", value:macAddress])
    d.updateSetting("televisionType",[type:"text", value:tvType])
    if (tvType == "NETCAST") {
      d.updateSetting("pairingKey",[type:"text", value:"${pairKey}"])
    }
    // d.setParameters(ipAddress,macAddress,tvType,pairkey)
  }
  getLgDevice().updated()
}

def getLgDevice() {
  logger("debug", "getLgDevice()")
  def childDevices = getChildDevices()
  logger("debug", "getLgDevice() - childDevices: ${childDevices?.inspect()}")
  def LgDevice = childDevices[0]
  return LgDevice
}

def castLgDeviceStates(){
  logger("debug", "castLgDeviceStates() - televisionType: ${televisionType}, televisionIp: ${televisionIp}, televisionMac: ${televisionMac}, pairingKey: ${pairingKey}")
  state.televisionType = televisionType
  state.televisionIp = televisionIp
  state.televisionMac = televisionMac
  state.pairingKey = pairingKey ?: ""

  if (getLgDevice()){
    logger("debug", "castLgDeviceStates() - Found a Child LG ${getLgDevice().label}")
  } else{
    logger("debug", "castLgDeviceStates() - Did not find a Parent LG")
  }
}

// Returns a list of the found LG TVs from UPNP discovery
def getLGTvs() {
  logger("debug", "getLGTvs() - ${state?.televisions?.inspect()}")
  state.televisions = state.televisions ?: [:]
}

// Sends out a UPNP request, looking for the LG TV. Results are sent to [deviceLocationHandler]
private findTv() {
  logger("debug", "findTv()")
  // send ssdp search for NetCast TVs (2012 - 2015 models)
  sendHubCommand(new hubitat.device.HubAction("lan discovery urn:schemas-udap:service:netrcu:1", hubitat.device.Protocol.LAN))
  // send ssdp search for WebOS TVs (2016 and newer models)
  sendHubCommand(new hubitat.device.HubAction("lan discovery urn:lge-com:service:webos-second-screen:1", hubitat.device.Protocol.LAN))
}

// Parses results from [findTv], looking for the specific UPNP result that clearly identifies the TV we can use
def deviceLocationHandler(evt) {
  logger("debug", "deviceLocationHandler() - ${evt?.inspect()}")

  def upnpResult = parseEventMessage(evt.description)
  def hub = evt?.hubId

  if (upnpResult?.ssdpUSN?.contains("urn:lge-com:service:webos-second-screen:1")) {
    // found a WebOS TV
    logger("debug", "deviceLocationHandler() - Found WebOS TV: ${upnpResult}")
    state.televisions << [device:"${upnpResult.mac}!${upnpResult.ip}!${hub}!WEBOS"]
  }

  if (upnpResult?.ssdpPath?.contains("udap/api/data")) {
    // found a NetCast TV
    logger("debug", "deviceLocationHandler() - Found TV: ${upnpResult}")
    state.televisions << [device:"${upnpResult.mac}!${upnpResult.ip}!${hub}!NETCAST"]
  }
}

// Display pairing key on TV
private tvRequestPairingKey() {
  logger("debug", "tvRequestPairingKey()")

  def deviceSettings = selectedTv.split("!")
  def ipAddressHex = deviceSettings[1]
  def ipAddress = convertHexToIP(ipAddressHex)

  if (deviceSettings[3] == "NETCAST") {
    // Netcast TV pairing
    def reqKey = "<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthKeyReq</type></auth>"

    def httpRequest = [
      method: "POST",
      path: "/roap/api/auth",
      body: "$reqKey",
      headers: [
        HOST: "$ipAddress:8080",
        "Content-Type": "application/atom+xml",
      ]
    ]

    logger("debug", "tvRequestPairingKey() - httpRequest: ${httpRequest}")
    def hubAction = new hubitat.device.HubAction(httpRequest)
    sendHubCommand(hubAction)
  } else {
    // WebOS pairing - WebSockets can not be opened from an APP - Defer pairing to the device initialization
  }
}

private def parseEventMessage(String description) {
  logger("debug", "parseEventMessage() - description: ${description}")

  if (!description) { return }

  def event = [:]
  def parts = description.split(',')
  parts.each { part ->
    part = part.trim()
    if (part.startsWith('devicetype:')) {
      def valueString = part.split(":")[1].trim()
      event.devicetype = valueString
    } else if (part.startsWith('mac:')) {
      def valueString = part.split(":")[1].trim()
      if (valueString) {
        event.mac = valueString
      }
    } else if (part.startsWith('networkAddress:')) {
      def valueString = part.split(":")[1].trim()
      if (valueString) {
        event.ip = valueString
      }
    } else if (part.startsWith('ssdpPath:')) {
      def valueString = part.split(":")[1].trim()
      if (valueString) {
        event.ssdpPath = valueString
        logger("debug", "parseEventMessage() - Found ssdpPath: ${valueString}")
      }
    } else if (part.startsWith('ssdpUSN:')) {
      part -= "ssdpUSN:"
      def valueString = part.trim()
      if (valueString) {
        event.ssdpUSN = valueString
        logger("debug", "parseEventMessage() - Found ssdpUSN: ${valueString}")
      }
    } else if (part.startsWith('ssdpTerm:')) {
      part -= "ssdpTerm:"
      def valueString = part.trim()
      if (valueString) {
        event.ssdpTerm = valueString
        logger("debug", "parseEventMessage() - Found ssdpTerm: ${valueString}")
      }
    }
  }
  event
}

private Integer convertHexToInt(hex) {
  Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
  [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private String convertIPtoHex(ipAddress) {
  String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
  return hex
}

private String convertPortToHex(port) {
  String hexport = port.toString().format( '%04X', port.toInteger() )
  return hexport
}
private removeChildDevices(delete) {
  logger("debug", "removeChildDevices()")

  delete.each {deleteChildDevice(it.deviceNetworkId)}
}


/**
 * @param level Level to log at, see LOG_LEVELS for options
 * @param msg Message to log
 */
private logger(level, msg) {
  if (level && msg) {
    Integer levelIdx = LOG_LEVELS.indexOf(level)
    Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel)
    if (setLevelIdx<0) {
      setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
    }
    if (levelIdx<= setLevelIdx) {
      log."${level}" "${app.name} ${msg}"
    }
  }
}
