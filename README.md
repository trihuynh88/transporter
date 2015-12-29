# transporter

The goal of the project was to build a device that captures 3D video, and transfers in realtime through mobile phone's internet connection to other mobile devices in realtime. 
The work flow is as follows: The linux-based device captures 3D video -> this device connects and transfer data to user's mobile phone -> user's phone receives the video, perform realtime video stabilization to reduce camera shaking effect, and transfer 3D video to server through its internet connection (Wifi or 4G) -> friends at end points connect to server, retrieve the 3D video and can view by Virtual Reality Kits, e.g. Google Cardboard, Oculus, in realtime.
The code is organized as follows:
- "board": Contains the code for the linux-based device.
- "Transporter_Server_Android_Processing_NoAudio_Queue": Code for user's mobile phone, which connects to the linux-based device for receiving captured video, perform realtime processing and then send to the intermediate server.
- "Transporter_Central_Server_NoProcessing_NoAudio_Queue": Code for the intermediate server, which receives data from user's mobile phone.
- Transporter_Client_NoAudio_Queue: The ending client phone, which connects to the intermediate server and retrieves 3D video to view in Google Cardboard.
