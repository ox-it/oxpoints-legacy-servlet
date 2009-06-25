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
import java.util.Calendar;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

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
import org.oucs.gaboto.vocabulary.GeoVocab;
import org.oucs.gaboto.vocabulary.OxPointsVocab;
import org.oucs.gaboto.vocabulary.VCard;

import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.DC_11;

/**
 * A servlet to interrogate the OxPoints data.
 * 
 * Try invoking with
 * http://127.0.0.1:8080/oxp/OxPointsQueryServlet?property=oxp:
 * hasOUCSCode&value=oucs&format=kml
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
  private static Logger logger = Logger.getLogger(OxPointsQueryServlet.class
      .getName());
  static Map<String, String> namespacePrefixes = new TreeMap<String, String>();
  {
    namespacePrefixes.put("oxp:",   OxPointsVocab.NS);
    namespacePrefixes.put("dc:",    DC_11.NS);
    namespacePrefixes.put("vCard:", VCard.NS);
    namespacePrefixes.put("geo:",   GeoVocab.NS);
  }

  private static Gaboto gaboto;

  private static GabotoSnapshot snapshot;

  private static GabotoConfiguration config;

  private static Calendar startTime;

  // See http://bob.pythonmac.org/archives/2005/12/05/remote-json-jsonp/
  // No facility to alter this
  String jsCallback = "oxpoints";

  
  String format = "xml";

  String arc = null;
  String orderBy = null;
  String folderType = null;
  String property = null;
  String value = null;
  boolean displayParentName;
  int jsonDepth = 1;

  public void init() {
    logger.debug("init");
    // load Gaboto
    try {
      config = GabotoConfiguration.fromConfigFile();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    GabotoLibrary.init(config);
    gaboto = GabotoFactory.getEmptyInMemoryGaboto();

    gaboto.read(getResourceOrDie("graphs.rdf"), getResourceOrDie("cdg.rdf"));
    gaboto.recreateTimeDimensionIndex();

    startTime = Calendar.getInstance();

    snapshot = gaboto.getSnapshot(TimeInstant.from(startTime));

  }

  private InputStream getResourceOrDie(String fileName) {
    String resourceName = fileName;
    InputStream is = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(resourceName);
    if (is == null)
      throw new NullPointerException("File " + resourceName
          + " cannot be loaded");
    return is;
  }

  private String setFormatFromPathInfo(String pathInfo) {
    int dotPosition = pathInfo.indexOf('.');
    if (dotPosition == -1) {
      return pathInfo;
    } else {
      format = pathInfo.substring(dotPosition + 1);
      return pathInfo.substring(0, dotPosition);
    }
  }

  /**
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    setParameters(request);
    
    GabotoEntityPool pool = null;

    String pathInfo = request.getPathInfo();
    if (pathInfo != null) {
      pathInfo = setFormatFromPathInfo(pathInfo);
      if (pathInfo.startsWith("/timestamp")) {
        try {
          response.getWriter().write(
              new Long(startTime.getTimeInMillis()).toString());
          return;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else if (pathInfo.startsWith("/all")) {
        pool = GabotoEntityPool.createFrom(snapshot);
      } else if (pathInfo.startsWith("/id/")) {

        String id = pathInfo.substring(4);
        String uri = config.getNSData() + id;
        pool = new GabotoEntityPool(gaboto, snapshot);
        try {
          pool.addEntity(snapshot.loadEntity(uri));
        } catch (ResourceDoesNotExistException e) {
          throw new RuntimeException("Resource not found with uri " + uri, e);
        }
      } else if (pathInfo.startsWith("/types")) {
        output(GabotoOntologyLookup.getRegisteredEntityClassesAsClassNames(),
            response, format);
        return;
      } else if (pathInfo.startsWith("/type/")) {  
        String type = pathInfo.substring(6);
        pool = loadPoolWithEntitiesOfType(type);
      } else
        throw new IllegalArgumentException("Unexpected path info : " + pathInfo);
    } else {
      System.err.println("Property " + property);
      if (property != null) {
        pool = loadPoolWithEntitiesOfProperty(property, value);
      } else {
        pool = GabotoEntityPool.createFrom(snapshot);
      }
    }
    System.err.println("Pool has " + pool.getSize() + " elements");
    output(pool, request, response, format);

  }

  /**
   * @param request
   */
  @SuppressWarnings("unchecked")
  private void setParameters(HttpServletRequest request) {
    format = "xml";

    arc = null;
    orderBy = null;
    folderType = null;
    property = null;
    value = null;
    displayParentName = true;
    jsonDepth = 1;

    Enumeration<String> en = request.getParameterNames();
    while (en.hasMoreElements()) {
      String pName = en.nextElement();
      String pValue =  request.getParameter(pName);

      System.err.println("Param:" + pName + "=" + pValue);
      if (pName.equals("arc"))
        arc = pValue;
      else if (pName.equals("folderType"))
        folderType = pValue;
      else if (pName.equals("property"))
        property = pValue;
      else if (pName.equals("value"))
        value = pValue;
      else if (pName.equals("format"))
        format = pValue;
      else if (pName.equals("orderBy"))
        orderBy = pValue;
      else if (pName.equals("parentName")) {
        if (pValue.equalsIgnoreCase("false")) 
          displayParentName = false;
      }
      else if (pName.equals("jsonNesting")) {
        if (pValue != null) {
          try {
            jsonDepth = Integer.parseInt(pValue);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
          }
        }
      } else throw new IllegalArgumentException(
          "Unrecognised parameter " + pName + ":" + request.getParameter(pName));
    }
  }


  private GabotoEntityPool loadPoolWithEntitiesOfProperty(String propertyName, String value) {
    System.err.println("Finding:" + propertyName + "='" + value + "'");
    String propertyURI = getPropertyURIOrDie(propertyName);

    GabotoEntityPool pool = null;

    Property property = snapshot.getProperty(propertyURI);
    
    if (value == null) {
      pool = snapshot.loadEntitiesWithProperty(property);
    } else {
      String values[] = value.split("[|]");

      for (String v : values) {
        System.err.println("property:" + property + " has value " + v);
        if (requiresResource(property)) { 
          Resource r = getResource(v);
          System.err.println("Found r: " + r);
          pool = becomeOrAdd(pool, snapshot.loadEntitiesWithProperty(property, r));
        } else  {
          pool = becomeOrAdd(pool,snapshot.loadEntitiesWithProperty(property, v));
        }
      }
    }
    return pool;
  }

  private GabotoEntityPool  becomeOrAdd(GabotoEntityPool pool,
      GabotoEntityPool poolToAdd) {
    if (poolToAdd == null)
      throw new NullPointerException();
    if (pool == null)
      return poolToAdd;
    else {
      for (GabotoEntity e : poolToAdd.getEntities())
        pool.addEntity(e);
      return pool;
    }
  }

  private Resource getResource(String v) {
    String vUri = config.getNSData() + v;
    return  snapshot.getResource(vUri);
  }

  private boolean requiresResource(Property property) {
    if (property.getLocalName().endsWith("subsetOf")) {
      return true;
    } else if (property.getLocalName().endsWith("physicallyContainedWithin")) {
      return true;
    } else if (property.getLocalName().endsWith("primaryPlace")) {
      return true;
    } else if (property.getLocalName().endsWith("occupies")) {
      return true;
    } else if (property.getLocalName().endsWith("associatedWith")) {
      return true;
    } else
    return false;
  }

  private GabotoEntityPool loadPoolWithEntitiesOfType(String type) {
    System.err.println("Type:" + type);
    String types[] = type.split("[|]");

    GabotoEntityPoolConfiguration config = new GabotoEntityPoolConfiguration(snapshot);
    for (String t : types) {
      if (!GabotoOntologyLookup.isValidName(t))
        throw new IllegalArgumentException("Found no URI matching type " + t);
      String typeURI = OxPointsVocab.NS +  t;
      config.addAcceptedType(typeURI);
    }

    return GabotoEntityPool.createFrom(config);
  }
  
  private void output(Collection<String> them, HttpServletResponse response,
      String format) {
    try {
      if (format.equals("txt")) {
        boolean doneOne = false;
        for (String member : them) {
          if (doneOne)
            response.getWriter().write("|");
          response.getWriter().write(member);
          doneOne = true;
        }
        response.getWriter().write("\n");
      } else if (format.equals("csv")) {
        boolean doneOne = false;
        for (String member : them) {
          if (doneOne)
            response.getWriter().write(",");
          response.getWriter().write(member);
          doneOne = true;
        }
        response.getWriter().write("\n");
      } else if (format.equals("js")) {
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
      } else if (format.equals("xml")) {
        response.getWriter().write("<c>");
        for (String member : them) {
          response.getWriter().write("<i>");
          response.getWriter().write(member);
          response.getWriter().write("</i>");
        }
        response.getWriter().write("</c>");
        response.getWriter().write("\n");
      } else
        throw new IllegalArgumentException("Unexpected format " + format);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void output(GabotoEntityPool pool, HttpServletRequest request,
      HttpServletResponse response, String format) {
    System.err.println("output.Format:" + format + ":");


    String output = "";
    if (format.equals("kml")) {
      response.setContentType("application/vnd.google-earth.kml+xml");
      output = createKml(pool, displayParentName);
    } else if (format.equals("json") || format.equals("js")) {
      System.err.println("output.Format:" + format);
      response.setContentType("text/javascript");
      JSONPoolTransformer transformer = new JSONPoolTransformer();
      transformer.setNesting(jsonDepth);

      output = transformer.transform(pool);
      if (format.equals("js"))
        output = jsCallback + "(" + output + ");";
    } else if (format.equals("gjson")) {
      response.setContentType("text/javascript");
      GeoJSONPoolTransfomer transformer = new GeoJSONPoolTransfomer();
      if (arc != null) {
        transformer.addEntityFolderType(getFolderTypeUri(folderType),
            getPropertyURIOrDie(arc));
      }
      if (orderBy != null) {
        transformer.setOrderBy(getPropertyURIOrDie(orderBy));
      }
      transformer.setDisplayParentName(displayParentName);

      output += transformer.transform(pool);
      
      output = jsCallback + "(" + output + ");";
    } else if (format.equals("xml")) { 
      response.setContentType("text/xml");

      System.err.println("Pool has " + pool.getSize() + " elements");
      EntityPoolTransformer transformer;
      try {
        transformer = RDFPoolTransformerFactory
            .getRDFPoolTransformer(GabotoQuery.FORMAT_RDF_XML_ABBREV);
        output = transformer.transform(pool);
      } catch (UnsupportedFormatException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      output = runGPSBabel(createKml(pool, displayParentName), "kml", format);
      if (output.equals(""))
        throw new RuntimeException("No output created by GPSBabel");
    }
    //System.err.println("output:" + output + ":");
    try {
      response.getWriter().write(output);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String createKml(GabotoEntityPool pool, boolean displayParentName) {
    String output;
    KMLPoolTransformer transformer = new KMLPoolTransformer();
    if (arc != null) {
      System.err.println("folderType=" + folderType + ", arc=" + arc);
      transformer.addEntityFolderType(getFolderTypeUri(folderType),
          getPropertyURIOrDie(arc));
    }
    if (orderBy != null) {
      transformer.setOrderBy(getPropertyURIOrDie(orderBy));
    }
    transformer.setDisplayParentName(displayParentName);

    output = transformer.transform(pool);
    return output;
  }

  private String getPropertyURIOrDie(String propertyKey) {
    String returnString = getPropertyURI(propertyKey);
    if (returnString == null)
      throw new IllegalArgumentException(
          "No namespace found matching property " + propertyKey);
    return returnString;
  }

  private String getPropertyURI(String propertyKey) {
    for (String prefix : namespacePrefixes.keySet()) {
      if (propertyKey.startsWith(prefix))
        return namespacePrefixes.get(prefix)
            + propertyKey.substring(prefix.length());
    }
    return null;
  }

  private String getFolderTypeUri(String propertyKey) {
    if (propertyKey == null)
      return OxPointsVocab.NS + "College";
    return getPropertyURIOrDie(propertyKey);
  }

  /**
   * @param input A String, normally kml
   * @param formatIn format name, other than kml
   * @param formatOut what you want out
   * @return the reformatted String
   */
  public static  String runGPSBabel(String input, String formatIn, String formatOut) {
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

      BufferedReader brCleanUp = new BufferedReader(new InputStreamReader(
          stdout));
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
      try {
        while ((line = brCleanUp.readLine()) != null) {
          System.err.println("[Stderr] " + line);
        }
        brCleanUp.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } finally {
      process.destroy();
    }
    return output;
  }

}
