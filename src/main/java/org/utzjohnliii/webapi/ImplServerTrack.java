package org.utzjohnliii.webapi;

// restful implmentations of ServerTrack submit and display endpoints.
//
// john.of.utz@gmail.com 150731 1.0-SNAPSHOT-utzjohnliii


// things required for creating a jetty and jersey rest engine.

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;


// things required for parsing our incoming and outgoing xml server data

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


// things required for concurrent scheduling of data insertion tasks

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


// things required for implementing averaging

import java.util.ArrayDeque;
import java.lang.Runnable;


// things required for collecting and operating on server load data

import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;


/*
ASSIGNMENT:

1. Record load for a given server
This should take a:
 • server name (string)
 • CPU load (double)
 • RAM load (double)

Apply the values to an in-memory model used to provide the data in endpoint #2.

2. Display loads for a given server
This should return data (if it has any) for the given server:
 • List of the average load values for the last 60 minutes broken down by minute
 • List of the average load values for the last 24 hours broken down by hour

Assume these endpoints will be under a continuous load being called for
thousands of individual servers every minute.
*/


// fundamental container object for the load data, used in numerous places

class StatLoad
{
    public double loadcpu;
    public double loadram;

    
    StatLoad(){}


    StatLoad(double loadcpu, double loadram)
    {
	this.loadcpu = loadcpu;
	this.loadcpu = loadcpu;
    }
}


// object that maps the data from the server with the name of the server, the
// caller submits it as XML and the server uses JAXP to transmogrify it to
// an instance of this class; thus the XML decorations.

@XmlRootElement(name="dataservertrack")
class StatServerTrack
{
    public String   servername;
    public StatLoad dl = new StatLoad();

    public StatServerTrack(){}
    
    public StatServerTrack(String servername, double loadcpu, double loadram)
    {
	this.servername = servername;
	this.dl.loadcpu = loadcpu;
	this.dl.loadram = loadram;
    }

    @XmlElement(name="servername")
    public void setName(String name)
    {
	this.servername = name;
    }
    
    public String getName()
    {
	return servername;
    }

    
    @XmlElement(name="loadcpu")
    public void setCpuLoad(double load)
    {
	this.dl.loadcpu = load;
    }

    public double getCpuLoad()
    {
	return dl.loadcpu;
    }


    @XmlElement(name="loadram")
    public void setRamLoad(double load)
    {
	this.dl.loadram = load;
    }

    public double getRamLoad()
    {
	return dl.loadram;
    }

}


// run a runnable repeatedly in a timely fashion and kill it as needed.
//
// NB: once it's killed, a new instance would have to be created to
// 'restart' the runnable

class RunSchedServerTrack
{
    private final ScheduledExecutorService ses;
    private final ScheduledFuture          sf;

    public RunSchedServerTrack(Runnable r, int iInterval, int iCntThrd)
    {
	this.ses       = Executors.newScheduledThreadPool(iCntThrd);
	this.sf        = this.ses.scheduleAtFixedRate(r, 0, iInterval, TimeUnit.SECONDS);
    }

    public void shutdownRequest()
    {
	try
	{
	    this.ses.shutdownNow();
	
	    if(this.ses.awaitTermination(100,  TimeUnit.MICROSECONDS))
		return;
	}
	// dropping thru since the bailout mechanism would be identical in the catch...
	catch(InterruptedException e) {}
	
	System.out.println("shutdownRequest: failed to shutdown, calling System.exit(0)");
	System.exit(0); // should we return a particular error code here?
    }
}


// per server data repository of moving averages, will get inserted into
// concurrent parent data structure, thus it's sychronization free.

class AvgServerTrack implements Runnable
{
    private final static int iSIZROLL = 100;
    private final static int iSIZHOUR =  60;
    private final static int iSIZDAY  =  24;

    private final ArrayDeque<StatLoad> adqAvgRoll =
	new ArrayDeque<StatLoad>(iSIZROLL);
    
    private final ArrayDeque<StatLoad> adqAvgHour =
	new ArrayDeque<StatLoad>(iSIZHOUR);
    
    private final ArrayDeque<StatLoad> adqAvgDay  =
	new ArrayDeque<StatLoad>(iSIZDAY);

    private static int        iCntRun = 0;
    
    private        double dAccLoadCPU = 0;
    private        double dAccLoadRAM = 0;

    RunSchedServerTrack rsst = new RunSchedServerTrack((Runnable)this, 1, 1);

    // insert latest submitted stats to update average
    
    void Submit(StatLoad slNew)
    {
	dAccLoadCPU = dAccLoadCPU + slNew.loadcpu;
	dAccLoadRAM = dAccLoadRAM + slNew.loadram;

	adqAvgRoll.add(slNew);

	if(iSIZROLL <= adqAvgRoll.size())
	    return;

	StatLoad slFst = adqAvgRoll.remove();
   
	dAccLoadCPU = dAccLoadCPU - slFst.loadcpu;
	dAccLoadRAM = dAccLoadRAM - slFst.loadram;
    }

    
    // 'display' the accumulated Rolling List(s)
    // TODO: return adqAvgDay list too
    
    ArrayDeque<StatLoad> Display()
    {
	return adqAvgHour;
    }

    
    // Exists as an incremental debugging step

    StatLoad DisplayOne()
    {
	return new StatLoad(dAccLoadCPU, dAccLoadRAM);
    }
    
    
    // collect the current average values for cpu and ram load and return as
    // a StatLoad

