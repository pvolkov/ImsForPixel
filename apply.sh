#!/bin/bash
# Colors for beautiful console output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color
BOLD='\033[1m'

echo -e "${BOLD}=== IMS for Sven: Applying Patch via Wireless/USB ADB ===${NC}"

# Check if adb is installed
if ! command -v adb &> /dev/null
then
    echo -e "${RED}Error: adb is not installed or not in PATH.${NC}"
    exit 1
fi

# Check connected devices
DEVICES=$(adb devices | grep -v "List of devices" | grep "device")
if [ -z "$DEVICES" ]; then
    echo -e "${RED}Error: No authorized ADB devices connected.${NC}"
    echo "Please check if your phone is connected and authorized under Wireless/USB Debugging."
    exit 1
fi

echo "Connected device found."
echo "Syncing ADB keys to phone for local wireless debugging..."

# Push keys to /data/local/tmp and move to app files dir
adb push ~/.android/adbkey /data/local/tmp/adbkey_temp &>/dev/null
adb push ~/.android/adbkey.pub /data/local/tmp/adbkey_temp.pub &>/dev/null
adb shell run-as com.clean.pixelvolte cp /data/local/tmp/adbkey_temp /data/data/com.clean.pixelvolte/files/adbkey &>/dev/null
adb shell run-as com.clean.pixelvolte cp /data/local/tmp/adbkey_temp.pub /data/data/com.clean.pixelvolte/files/adbkey.pub &>/dev/null
adb shell run-as com.clean.pixelvolte chmod 600 /data/data/com.clean.pixelvolte/files/adbkey /data/data/com.clean.pixelvolte/files/adbkey.pub &>/dev/null
adb shell rm /data/local/tmp/adbkey_temp /data/local/tmp/adbkey_temp.pub &>/dev/null

echo "Executing Patch..."
# Execute instrumentation to apply patch
adb shell am instrument -w com.clean.pixelvolte/com.clean.pixelvolte.BrokerInstrumentation

echo "Waiting 8 seconds for network reconnection..."
sleep 8

# Run query only to update the status file
adb shell am instrument -e query_only true -w com.clean.pixelvolte/com.clean.pixelvolte.BrokerInstrumentation

# Read registration status from app cache files
STATUS0=$(adb shell run-as com.clean.pixelvolte cat /data/data/com.clean.pixelvolte/files/ims_status_0.txt 2>/dev/null | tr -d '\r')
STATUS1=$(adb shell run-as com.clean.pixelvolte cat /data/data/com.clean.pixelvolte/files/ims_status_1.txt 2>/dev/null | tr -d '\r')

echo -e "\n=== Verification ==="
if [ -n "$STATUS0" ]; then
    if [ "$STATUS0" == "true" ]; then
        echo -e "${GREEN}✔ SIM 1 (Slot 1): IMS is REGISTERED.${NC}"
    else
        echo -e "${RED}✘ SIM 1 (Slot 1): IMS is UNREGISTERED.${NC}"
    fi
fi

if [ -n "$STATUS1" ]; then
    if [ "$STATUS1" == "true" ]; then
        echo -e "${GREEN}✔ SIM 2 (Slot 2): IMS is REGISTERED.${NC}"
    else
        echo -e "${RED}✘ SIM 2 (Slot 2): IMS is UNREGISTERED.${NC}"
    fi
fi

if [ "$STATUS0" != "true" ] && { [ -z "$STATUS1" ] || [ "$STATUS1" != "true" ]; }; then
    echo -e "${NC}Tips: Try toggling Airplane Mode on/off, or rebooting your phone to force renegotiation.${NC}"
fi

echo -e "${BOLD}=== Done ===${NC}"
