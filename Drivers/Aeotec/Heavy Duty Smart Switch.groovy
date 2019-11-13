/**
 *	Copyright 2015 SmartThings
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.transform.Field

@Field int VERSION = 1

@Field List<String> LOG_LEVELS = ["warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Z-Wave Metering Switch", namespace: "smartthings", author: "SmartThings") {
    capability "Energy Meter"
    capability "Actuator"
    capability "Switch"
    capability "Power Meter"
    capability "Energy Meter"
    capability "Voltage Measurement"
    capability "Temperature Measurement"
    capability "Refresh"
    capability "Polling"
    capability "Sensor"
    capability "Light"
    capability "Health Check"
    capability "Configuration"


    command "clearState"
    command "reset"

    attribute "amperage", "number"
    attribute "electric", "number"

    fingerprint inClusters: "0x25,0x32"
    fingerprint mfr: "0086", prod: "0003", model: "004E", deviceJoinName: "Aeotec Heavy Duty Smart Switch" //EU
    fingerprint mfr: "0086", prod: "0103", model: "004E", deviceJoinName: "Aeotec Heavy Duty Smart Switch" //US
  }

  preferences {
    section("General") {
      input name: "logLevel", title: "Log Level", type: "enum", options:[[0:"Off"],[1:"Info"],[2:"Debug"],[3:"Trace"]], defaultValue: 0
    }
    section("Configuration") {
      input name: "param20", title: "Default Load state (20)", description: "Used for indicating the default state of output load after re-power on", type: "enum", options:[[0:"Last state after power on"],[1:"Always on after re-power on"],[2:"Always off stare after re-power on"]], defaultValue: 0, required: true
      input name: "param111", title: "Report interval (111)", description: "Interval (seconds) between each report", type: "number", range: "0..268435456", defaultValue: 300, required: true

      input name: "param101_voltage", title: "Report Instantaneous Voltage (101)", description: "", type: "bool", defaultValue: "true", required: true
      input name: "param101_current", title: "Report Instantaneous Current (Amperes) (101)", description: "", type: "bool", defaultValue: "true", required: true
      input name: "param101_watts", title: "Report Instantaneous Watts (101)", description: "", type: "bool", defaultValue: "true", required: true
      input name: "param101_currentUsage", title: "Report Accumulated kWh (101)", description: "", type: "bool", defaultValue: "true", required: true

      input name: "param80", title: "Load change notifications (80)", description: "Send notifications when the state of the load is changed", type: "enum", options:[[0:"Send Nothing (Disabled)"],[1:"Send HAIL Command"],[2:"Send BASIC Report Command"]], defaultValue: 0, required: true

      input name: "param91", title: "Minimum change in wattage (91)", description: "Report when the change of the current power is more/less than the threshold in wattage", type: "number", range: "0..32767", defaultValue: 50, required: true
      input name: "param92", title: "Minimum change in percentage (92)", description: "Report when the change of the current power is more/less than the threshold in percentage", type: "number", range: "0..100", defaultValue: 10, required: true
    }
  }

}


def installed() {
  logger("trace", "installed(${VERSION})")

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  }
  state.driverVer = VERSION

  // Device-Watch simply pings if no device events received for 32min(checkInterval)
  initialize()
  if (state?.mfr?.equals("0063") || state?.mfr?.equals("014F")) { // These old GE devices have to be polled. GoControl Plug refresh status every 15 min.
    runEvery15Minutes("poll", [forceForLocallyExecuting: true])
  }
}

def updated() {
  logging "updated()", 2

  // Make sure installation has completed:
  if (!state.driverVer || state.driverVer != VERSION) {
    installed()
  }

  // Device-Watch simply pings if no device events received for 32min(checkInterval)
  initialize()
  if (state?.mfr?.equals("0063") || state?.mfr?.equals("014F")) { // These old GE devices have to be polled. GoControl Plug refresh status every 15 min.
    unschedule("poll", [forceForLocallyExecuting: true])
    runEvery15Minutes("poll", [forceForLocallyExecuting: true])
  }
  try {
    if (!state.MSR) {
      response(zwave.manufacturerSpecificV2.manufacturerSpecificGet().format())
    }
  } catch (e) {
    log.debug e
  }
}

def initialize() {
  logging "initialize()", 2

  sendEvent(name: "checkInterval", value: 1 * 60 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}


// parse events into attributes
def parse(String description) {
  logging "parse() - description: ${description.inspect()}", 2

  def result = null
  if (description != "updated") {
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
      result = zwaveEvent(cmd)
      log.debug("'$description' parsed to $result")
    } else {
      log.debug("Couldn't zwave.parse '$description'")
    }
  }
  result
}


def handleMeterReport(cmd){
  def meterTypes = ["Unknown", "Electric", "Gas", "Water"]
  def electricNames = ["energy", "energy", "power", "count", "voltage", "current", "powerFactor", "unknown"]
  def electricUnits = ["kWh", "kVAh", "W", "pulses", "V", "A", "Power Factor", ""]

  logging "handleMeterReport(deltaTime:${cmd.deltaTime} secs, meterType:${meterTypes[cmd.meterType]}, meterValue:${cmd.scaledMeterValue}, previousMeterValue:${cmd.scaledPreviousMeterValue}, scale:${electricNames[cmd.scale]}(${cmd.scale}), unit: ${electricUnits[cmd.scale]}, precision:${cmd.precision}, rateType:${cmd.rateType})", 2

  //NOTE ScaledPreviousMeterValue does not always contain a value
  def previousValue = cmd.scaledPreviousMeterValue ?: 0

  def map = [ name: electricNames[cmd.scale], unit: electricUnits[cmd.scale], displayed: true]
  switch(cmd.scale) {
      case 0: //kWh
          previousValue = device.currentValue("energy") ?: cmd.scaledPreviousMeterValue ?: 0
          map.value = cmd.scaledMeterValue
          break;
      case 1: //kVAh
          map.value = cmd.scaledMeterValue
          break;
      case 2: //Watts
          previousValue = device.currentValue("power") ?: cmd.scaledPreviousMeterValue ?: 0
          map.value = Math.round(cmd.scaledMeterValue)
          break;
      case 3: //pulses
          map.value = Math.round(cmd.scaledMeterValue)
          break;
      case 4: //Volts
          previousValue = device.currentValue("voltage") ?: cmd.scaledPreviousMeterValue ?: 0
          map.value = cmd.scaledMeterValue
          break;
      case 5: //Amps
          previousValue = device.currentValue("current") ?: cmd.scaledPreviousMeterValue ?: 0
          map.value = cmd.scaledMeterValue
          break;
      case 6: //Power Factor
      case 7: //Unknown
          map.value = cmd.scaledMeterValue
          break;
      default:
          break;
  }

  //Check if the value has changed my more than 5%, if so mark as a stateChange
  //map.isStateChange = ((cmd.scaledMeterValue - previousValue).abs() > (cmd.scaledMeterValue * 0.05))

  createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
  logging "zwaveEvent(MeterReport) - cmd: ${cmd.inspect()}", 2
  handleMeterReport(cmd)
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logging "zwaveEvent(BasicReport) - cmd: ${cmd.inspect()}", 2
  def value = (cmd.value ? "on" : "off")
  def evt = createEvent(name: "switch", value: value, type: "physical", descriptionText: "$device.displayName was turned $value")
  if (evt.isStateChange) {
    [evt, response(["delay 3000", zwave.meterV3.meterGet(scale: 2).format()])]
  } else {
    evt
  }
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  logging "zwaveEvent(SwitchBinaryReport) - cmd: ${cmd.inspect()}", 2
  def value = (cmd.value ? "on" : "off")
  createEvent(name: "switch", value: value, type: "digital", descriptionText: "$device.displayName was turned $value")
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
  logging "zwaveEvent(Hail) - cmd: ${cmd.inspect()}", 2
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  logging "zwaveEvent(VersionReport) - cmd: ${cmd.inspect()}", 2
  state.deviceInfo['applicationVersion'] = "${cmd.applicationVersion}"
  state.deviceInfo['applicationSubVersion'] = "${cmd.applicationSubVersion}"
  state.deviceInfo['zWaveLibraryType'] = "${cmd.zWaveLibraryType}"
  state.deviceInfo['zWaveProtocolVersion'] = "${cmd.zWaveProtocolVersion}"
  state.deviceInfo['zWaveProtocolSubVersion'] = "${cmd.zWaveProtocolSubVersion}"

}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
  logging "zwaveEvent(DeviceSpecificReport) - cmd: ${cmd.inspect()}", 2
  state.deviceInfo['deviceIdData'] = "${cmd.deviceIdData}"
  state.deviceInfo['deviceIdDataFormat'] = "${cmd.deviceIdDataFormat}"
  state.deviceInfo['deviceIdDataLengthIndicator'] = "l${cmd.deviceIdDataLengthIndicator}"
  state.deviceInfo['deviceIdType'] = "${cmd.deviceIdType}"
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  logging "zwaveEvent(ManufacturerSpecificReport) - cmd: ${cmd.inspect()}", 2

  state.deviceInfo['manufacturerId'] = "${cmd.manufacturerId}"
  state.deviceInfo['manufacturerName'] = "${cmd.manufacturerName}"
  state.deviceInfo['productId'] = "${cmd.productId}"
  state.deviceInfo['productTypeId'] = "${cmd.productTypeId}"

  def result = []

  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)

  result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
}

def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
  logging "zwaveEvent(FirmwareMdReport) - cmd: ${cmd.inspect()}", 2

  state.deviceInfo['firmwareChecksum'] = "${cmd.checksum}"
  state.deviceInfo['firmwareId'] = "${cmd.firmwareId}"
}

def zwaveEvent(hubitat.zwave.commands.powerlevelv1.PowerlevelReport cmd) {
    logging "zwaveEvent(PowerlevelReport) - cmd: ${cmd.inspect()}", 2

    def power = (cmd.powerLevel > 0) ? "minus${cmd.powerLevel}dBm" : "NormalPower"
    // logger("Powerlevel Report: Power: ${power}, Timeout: ${cmd.timeout}","info")
    // state.zwtGeneralMd.powerlevel = power
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logging "zwaveEvent(SensorMultilevelReport) - cmd: ${cmd.inspect()}", 2
  //The temperature sensor only measures the internal temperature of product (Circuit board)
  if (cmd.sensorType == hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport.SENSOR_TYPE_TEMPERATURE_VERSION_1) {
    createEvent(name: "temperature", value: cmd.scaledSensorValue, unit: cmd.scale ? "F" : "C", displayed: false )
  }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  log.warn "zwaveEvent(Command) - Unhandled - cmd: ${cmd.inspect()}"
  [:]
}

def on() {
  logging "on()", 2

  secureSequence([
    zwave.basicV1.basicSet(value: 0xFF),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.meterV3.meterGet(scale: 2)
  ], 3000)
}

def off() {
  logging "off()", 2

  secureSequence([
    zwave.basicV1.basicSet(value: 0x00),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.meterV3.meterGet(scale: 2)
  ], 3000)
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
  logging "ping()", 2
  refresh()
}

def poll() {
  logging "poll()", 2

  secureSequence([
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0),
    zwave.basicV1.basicGet(),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.meterV3.meterGet(scale: 0), // energy kWh
    zwave.meterV3.meterGet(scale: 1), // energy kVAh
    zwave.meterV3.meterGet(scale: 2), // watts
    zwave.meterV3.meterGet(scale: 4), // volts
    zwave.meterV3.meterGet(scale: 5)  // amps
  ])
}

def refresh() {
  logging "refresh() - state: ${state.inspect()}", 2


  secureSequence([
    zwave.versionV1.versionGet(),
    zwave.firmwareUpdateMdV2.firmwareMdGet(),
    zwave.manufacturerSpecificV2.manufacturerSpecificGet(),
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: 0),
    zwave.basicV1.basicGet(),
    zwave.switchBinaryV1.switchBinaryGet(),
    zwave.meterV3.meterGet(scale: 0), // energy kWh
    zwave.meterV3.meterGet(scale: 1), // energy kVAh
    zwave.meterV3.meterGet(scale: 2), // watts
    zwave.meterV3.meterGet(scale: 4), // volts
    zwave.meterV3.meterGet(scale: 5)  // amps
  ])
}

def configure() {
  logging "configure()", 2
  def result = []

  Integer reportGroup;
  reportGroup = ("$param101_voltage" == "true" ? 1 : 0)
  reportGroup += ("$param101_current" == "true" ? 2 : 0)
  reportGroup += ("$param101_watts" == "true" ? 4 : 0)
  reportGroup += ("$param101_currentUsage" == "true" ? 8 : 0)

  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 20, size: 1, scaledConfigurationValue: new BigInteger("$param20"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: new BigInteger("$param111"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: reportGroup)))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 80, size: 1, scaledConfigurationValue: new BigInteger("$param80"))))

  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 90, size: 1, scaledConfigurationValue: 1)))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 91, size: 2, scaledConfigurationValue: new BigInteger("$param91"))))
  result << response(secure(zwave.configurationV1.configurationSet(parameterNumber: 92, size: 1, scaledConfigurationValue: new BigInteger("$param92"))))

  result
}

def reset() {
  logging "reset()", 2

  sendEvent(name: "power", value: "0", displayed: true, unit: "W")
  sendEvent(name: "energy", value: "0", displayed: true, unit: "kWh")
  sendEvent(name: "amperage", value: "0", displayed: true, unit: "A")
  sendEvent(name: "voltage", value: "0", displayed: true, unit: "V")

  secureSequence([
    zwave.meterV3.meterReset(),
    zwave.meterV3.meterGet(scale: 0),
    zwave.meterV3.meterGet(scale: 1),
    zwave.meterV3.meterGet(scale: 2),
    zwave.meterV3.meterGet(scale: 4),
    zwave.meterV3.meterGet(scale: 5)
  ])
}

def clearState() {
  logging "ClearStates() - Clearing device states", 2

  state.clear()

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  } else {
    state.deviceInfo.clear()
  }
}



/*
 * Security encapsulation support:
 */
def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
  if (encapsulatedCommand) {
    log.debug "Parsed SecurityMessageEncapsulation into: ${encapsulatedCommand}"
    zwaveEvent(encapsulatedCommand)
  } else {
    log.warn "Unable to extract Secure command from $cmd"
  }
}

private secure(hubitat.zwave.Command cmd) {
  logging "secure(Command) - cmd: ${cmd.inspect()}", 3

  if (state.sec) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private secureSequence(Collection commands, ...delayBetweenArgs=250) {
  logging "secureSequence(Command) - commands: ${commands.inspect()}", 3
  delayBetween(commands.collect{ secure(it) }, *delayBetweenArgs)
}

private logging(msg,level=1) {
  logLevel = logLevel ?: 0

  if (logLevel > 2 && level == 3) {
    log.trace "${msg}"
  } else if (logLevel > 1 && level == 2) {
    log.debug "${msg}"
  } else if (logLevel > 0 && level == 1) {
    log.info "${msg}"
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
          log."${level}" "${msg}"
        }
    }
}