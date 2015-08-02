package org.utzjohnliii.webapi;

// junit impl for package

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.net.URL;
import java.net.HttpURLConnection;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;


/**
 * Unit test for ServerTrack App
 */
public class AppServerTrackTest 
    extends TestCase
{
    // the Display and Submit Endpoints
    
    public String sUrlStatsDy = "http://localhost:8080/servertrack/statsdisplay";
    public String sUrlStatsSt = "http://localhost:8080/servertrack/statssubmit";

    
    // sample submit data
    
    public String sXMLStat    = "<dataservertrack>" +
                                     "<servername>megadroid</servername>" +
                                     "<loadcpu>1000</loadcpu>" +
                                     "<loadram>2222</loadram>" +
                                "</dataservertrack>";

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppServerTrackTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppServerTrackTest.class );
    }

    
    // This exists to make maven happy in the event that i have to comment the 2 real tests out.
    
    public void testAppServerTrackDummy()
    {
	assertTrue(true);
    }


    // TODO: extend these tests to create a significant amount of data to send to the server.
    // include both correct and bogus stuff
    
    // connect to the Submit endpoint and send over the sample data.
    
    public void testAppServerTrackSubmit()
    {
	String sLinIn;
	HttpURLConnection huc = null;
	
	try
	{	    
	    huc = (HttpURLConnection) new URL(sUrlStatsSt).openConnection();

	    huc.setDoOutput(true);
	    huc.setRequestProperty("Content-Type", "application/xml; charset=utf-8");

	    huc.connect();
	    
	    BufferedWriter bw =
		new BufferedWriter(new OutputStreamWriter(huc.getOutputStream()));
	    
	    bw.write(sXMLStat);
	    
	    bw.close();

	    System.out.println("testAppServerTrackSubmit RC: " +  huc.getResponseCode());
	    assertTrue("Response Code",
	    	       huc.getResponseCode()==HttpURLConnection.HTTP_CREATED);
	}
	catch(Exception e)
	{
	    e.printStackTrace();
	    assertTrue("testAppServerTrackSubmit ERROR", false);
	}

	if(null!=huc)
	    huc.disconnect();
    }


    // test that we get the data back that we want. This is not idempotent. it requires the submit
    // endpoint to be called at least once during the lifetime of the server.
    //
    // TODO: make this test selfcontained so that it loads at least one data item so that there is
    // a data item to find.
    //
    // split out the unknown server name testing to another test.
    //
    // hit it hard.

    public void testAppServerTrackXDisplay()
    {
	String sLinIn;
	HttpURLConnection huc = null;
	
	try
	{	    
	    huc = (HttpURLConnection) new URL(sUrlStatsDy+"?servername=megadroid").openConnection();
	    huc.connect();
	    
	    System.out.println("testAppServerTrackDisplay RC: " +  huc.getResponseCode());
	    
	    assertTrue("Response Code",
		       huc.getResponseCode()==HttpURLConnection.HTTP_OK);

	    BufferedReader br =
		new BufferedReader(new InputStreamReader(huc.getInputStream()));

	    while(null != (sLinIn = br.readLine()))
		System.out.println("testAppServerTrackDisplay MSG: " + sLinIn + "\n");

	    br.close();
	}
	catch(Exception e)
	{
	    e.printStackTrace();
	    assertTrue("testAppServerTrackDisplay ERROR", false);
	}
	
	if(null!=huc)
	    huc.disconnect();
    }
}
