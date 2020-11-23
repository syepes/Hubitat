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

@Field String VERSION = "1.1.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

@Field static Map deviceData = [:]
@Field static List sendQueue = []
@Field static boolean asyncInProgress = false
@Field static final asyncLock = new Object[0]
@Field static TreeMap stats = [_totalSends: 0, _totalSendTime: 0, _maxSendTime: 0, _totalEvents: 0, _sumQueueSize: 0, _maxQueueSize: 0]

definition(
  name: "MetricLogger",
  namespace: "syepes",
  author: "Sebastian YEPES",
  description: "Metric Logger",
  category: "",
  oauth: false,
  singleInstance: true,
  iconUrl: "https://github.com/CopyCat73/SmartThings-Dev/raw/master/NetatmoSecurity.png",
  iconX2Url: "https://github.com/CopyCat73/SmartThings-Dev/raw/master/NetatmoSecurity@2x.png"
)

preferences {
  page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
    section("Internal") {
      href name: "href", title: "Statistics", page: "statsPage", required: false
    }

    section("General") {
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "deviceDetails", title: "Device Details", description: "Collects additional device details (Additional authentication is required if the Hub Login Security is Enabled)", type: "enum", options:[[0:"Disabled"], [1:"Enabled"]], defaultValue: 1, required: true
      input name: "he_usr", title: "HE User", type: "text", description: "Only if Hub Login Security is Enabled", defaultValue: "", required: false
      input name: "he_pwd", title: "HE Password", type: "password", description: "Only if Hub Login Security is Enabled", defaultValue: "", required: false
    }

    section ("Destination") {
      input name: "ip", title: "FQDN or IP Address", type: "text", defaultValue: "192.168.1.1", required: true
      input name: "port", title: "API Port", type: "number", defaultValue: 8428, required: true
      input name: "queueMaxSize", title: "Queue Max Size", type: "number", defaultValue: 5000, required: true
    }

    section("Polling:") {
      input name: "manualPollingInterval", title: "Manual device polling interval (minutes)", type: "number", defaultValue: 10, required: true
    }

    section("Devices") {
      input "accelerationSensor", "capability.accelerationSensor", title: "AccelerationSensor", multiple: true, required: false
      input "alarm", "capability.alarm", title: "Alarm", multiple: true, required: false
      input "battery", "capability.battery", title: "Battery", multiple: true, required: false
      input "beacon", "capability.beacon", title: "Beacon", multiple: true, required: false
      input "bulb", "capability.bulb", title: "Bulb", multiple: true, required: false
      input "buttons", "capability.button", title: "Buttons", multiple: true, required: false
      input "co2s", "capability.carbonDioxideMeasurement", title: "CarbonDioxideMeasurement", multiple: true, required: false
      input "cos", "capability.carbonMonoxideDetector", title: "CarbonMonoxideDetector", multiple: true, required: false
      input "chime", "capability.chime", title: "Chime", multiple: true, required: false
      input "colorControl", "capability.colorControl", title: "ColorControl", multiple: true, required: false
      input "colorTemperature", "capability.colorTemperature", title: "ColorTemperature", multiple: true, required: false
      input "consumable", "capability.consumable", title: "Consumable", multiple: true, required: false
      input "contactSensor", "capability.contactSensor", title: "ContactSensor", multiple: true, required: false
      input "doorControl", "capability.doorControl", title: "DoorControl", multiple: true, required: false
      input "doubleTapableButton", "capability.doubleTapableButton", title: "DoubleTapableButton", multiple: true, required: false
      input "energyMeter", "capability.energyMeter", title: "EnergyMeter", multiple: true, required: false
      input "garageDoorControl", "capability.garageDoorControl", title: "GarageDoorControl", multiple: true, required: false
      input "holdableButton", "capability.holdableButton", title: "HoldableButton", multiple: true, required: false
      input "illuminanceMeasurement", "capability.illuminanceMeasurement", title: "IlluminanceMeasurement", multiple: true, required: false
      input "indicator", "capability.indicator", title: "Indicator", multiple: true, required: false
      input "light", "capability.light", title: "Light", multiple: true, required: false
      input "lock", "capability.lock", title: "Lock", multiple: true, required: false
      input "motionSensor", "capability.motionSensor", title: "MotionSensor", multiple: true, required: false
      input "musicPlayer", "capability.musicPlayer", title: "MusicPlayer", multiple: true, required: false
      input "outlet", "capability.outlet", title: "Outlet", multiple: true, required: false
      input "powerMeter", "capability.powerMeter", title: "PowerMeter", multiple: true, required: false
      input "presenceSensor", "capability.presenceSensor", title: "PresenceSensor", multiple: true, required: false
      input "pressureMeasurement", "capability.pressureMeasurement", title: "PressureMeasurement", multiple: true, required: false
      input "pushableButton", "capability.pushableButton", title: "PushableButton", multiple: true, required: false
      input "relativeHumidityMeasurement", "capability.relativeHumidityMeasurement", title: "RelativeHumidityMeasurement", multiple: true, required: false
      input "relaySwitch", "capability.relaySwitch", title: "RelaySwitch", multiple: true, required: false
      input "releasableButton", "capability.releasableButton", title: "ReleasableButton", multiple: true, required: false
      input "samsungTV", "capability.samsungTV", title: "SamsungTV", multiple: true, required: false
      input "shockSensor", "capability.shockSensor", title: "ShockSensor", multiple: true, required: false
      input "signalStrength", "capability.signalStrength", title: "SignalStrength", multiple: true, required: false
      input "sleepSensor", "capability.sleepSensor", title: "SleepSensor", multiple: true, required: false
      input "smokeDetector", "capability.smokeDetector", title: "SmokeDetector", multiple: true, required: false
      input "soundPressureLevel", "capability.soundPressureLevel", title: "SoundPressureLevel", multiple: true, required: false
      input "soundSensor", "capability.soundSensor", title: "SoundSensor", multiple: true, required: false
      input "stepSensor", "capability.stepSensor", title: "StepSensor", multiple: true, required: false
      input "switch", "capability.switch", title: "Switch", multiple: true, required: false
      input "switchLevel", "capability.switchLevel", title: "SwitchLevel", multiple: true, required: false
      input "tamperAlert", "capability.tamperAlert", title: "TamperAlert", multiple: true, required: false
      input "temperatureMeasurement", "capability.temperatureMeasurement", title: "TemperatureMeasurement", multiple: true, required: false
      input "thermostat", "capability.thermostat", title: "Thermostat", multiple: true, required: false
      input "thermostatCoolingSetpoint", "capability.thermostatCoolingSetpoint", title: "ThermostatCoolingSetpoint", multiple: true, required: false
      input "thermostatFanMode", "capability.thermostatFanMode", title: "ThermostatFanMode", multiple: true, required: false
      input "thermostatHeatingSetpoint", "capability.thermostatHeatingSetpoint", title: "ThermostatHeatingSetpoint", multiple: true, required: false
      input "thermostatSetpoint", "capability.thermostatSetpoint", title: "ThermostatSetpoint", multiple: true, required: false
      input "timedSession", "capability.timedSession", title: "TimedSession", multiple: true, required: false
      input "touchSensor", "capability.touchSensor", title: "TouchSensor", multiple: true, required: false
      input "ultravioletIndex", "capability.ultravioletIndex", title: "UltravioletIndex", multiple: true, required: false
      input "valve", "capability.valve", title: "Valve", multiple: true, required: false
      input "videoCapture", "capability.videoCamera", title: "VideoCamera", multiple: true, required: false
      input "voltageMeasurement", "capability.voltageMeasurement", title: "VoltageMeasurement", multiple: true, required: false
      input "waterSensor", "capability.waterSensor", title: "WaterSensor", multiple: true, required: false
      input "windowShade", "capability.windowShade", title: "WindowShade", multiple: true, required: false
      input "pHMeasurement", "capability.pHMeasurement", title: "pHMeasurement", multiple: true, required: false
    }
  }

  page(name: "statsPage", title: "Statistics") {
    section() {
      String lines = ""
      lines += "<b>Total Received:</b> ${stats._totalEvents}\n"
      lines += "<b>Total Sent:</b> ${stats._totalSends}\n"
      lines += "<b>Current Queue Size:</b> ${sendQueue.size()}\n"
      if (stats._totalSends > 0) {
        lines += "<b>Max Queue Size:</b> ${stats._maxQueueSize}\n"
        lines += "<b>Average Queue Size:</b> ${stats._sumQueueSize / stats._totalSends}\n"
        lines += "<b>Max Send Time:</b> ${stats._maxSendTime}\n"
        lines += "<b>Average Send Time:</b> ${stats._totalSendTime / stats._totalSends}\n"
      }
      lines += "<hr />"
      stats.each { device_name, eventCount ->
        if (device_name.startsWith("_")){ return }
        lines += "<b>$device_name:</b> $eventCount\n"
      }
      paragraph lines
    }
  }
}

