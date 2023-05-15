# Obtain valid credentials

Using `script/get-credentials.sh`. You don't need the credentials themselves as the default configuration of ota-lith runs without auth, but the script to obtain credentials.zip will create the necessary resources on the server.

# Create a device

Provision a device with a specific hardware id

Get uuid and hardware id from device. `scripts/gen-device.sh` uses `ota-ce-device`.

# Create device group

http://deviceregistry.ota.ce/api/v1/device_groups

    {
      "name": "esc-dev-group",
      "groupType": "static",
      "expression": null
    }

Save id to use in campaign below

# Add device to group

`POST http://deviceregistry.ota.ce/api/v1/device_groups/:group-id/devices/:device-id`

# Create software version

Use hardwareid from device, some random jpeg will work or any binary file.

    curl -X PUT 'http://reposerver.ota.ce/api/v1/user_repo/targets/mypkg_0.0.2?name=mypkg&version=0.0.2&hardwareIds=ota-ce-device' -F file=@path/to/binary.bin`

Use name/version below.

# Create Multi target Update

Get these value from targets.json

POST http://director.ota.ce/api/v1/multi_target_updates

    {
      "targets": {
        "ota-ce-device": {
          "to": {
            "target": "mypkg_0.0.2",
            "checksum": {
              "method": "sha256",
              "hash": "b02a1d4166c0c89f87ecd90ddff0ba059b219e2b4473cb9f075aeded349a4e4b"
            },
            "targetLength": 35445
          },
          "targetFormat": "BINARY",
          "generateDiff": false
        }
      }
    }

Save the response uuid

# Launch the Multi Target Update

POST http://director.ota.ce/api/v1/admin/devices/:device_id/multi_target_update/:multi-target-uuid
