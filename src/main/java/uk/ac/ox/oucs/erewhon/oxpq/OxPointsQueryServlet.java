/**
 * Copyright 2009 University of Oxford
 *
 * Written by Arno Mittelbach for the Erewhon Project
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *  - Neither the name of the University of Oxford nor the names of its 
 *    contributors may be used to endorse or promote products derived from this 
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */
package uk.ac.ox.oucs.erewhon.oxpq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Collection;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.oucs.gaboto.GabotoConfiguration;
import org.oucs.gaboto.GabotoLibrary;
import org.oucs.gaboto.entities.GabotoEntity;
import org.oucs.gaboto.entities.pool.GabotoEntityPool;
import org.oucs.gaboto.entities.pool.GabotoEntityPoolConfiguration;
import org.oucs.gaboto.transformation.EntityPoolTransformer;
import org.oucs.gaboto.transformation.RDFPoolTransformerFactory;
import org.oucs.gaboto.transformation.json.GeoJSONPoolTransfomer;
import org.oucs.gaboto.transformation.json.JSONPoolTransformer;
import org.oucs.gaboto.transformation.kml.KMLPoolTransformer;
import org.oucs.gaboto.exceptions.ResourceDoesNotExistException;
import org.oucs.gaboto.exceptions.UnsupportedFormatException;
import org.oucs.gaboto.model.Gaboto;
import org.oucs.gaboto.model.GabotoFactory;
import org.oucs.gaboto.model.GabotoSnapshot;
import org.oucs.gaboto.model.query.GabotoQuery;
import org.oucs.gaboto.timedim.TimeInstant;
import org.oucs.gaboto.util.GabotoOntologyLookup;
import org.oucs.gaboto.vocabulary.OxPointsVocab;


import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * A servlet to interrogate the OxPoints data.
 * 
 * Try invoking with
 * http://127.0.0.1:8080/oxp/OxPointsQueryServlet/type/College.kml
 * 
 * Slashes are like dots in a method invocation.
 * 
 * Format is a special case.
 * 
 * Parameters are used for qualifiers which do not effect the number of entities
 * in the pool.
 * 
 */

public class OxPointsQueryServlet extends HttpServlet {

  private static final long serialVersionUID = 4155078999145248554L;
  private static Logger logger = Logger.getLogger(OxPointsQueryServlet.class.getName());

  private static Gaboto gaboto;

  private static GabotoSnapshot snapshot;

  private static GabotoConfiguration config;

  private static Calendar startTime;

  public void init() {
    logger.debug("init");
    config = GabotoConfiguration.fromConfigFile();

    GabotoLibrary.init(config);
    gaboto = GabotoFactory.getEmptyInMemoryGaboto();

    gaboto.read(getResourceOrDie("graphs.rdf"), getResourceOrDie("cdg.rdf"));
    gaboto.recreateTimeDimensionIndex();

    startTime = Calendar.getInstance();

    snapshot = gaboto.getSnapshot(TimeInstant.from(startTime));

  }