def installed() {
  logger("debug", "installed(${VERSION}) - settings: ${settings}")
  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION, status:'Current version']
  }
  updated()
}

def uninstalled() {
  logger("debug", "uninstalled()")
  unschedule()
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    if (state.driverInfo == null || state.driverInfo.isEmpty()) {
      state.driverInfo = [ver:VERSION, status:'Current version']
    }
  }

  unschedule()

  state.deviceAttributes = []
  state.deviceAttributes << [ devices: 'accelerationSensor', attributes: ['acceleration']]
  state.deviceAttributes << [ devices: 'alarm', attributes: ['alarm']]
  state.deviceAttributes << [ devices: 'battery', attributes: ['battery']]
  state.deviceAttributes << [ devices: 'beacon', attributes: ['presence']]
  state.deviceAttributes << [ devices: 'bulb', attributes: ['switch']]
  state.deviceAttributes << [ devices: 'buttons', attributes: ['button']]
  state.deviceAttributes << [ devices: 'co2s', attributes: ['carbonDioxide']]
  state.deviceAttributes << [ devices: 'cos', attributes: ['carbonMonoxide']]
  state.deviceAttributes << [ devices: 'chime', attributes: ['status']]
  state.deviceAttributes << [ devices: 'colorControl', attributes: ['hue','saturation']]
  state.deviceAttributes << [ devices: 'colorTemperature', attributes: ['colorTemperature']]
  state.deviceAttributes << [ devices: 'consumable', attributes: ['consumableStatus']]
  state.deviceAttributes << [ devices: 'contactSensor', attributes: ['contact']]
  state.deviceAttributes << [ devices: 'doorControl', attributes: ['door']]
  state.deviceAttributes << [ devices: 'doubleTapableButton', attributes: ['doubleTapped']]
  state.deviceAttributes << [ devices: 'energyMeter', attributes: ['energy']]
  state.deviceAttributes << [ devices: 'garageDoorControl', attributes: ['door']]
  state.deviceAttributes << [ devices: 'holdableButton', attributes: ['held']]
  state.deviceAttributes << [ devices: 'illuminanceMeasurement', attributes: ['illuminance']]
  state.deviceAttributes << [ devices: 'indicator', attributes: ['indicatorStatus']]
  state.deviceAttributes << [ devices: 'light', attributes: ['switch']]
  state.deviceAttributes << [ devices: 'locks', attributes: ['lock']]
  state.deviceAttributes << [ devices: 'motionSensor', attributes: ['motion']]
  state.deviceAttributes << [ devices: 'musicPlayer', attributes: ['status','level','mute']]
  state.deviceAttributes << [ devices: 'outlet', attributes: ['switch']]
  state.deviceAttributes << [ devices: 'powerMeter', attributes: ['power','voltage','current','powerFactor']]
  state.deviceAttributes << [ devices: 'presenceSensor', attributes: ['presence']]
  state.deviceAttributes << [ devices: 'pressureMeasurement', attributes: ['pressure']]
  state.deviceAttributes << [ devices: 'pushableButton', attributes: ['numberOfButtons', 'pushed']]
  state.deviceAttributes << [ devices: 'relativeHumidityMeasurement', attributes: ['humidity']]
  state.deviceAttributes << [ devices: 'relaySwitch', attributes: ['switch']]
  state.deviceAttributes << [ devices: 'releasableButton', attributes: ['released']]
  state.deviceAttributes << [ devices: 'samsungTV', attributes: ['switch','mute','volume']]
  state.deviceAttributes << [ devices: 'shockSensor', attributes: ['shock']]
  state.deviceAttributes << [ devices: 'signalStrength', attributes: ['lqi','rssi']]
  state.deviceAttributes << [ devices: 'sleepSensor', attributes: ['sleeping']]
  state.deviceAttributes << [ devices: 'smokeDetector', attributes: ['smoke']]
  state.deviceAttributes << [ devices: 'soundPressureLevel', attributes: ['soundPressureLevel']]
  state.deviceAttributes << [ devices: 'soundSensor', attributes: ['sound']]
  state.deviceAttributes << [ devices: 'stepSensor', attributes: ['steps','goal']]
  state.deviceAttributes << [ devices: 'switch', attributes: ['switch']]
  state.deviceAttributes << [ devices: 'switchLevel', attributes: ['level']]
  state.deviceAttributes << [ devices: 'tamperAlert', attributes: ['tamper']]
  state.deviceAttributes << [ devices: 'temperatureMeasurement', attributes: ['temperature']]
  state.deviceAttributes << [ devices: 'thermostat', attributes: ['temperature','heatingSetpoint','coolingSetpoint','thermostatSetpoint','thermostatMode','thermostatFanMode','thermostatOperatingState','thermostatSetpointMode','scheduledSetpoint','optimisation','windowFunction']]
  state.deviceAttributes << [ devices: 'thermostatCoolingSetpoint', attributes: ['coolingSetpoint']]
  state.deviceAttributes << [ devices: 'thermostatFanMode', attributes: ['thermostatFanMode']]
  state.deviceAttributes << [ devices: 'thermostatHeatingSetpoint', attributes: ['heatingSetpoint']]
  state.deviceAttributes << [ devices: 'thermostatSetpoint', attributes: ['thermostatSetpoint']]
  state.deviceAttributes << [ devices: 'timedSession', attributes: ['timeRemaining', 'sessionStatus']]
  state.deviceAttributes << [ devices: 'touchSensor', attributes: ['touch']]
  state.deviceAttributes << [ devices: 'ultravioletIndex', attributes: ['ultravioletIndex']]
  state.deviceAttributes << [ devices: 'valve', attributes: ['valve']]
  state.deviceAttributes << [ devices: 'videoCapture', attributes: ['camera','mute']]
  state.deviceAttributes << [ devices: 'voltageMeasurement', attributes: ['voltage']]
  state.deviceAttributes << [ devices: 'waterSensor', attributes: ['water']]
  state.deviceAttributes << [ devices: 'windowShade', attributes: ['windowShade', 'position']]
  state.deviceAttributes << [ devices: 'pHMeasurement', attributes: ['pH']]

  // get device inventory
  if (deviceDetails.toInteger()) { deviceInventory() }

  // Configure Scheduling:
  manageSchedules()

  // Configure Subscriptions:
  manageSubscriptions()

  schedule("0 0 12 */7 * ?", updateCheck)
  if (deviceDetails.toInteger()) { schedule("0 0 * ? * *", deviceInventory) }
}

