## Use built-in Debug Adapter launch Configuration

There are two sort of use cases in which it is very interesting:

* A Debug Adapter Server is available. It allows remote connection or can be launched locally. No specific Eclipse integration has been provided.
* During the development of a Debug Adapter Server. 


### Use Case 1: use an existing Debug Adapter Server

Steps to use launch configuration:

* In `Run configuration`, select `Debug Adapter launcher` then click `New` button.
* Provide information to either start the Debug Adapter Server or connect to it.
* Fill the launch parameters as Json. It is the ones which are sent during [`launch request`](https://microsoft.github.io/debug-adapter-protocol/specification#Requests_Launch).

### Use Case 2: develop a Debug Adapter Server

When starting the development of a Debug Adapter Server, using the built-in Debug Adapter launch configuration avoids the requirement to provide a specific Eclipse plugin. It will be possible to provide it later.

The `monitor Debug Adapter launcher process` option is very useful, it allows to track the communication between the client and the server.

By default, the `launch` option for the Application Under Debug is used. In case, the Application Under Debug is already running and the goal is to `attach`, you need to add `"request": "attach"` in the `Launch parameters as Json`.
