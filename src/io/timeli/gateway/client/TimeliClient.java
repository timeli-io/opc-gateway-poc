package io.timeli.gateway.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.net.URISyntaxException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Properties;
import java.util.Iterator;
import java.io.InputStream;
import java.lang.Exception;
import static java.util.concurrent.TimeUnit.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.log4j.PropertyConfigurator;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import org.opcfoundation.ua.builtintypes.DataValue;
import org.opcfoundation.ua.builtintypes.DateTime;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.StatusCode;
import org.opcfoundation.ua.builtintypes.UnsignedInteger;
import org.opcfoundation.ua.builtintypes.Variant;
import org.opcfoundation.ua.builtintypes.ExtensionObject;

import org.opcfoundation.ua.core.AccessLevel;
import org.opcfoundation.ua.core.BrowseDirection;
import org.opcfoundation.ua.core.EventFilter;
import org.opcfoundation.ua.core.HistoryEventFieldList;
import org.opcfoundation.ua.core.Identifiers;
import org.opcfoundation.ua.core.NodeClass;
import org.opcfoundation.ua.core.ReferenceDescription;
import org.opcfoundation.ua.core.TimestampsToReturn;
import org.opcfoundation.ua.utils.CertificateUtils;
import org.opcfoundation.ua.utils.MultiDimensionArrayUtils;
import org.opcfoundation.ua.core.ReadRawModifiedDetails;
import org.opcfoundation.ua.core.HistoryReadValueId;
import org.opcfoundation.ua.core.HistoryReadResult;
import org.opcfoundation.ua.core.StatusCodes;
import org.opcfoundation.ua.core.HistoryData;


import com.prosysopc.ua.EventNotifierClass;
import com.prosysopc.ua.ServiceException;
import com.prosysopc.ua.StatusException;
import com.prosysopc.ua.client.AddressSpaceException;
import com.prosysopc.ua.client.ServerListException;
import com.prosysopc.ua.nodes.UaNode;
import com.prosysopc.ua.nodes.UaObject;
import com.prosysopc.ua.nodes.UaType;
import com.prosysopc.ua.nodes.UaVariable;

import com.prosysopc.ua.samples.client.*;

public class TimeliClient extends SampleConsoleClient {
    
    static {
        APP_NAME = "TimeliClient";
    }
    
    private static Logger logger = LoggerFactory.getLogger(TimeliClient.class);
    protected static final String HISTORY_READ_PERIOD = "history_read_period";
    protected static final Properties timeliProps = new Properties();
    protected JSONArray readTagList = null;
    
    
    protected Set<String> browseForVariableNodesWithHistory(NodeId nodeId, List<String> historyVars)  {
        
        //printCurrentNode(nodeId);
        List<ReferenceDescription> references;
        client.getAddressSpace().setMaxReferencesPerNode(1000);
        client.getAddressSpace().setBrowseDirection(BrowseDirection.Forward);
        client.getAddressSpace().setReferenceTypeId(Identifiers.HierarchicalReferences);
        try {
            references = client.getAddressSpace().browse(nodeId);
            for (int i = 0; i < references.size(); i++) {
                ReferenceDescription r = references.get(i);
                //printf(">> %s\n", referenceToString(r));
                if (r.getNodeClass() == NodeClass.Object)  {
                    NodeId targetNode = client.getAddressSpace().getNamespaceTable().toNodeId(r.getNodeId());
                    if (targetNode != nodeId) {
                        browseForVariableNodesWithHistory(targetNode, historyVars);
                    }
                }
                else if (r.getNodeClass() == NodeClass.Variable) {
                    UaNode variable = client.getAddressSpace().getNode(client.getAddressSpace().getNamespaceTable().toNodeId(r.getNodeId()));
                    if (variable instanceof UaVariable) {
                        //printf("Variable found-> %s\n", referenceToString(r));
                        if (((UaVariable)variable).getAccessLevel().contains(AccessLevel.HistoryRead)) { 
                            String tag = client.getAddressSpace().getNamespaceTable().toNodeId(r.getNodeId()).toString();
                            //printf("Node: %s\n", tag);
                            historyVars.add(tag);
                        }
                    }
                }
            }
        } catch (Exception e) {
            printException(e);
        }
        return new HashSet<String>(historyVars);
    }

