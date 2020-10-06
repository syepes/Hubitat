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

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

@Field static Map callbacks = [:]


metadata {
  definition (name: "LG WebOS TV", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/LG/LG%20WebOS%20TV.groovy") {
    capability "Initialize"
    capability "TV"
    capability "AudioVolume"
    capability "Refresh"
    capability "Switch"
    capability "Notification"

    command "off"
    command "refresh"
    command "refreshInputList"
    command "getMouseURI"
    command "externalInput", ["string"]
    command "sendJson", ["string"]
    command "myApps"
    command "ok"
    command "home"
    command "left"
    command "right"
    command "up"
    command "down"
    command "back"
    command "enter"
    command "notificationIcon", ["string", "string"]
    command "setIcon", ["string", "string"]
    command "clearIcons"
    command "testWebSocketReply", ["string"]

    attribute "availableInputs", "list"

    attribute "channelDesc", "string"
    attribute "channelName", "string"
    attribute "channelFullNumber", "string"
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
    }
    section { // Configuration
      input name: "televisionIp", type: "text", title: "Television IP Address",  defaultValue: "",  required: true
      input name: "televisionMac", type: "text", title: "Television MAC Address", defaultValue: "",  required: true
      input name: "pairingKey", type: "text", title: "Pairing Key", required: true, defaultValue: ""
      input name: "retryDelay", title: "Device Reconnect delay", type: "enum", options: [["5":"Retry every 5 seconds"], ["10":"Retry every 10 seconds"], ["15":"Retry every 15 seconds"], ["30":"Retry every 30 seconds"], ["45":"Retry every 45 seconds"], ["60":"Retry every minute"], ["120":"Retry 2 minute"], ["300":"Retry every 5 minutes"], ["600":"Retry every 10 minutes"]], defaultValue: 60
    }
  }
}


def installed() {
  logger("debug", "installed() - settings: ${settings?.inspect()}")
//    initialize()
}

def refresh() {
  logger("debug", "refresh()")
  state.deviceInfo = null
  state.televisionModel = null
  state.nameToInputId = null

  webosRegister()
}

def webosRegister() {
  logger("debug", "webosRegister() - pairing key: ${state.pairingKey}")
  state.pairFailCount = 0

  def payload = [
    pairingType: "PROMPT",
    forcePairing: false,
    'client-key': state?.pairingKey,
    manifest: [
      appVersion: "1.1",
      signed: [
        localizedVendorNames: [ "": "LG Electronics" ],
        appId: "com.lge.test",
        created: "20140509",
        permissions: [
          "TEST_SECURE",
          "CONTROL_INPUT_TEXT",
          "CONTROL_MOUSE_AND_KEYBOARD",
          "READ_INSTALLED_APPS",
          "READ_LGE_SDX",
          "READ_NOTIFICATIONS",
          "SEARCH",
          "WRITE_SETTINGS",
          "WRITE_NOTIFICATION_ALERT",
          "CONTROL_POWER",
          "READ_CURRENT_CHANNEL",
          "READ_RUNNING_APPS",
          "READ_UPDATE_INFO",
          "UPDATE_FROM_REMOTE_APP",
          "READ_LGE_TV_INPUT_EVENTS",
          "READ_TV_CURRENT_TIME",
        ],
        localizedAppNames: [
          "": "LG Remote App",
          "ko-KR": "리모컨 앱",
          "zxx-XX": "ЛГ Rэмotэ AПП",
        ],
        vendorId: "com.lge",
        serial: "2f930e2d2cfe083771f68e4fe7bb07",
      ],
      permissions: [
        "LAUNCH",
        "LAUNCH_WEBAPP",
        "APP_TO_APP",
        "CLOSE",
        "TEST_OPEN",
        "TEST_PROTECTED",
        "CONTROL_AUDIO",
        "CONTROL_DISPLAY",
        "CONTROL_INPUT_JOYSTICK",
        "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_MEDIA_PLAYBACK",
        "CONTROL_INPUT_TV",
        "CONTROL_POWER",
        "READ_APP_STATUS",
        "READ_CURRENT_CHANNEL",
        "READ_INPUT_DEVICE_LIST",
        "READ_NETWORK_STATE",
        "READ_RUNNING_APPS",
        "READ_TV_CHANNEL_LIST",
        "WRITE_NOTIFICATION_TOAST",
        "READ_POWER_STATE",
        "READ_COUNTRY_INFO",
      ],
      manifestVersion: 1,
      signatures: [
        [
          signatureVersion: 1,
          signature: "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRyaMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQojoa7NQnAtw==",
        ],
      ]
    ]
  ]

  sendWebosCommand(type: "register", payload: payload, callback: { json ->
    logger("trace", "webosRegister() - json: ${json?.inspect()}")

    if (json?.type == "registered") {
      pKey = json.payload["client-key"]
      if (pKey != null) {
        logger("debug", "webosRegister() - received registered client-key: ${pKey}")

        state.pairingKey = pKey
        device.updateSetting("pairingKey",[type:"text", value:"${pKey}"])
        runInMillis(10, webosSubscribeToStatus)
        runInMillis(25, getMouseURI)
        // Hello doesn't seem to do anything?
        if (!state.deviceInfo) runInMillis(50, sendHello)
        if (!state.televisionModel) runInMillis(75, sendRequestInfo)
        if (!state.nameToInputId) runInMillis(100, refreshInputList)
        if (!state.serviceList) runInMillis(125, getServiceList)
      }
      return true
    } else if (json?.type == "response") {
        return false
    }
  })
}

