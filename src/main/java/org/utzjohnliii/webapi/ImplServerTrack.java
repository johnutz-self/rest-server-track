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


// things required for implementing averaging

import java.util.ArrayDeque;


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


class AvgServerTrack
{
    private final static int iSIZROLL = 100;
    private final static int iSIZHOUR =  60;
    private final static int iSIZDAY  =  24;

    private double dAccLoadCPU = 0;
    private double dAccLoadRAM = 0;

    private final ArrayDeque<StatLoad> adqAvgRoll = new ArrayDeque<StatLoad>(iSIZROLL);
    private final ArrayDeque<StatLoad> adqAvgHour = new ArrayDeque<StatLoad>(iSIZHOUR);
    private final ArrayDeque<StatLoad> adqAvgDay  = new ArrayDeque<StatLoad>(iSIZDAY);

    
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

    
    StatLoad getAvg()
    {
	if(adqAvgRoll.isEmpty())
	    return new StatLoad(0.0,0.0);

	double dTmpLoadCPU = dAccLoadCPU/(double)adqAvgRoll.size();
	double dTmpLoadRAM = dAccLoadRAM/(double)adqAvgRoll.size();

	return new StatLoad(dTmpLoadCPU, dTmpLoadRAM);
    }
}


// ConcurrentHashMap is a good choice when there are high number of updates and
// not so many reads; does not lock the entire collection for synchronization.
//
// The single static instance of this object is what is used to provide thread
// safe access to the reading and writing of the ServerTrack data.
//
// NB: We are choosing to use the default initial size, we will take a
// performance hit at an unknown time if and when the JVM concludes that it
// needs to grow our map. 
//
// TODO: this currently only supports the storage of 1 data sample per machine,
// the goal is to replace the stored StatLoad object with an AvgServerTrack
// object.

class StatMapServerTrack
{
    ConcurrentHashMap chm = new ConcurrentHashMap();


    public void insert(StatServerTrack dts)
    {
	chm.put(dts.servername, dts.dl);
    }


    public StatLoad get(String servername)
    {
	return (StatLoad)chm.get(servername);
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



@Path("/servertrack")
public class ImplServerTrack
{
    static private StatMapServerTrack smst = new StatMapServerTrack();
    
    @Context private Response response;
    @Context private UriInfo   uriInfo;
    
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

	StatServerTrack     dts = new StatServerTrack(sName, dl.loadcpu, dl.loadram);

	StringWriter      swdts = new StringWriter();
	
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
	    this.smst.view();
	}
	catch(JAXBException e)
	{
	    e.printStackTrace();
	    return Response.noContent().build();
	}
	
	return Response.created(uriInfo.getAbsolutePath()).build();
    }
}
