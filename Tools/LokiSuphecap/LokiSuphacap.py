#!/usr/bin/python3

# https://www.suphammer.net/suphacap
# pip3 install pyserial pytz tendo
import serial
import time
import requests
import json
import datetime
import pytz
from tendo import singleton
me = singleton.SingleInstance()

ser = serial.Serial(
    port='/dev/ttyUSB0',
    baudrate=115200
)
ser.reset_input_buffer()

time.sleep(1)
ser.write('c'.encode())
time.sleep(0.5)
ser.write('q1 i1 o1\n'.encode())
time.sleep(1)

url = 'http://192.168.1.1:3100/api/prom/push'
headers = {'Content-type': 'application/json'}


def gethex(decimal):
    return hex(int(decimal))[2:].upper()


while 1:
    line = ser.readline().strip().decode()
    rec = line.split(',')
    # print('Length: ' + str(len(rec)))

    if len(rec) == 6 and rec[0] == 'r':
        # print('Raw: ' + str(rec))
        try:
            curr_datetime = datetime.datetime.now(pytz.timezone('Europe/Paris'))
            curr_datetime = curr_datetime.isoformat('T')
            cc = rec[5]
            if cc == "":
                cc = 'NONE'

            payload = {
                'streams': [
                    {
                        'labels': '{event_type=\"suphacap\",level=\"trace\",home_id=\"' + rec[2] + '\",node_src=\"' + gethex(rec[3]) + '\",node_dst=\"' + gethex(rec[4]) + '\",content=\"' + cc + '\"}',
                        'entries': [
                            {
                                'ts': curr_datetime,
                                'line': '[' + cc + '] [NONE] ' + rec[1]
                            }
                        ]
                    }
                ]
            }
            payload = json.dumps(payload)
            # print(payload)

            answer = requests.post(url, data=payload, headers=headers)
            if answer.status_code >= 300:
                print(answer)

        except Exception as e:
            print('Error: ' + str(e))

    if len(rec) == 10 and rec[0] == 'r':
        # print('Raw: ' + str(rec))
        try:
            curr_datetime = datetime.datetime.now(pytz.timezone('Europe/Paris'))
            curr_datetime = curr_datetime.isoformat('T')
            cc = rec[5]
            if cc == "":
                cc = 'NONE'

            payload = {
                'streams': [
                    {
                        'labels': '{event_type=\"suphacap\",level=\"trace\",home_id=\"' + rec[2] + '\",node_src=\"' + gethex(rec[3]) + '\",node_dst=\"' + gethex(rec[4]) + '\",content=\"' + cc + '\",crc_error=\"' + rec[7] + '\"}',
                        'entries': [
                            {
                                'ts': curr_datetime,
                                'line': '[' + cc + '] [' + rec[9] + '] ' + rec[1]
                            }
                        ]
                    }
                ]
            }
            payload = json.dumps(payload)
            # print(payload)

            answer = requests.post(url, data=payload, headers=headers)
            if answer.status_code >= 300:
                print(answer)

        except Exception as e:
            print('Error: ' + str(e))

    else:
        continue