def sendHello() {
  logger("debug", "sendHello()")
  sendWebosCommand(type: "hello", id: "hello")
}

def handler_hello(data) {
  logger("debug", "handler_hello() - data: ${data?.inspect()}")
  state.deviceInfo = data
}

def sendRequestInfo() {
  logger("debug", "sendRequestInfo()")

  sendWebosCommand(uri: "system/getSystemInfo", callback: { json ->
    logger("trace", "sendRequestInfo() - json: ${json?.inspect()}")
    state.televisionModel = json.payload?.modelName
    state.televisionReceiver = json.payload?.receiverType
  })
}

def refreshInputList() {
  logger("debug", "refreshInputList() - current list size: ${state.nameToInputId?.size()}")

  sendWebosCommand(uri: "com.webos.applicationManager/listLaunchPoints", payload: [], callback: { json ->
    logger("trace", "refreshInputList() - json: ${json?.inspect()}")
    def inputList = []
    def nameToInputId = [:]
    json?.payload?.launchPoints.each { app ->
      logger("debug", "refreshInputList() - App Name: ${app.title}, App: ${app}")
      inputList += app.title
      nameToInputId[app.title] = app.id
    }

    state.nameToInputId = nameToInputId
    state.inputList = inputList

    sendWebosCommand(uri: 'tv/getExternalInputList', callback: { jsonExt ->
      logger("trace", "refreshInputList() - jsonExt: ${jsonExt?.inspect()}")

      jsonExt?.payload?.devices?.each { device ->
        logger("debug", "refreshInputList() - Device: ${device?.label}")
        inputList += device.label
        nameToInputId[device.label] = device.appId
      }
      state.nameToInputId = nameToInputId
      state.inputList = inputList
      logger("debug", "refreshInputList() - Inputs: ${state.inputList}")

      sendEvent(name: "availableInputs", value: inputList);
    })

  })
}

def getMouseChild() {
  logger("debug", "getMouseChild() - televisionIp: ${televisionIp}")

  try {
    def mouseDev = getChildDevice("LG_TV_Mouse_${televisionIp}")
    if(!mouseDev) mouseDev = addChildDevice("syepes", "LG WebOS Mouse", "LG_TV_Mouse_${televisionIp}")
    return mouseDev
  } catch(e) {
    logger("error", "getMouseChild() - Failed to get mouse dev: ${e}")
  }
  return null
}

def getMouseURI() {
  logger("debug", "getMouseURI()")

  def mouseDev = getMouseChild()

  sendWebosCommand(uri: "com.webos.service.networkinput/getPointerInputSocket", payload: [], callback: { json ->
    logger("trace", "getMouseURI() - json: ${json?.inspect()}")

    if (json?.payload?.socketPath) {
      logger("debug", "getMouseURI() - Send Mouse driver URI: ${json.payload.socketPath}")
      mouseDev?.setMouseURI(json.payload.socketPath)
    }
  })
}

def sendJson(String json) {
  sendCommand(json);
}