    StatLoad getAvg()
    {
	if(adqAvgRoll.isEmpty())
	    return new StatLoad(0.0,0.0);

	double dTmpLoadCPU = dAccLoadCPU/(double)adqAvgRoll.size();
	double dTmpLoadRAM = dAccLoadRAM/(double)adqAvgRoll.size();

	return new StatLoad(dTmpLoadCPU, dTmpLoadRAM);
    }


    // update our running averages 
  
    public void run()
    {	
	StatLoad sl = getAvg();
	
	this.adqAvgRoll.addFirst(sl);

	if(0==iCntRun%iSIZHOUR)
	    this.adqAvgHour.addFirst(sl);

	if(0==iCntRun%iSIZDAY)
	    this.adqAvgDay.addFirst(sl);

	iCntRun++;
    }
}


// ConcurrentHashMap is a good choice when there are high number of updates and
// not so many reads; does not lock the entire collection for synchronization,
// it uses lock striping in the region being written to and usually doesnt lock
// on writes - I am unsure what inspires it to lock on a read, i have yet to
// find a list of reasons for it to choose that path.
//
// The single static instance of this object is what is used to provide thread
// safe access to the reading and writing of the ServerTrack data.
//
//
// NB:
//
// We are choosing to use the default initial size, we will take a
// performance hit at an unknown time if and when the JVM concludes that it
// needs to grow our map. 
//
// Also, the default constructor allocates 16 shards, it can be easily increased
// if it's shown to be needed.
//
//
// TODO: this currently only supports the storage of 1 data sample per machine,
// the goal is to replace the stored StatLoad object with an AvgServerTrack
// object.

class StatMapServerTrack
{
    ConcurrentHashMap chm = new ConcurrentHashMap();


    // based on the ConcurrentHashMap implementers decision to *have*
    // putIfAbsent(), i have to believe that the following 2 lines are the
    // fastest way to solve the problem of adding new things and writing to new
    // or existing things - remember, reads are not locked, so the only time
    // this would lock would be to write the new obj after having discovered
    // that it doesnt exist.

    public void insert(StatServerTrack sst)
    {	
	chm.putIfAbsent(sst.servername, new AvgServerTrack());
	((AvgServerTrack)chm.get(sst.servername)).Submit(sst.dl);
	
	//chm.put(sst.servername, sst.dl);
    }


    public StatLoad get(String servername)
    {
	return ((AvgServerTrack)chm.get(servername)).DisplayOne();

	// return chm.get(servername);
    }


    // handy debugging tool - results in printout of contents to jetty stdout.

    public void view()
    {
	System.out.println("View Start");
	Iterator itr = chm.entrySet().iterator();
	while(itr.hasNext())
	{
	    Map.Entry mePair = (Map.Entry)itr.next();
	    System.out.println("Key: " + mePair.getKey() + " Val: " + mePair.getValue());
	    //itr.remove();
        }
	System.out.println("View End\n");
    }
}


// Endpoint Implementations - Submit and Display

@Path("/servertrack")

public class ImplServerTrack
{
    // NB: this has to be static.
    //
    // Figuring this out was a real hairpuller.
    //
    // jetty spins up a new class instance for each request. so, absent being
    // static, the lifetime of the  variable is the lifetime of the request.
    // Thus the instance that just got loaded by the Submit request goes out of
    // scope and disappears and the instance that the Display request sees is
    // brand new and has no data in it. }:-{
    
    static private StatMapServerTrack smst = new StatMapServerTrack();


    @Context private Response response;
    @Context private UriInfo   uriInfo;
    

    @POST
    @Path("statssubmit")
    @Consumes(MediaType.APPLICATION_XML)

    public Response ServerTrackLoadStatsSubmit(String sXMLStatServerTrack)
    {
	System.out.println("SUBMIT: " +  sXMLStatServerTrack + '\n');

	StringReader          srdts = new StringReader(sXMLStatServerTrack);
	
	try
	{
	    JAXBContext     jaxbCtx =
		JAXBContext.newInstance(StatServerTrack.class);
	    
	    Unmarshaller    jaxbUnm = jaxbCtx.createUnmarshaller();
	    
	    StatServerTrack     dts = (StatServerTrack) jaxbUnm.unmarshal(srdts);
	    
	    this.smst.insert(dts);
	    //this.smst.view();
	}
	catch(JAXBException e)
	{
	    e.printStackTrace();
	    return Response.noContent().build();
	}
	
	return Response.created(uriInfo.getAbsolutePath()).build();
    }

    
    @GET
    @Path("statsdisplay")
    @Produces(MediaType.TEXT_PLAIN) // MediaType.APPLICATION_XML

    public String ServerTrackLoadStatsDisplay(@QueryParam("servername") String sName)
    {
	if(null==sName)
	    return "ERROR: NULL severname Parameter";
	
	System.out.println("DISPLAY SERVER: " + sName);
	this.smst.view();
        StatLoad dl=this.smst.get(sName);
	
	if(null==dl)
	   return "Server not found: " + sName;

	StatServerTrack    dts = new StatServerTrack(sName, dl.loadcpu, dl.loadram);

	StringWriter     swdts = new StringWriter();
	
	try
	{
	    JAXBContext     jaxbCtx = JAXBContext.newInstance(StatServerTrack.class);
	    Marshaller      jaxbMar = jaxbCtx.createMarshaller();
	    
	    jaxbMar.marshal(dts, swdts);
	}
	catch(JAXBException e)
	{
	    e.printStackTrace();
	    return "Server data access error: " + sName;
	}
	return swdts.toString(); 
    }
}
