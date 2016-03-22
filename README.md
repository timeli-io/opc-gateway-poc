Summary of work done on the OPC client gateway
----------------------------------------------

The prototype client extends the SampleConsoleClient (provided along with the 
evaluation copy of the Prosys Java SDK) to incorporate the capabilities needed 
by a Timeli gateway.

It is still a prototype because its various capabilities are driven off a menu. 
It however also has a non-interactive mode that reads tags off a file and 
continuously polls those tags at intervals specified in the file.

Once the specific requirements of the Timeli gateway are known,  this prototype 
can be easily converted to behave to those requirements, using the 
capabilities it already has.

The prototype client today has the following capabilities:

1. Can browse a DA server for nodes that hold history information.
2. Can read a variable value of a node.
3. Can read the historical variable values of a node for a given period.
3. Can read the historical variable values in a continuous mode.
4. Can read a set of variables in batch mode (simultaneously)
5. Can read the node ids of variables, and the frequency at which to read 
them,  from a file, and continuously poll those ids at the specified 
frequencies.
6. Anonymous, signed and encrypted sessions with the sample server

The input file has the following format: (interval is specified in seconds)

    [ 
        { 
            "interval":60, 
            "tags":["ns=5;s=iAMAZON-E174707A.Simulation00012", 
                    "ns=5;s=iAMAZON-E174707A.Simulation00002"] 
        }, 
        { 
            "interval":30, 
            "tags":["ns=5;s=iAMAZON-E174707A.Simulation00001", 
                    "ns=5;s=iAMAZON-E174707A.Simulation00004", 
                    "ns=5;s=iAMAZON-E174707A.Simulation00003"] 
        }
    ]

*This prototype has been tested with:*

1. The SampleConsoleServer provided with the Java SDK.
2. The Prosys demo server at: 
opc.tcp://uademo.prosysopc.com:53530/OPCUA/SimulationServer
3. The Matrikon OPC Wrapper to the Proficy Historian HDA. However, in this 
environment, I am seeing a problem with a certain server behavior. 
Continuation points (that should be returned by the server when the number of 
values available to be read, exceeds the batch size per read that is set by 
the client) are not being set correctly  Consequently, the client does not 
read more than once although there may be more data points to be read. 
For example, if 10 points are requested in one read, but 25 points are 
available, only 10 points are read because continuation points are not set. 
(The other two servers behave correctly and according to spec)

*Tested features that did not work - needs investigation and support from server side*

1. Username/password based access to the UA gateway. 
The server validates by delegating to the OS. However, providing a valid user and password i
did not allow the client to connect. Anonymous access works. The server tested against was an 
instance of the Matrikon UA Wrapper running on Amazon.

2. PKI certificate based signature and encryption of communications between client and server. 
It is not clear where to deposit the client public key on the server (directory) and hence could 
not get it to work. Awaiting help from Matrikon support.