def powerEvent(String onOrOff, String type = "digital") {
  logger("debug", "powerEvent() - onOrOff: ${onOrOff}, type: ${type}")

  def descriptionText = "is ${onOrOff}"
  if (state.power != onOrOff){
    logger("info", "powerEvent() - ${descriptionText} [$type]")
  }

  state.power = onOrOff
  sendEvent(name: "switch", value: onOrOff, descriptionText: descriptionText, type: type)
  if (type == "physical") {
    sendEvent(name: "power", value: onOrOff, descriptionText: descriptionText, type: type)
  }

  if ((onOrOff == "off") && (type == "physical")) {
    sendEvent(name: "channelDesc", value: "[off]", descriptionText: descriptionText)
    sendEvent(name: "channelName", value: "[off]", descriptionText: descriptionText)
    sendEvent(name: "input", value: "[off]", descriptionText: descriptionText)
    // Socket status should follow the system reported status
    interfaces.webSocket.close()
  }
}

def initialize() {
  logger("debug", "initialize() - ip: ${televisionIp}, mac: ${televisionMac}, key: ${pairingKey}, debug: ${debug}, logText: ${descriptionText}")
  logger("debug", "initialize() - settings: ${settings.inspect()}")

  // Websocket has closed/errored, erase all callbacks
  callbacks = [:]

  // Set some basic state, clear channel info
  state.sequenceNumber = 1
  state.lastChannel = [:]
  state.pairFailCount = 0

  // When reconnectPending is true it stops reconnectWebsocket
  // from rescheudling initialize()
  state.reconnectPending = false
  state.webSocket = "initialize"

  unschedule()

  def mouseDev = getMouseChild()

  interfaces.webSocket.close()

  if(!televisionMac) {
    def mac = getMACFromIP(televisionIp)
    if (mac){
      device.updateSetting("televisionMac",[value:mac,type:"string"])
    }
  }

  try {
    logger("info", "initialize() - Connecting websocket to: ws://${televisionIp}:3000/")
    interfaces.webSocket.connect("ws://${televisionIp}:3000/")
  } catch(e) {
    logger("error", "initialize() - WebSocket connect ${e?.inspect()}")
  }
}

def webSocketStatus(String status){
  logger("debug", "webSocketStatus() - status: [${status}], State: [${state.webSocket}]")

  if(status.startsWith('failure: ')) {
    //logger("error", "webSocketStatus() - ${status}")

    if ((status == "failure: No route to host (Host unreachable)") || (status == "failure: connect timed out") || status.startsWith("failure: Failed to connect") || status.startsWith("failure: sent ping but didn't receive pong")) {
      logger("info", "webSocketStatus() - WebSocket is closed")
      powerEvent("off", "physical")
    }
    state.webSocket = "closed"
    reconnectWebSocket()
  } else if(status == 'status: open') {
    logger("info", "webSocketStatus() - WebSocket is open")

    // success! reset reconnect delay
    powerEvent("on", "physical")
    state.webSocket = "open"
    webosRegister()
    state.reconnectDelay = 2
  } else if (status == "status: closing"){
    logger("info", "webSocketStatus() - WebSocket connection closing")
    unschedule()

    if (state.webSocket == 'initialize') {
      logger("info", "webSocketStatus() - Ignoring WebSocket close due to initialization")
    } else {
      if (state.power == "on") {
        // TV should be on and reachable - try to reconnect
        reconnectWebSocket(1)
      } else {
        reconnectWebSocket()
      }
    }
    state.webSocket = "closed"
  } else {
    logger("error", "webSocketStatus() - WebSocket error, reconnecting")
    powerEvent("off", "physical")
    state.webSocket = "closed"
    reconnectWebSocket()
  }
}

def reconnectWebSocket(delay = null) {
  logger("debug", "reconnectWebSocket() - delay: ${delay}")

  // first delay is 2 seconds, doubles every time
  if (state.reconnectPending == true) {
    logger("warn", "reconnectWebSocket() - Rejecting additional reconnect request")
    return
  }
  delay = delay ?: state.reconnectDelay
  state.reconnectDelay = delay * 2
  settings_retryDelay = settings.retryDelay.toInteger()
  // don't let delay get too crazy, max it out at user setting
  if (state.reconnectDelay > settings_retryDelay) {
    state.reconnectDelay = settings_retryDelay
  }

  //If the TV is offline, give it some time before trying to reconnect
  state.reconnectPending = true
  runIn(delay, initialize)
}

