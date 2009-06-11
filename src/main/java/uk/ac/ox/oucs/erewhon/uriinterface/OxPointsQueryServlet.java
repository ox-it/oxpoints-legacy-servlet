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
package uk.ac.ox.oucs.erewhon.uriinterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collection;
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

import com.hp.hpl.jena.vocabulary.DC;

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
 * in the the pool.
 * 
 */

public class OxPointsQueryServlet extends HttpServlet {

  private static final long serialVersionUID = 4155078999145248554L;
  private static Logger logger = Logger.getLogger(OxPointsQueryServlet.class
      .getName());
  static Map<String, String> namespacePrefixes = new TreeMap<String, String>();
  {
    namespacePrefixes.put("oxp:", OxPointsVocab.NS);
    namespacePrefixes.put("dc:", DC.NS);
    namespacePrefixes.put("vCard:", VCard.NS);
    namespacePrefixes.put("geo:", GeoVocab.NS);
  }

  private static Gaboto gaboto;

  private static GabotoSnapshot snapshot;

  private static GabotoConfiguration config;

  private static Calendar startTime;

  String arc = null;
  String format = null;
  String orderBy = null;
  String folderType = null;

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
      format = "xml";
    } else {
      format = pathInfo.substring(dotPosition + 1);
    }
    if (dotPosition == -1) {
      return pathInfo;
    } else {
      return pathInfo.substring(0, dotPosition);
    }
  }

  /**
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    arc = request.getParameter("arc");
    folderType = request.getParameter("folderType");
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
        output(GabotoOntologyLookup.getRegisteredEntityClassesAsClassNames(),
            response, format);
        return;
      } else
        throw new IllegalArgumentException("Unexpected path info : " + pathInfo);
    } else {
      format = lowercaseRequestParameter(request, "format");
      if (format == null)
        format = "xml";
      String type = request.getParameter("type");
      String property = request.getParameter("property");
      if (type != null) {
        pool = loadPoolWithEntitiesOfType(type);
      } else if (property != null) {
        pool = loadPoolWithEntitiesOfProperty(property, request
            .getParameter("value"));
      } else {
        pool = GabotoEntityPool.createFrom(snapshot);
      }
    }
    output(pool, request, response, format);

  }


  private GabotoEntityPool loadPoolWithEntitiesOfProperty(String property,
      String value) {
    String propertyURI = getPropertyURIOrDie(property);

    GabotoEntityPool pool = null;

    if (value == null) {
      pool = snapshot.loadEntitiesWithProperty(propertyURI);
    } else {

      String values[] = value.split("[|]");

      pool = new GabotoEntityPool(gaboto, snapshot);
      for (String v : values) {
        GabotoEntityPool p = snapshot.loadEntitiesWithProperty(propertyURI, v);
        for (GabotoEntity e : p.getEntities()) {
          pool.addEntity(e);
          System.err.println("Adding:" + v);
        }
      }
    }
    return pool;
  }

  private GabotoEntityPool loadPoolWithEntitiesOfType(String type) {
    System.err.println("Type:" + type);
    String types[] = type.split("[|]");

    GabotoEntityPoolConfiguration config = new GabotoEntityPoolConfiguration(
        snapshot);
    for (String t : types) {
      if (!GabotoOntologyLookup.isValidName(t))
        throw new IllegalArgumentException("Found no URI matching type " + t);
      String typeURI = OxPointsVocab.NS +  t;
      System.err.println("Adding:" + typeURI);
      config.addAcceptedType(t);
    }

    return GabotoEntityPool.createFrom(config);
  }

  private String lowercaseRequestParameter(HttpServletRequest request,
      String name) {
    String p = request.getParameter(name);
    if (p != null)
      p = p.toLowerCase();
    return p;
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
        throw new IllegalArgumentException("Unexpeted format " + format);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void output(GabotoEntityPool pool, HttpServletRequest request,
      HttpServletResponse response, String format) {
    orderBy = lowercaseRequestParameter(request, "orderBy");

    String displayParentNameStringValue = lowercaseRequestParameter(request,
        "parentName");
    boolean displayParentName;
    if (displayParentNameStringValue == null) {
      displayParentName = true;
    } else {
      if (displayParentNameStringValue.equals("false"))
        displayParentName = false;
      else
        displayParentName = true;
    }

    String jsCallback = lowercaseRequestParameter(request, "jsCallback");
    // clean params
    if (jsCallback != null) {
      jsCallback = jsCallback.replaceAll("[^a-zA-Z0-9_]", "");
    }

    String output = "";
    if (format.equals("kml")) {
      response.setContentType("application/vnd.google-earth.kml+xml");
      output = createKml(pool, displayParentName);
    } else if (format.equals("json") || format.equals("js")) {
      response.setContentType("text/javascript");
      JSONPoolTransformer transformer = new JSONPoolTransformer();
      String jsonNesting = lowercaseRequestParameter(request, "jsonNesting");
      if (jsonNesting != null) {
        try {
          int depth = Integer.parseInt(jsonNesting);
          transformer.setNesting(depth);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException(e);
        }
      }

      output = transformer.transform(pool);
      if (format.equals("js") && jsCallback == null)
        jsCallback = "oxpoints";
      if (jsCallback != null)
        output = jsCallback + "(" + output + ");";
    } else if (format.equals("gjson")) {
      response.setContentType("text/javascript");
      GeoJSONPoolTransfomer transformer = new GeoJSONPoolTransfomer();
      if (arc != null) {
        transformer.addEntityFolderType(getFolderTypeUri(folderType),
            getPropertyURIOrDie(arc));
      }// oxpq
      if (orderBy != null) {
        transformer.setOrderBy(getPropertyURIOrDie(orderBy));
      }
      transformer.setDisplayParentName(displayParentName);

      output += transformer.transform(pool);
      if (jsCallback != null)
        output = jsCallback + "(" + output + ");";
    } else if (format.equals("xml")) { // default
      response.setContentType("text/xml");

      System.err.println("Pool has " + pool.getSize() + " elements");
      EntityPoolTransformer transformer;
      try {
        transformer = RDFPoolTransformerFactory
            .getRDFPoolTransformer(GabotoQuery.FORMAT_RDF_XML_ABBREV);
        output = transformer.transform(pool);
        // System.err.println(output);
      } catch (UnsupportedFormatException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      output = runGPSBabel(createKml(pool, displayParentName), "kml", format);
      if (output.equals(""))
        throw new RuntimeException("No output created by GPSBabel");
    }
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
      System.err.println("Prop:" + propertyKey + ":" + prefix);
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