def handleEvent(evt) {
  logger("debug", "handleEvent() - evt: ${evt}")
  try {
    if (!stats[evt.displayName]){ stats[evt.displayName] = 0 }
    stats[evt.displayName] += 1
    stats._totalEvents += 1

    def hub = location.hubs[0]

    def metric = evt.name
    Integer device_id = escapeCharacters(evt.deviceId).toInteger()
    def device_name = escapeCharacters(evt.displayName)
    def groupId = escapeCharacters(evt?.device.device.groupId)
    def hub_id = escapeCharacters(hub?.id)
    def hub_name = escapeCharacters(hub?.name)
    def hub_ip = escapeCharacters(hub?.localIP)

    if (deviceDetails.toInteger()) {
      if(deviceData?.size() == 0) {
        deviceInventory()
      }
      if(deviceData?.size() > 0 && deviceData?.containsKey(device_id)) {deviceDetails
        Map labels = [hub_name: hub_name,
                      hub_ip: hub_ip,
                      device_id: device_id,
                      device_id_net: escapeCharacters(deviceData[device_id].deviceNetworkId),
                      device_name: device_name,
                      device_label: escapeCharacters(deviceData[device_id].label),
                      device_driver: escapeCharacters(deviceData[device_id].deviceTypeName),
                      device_driver_type: escapeCharacters(deviceData[device_id].type)
        ]

        String device_inventory = "hub_info,${labels.collect{ k,v -> "$k=$v" }.join(',')} value=1"
        pushMetric(device_inventory)
      } else {
        logger("warn", "handleEvent() - Device not found in the inventory device_id:${device_id},  device_name:${evt?.name}, device_label:${evt?.displayName}")
      }
    }

    def value = escapeCharacters(evt.value)

    String data = "${metric},hub_name=${hub_name},hub_ip=${hub_ip},device_id=${device_id},device_name=${device_name}"

    // String-valued attributes can be translated to Numbers
    if ('acceleration' == evt.name) { // acceleration: active = 1, inactive = 0
      value = ('active' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('alarm' == evt.name) { // alarm: strobe/siren/both = 1, off = 0
      value = ('off' == evt.value) ? '0' : '1'
      data += " value=${value}"
    } else if ('presence' == evt.name) { // presence: present = 1, not present = 0
      value = ('present' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('switch' == evt.name) { // switch: on = 1, off = 0
      value = ('on' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('button' == evt.name) { // button: held = 1, pushed = 0
      value = ('pushed' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('carbonMonoxide' == evt.name) { // carbonMonoxide: detected = 1, clear/tested = 0
      value = ('detected' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('status' == evt.name) { // status: playing = 1, stopped = 0
      value = ('playing' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('consumableStatus' == evt.name) { // consumableStatus: good" = 1, "missing"/"replace"/"maintenance_required"/"order" = 0
      value = ('good' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('contact' == evt.name) { // contact: closed = 0, open = 1
      value = ('closed' == evt.value) ? '0' : '1'
      data += " value=${value}"
    } else if ('door' == evt.name) { // door: closed = 0, open/opening/closing/unknown = 1
      value = ('closed' == evt.value) ? '0' : '1'
      data += " value=${value}"
    } else if ('indicatorStatus' == evt.name) { // door: when on = 1, never/when off = 0
      value = ('when on' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('lock' == evt.name) { // door: locked = 0, unlocked/unlocked with timeout/unlocked/unknown = 1
      value = ('locked' == evt.value) ? '0' : '1'
      data += " value=${value}"
    } else if ('motion' == evt.name) { // Motion: active = 1, inactive = 0
      value = ('active' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('shock' == evt.name) { // Motion: detected = 1, clear = 0
      value = ('detected' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('mute' == evt.name) { // mute: muted = 1, unmuted = 0
      value = ('muted' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('sleeping' == evt.name) { // sleeping: sleeping = 1, not sleeping = 0
      value = ('sleeping' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('smoke' == evt.name) { // smoke: detected = 1, clear/tested = 0
      value = ('detected' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('sound' == evt.name) { // sound: detected = 1, not detected = 0
      value = ('detected' == evt.value) ? '1' : '0'
      data += " value=${value}"
    }  else if ('tamper' == evt.name) { // tamper: detected = 1, clear = 0
      value = ('detected' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('thermostatMode' == evt.name) { // thermostatMode: <any other value> = 1, off = 0
      value = ('off' == evt.value) ? '0' : '1'
      data += " value=${value}"
    } else if ('thermostatFanMode' == evt.name) { // thermostatFanMode: <any other value> = 1, off = 0
      value = ('off' == evt.value) ? '0' : '1'
      data += " value=${value}"
    } else if ('thermostatOperatingState' == evt.name) { // thermostatOperatingState: heating = 1, <any other value> = 0
      value = ('heating' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('thermostatSetpointMode' == evt.name) { // thermostatSetpointMode: followSchedule = 0, <any other value> = 1
      value = ('followSchedule' == evt.value) ? '0' : '1'
      data += " value=${value}"
    } else if ('timedSession' == evt.name) { // timedSession: stopped/canceled/paused = 0, running = 1
      value = ('running' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('touch' == evt.name) { // touch: touched = 1, "" = 0
      value = ('touched' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('valve' == evt.name) { // valve: valve = 1, closed = 0
      value = ('open' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('camera' == evt.name) { // valve: on = 1, off/restarting/unavailable = 0
      value = ('on' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('water' == evt.name) { // water: wet = 1, dry = 0)
      value = ('wet' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('windowShade' == evt.name) { // windowShade: opening/partially open/open = 1, closing/closed/unknown = 0
      value = ('opening' == evt.value || 'partially open' == evt.value || 'open' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('optimisation' == evt.name) { // optimisation: active = 1, inactive = 0
      value = ('active' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if ('windowFunction' == evt.name) { // windowFunction: active = 1, inactive = 0
      value = ('active' == evt.value) ? '1' : '0'
      data += " value=${value}"
    } else if (evt.value ==~ /.*[^0-9\.,-].*/) {
      logger("warn", "handleEvent() - Found a string value that's not explicitly handled: ${evt}")
    } else {
      data += " value=${value}"
    }

    pushMetric(data)
  } catch (e) {
    logger("error", "handleEvent() - ${e}, evt: ${evt}")
  }
}

@groovy.transform.Synchronized("asyncLock")
void pushMetric(String data) {
  logger("debug", "pushMetric() - ${data}")

  // Add timestamp
  sendQueue << data + " ${new Date().getTime()}"
  runIn(1, pushMetricFromQueue)
}

@groovy.transform.Synchronized("asyncLock")
void pushMetricFromQueue() {
  logger("debug", "pushMetricFromQueue() - sendQueue: ${sendQueue.size()} / asyncInProgress: ${asyncInProgress}")

  if (sendQueue.size() == 0){ return }
  if (asyncInProgress){ return }
  asyncInProgress = true

  stats._totalSends += 1
  stats._sumQueueSize += sendQueue.size()
  if (sendQueue.size() > stats._maxQueueSize){ stats._maxQueueSize = sendQueue.size() }
  String data = sendQueue.join("\n")

  try {
    Map postParams = [
      uri: "http://${ip}:${port}/write?precision=ms",
      requestContentType: 'application/json',
      contentType: 'application/json',
      headers: ['Content-type':'application/json'],
      body : data
    ]
    asynchttpPost('metricResponse', postParams, [message: data, send_time: new Date().getTime()])

  } catch (e) {
    logger("error", "pushMetricFromQueue() - Sending Metrics: ${e}")
    asyncInProgress = false
  }
}

@groovy.transform.Synchronized("asyncLock")
void metricResponse(hubResponse, payload) {
  logger("trace", "metricResponse() - Response: ${hubResponse.status}, Payload: ${payload}")

  try {
    if(hubResponse.status < 300) { // OK
      logger("info", "Sent Metrics: ${sendQueue.size()}")

      def delta = new Date().getTime() - payload.send_time
      stats._totalSendTime += delta
      if (delta > stats._maxSendTime){ stats._maxSendTime = delta }
      sendQueue = []

    } else { // Failed
      sendQueue << payload?.message
      String errData = hubResponse.getErrorData()
      String errMsg = hubResponse.getErrorMessage()
      logger("warn", "Failed Sending Metrics - QueueSize: ${sendQueue?.size()}, Response: ${hubResponse?.status}, Error: ${errData} ${errMfg}")
      logger("trace", "metricResponse() - API: http://${ip}:${port}/write?precision=ms, Response: ${hubResponse.status}, Error: ${errData} ${errMfg}, Headers: ${hubResponse?.headers}, Payload: ${payload}")
      if (sendQueue?.size() >= queueMaxSize) {
        logger("error", "Maximum Queue size reached: ${sendQueue?.size()} >= ${queueMaxSize}, all current logs have been droped")
        sendQueue = []
      }
    }
    asyncInProgress = false

  } catch (e) {
    logger("error", "metricResponse() - Response: ${hubResponse.status}, Payload: ${payload}, Error: ${e}")
    asyncInProgress = false
  }
}

def manualPoll() {
  logger("debug", "manualPoll()")

  // Iterate over each attribute for each device, in each device collection in deviceAttributes:
  def devs // temp variable to hold device collection.
  state.deviceAttributes.each { da ->
    devs = settings."${da.devices}"
    if (devs && (da.attributes)) {
      devs.each { d ->
        da.attributes.each { attr ->
          if (d.hasAttribute(attr) && d.latestState(attr)?.value != null) {
            logger("debug", "manualPoll() - device: ${d} for attribute: ${attr}")

            // Send fake event to handleEvent():
            handleEvent([
              name: attr,
              value: d.latestState(attr)?.value,
              device: d,
              deviceId: d.id,
              displayName: d.displayName
            ])
          }
        }
      }
    }
  }
}

private manageSchedules() {
  logger("debug", "manageSchedules()")

  // Generate a random offset (1-60):
  Random rand = new Random(now())
  Integer randomOffset = 0

  try {
    unschedule(manualPoll)
  } catch(e) { }

  if (manualPollingInterval > 0) {
    logger("info", "Manual Device Polling Enabled")
    randomOffset = rand.nextInt(60)
    logger("debug", "manageSchedules() - Scheduling Device Polling to run every ${manualPollingInterval} minutes (offset of ${randomOffset} seconds)")
    schedule("${randomOffset} 0/${manualPollingInterval} * * * ?", "manualPoll")
  } else {
    logger("info", "Manual Device Polling Disabled")
  }
}

private manageSubscriptions() {
  logger("debug", "manageSubscriptions()")

  // Unsubscribe:
  unsubscribe()

  // Subscribe to App Touch events:
  subscribe(app,handleAppTouch)

  // Subscribe to device attributes (iterate over each attribute for each device collection in state.deviceAttributes):
  def devs // dynamic variable holding device collection.
  state.deviceAttributes.each { da ->
    devs = settings."${da.devices}"
    if (devs && (da.attributes)) {
      da.attributes.each { attr ->
        logger("info", "Subscribing to attribute: ${attr} on device: ${devs?.getAt(0)}")
        // There is no need to check if all devices in the collection have the attribute.
        subscribe(devs, attr, handleEvent)
      }
    }
  }
}

def handleAppTouch(evt) {
  logger("debug", "handleAppTouch() - ${evt}")
  manualPoll()
}

// https://docs.influxdata.com/influxdb/v0.10/write_protocols/write_syntax/
private escapeCharacters(str) {
  if (str) {
    str = str.toString()
    str = str.replaceAll(" ", "\\\\ ") // Escape spaces
    str = str.replaceAll(",", "\\\\,") // Escape commas
    str = str.replaceAll("=", "\\\\=") // Escape equal signs
    str = str.replaceAll("\"", "\\\\\"") // Escape double quotes
    str = str.trim() // Trim end spaces
  }
  return str
}


private void loginHE() {
  if (state.authToken != "") { return }

  try {
    state.authToken = ''
    Map params = [
      uri: 'http://localhost:8080',
      path: '/login',
      ignoreSSLIssues:  true,
      requestContentType: 'application/x-www-form-urlencoded',
      body: [username: "syepes", password: '***REMOVED***']
    ]

    httpPost(params) { resp ->
      if (resp.getStatus() == 302) {
        resp.getHeaders('Set-Cookie').each {
          state.authToken = state.authToken + it.value.split(';')[0] + ';'
        }
      } else {
        state.authToken = ''
      }
    }
  } catch (e) {
    logger("error", "loginHE() - Error: ${e}")
  }
}

void deviceInventory() {
  if (he_usr != "" && he_pwd != "") { loginHE() }

  try {
    Map headers = ['Content-Type': 'application/json;charset=UTF-8', 'Host': 'localhost']
    if (he_usr != "" && he_pwd != "") { headers['Cookie'] = state.authToken }

    Map params = [
      uri: 'http://localhost:8080',
      path: '/device/list/all/data',
      contentType: "application/json; charset=utf-8",
      requestContentType: "application/json; charset=utf-8",
      headers: headers
    ]

    deviceData = [:]
    httpGet(params) { resp ->
      logger("trace", "deviceInventory() - Status: ${resp?.getStatus()} / Data: ${resp?.getData()}")

      if (resp.getStatus() == 200) {
        def inv = resp.getData()
        inv?.each { deviceData[it.id] = it }
        logger("info", "Device inventory: ${deviceData?.size()}")
      }
    }
  } catch (e) {
    if (he_usr != "" && he_pwd != "") { state.authToken = '' }
    logger("error", "deviceInventory() - Error: ${e}")
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
      log."${level}" "${app.name} ${msg}"
    }
  }
}

def updateCheck() {
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Apps/VictoriaMetrics/MetricLogger.groovy"]
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