def updated() {
  logger("debug", "updated() - ip: ${settings.televisionIp}, mac: ${settings.televisionMac}, key: ${settings.pairingKey}")
  initialize()
}

def logsStop() {
  logger("debug", "logsStop()")
}

def setParameters(String IP, String MAC, String TVTYPE, String KEY) {
  logger("debug", "setParameters() - ip: ${IP}, mac: ${MAC}, type: ${TVTYPE}, key: ${KEY}")

  state.televisionIp = IP
  device.updateSetting("televisionIp",[type:"text", value:IP])

  state.televisionMac = MAC
  device.updateSetting("televisionMac",[type:"text", value:MAC])
}

def testWebSocketReply(String data) {
    logger("debug", "testWebSocketReply() - data: ${data}")
    parse(data)
}

// parse events into attributes
def parse(String description) {
  logger("debug", "parse() - description: ${description}")

  // parse method is shared between HTTP and Websocket implementations
  def json = null
    try {
      json = new JsonSlurper().parseText(description)
      if(json == null){
        logger("warn", "parse() - String description not parsed")
        return
      }
    } catch(e) {
      logger("error", "parse() - Failed to parse json e = ${e}")
      return
    }

    if (this."handler_${json.id}") {
      this."handler_${json.id}"(json.payload)
    } else if (this."handler_${json.type}") {
      this."handler_${json.type}"(json.payload)
    } else if (callbacks[json.id]) {
      logger("debug", "parse() - callback for json.id: " + json.id)

      callbacks[json.id].delegate = this
      callbacks[json.id].resolveStrategy = Closure.DELEGATE_FIRST
      def done = callbacks[json.id].call(json)
      if ((done instanceof Boolean) && (done == false)) {
        logger("debug", "parse() - callback[${json.id}]: being kept, done is false")
      } else {
        callbacks[json.id] = null
      }
  } else if (json?.type == "error") {
    if (json?.id == "register_0") {
      if (json?.error.take(3) == "403") {
        // 403 error cancels the pairing process
        pairingKey = ""
        state.pairFailCount = state.pairFailCount ? state.pairFailCount + 1 : 1
        logger("debug", "parse() -  received register_0 error: ${json.error} fail count: ${state.pairFailCount}")
        if (state.pairFailCount < 6) { webosRegister() }
      }
    } else {
      if (json?.error.take(3) == "401") {
        logger("warn", "parse() - received error: ${json.error}")
        //if (state.registerPending == false) { webosRegister() }
        //webosRegister()
      }
    }
  }
}

def webosSubscribeToStatus() {
  logger("debug", "webosSubscribeToStatus()")

  sendWebosCommand(uri: 'audio/getStatus', type: 'subscribe', id: 'audio_getStatus')
  sendWebosCommand(uri: 'com.webos.applicationManager/getForegroundAppInfo', type: 'subscribe', id: 'getForegroundAppInfo')
  sendWebosCommand(uri: 'tv/getChannelProgramInfo', type: 'subscribe', id: 'getChannelProgramInfo')
  //sendCommand('{"type":"subscribe","id":"status_%d","uri":"ssap://com.webos.applicationManager/getForegroundAppInfo"}')
  sendCommand('{"type":"subscribe","id":"status_%d","uri":"ssap://com.webos.service.tv.time/getCurrentTime"}')

// schedule a poll every 10 minutes to help keep the websocket open
// runEvery10Minutes("webosSubscribeToStatus")
}

def getServiceList() {
  logger("debug", "getServiceList()")

  state.remove('serviceList')
  state.serviceList = []
  sendWebosCommand(uri: 'api/getServiceList', callback: { json ->
    logger("trace", "getServiceList() - json: ${json.serviceList}")
    json?.payload?.services.each { service ->
      state.serviceList << service?.name
    }
    logger("debug", "getServiceList() - Services: ${state.serviceList}")
  })
}

def handler_audio_getStatus(data) {
    logger("debug", "handler_audio_getStatus() - data: ${data?.inspect()}")
    def descriptionText = "volume is ${data.volume}"

    logger("info", "${descriptionText}")
    sendEvent(name: "volume", value: data.volume, descriptionText: descriptionText)
}