    public static void main(String[] args) throws Exception {
        // Load Log4j configurations from external file
        PropertyConfigurator.configureAndWatch(TimeliClient.class.getResource("/log.properties").getFile(), 5000);
        
        //Load Timeli properties file
        
        try (final InputStream stream =
                   TimeliClient.class.getResourceAsStream("/timeli.properties")) {
            timeliProps.load(stream);
        }
        catch (Exception e) {
            throw new TimeliException("Could not open timeli properties file.");
        }
        
        TimeliClient timeliClient = new TimeliClient();
        
        try {
            if (!timeliClient.parseCmdLineArgs(args)) {
                usage();
                return;
            }
        } catch (IllegalArgumentException e) {
            // If message is not defined, the command line was empty and the
            // user did not enter any URL when prompted. Otherwise, the
            // exception is used to notify of an invalid argument.
            if (e.getMessage() != null)
                println("Invalid cmd line argument: " + e.getMessage());
            usage();
            return;
        }

        timeliClient.initialize(args);
        timeliClient.connect();
        // Show the menu, which is the main loop of the client application
        if (timeliClient.readTagList == null) {
            timeliClient.mainMenu();
        }
        else {
            timeliClient.batchReadTagListContinuous();
        }
        timeliClient.disconnect();
        println(APP_NAME + ": Closed");
    }
    
    protected void mainMenu() throws ServerListException, URISyntaxException {
        // Identifiers contains a list of all standard node IDs
        if (nodeId == null)
            nodeId = Identifiers.RootFolder;
        
        NodeId n;
        Set<String> historyVars = null;
        do {
            printMenu(nodeId);
            try {
                switch (readAction()) {
                case ACTION_RETURN:
                    disconnect();
                    return;
                case 1:
                    historyVars = browseForVariableNodesWithHistory(nodeId, new ArrayList<String>());
                    Iterator<String> it = historyVars.iterator();
                    while (it.hasNext()) {
                        printf("Node: %s\n", it.next());
                    }
                    break;
                case 2:
                    n = readNodeTag();
                    if (n == null)
                        continue;
                    readValue(n);
                    break;
                case 3:
                    n = readNodeTag();
                    if (n == null)
                        continue;
                    readHistory(n);
                    break;
                case 4:
                    n = readNodeTag();
                    if (n == null)
                        continue;
                    readHistoryContinuous(n);
                    break;
                case 5:
                    //Set<String> h = new HashSet<String>();
                    //h.add("ns=2;s=MyLevel");
                    int secs = Integer.parseInt(timeliProps.getProperty(HISTORY_READ_PERIOD));
                    readHistoryBatch(secs, historyVars.toArray(new String[historyVars.size()]));
                    break;
                case 6:
                    batchReadTagListContinuous();
                    break;
                default:
                    continue;
                }
            } catch (Exception e) {
                printException(e);
            }
    
        } while (true);
    }
    
