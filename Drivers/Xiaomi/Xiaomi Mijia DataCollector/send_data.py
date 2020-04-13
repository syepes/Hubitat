#!/usr/bin/env python3
# -*- coding: UTF-8 -*-
import sys
import urllib.request
import base64
import time
import btlewrap
import json
from btlewrap.base import BluetoothBackendException
from mijia.mijia_v1_poller import MijiaV1Poller, MI_HUMIDITY, MI_TEMPERATURE, MI_BATTERY
from mijia.mijia_v2_poller import MijiaV2Poller, MI_HUMIDITY, MI_TEMPERATURE, MI_BATTERY
import requests

# Create virtual sensors in dummy hardware
try:
    import bluepy.btle  # noqa: F401 pylint: disable=unused-import
    BACKEND = btlewrap.BluepyBackend
except ImportError:
    BACKEND = btlewrap.GatttoolBackend


# Get Measurements from v1 and v2 devices
def get_measurements(address, device):
    data = {}
    version = int(device['ver'])

    if 1 == version:
        poller = MijiaV1Poller(address)
    elif 2 == version:
        poller = MijiaV2Poller(address, BACKEND)
    else:
        print("Unsupported Mijia sensor version\n")
        return

    loop = 0
    try:
        temp = poller.parameter_value(MI_TEMPERATURE)
    except:
        temp = "Not set"

    while loop < 2 and temp == "Not set":
        print("Error reading value retry after 3 seconds...\n")
        time.sleep(3)
        if 1 == version:
            poller = MijiaV1Poller(address)
        elif 2 == version:
            poller = MijiaV2Poller(address, BACKEND)
        loop += 1
        try:
            temp = poller.parameter_value(MI_TEMPERATURE)
        except:
            temp = "Not set"

    if temp == "Not set":
        # print("Error reading value\n")
        return data

    data['type'] = device['type']
    data['location'] = device['location']
    data['label'] = device['label']
    data['name'] = "{}".format(poller.name().strip('\u0000'))
    data['mac'] = address
    data['firmware'] = "{}".format(poller.firmware_version().strip('\u0000'))
    data['temperature'] = "{}".format(poller.parameter_value(MI_TEMPERATURE))
    data['humidity'] = "{}".format(poller.parameter_value(MI_HUMIDITY))
    data['battery'] = "{}".format(poller.parameter_value(MI_BATTERY))
    json_data = json.dumps(data)
    return json_data


def send_data(url, payload):
    header = {'content-type': 'application/json'}
    rc = requests.post(url, data=json.dumps(payload), headers=header, verify=False)
    return rc


# List of devices and attributes
sensor_list = {
    '4C:65:A8:D4:CA:5C': {'label': 'Sensor - Interior - Temp - Bedroom - Main', 'location': 'Bedroom', 'type': 'Sensor Temperature Humidity', 'ver': 1},
}

# Set the Hubitat IP
he_url = 'http://HUBITAT-IP:39501'
for k, v in sensor_list.items():
    try:
        data = get_measurements(k, v)
        if len(data.keys()) > 0:
            # print("json: ", data)
            rc = send_data(he_url, data)
            print("Name: %s (%s), status_code: %s" % (k, v['label'], rc.status_code))
    except:
        print("Unexpected error: %s (%s) - %s" % (k, v['label'], sys.exc_info()[0]))