def handler_getForegroundAppInfo(data) {
  logger("debug", "handler_getForegroundAppInfo() - data: ${data?.inspect()}")

  // Some TVs send this message when powering off
  // data: [subscribed:true, appId:, returnValue:true, windowId:, processId:]
  // json for testing: {"type":"response","id":"getForegroundAppInfo","payload":{"subscribed":true,"appId":"","returnValue":true,"windowId":"","processId":""}}
  if (!data.appId && !data.processId) {
    powerEvent("off", "physical")
    logger("info", "handler_getForegroundAppInfo() - Received POWER DOWN notification")
    return
  }

  def appId = data.appId
  def niceName = appId
  state.nameToInputId.each { name, id ->
    if (appId == id) niceName = name
  }

  def descriptionText = "channelName is ${niceName}"
  logger("info", "${descriptionText}")

  sendEvent(name: "channelName", value: niceName, descriptionText: descriptionText)
  if (niceName != "LiveTV") sendEvent(name: "channelDesc", value: "[none]")

  state.lastApp = niceName
  if (niceName == "LiveTV") {
    runIn(3, "getChannelInfo")
  } else {
    state.lastChannel = [:]
  }
}

def getChannelInfo() {
  logger("debug", "getChannelInfo()")
  sendWebosCommand(uri: 'tv/getChannelProgramInfo', id: 'getChannelProgramInfo')
}

def handler_getChannelProgramInfo(data) {
  logger("debug", "handler_getChannelProgramInfo() - data: ${data?.inspect()}")

  if (data.errorCode) {
    def lastChannel = [:]
    lastChannel.description = "${data.errorText}"
    state.lastChannel = lastChannel
    sendEvent(name: "channelDesc", value: lastChannel.channelDesc)
    // Resubscribe, after error subscription appears to be ended
    if (device.currentChannelName == "LiveTV") {
      runIn(15, "getChannelInfo")
    }
    return
  }

  def lastChannel = [
    description: "${data.channel.channelNumber}/${data.channel.channelName}",
    number: data.channel.channelNumber,
    majorNumber: data.channel.majorNumber ?: data.channel.channelNumber,
    minorNumber: data.channel.minorNumber ?: 0,
    name: data.channel.channelName ?: "",
  ]

  state.lastChannel = lastChannel
  sendEvent(name: "channelDesc", value: lastChannel.description)
  // This is defined as a number, not a decimal so send the major number
  def descriptionText = "full channel number is ${lastChannel.majorNumber}-${lastChannel.minorNumber}"
  sendEvent(name: "channel", value: lastChannel.majorNumber)
  logger("info", "${descriptionText}")

  descriptionText = "channelName is ${lastChannel.name}"
  sendEvent(name: "channelName", value: lastChannel.name, descriptionText: descriptionText)
  logger("info", "${descriptionText}")
}

def genericHandler(json) {
  logger("debug", "genericHandler() - json: ${data?.inspect()}")
}

def deviceNotification(String notifyMessage) {
  logger("debug", "deviceNotification() - notifyMessage: ${notifyMessage?.inspect()}")

  def icon_info = notifyMessage =~ /^\[(.+?)\](.+)/
  logger("debug", "deviceNotification() - new message $notifyMessage found icon: ${icon_info != null}")

  if (!icon_info) {
    sendWebosCommand(uri: "system.notifications/createToast", payload: [message: notifyMessage])
  } else {
    logger("debug", "deviceNotification() - icon_name match ${icon_name}")
    def icon_name = icon_info[0][1]
    def msg = icon_info[0][2]
    notificationIcon(msg, icon_name)
  }
}

def setIcon(String icon_name, String data) {
  logger("debug", "setIcon() - icon_name: ${icon_name?.inspect()}, data: ${data?.inspect()}")
  state.icon_data[icon_name] = data
}

def clearIcons() {
  logger("debug", "clearIcons()")
  state.icon_data = [:]
}