    protected void batchReadTagListContinuous() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(readTagList.size());
        Iterator<JSONObject> varIterator = readTagList.iterator();
        while (varIterator.hasNext()) {
            JSONObject item = varIterator.next();
            int interval = ((Long)item.get("interval")).intValue();
            JSONArray tags = (JSONArray)item.get("tags");   
            List<String> tagList = new ArrayList<String>();
            Iterator<String> tagIterator = tags.iterator();
            while (tagIterator.hasNext()) {
                tagList.add(tagIterator.next());
            }
            String[] tagArray = new String[tags.size()];
            tagArray = tagList.toArray(tagArray);
            scheduleBatchHistoryRead(interval, tagArray, scheduler);
        }
        do {
        } while (!scheduler.isShutdown());
    }
    
    private void scheduleBatchHistoryRead(int interval, String[] tags, ScheduledExecutorService scheduler) {
        final int run_interval = interval;
        final String[] read_tags = tags;
        
        Runnable run = new Runnable() {
            public void run() { 
                try {
                    readHistoryBatch(run_interval, read_tags);
                }
                catch (Exception e) {
                    // do nothing
                }
            }
        };

        final ScheduledFuture<?> handle =
                scheduler.scheduleAtFixedRate(run, 0, run_interval, SECONDS);
    }
    
    protected void readHistoryBatch(int secs, String[] historyVars) {
        DateTime endTime = DateTime.currentTime();
        //int secs = Integer.parseInt(timeliProps.getProperty(HISTORY_READ_PERIOD));
        DateTime startTime = new DateTime((endTime.getMilliSeconds() - (secs * 1000)) * 10000);
        TimestampsToReturn tstamps = TimestampsToReturn.Source;
        ReadRawModifiedDetails details = new ReadRawModifiedDetails(false, startTime, endTime, new UnsignedInteger(100), true);
        List<HistoryReadValueId> ids = new ArrayList<HistoryReadValueId>();
        for (int i=0; i<historyVars.length; i++) {
            String s = historyVars[i];
            NodeId n = NodeId.parseNodeId(s);
            HistoryReadValueId id = new HistoryReadValueId();
            id.setNodeId(n);
            ids.add(id);
        }
        HistoryReadValueId[] v = new HistoryReadValueId[ids.size()];
        v = ids.toArray(v);
        DataValue[][] allValues  = new DataValue[v.length][];
        List[] dataList = new List[v.length];
        for (int i=0;i<v.length; i++) {
            dataList[i] = null;
        }
        boolean runAgain;
        try {
            do {
                runAgain = false;
                HistoryReadResult[] results = client.historyRead(details, tstamps, false, v);
                for (int i=0; i<v.length; i++) {
                    byte[] continuationPoint = null;
                    HistoryData data = null;
                    if (results[i].getStatusCode().equals(StatusCode.GOOD)) {
                        if (!results[i].getStatusCode().getValue().equals(StatusCodes.Good_NoData)) {
                            ExtensionObject historyDataObject = results[i].getHistoryData();
                            if (historyDataObject != null) {
                                data = historyDataObject.decode(client.getEncoderContext());
                                continuationPoint = results[i].getContinuationPoint();
                                if (continuationPoint != null) {
                                    runAgain = true;
                                }
                                if (dataList[i] == null)
                                    dataList[i] = new ArrayList<DataValue>();
                                dataList[i].addAll(Arrays.asList(data.getDataValues()));
                            }
                            v[i].setContinuationPoint(continuationPoint);
                            if ((data.getDataValues().length == 0) && (continuationPoint != null)) {
                                printf("historyReadAll: Received empty HistoryData with continuationPoint\n");
                                break;
                            } 
                        }
                    }
                    else {
                        v[i].setContinuationPoint(null);
                        printf("Got bad status code from history read."+results[i].getStatusCode());
                    }
                }
            } while (runAgain);
            
            for (int i=0; i<v.length; i++) {
                List<DataValue> dv = (List<DataValue>)dataList[i];
                allValues[i] = dv == null ? new DataValue[0] : dv.toArray(new DataValue[dv.size()]);
            }
        }
        catch (Exception e) {
            printf("%s\n", e.getMessage());
            e.printStackTrace();
        }
        
        for (int j=0; j<allValues.length; j++) {
            DataValue[] values = allValues[j];
            printf("---------------"+historyVars[j]+"------------------------\n");
            for (int i = 0; i < values.length; i++)
                println("Value " + (i + 1) + " = " + values[i].getValue().getValue() + " | "
                    + values[i].getSourceTimestamp());
        }
    }
    
    protected void printMenu(NodeId nodeId) {
        println("");
        println("");
        println("");
        if (client.isConnected()) {
            println("*** Connected to: " + client.getUri());
            println("");
            if (nodeId != null)
                printCurrentNode(nodeId);
        } else
            println("*** NOT connected to: " + client.getUri());

        System.out.println("-------------------------------------------------------");
        println("- Enter x to close client");
        System.out.println("-------------------------------------------------------");
        System.out.println("- Enter 1 to browse server for signal data            -");
        System.out.println("- Enter 2 to read value                               -");
        System.out.println("- Enter 3 to read history                             -");
        System.out.println("- Enter 4 to read history (continuous)                -");
        System.out.println("- Enter 5 to read history in batch mode               -");
        System.out.println("- Enter 6 to read history (continuous) in batch mode  -");
        System.out.println("-------------------------------------------------------");
    }
    
    protected void readHistory(NodeId nodeId) throws ServiceException, StatusException, AddressSpaceException {
        UaNode node = client.getAddressSpace().getNode(nodeId);

        if (node instanceof UaVariable) {

            // Validate that history is readable for the node

            UaVariable variable = (UaVariable) node;
            if (!variable.getAccessLevel().contains(AccessLevel.HistoryRead)) {
                println("The variable does not have history");
                return;
            }
            println("0 - raw data");
            println("1 - at times");
            // The server does not support processed read, yet.
            // println("2 - processed (average)");
            int action = readAction();

            println("Reading history of variable " + variable.getBrowseName());
            try {
                DateTime endTime = DateTime.currentTime();
                int secs = Integer.parseInt(timeliProps.getProperty(HISTORY_READ_PERIOD));
                DateTime startTime = new DateTime((endTime.getMilliSeconds() - (secs * 1000)) * 10000);
                DataValue[] values = null;

                switch (action) {
                case 0:
                    println("between " + startTime + " and " + endTime);

                    values = client.historyReadRaw(nodeId, startTime, endTime, new UnsignedInteger(1000), true, null,
                            TimestampsToReturn.Source);
                    break;
                case 1:
                    println("at " + startTime + " and " + endTime);

                    DateTime[] reqTimes = new DateTime[] { startTime, endTime };
                    values = client.historyReadAtTimes(nodeId, reqTimes, null, TimestampsToReturn.Source);
                    break;
                case 2:
                    println("at " + startTime + " and " + endTime);

                    values = client.historyReadProcessed(nodeId, startTime, endTime, 60 * 1000.0, // your
                                                                                                    // 5
                                                                                                    // min
                                                                                                    // sample
                                                                                                    // rate
                            // in milliseconds
                            Identifiers.AggregateFunction_Average, // the
                            // aggregate
                            // type you
                            // wish to
                            // use
                            null, null, TimestampsToReturn.Source);
                    break;
                }

                if (values != null) {
                    println("Count = " + values.length);
                    for (int i = 0; i < values.length; i++)
                        println("Value " + (i + 1) + " = " + values[i].getValue().getValue() + " | "
                                + values[i].getSourceTimestamp());
                }
            } catch (Exception e) {
                printException(e);
            }
        } else if (node instanceof UaObject) {

            // Validate that history is readable for the node

            UaObject object = (UaObject) node;
            if (!object.getEventNotifier().contains(EventNotifierClass.HistoryRead)) {
                println("The object does not have history");
                return;
            }

            println("Reading event history of node " + node);
            try {
                DateTime endTime = DateTime.currentTime();
                DateTime startTime = new DateTime((endTime.getMilliSeconds() - (3600 * 1000)) * 10000);
                HistoryEventFieldList[] events = null;

                println("between " + startTime + " and " + endTime);

                // Use the same filter that is used by MonitoredEventItems
                initEventFieldNames();
                EventFilter eventFilter = createEventFilter(eventFieldNames);
                events = client.historyReadEvents(nodeId, startTime, endTime, UnsignedInteger.ZERO, eventFilter,
                        TimestampsToReturn.Source);

                if (events != null) {
                    println("Count = " + events.length);
                    for (int i = 0; i < events.length; i++)
                        println("Event " + (i + 1) + " = "
                                + eventFieldsToString(eventFieldNames, events[i].getEventFields()));
                }
            } catch (Exception e) {
                printException(e);
            }
        }

        else
            println("History is only available for object and variable nodes. The current node is a "
                    + node.getNodeClass() + ".");
    }
    
    protected void readHistoryContinuous(NodeId nodeId)
              throws ServiceException, StatusException, AddressSpaceException {
        
       final NodeId n = nodeId;
       final ScheduledExecutorService scheduler =
               Executors.newScheduledThreadPool(1);
       
       Runnable runHistory = new Runnable() {
               public void run() { 
                   try {
                       readHistory(n);
                   }
                   catch (Exception e) {
                       // do nothing
                   }
               }
       };
       final ScheduledFuture<?> runHandle =
               scheduler.scheduleAtFixedRate(runHistory, 10, 20, SECONDS);
       
       scheduler.schedule(new Runnable() {
           public void run() {
               printf(">> Shutting down continuous read..\n");
               runHandle.cancel(true); 
               scheduler.shutdown();
           }
       }, 1 * 60, SECONDS);
       
       do {
       } while (!scheduler.isShutdown());
       
    }
    
    protected NodeId readNodeTag() {
        print("Enter Node Id --> ");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String s = null;
        try {
            s = stdin.readLine();
        }
        catch (Exception e) {
            //ignore
        }
        if ((s == null) || (s.length() == 0)) {
           return null;
        }
        NodeId n = NodeId.parseNodeId(s);
        return n;
    }
    
    protected void readValue(NodeId nodeId) throws Exception {
        UaNode variable = client.getAddressSpace().getNode(nodeId);
        if (variable instanceof UaVariable) {
            DataValue value = ((UaVariable)variable).getValue();
            StringBuilder sb = new StringBuilder();
            sb.append("Node: ");
            sb.append(nodeId);
            sb.append(".");
            sb.append(" | Status: ");
            sb.append(value.getStatusCode());
            if (value.getStatusCode().isNotBad()) {
                sb.append(" | Value: ");
                if (value.isNull())
                    sb.append("NULL");
                else {
                    try {
                        NodeId dataTypeId = ((UaVariable)variable).getDataTypeId();
                        UaType dataType = ((UaVariable)variable).getDataType();
                        if (dataType == null)
                            dataType = client.getAddressSpace().getType(dataTypeId);

                        Variant variant = value.getValue();
                        variant.getCompositeClass();
                        if (dataType != null)
                            sb.append("(" + dataType.getDisplayName().getText() + ")");
                        else
                            sb.append("(DataTypeId: " + dataTypeId + ")");
                    } catch (ServiceException e) {
                    } catch (AddressSpaceException e) {
                    }
                    final Object v = value.getValue().getValue();
                    if (value.getValue().isArray())
                        sb.append(MultiDimensionArrayUtils.toString(v));
                    else
                        sb.append(v);
                }
            }
            sb.append(dateTimeToString(" | ServerTimestamp: ", value.getServerTimestamp(), value.getServerPicoseconds()));
            sb.append(dateTimeToString(" | SourceTimestamp: ", value.getSourceTimestamp(), value.getSourcePicoseconds()));
            printf(sb.toString()+"\n");
        }
    }
    
    public TimeliClient() {
    }
    
    /**
     * @param format
     * @param args
     */
    protected static void printf(String format, Object... args) {
        System.out.printf(format, args);
        //String s = String.format(format, args);
        //logger.debug(s);
    }

    /**
     * @param string
     */
    protected static void println(String string) {
        System.out.println(string);
        //logger.info(string);
    }
    
    protected boolean parseCmdLineArgs(String[] args) throws IllegalArgumentException  {
        List<String> oargs = new ArrayList<String>();
        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-i")) {
                println("Reading from file.");
                readTagsFromFile(args[++i]);
            }
            else {
                oargs.add(args[i]);
            }
        }
        args = new String[oargs.size()];
        args = oargs.toArray(args);
        return super.parseCmdLineArgs(args);
    }
    
    @SuppressWarnings("unchecked")
    protected void readTagsFromFile(String filename) throws IllegalArgumentException {
        JSONParser parser = new JSONParser();
        try {
            readTagList = (JSONArray)parser.parse(new FileReader(filename));
            Iterator<JSONObject> varIterator = readTagList.iterator();
            while (varIterator.hasNext()) {
                JSONObject item = varIterator.next();
                System.out.println("interval: "+item.get("interval"));
                JSONArray tagList = (JSONArray)item.get("tags");
                System.out.println("tags:");
                Iterator<String> tagIterator = tagList.iterator();
                while (tagIterator.hasNext()) {
                    System.out.println(tagIterator.next());
                }
            }
        }
        catch(Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    

}
