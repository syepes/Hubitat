
import groovy.json.JsonSlurper
import groovy.transform.Field

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
  definition (name: "Water Meter - API External", namespace: "syepes", author: "Sebastian YEPES", importUrl: "") {
    capability "Actuator"
    capability "Sensor"
    capability "Water Sensor"
    capability "Initialize"
    command "clearState"

    attribute "last_reading", "string"
    attribute "index_type", "string"
    attribute "index_count", "number"
    attribute "index_day", "number"
    attribute "status", "string"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    }
  }
}

def initialize() {
  logger("debug", "initialize()")
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
    msg?.body?.each { k, v ->
      switch (k) {
        case 'timestamp':
          Map map = [ name: "last_reading", value: v, displayed: true]
          sendEvent(map)
        break
        case 'index_type':
          Map map = [ name: "index_type", value: v, displayed: true]
          sendEvent(map)
        break
        case 'index_count':
          Map map = [ name: "index_count", value: v, displayed: true]
          sendEvent(map)
        break
        case 'index_day':
          Map map = [ name: "index_day", value: v, displayed: true]
          sendEvent(map)
        break
        default:
          logger("warn", "parse() - type: ${k} - Unhandled")
        break
      }
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
        def slurper = new JsonSlurper()
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