def notificationIcon(String notifyMessage, String icon_name) {
  logger("debug", "notificationIcon() - notifyMessage: ${notifyMessage?.inspect()}, icon_name: ${icon_name?.inspect()}")

  def base_url = "https://raw.githubusercontent.com/pasnox/oxygen-icons-png/master/oxygen/32x32"
  def icon_extention = "png"
  def full_uri = "${base_url}/${icon_name}.png"

  if (!state.icon_data) {
    state.icon_data = [:]
  }

  if (!state.icon_data[icon_name]) {
    try {
      logger("info", "notificationIcon() - asking for ${full_uri}")

      def start_time = now()
      httpGet(full_uri, { resp ->
        handleIconResponse(resp, [
          icon_extention: icon_extention,
          icon_name: icon_name,
          notify_message: notifyMessage,
          start_time: start_time
        ])
      })

    } catch (Exception e) {
      logger("warn", "notificationIcon() - asking for ${full_uri}")
      deviceNotification("<Failed to find icon: ${e.message}>${notifyMessage}")
    }
  } else {
    String icon = state.icon_data[icon_name]
    logger("debug", "notificationIcon() - icon size: ${icon.size()} sending notifcation: ${notifyMessage} name: ${icon_name} icon: ${state.icon_data[icon_name]}")
    sendWebosCommand(uri: "system.notifications/createToast", payload: [message: notifyMessage, iconData: icon, iconExtension: icon_extention])
  }
}

def handleIconResponse(resp, data) {
  logger("debug", "handleIconResponse() - resp: ${resp?.inspect()}, data: ${data?.inspect()}")

  int n = resp.data?.available()
  logger("debug", "handleIconResponse() - resp.status: ${resp.status} took: ${now() - data.start_time}ms size: ${n}")

  byte[] bytes = new byte[n]
  resp.data.read(bytes, 0, n)
  def base64String = bytes.encodeBase64().toString()
  logger("debug", "handleIconResponse() - size of b64: ${base64String.size()}")

  state.icon_data[data.icon_name] = base64String
  notificationIcon(data.notify_message, data.icon_name)
}

def on() {
  logger("debug", "on()")

  powerEvent("on")
  def mac = settings.televisionMac ?: state.televisionMac
  if (!mac) {
    logger("error", "on() - No mac address know for TV, can't send wake on lan")
    return
  }

  logger("info", "on() - Sending Magic Packet to: ${mac}")
  def result = new hubitat.device.HubAction (
    "wake on lan ${mac}",
    hubitat.device.Protocol.LAN,
    null,[secureCode: “0000”]
  )

  logger("debug", "on() - Sending Magic Packet to: ${mac}, result: ${result}")
  return result
}

def off() {
  logger("debug", "off()")
  powerEvent("off")
  sendWebosCommand(uri: 'system/turnOff')
}

def channelUp() {
  logger("debug", "channelUp()")
  sendWebosCommand(uri: 'tv/channelUp')
}

def channelDown() {
  logger("debug", "channelDown()")
  sendWebosCommand(uri: 'tv/channelDown')
}

// handle commands
def volumeUp() {
  logger("debug", "volumeUp()")
  sendWebosCommand(uri: 'audio/volumeUp')
}

def volumeDown() {
  logger("debug", "volumeDown()")
  sendWebosCommand(uri: 'audio/volumeDown')
}

def setVolume(level) {
  logger("debug", "setVolume() - level: ${level}")
  sendWebosCommand(uri: 'audio/setVolume', payload: [volume: level])
}

def setLevel(level) {
  logger("debug", "setLevel() - level: ${level}")
  setVolume(level)
}

def sendMuteEvent(muted) {
  logger("debug", "sendMuteEvent() - muted: ${muted}")

  def descriptionText = "mute is ${muted}"
  logger("info", "${descriptionText}")
  sendEvent(name: "mute", value: muted, descriptionText: descriptionText)
}

def unmute() {
  logger("debug", "unmute()")
  sendWebosCommand(uri: 'audio/setMute', payload: [mute: false], callback: { json ->
    logger("trace", "unmute() - json: ${json}")
    if (json?.payload?.returnValue) {
      sendMuteEvent("unmuted")
    }
  })
}

def mute() {
  logger("debug", "mute()")
  sendWebosCommand(uri: 'audio/setMute', payload: [mute: true], callback: { json ->
  logger("trace", "mute() - json: ${json}")
    if (json?.payload?.returnValue) {
      sendMuteEvent("muted")
    }
  })
}

def externalInput(String input) {
  logger("debug", "externalInput() - input: ${input}")

  if (state.nameToInputId && state.nameToInputId[input]) {
    input = state.nameToInputId[input]
  }
  sendWebosCommand(uri: "system.launcher/launch", payload: [id: input], callback: { json ->
    logger("trace", "externalInput() - json: ${json}")
  })
}

