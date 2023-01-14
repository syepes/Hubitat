
import groovy.json.JsonSlurper
import groovy.transform.Field

@Field String VERSION = "2.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Water Meter - API External", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Generic/Water%20Meter%20-%20API%20External.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "LiquidFlowRate"
    capability "Initialize"
    command "clearState"

    attribute "day_euro", "number"
    attribute "cumulative_euro", "number"
    attribute "day_cubic_meter", "number"
    attribute "cumulative_cubic_meter", "number"
    attribute "day_liter", "number"
    attribute "cumulative_liter", "number"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    }
  }
}

def initialize() {
  logger("debug", "initialize()")

  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION]
  }

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
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

def parse(String description) {
  logger("debug", "parse() - description: ${description?.inspect()}")
  def msg = parseDescriptionAsMap(description)
  logger("debug", "parse() - msg: ${msg?.inspect()}")

  if (msg?.body) {
    timestamp = msg?.body?.timestamp
    unit = msg?.body?.unit
    unit_name = msg?.body?.unit_name
    type = msg?.body?.index_type
    day = msg?.body?.index_day
    cumulative = msg?.body?.index_count

    sendEvent([name: "day_${unit_name}", value: day, unit: unit, type: type, descriptionText: timestamp, displayed: true])
    sendEvent([name: "cumulative_${unit_name}", value: cumulative, unit: unit, type: type, descriptionText: timestamp, displayed: true])

    // Calculate the LiquidFlowRate
    if (unit_name == "liter") {
      rate = day?.toInteger()/1440
      sendEvent([name: "rate", value: rate, unit: "LPM", type: "calculated", descriptionText: timestamp, displayed: true])
    }

    state.deviceInfo.lastevent = (new Date().getTime()/1000) as long
  }
}


private parseDescriptionAsMap(description) {
  logger("trace", "parseDescriptionAsMap() - description: ${description.inspect()}")
  try {
    def descMap = description.split(",").inject([:]) { map, param ->
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
      if(body.startsWith("\"{") || body.startsWith("{") || body.startsWith("\"[") || body.startsWith("[")) {
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
