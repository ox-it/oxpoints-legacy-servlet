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

import java.io.IOException;
import java.io.InputStream;
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
  private static Logger logger = Logger.getLogger(OxPointsQueryServlet.class.getName());

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
    String resourceName = "/resources/" + fileName;
    InputStream is = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(resourceName);
    if (is == null)
      throw new NullPointerException("File " + resourceName
          + " cannot be loaded");
    return is;
  }

  private void setFormatFromPathInfo(String pathInfo) {
    int dotPosition = pathInfo.indexOf('.');
    if (dotPosition == -1) {
      format = "xml";
    } else {
      format = pathInfo.substring(dotPosition + 1);
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
      setFormatFromPathInfo(pathInfo);
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

        int dotPosition = pathInfo.indexOf('.');
        String id = null;
        if (dotPosition == -1) {
          id = pathInfo.substring(4);
        } else {
          id = pathInfo.substring(4, dotPosition);
        }
        String uri = config.getNSData() + id;
        pool = new GabotoEntityPool(gaboto, snapshot);
        try {
          pool.addEntity(snapshot.loadEntity(uri));
        } catch (ResourceDoesNotExistException e) {
          throw new RuntimeException("Resource not found with uri " + uri, e);
        }
      } else if (pathInfo.startsWith("/types")) {
        output(GabotoOntologyLookup.getRegisteredEntityClassesAsClassNames(),
            request, response, format);
        return;
      } else
        throw new IllegalArgumentException("Unexpected path info : " + pathInfo);
    } else {
      format = lowercaseRequestParameter(request, "format");
      if (format == null)
        format = "xml";
      String mode = request.getParameter("mode");
      if (mode == null) {
        pool = loadPoolWithEntitiesOfProperty(request);
      } else if (mode.equals("all")) {
        pool = loadPoolWithEntitiesOfType(request);
      } else
        throw new IllegalArgumentException("Unexpected 'mode' parameter: "
            + mode);
    }
    output(pool, request, response, format);

  }

  private GabotoEntityPool loadPoolWithEntitiesOfProperty(
      HttpServletRequest request) {

    // load parameters
    String property = request.getParameter("property");
    if (property == null) {
      throw new IllegalArgumentException("'property' parameter missing");
    }
    String propertyURI = getPropertyURIOrDie(property);

    GabotoEntityPool pool = null;

    String value = request.getParameter("value");
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

  private GabotoEntityPool loadPoolWithEntitiesOfType(HttpServletRequest request) {

    String type = request.getParameter("type");
    if (type == null) {
      throw new IllegalArgumentException("'type' parameter missing");
    }
    String types[] = type.split("[|]");

    GabotoEntityPoolConfiguration config = new GabotoEntityPoolConfiguration(snapshot);
    for (String t : types) {
      if (!GabotoOntologyLookup.isValidName(t)) 
        throw new IllegalArgumentException("Found no URI matching type " + t);
      String typeURI  = "http://ns.ox.ac.uk/namespace/oxpoints/2009/02/owl#" + t;
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

  private void output(Collection<String> them, HttpServletRequest request,
      HttpServletResponse response, String format) {
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
    } else if (format.equals("json")) {
      response.setContentType("text/javascript");
      JSONPoolTransformer transformer = new JSONPoolTransformer();
      String jsonNesting = lowercaseRequestParameter(request, "jsonNesting");
      if (jsonNesting != null) {
        try {
          int level = Integer.parseInt(jsonNesting);
          transformer.setNesting(level);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException(e);
        }
      }

      output = transformer.transform(pool);
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
      throw new IllegalArgumentException("Unexpected format parameter: ["
          + format + "]");
    }

    try {
      response.getWriter().write(output);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
      return "http://ns.ox.ac.uk/namespace/oxpoints/2009/02/owl#College";
    return getPropertyURIOrDie(propertyKey);
  }
}