  /**
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      outputPool(request, response);
    } catch (AnticipatedException e) {
      error(request, response, e);
    }
  }

  void error(HttpServletRequest request, HttpServletResponse response, AnticipatedException exception) {
    response.setContentType("text/html");
    PrintWriter out = null;
    try {
      out = response.getWriter();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    out.println("<html><head><title>OxPoints Anticipated Error</title></head>");
    out.println("<body>");
    out.println("<h2>OxPoints Anticipated Error</h2>");
    out.println("<h3>" + exception.getMessage() + "</h3>");
    out.println("<p>An anticipated error has occured in the application");
    out.println("that runs this website, please contact <a href='mailto:");
    out.println(getSysAdminEmail() + "'>" + getSysAdminName() + "</a>");
    out.println(", with the information given below.</p>");
    out.println("<h3> Invoked by " + request.getRequestURL().toString() + "</h3>");
    out.println("<h3> query " + request.getQueryString() + "</h3>");
    out.println("<h4><font color='red'><pre>");
    exception.printStackTrace(out);
    out.println("</pre></font></h4>");
    out.println("</body></html>");

  }

  private String getSysAdminEmail() {
    return "Tim.Pizey@oucs.ox.ac.uk";

  }

  private String getSysAdminName() {
    return "Tim Pizey";
  }

  void outputPool(HttpServletRequest request, HttpServletResponse response) {
    Query query = Query.fromRequest(request);
    switch (query.getReturnType()) {
    case META_TIMESTAMP:
      try {
        response.getWriter().write(new Long(startTime.getTimeInMillis()).toString());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    case META_TYPES:
      output(GabotoOntologyLookup.getRegisteredEntityClassesAsClassNames(), query, response);
      return;
    case ALL:
      output(GabotoEntityPool.createFrom(snapshot), query, response);
      return;
    case INDIVIDUAL:
      GabotoEntityPool pool = new GabotoEntityPool(gaboto, snapshot);
      try {
        pool.addEntity(snapshot.loadEntity(query.getUri()));
      } catch (ResourceDoesNotExistException e) {
        throw new AnticipatedException("Resource not found with uri " + query.getUri(), e);
      }
      output(pool, query, response);
      return;
    case TYPE_COLLECTION:
      output(loadPoolWithEntitiesOfType(query.getType()), query, response);
      return;
    case COLLECTION:
      output(loadPoolWithEntitiesOfProperty(query.getRequestedProperty(), query.getRequestedPropertyValue()), query,
              response);
      return;
    case NOT_FILTERED_TYPE_COLLECTION:
      GabotoEntityPool p = loadPoolWithEntitiesOfType(query.getType());
      GabotoEntityPool p2 = loadPoolWithEntitiesOfType(query.getType());
      for (GabotoEntity e : p.getEntities()) 
        if (e.getPropertyValue(query.getNotProperty(), false, false) != null)
          p2.removeEntity(e);
        
      output(p2, query,
              response);
      return;
    default:
      throw new RuntimeException("Fell through case with value " + query.getReturnType());
    }

  }

  private GabotoEntityPool loadPoolWithEntitiesOfProperty(Property prop, String value) {
    if (prop == null)
      throw new NullPointerException();
    GabotoEntityPool pool = null;
    if (value == null) {
      pool = snapshot.loadEntitiesWithProperty(prop);
    } else {
      String values[] = value.split("[|]");

      for (String v : values) {
        if (requiresResource(prop)) {
          Resource r = getResource(v);
          System.err.println("Found r: " + r + " for prop " + prop);
          pool = becomeOrAdd(pool, snapshot.loadEntitiesWithProperty(prop, r));
        } else {
          pool = becomeOrAdd(pool, snapshot.loadEntitiesWithProperty(prop, v));
        }
      }
    }
    return pool;
  }

  private GabotoEntityPool becomeOrAdd(GabotoEntityPool pool, GabotoEntityPool poolToAdd) {
    if (poolToAdd == null)
      throw new NullPointerException();
    if (pool == null) {
      System.err.println("Returning new");
      return poolToAdd;
    } else {
      System.err.println("Returning added");
      for (GabotoEntity e : poolToAdd.getEntities())
        pool.addEntity(e);
      return pool;
    }
  }

  private Resource getResource(String v) {
    String vUri = config.getNSData() + v;
    return snapshot.getResource(vUri);
  }

  private boolean requiresResource(Property property) {
    if (property.getLocalName().endsWith("subsetOf")) {
      return true;
    } else if (property.getLocalName().endsWith("physicallyContainedWithin")) {
      return true;
    } else if (property.getLocalName().endsWith("hasPrimaryPlace")) {
      return true;
    } else if (property.getLocalName().endsWith("occupies")) {
      return true;
    } else if (property.getLocalName().endsWith("associatedWith")) {
      return true;
    }
    return false;
  }

  private GabotoEntityPool loadPoolWithEntitiesOfType(String type) {
    System.err.println("Type:" + type);
    String types[] = type.split("[|]");

    GabotoEntityPoolConfiguration conf = new GabotoEntityPoolConfiguration(snapshot);
    for (String t : types) {
      if (!GabotoOntologyLookup.isValidName(t))
        throw new IllegalArgumentException("Found no URI matching type " + t);
      String typeURI = OxPointsVocab.NS + t;
      conf.addAcceptedType(typeURI);
    }

    return GabotoEntityPool.createFrom(conf);
  }

  private void output(Collection<String> them, Query query, HttpServletResponse response) {
    try {
      if (query.getFormat().equals("txt")) {
        boolean doneOne = false;
        for (String member : them) {
          if (doneOne)
            response.getWriter().write("|");
          response.getWriter().write(member);
          doneOne = true;
        }
        response.getWriter().write("\n");
      } else if (query.getFormat().equals("csv")) {
        boolean doneOne = false;
        for (String member : them) {
          if (doneOne)
            response.getWriter().write(",");
          response.getWriter().write(member);
          doneOne = true;
        }
        response.getWriter().write("\n");
      } else if (query.getFormat().equals("js")) {
        boolean doneOne = false;
        response.getWriter().write("var oxpointsTypes = [");
        for (String member : them) {
          if (doneOne)
            response.getWriter().write(",");
          response.getWriter().write("'");
          response.getWriter().write(member);
          response.getWriter().write("'");
          doneOne = true;
        }
        response.getWriter().write("];\n");
      } else if (query.getFormat().equals("xml")) {
        response.getWriter().write("<c>");
        for (String member : them) {
          response.getWriter().write("<i>");
          response.getWriter().write(member);
          response.getWriter().write("</i>");
        }
        response.getWriter().write("</c>");
        response.getWriter().write("\n");
      } else
        throw new AnticipatedException("Unexpected format " + query.getFormat());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void output(GabotoEntityPool pool, Query query, HttpServletResponse response) {
    System.err.println("Pool has " + pool.getSize() + " elements");
    System.err.println("output.Format:" + query.getFormat() + ":");

    String output = "";
    if (query.getFormat().equals("kml")) {
      output = createKml(pool, query);
      response.setContentType("application/vnd.google-earth.kml+xml");
    } else if (query.getFormat().equals("json") || query.getFormat().equals("js")) {

      System.err.println("output.Format:" + query.getFormat());
      JSONPoolTransformer transformer = new JSONPoolTransformer();
      transformer.setNesting(query.getJsonDepth());

      output = transformer.transform(pool);
      if (query.getFormat().equals("js")) {
        output = query.getJsCallback() + "(" + output + ");";
      }
      response.setContentType("text/javascript");
    } else if (query.getFormat().equals("gjson")) {
      GeoJSONPoolTransfomer transformer = new GeoJSONPoolTransfomer();
      if (query.getArc() != null) {
        transformer.addEntityFolderType(query.getFolderClassURI(), query.getArcProperty().getURI());
      }
      if (query.getOrderBy() != null) {
        transformer.setOrderBy(query.getOrderByProperty().getURI());
      }
      transformer.setDisplayParentName(query.getDisplayParentName());

      output += transformer.transform(pool);
      if (query.getJsCallback() != null)
        output = query.getJsCallback() + "(" + output + ");";
      response.setContentType("text/javascript");
    } else if (query.getFormat().equals("xml")) {

      System.err.println("Pool has " + pool.getSize() + " elements");
      EntityPoolTransformer transformer;
      try {
        transformer = RDFPoolTransformerFactory.getRDFPoolTransformer(GabotoQuery.FORMAT_RDF_XML_ABBREV);
        output = transformer.transform(pool);
      } catch (UnsupportedFormatException e) {
        throw new IllegalArgumentException(e);
      }
      response.setContentType("text/xml");
    } else {
      output = runGPSBabel(createKml(pool, query), "kml", query.getFormat());
      if (output.equals(""))
        throw new RuntimeException("No output created by GPSBabel");
    }
    // System.err.println("output:" + output + ":");
    try {
      response.getWriter().write(output);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String createKml(GabotoEntityPool pool, Query query) {
    String output;
    KMLPoolTransformer transformer = new KMLPoolTransformer();
    if (query.getArc() != null) {
      transformer.addEntityFolderType(query.getFolderClassURI(), query.getArcProperty().getURI());
    }
    if (query.getOrderBy() != null) {
      transformer.setOrderBy(query.getOrderByProperty().getURI());
    }
    transformer.setDisplayParentName(query.getDisplayParentName());

    output = transformer.transform(pool);
    return output;
  }

  /**
   * @param input
   *          A String, normally kml
   * @param formatIn
   *          format name, other than kml
   * @param formatOut
   *          what you want out
   * @return the reformatted String
   */
  public static String runGPSBabel(String input, String formatIn, String formatOut) {
    // '/usr/bin/gpsbabel -i kml -o ' . $format . ' -f ' . $In . ' -F ' . $Out;
    if (formatIn == null)
      formatIn = "kml";
    if (formatOut == null)
      throw new IllegalArgumentException("Missing output format for GPSBabel");

    OutputStream stdin = null;
    InputStream stderr = null;
    InputStream stdout = null;

    String output = "";
    String command = "/usr/bin/gpsbabel -i " + formatIn + " -o " + formatOut + " -f - -F -";
    System.err.println("GPSBabel command:" + command);
    Process process;
    try {
      process = Runtime.getRuntime().exec(command, null, null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      stdin = process.getOutputStream();

      stdout = process.getInputStream();
      stderr = process.getErrorStream();
      try {
        stdin.write(input.getBytes());
        stdin.flush();
        stdin.close();
      } catch (IOException e) {
        // clean up if any output in stderr
        BufferedReader errBufferedReader = new BufferedReader(new InputStreamReader(stderr));
        String stderrString = "";
        String stderrLine = null;
        try {
          while ((stderrLine = errBufferedReader.readLine()) != null) {
            System.err.println("[Stderr Ex] " + stderrLine);
            stderrString += stderrLine;
          }
          errBufferedReader.close();
        } catch (IOException e2) {
          throw new RuntimeException("Command " + command + " stderr reader failed:" + e2);
        }
        throw new RuntimeException("Command " + command + " gave error:\n" + stderrString);
      }

      BufferedReader brCleanUp = new BufferedReader(new InputStreamReader(stdout));
      String line;
      try {
        while ((line = brCleanUp.readLine()) != null) {
          System.out.println("[Stdout] " + line);
          output += line;
        }
        brCleanUp.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      // clean up if any output in stderr
      brCleanUp = new BufferedReader(new InputStreamReader(stderr));
      String stderrString = "";
      try {
        while ((line = brCleanUp.readLine()) != null) {
          System.err.println("[Stderr] " + line);
          stderrString += line;
        }
        brCleanUp.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (!stderrString.equals(""))
        throw new RuntimeException("Command " + command + " gave error:\n" + stderrString);
    } finally {
      process.destroy();
    }
    return output;
  }

  private InputStream getResourceOrDie(String fileName) {
    String resourceName = fileName;
    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
    if (is == null)
      throw new NullPointerException("File " + resourceName + " cannot be loaded");
    return is;
  }

}
