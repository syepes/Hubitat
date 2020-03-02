#!/usr/bin/env python

try:
    # base
    import argparse
    import os
    import re
    # import subprocess
    import json
    import pathlib
    # dotenv
    from dotenv import load_dotenv, find_dotenv
    # import urllib.parse
    import time
    import requests
    import http.cookiejar
    import sys
except ModuleNotFoundError as e:
    print("ModuleNotFoundError exception while attempting to import the needed modules: " + str(e))
    exit(99)


def find_files(find='.'):
    data = []
    for root, directories, filenames in os.walk(find):
        for filename in filenames:
            data.append({'name': filename.replace('.groovy', ''), 'path': os.path.join(root, filename)})
    return data


def merge_data(local, remote):
    data = []
    for l in local:
        # print(l)
        for h in remote:
            if h['name'] == l['name']:
                data.append(dict(l, **h))
    return data


def update_driver(s, drv):
    print("> Processing Driver: " + drv['name'] + " (" + str(drv['id']) + ")")
    response = s.get(
        url=he_url + "/driver/ajax/code",
        params={'id': drv['id']}
    )
    # print(response.text)

    if (response.json()['status'] != "success"):
        print("\tFailed downloading")
        return None

    version = response.json()['version']
    print("\tCurrent version: " + str(version))

    print("\tUploading driver")
    with open(drv['path'], 'r') as f:
        sourceContents = f.read()

    response = s.post(
        url=he_url + "/driver/ajax/update",
        data={'id': drv['id'],
              'version': version,
              'source': sourceContents
              }
    )
    # print(response.text)

    if(response.json()['status'] == "success"):
        print("\tSuccessfully uploaded")
    elif (response.json()['status'] == "error"):
        print("\tFailed uploading: " + response.json()['errorMessage'])
        return None
    else:
        print("\tFailed uploading: " + response.json())
        return None


def update_app(s, app):
    print("> Processing App: " + app['name'] + " (" + str(app['id']) + ")")
    response = s.get(
        url=he_url + "/app/ajax/code",
        params={'id': app['id']}
    )
    # print(response.text)

    if (response.json()['status'] != "success"):
        print("\tFailed downloading")
        return None

    version = response.json()['version']
    print("\tCurrent version: " + str(version))

    print("\tUploading app")
    with open(app['path'], 'r') as f:
        sourceContents = f.read()

    response = s.post(
        url=he_url + "/app/ajax/update",
        data={'id': app['id'],
              'version': version,
              'source': sourceContents
              }
    )
    # print(response.text)

    if(response.json()['status'] == "success"):
        print("\tSuccessfully uploaded")
    elif (response.json()['status'] == "error"):
        print("\tFailed uploading: " + response.json()['errorMessage'])
        return None
    else:
        print("\tFailed uploading: " + response.json())
        return None


def he_login(path):
    credentialStorageFolderPath = pathlib.Path(path.parent, ".creds")
    cookieJarFilePath = pathlib.Path(credentialStorageFolderPath, "cookie-jar.txt")
    # print("str(cookieJarFilePath.resolve()): " + str(cookieJarFilePath.resolve()))

    session = requests.Session()
    cookieJarFilePath.resolve().parent.mkdir(parents=True, exist_ok=True)
    session.cookies = http.cookiejar.MozillaCookieJar(filename=str(cookieJarFilePath.resolve()))

    # Ensure that the cookie jar file exists and contains a working cookie to authenticate into the hubita  web interface
    if os.path.isfile(session.cookies.filename):
        session.cookies.load(ignore_discard=True)
    else:
        # Collect username and password from the user
        print("Hubitat username: ")
        hubitatUsername = input()
        print("Hubitat password: ")
        hubitatPassword = input()
        print("Entered " + hubitatUsername + " and " + hubitatPassword)

        response = session.post(
            he_url + "/login",
            data={
                'username': hubitatUsername,
                'password': hubitatPassword,
                'submit': 'Login'
            }
        )
        # print("cookies: " + str(response.cookies.get_dict()))
        session.cookies.save(ignore_discard=True)

    return session


# ------------------------------------ MAIN
load_dotenv(find_dotenv(), verbose=True)
fs_base = pathlib.Path(os.getcwd()).resolve()

he_url = os.getenv("HE_URL")
if he_url == None:
    print("HE_URL is not defined in the .env file")
    exit(99)

print("Connecting to: " + he_url)
session = he_login(fs_base)


# ------------------------------------ DRIVERS
# Find Local Drivers
local_drivers = find_files(pathlib.Path(fs_base, "Drivers").resolve())
# print(local_drivers)

# Load Remote Drivers
resp = session.get(url=he_url + "/driver/list/data")
# print(resp)
he_drivers = resp.json()
# print(he_drivers)

# Filter out system drivers
he_drivers_usr = [x for x in he_drivers if x['type'] == 'usr']
# print(he_drivers_usr)

drvs = merge_data(he_drivers_usr, local_drivers)
print("Found HE Drivers: " + str(len(drvs)))
# print("Found HE Drivers: " + str(drv))

for d in drvs:
    update_driver(session, d)


# ------------------------------------ APP
# Find Local Apps
local_apps = find_files(pathlib.Path(fs_base, "Apps").resolve())
# print(local_apps)

# Load Remote Apps
resp = session.get(url=he_url + "/app/list/data")
# print(resp)
he_apps = resp.json()
# print(he_apps)

# Filter out system apps
he_apps_usr = [x for x in he_apps if x['type'] == 'usr']
# print(he_apps_usr)

apps = merge_data(he_apps_usr, local_apps)
print("Found HE Apps: " + str(len(apps)))
# print("Found HE Apps: " + str(apps))

for a in apps:
    update_app(session, a)


exit(0)