def enter() {
  logger("debug", "enter()")

  def mouseDev = getMouseChild()
  mouseDev?.sendButton('ENTER')
//return sendWebosCommand(uri: "com.webos.service.ime/sendEnterKey")
}

def back() {
  logger("debug", "back()")
  def mouseDev = getMouseChild()
  mouseDev?.sendButton('BACK')
}

def up() {
  logger("debug", "up()")
  def mouseDev = getMouseChild()
  mouseDev?.sendButton('UP')
}

def down() {
  logger("debug", "down()")
  def mouseDev = getMouseChild()
  mouseDev?.sendButton('DOWN')
}

def left() {
  logger("debug", "left()")
  def mouseDev = getMouseChild()
  mouseDev?.left()
}

def right() {
  logger("debug", "right()")
  def mouseDev = getMouseChild()
  mouseDev?.right()
}

def myApps() {
  logger("debug", "myApps()")
  sendWebosCommand(uri: 'system.launcher/launch', payload: [id: 'com.webos.app.discovery'])
}

def play() {
  logger("debug", "play()")
  sendWebosCommand(uri: "media.controls/play")
}

def pause() {
  logger("debug", "pause()")
  sendWebosCommand(uri: "media.controls/pause")
}

def home() {
  logger("debug", "home()")
  logger("debug", "home() - OLD Inputs: ${state.inputList} total length: ${state.toString().length()}")

  state.remove('serviceList')
  state.serviceList = []
  sendWebosCommand(uri: 'api/getServiceList', callback: { json ->
    logger("trace", "home() - getServiceList: ${json?.payload}")

    json?.payload?.services.each { service ->
      state.serviceList << service?.name
    }
    logger("info", "home() - Services: ${state.serviceList}")
  })
}

def sendCommand(cmd) {
  logger("debug", "sendCommand() - cmd: ${cmd?.inspect()}")

  def msg = String.format(cmd,state.sequenceNumber)
  logger("debug", "sendCommand() - msg: ${msg?.inspect()}")

  try {
    // send the command
    interfaces.webSocket.sendMessage(msg)
  } catch (Exception e) {
    logger("warn", "sendCommand() - Exception ${e}")
  }
  state.sequenceNumber++
}

def sendWebosCommand(Map params) {
  logger("debug", "sendWebosCommand() - params: ${params?.inspect()}")

  def id = params.id ?: ("command_" + state.sequenceNumber++)
  def cb = params.callback ?: { genericHandler(it) }
  def message_data = [
    'id': id,
    'type': params.type ?: "request",
  ]

  if (params.uri) {
    message_data.uri = "ssap://" + params.uri
  }

  if (params.payload) {
    message_data.payload = params.payload
  }

  def json = JsonOutput.toJson(message_data)
  logger("debug", "sendWebosCommand() - Sending: ${json} storing callback: ${id}")

  callbacks[id] = cb
  interfaces.webSocket.sendMessage(json)
  logger("debug", "sendWebosCommand() - Sending json: ${json}")
}

private void parseStatus(state, json) {
  logger("debug", "parseStatus() - state: ${state?.inspect()}, json: ${json?.inspect()}")

  def rResp = false
  if ((state.power == "off") && !(json?.payload?.subscribed == true)) {
    // when TV has indicated power off, do not process status messages unless they are subscriptions
    logger("debug", "parseStatus() - ignoring unsubscribed status updated during power off... message: ${json}")
    return
  }

  if (json?.payload?.returnValue == true) {
    // The last (valid) message sent by the TV when powering off is a subscription response for foreground app status with appId, windowId and processID all NULL
    if (json?.payload?.subscribed) {
      logger("debug", "parseStatus() - appID: "+ (description.contains("appId")?"T":"F") +", windowId: "+ (description.contains("windowId")?"T":"F") +", processId: "+ (description.contains("processId")?"T":"F"))

      if (description.contains("appId") && description.contains("windowId") && description.contains("processId")) {
        if ((json?.payload?.appId == null) || (json?.payload?.appId == "")) {
          // The TV is powering off - change the power state, but leave the websocket to time out
          powerEvent("off", "physical")
          logger("info", "Received POWER DOWN notification")
        }
      }
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
    if (setLevelIdx<0) {
      setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
    }
    if (levelIdx<= setLevelIdx) {
      log."${level}" "${device.displayName} ${msg}"
    }
  }
}