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
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.gaboto.EntityDoesNotExistException;
import net.sf.gaboto.ResourceDoesNotExistException;
import net.sf.gaboto.node.GabotoEntity;
import net.sf.gaboto.node.pool.EntityPool;
import net.sf.gaboto.node.pool.EntityPoolConfiguration;
import net.sf.gaboto.query.GabotoQuery;
import net.sf.gaboto.query.UnsupportedQueryFormatException;
import net.sf.gaboto.transformation.EntityPoolTransformer;
import net.sf.gaboto.transformation.GeoJSONPoolTransfomer;
import net.sf.gaboto.transformation.JSONPoolTransformer;
import net.sf.gaboto.transformation.KMLPoolTransformer;
import net.sf.gaboto.transformation.RDFPoolTransformerFactory;
import net.sf.gaboto.vocabulary.OxPointsVocab;

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

public class OxPointsQueryServlet extends OxPointsServlet {

  private static final long serialVersionUID = 4155078999145248554L;

  private static String gpsbabelVersion = null;

  public void init() {
    super.init();
  }
  
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    System.err.println("Still here");
    Enumeration<String> them = getServletConfig().getInitParameterNames();
    while (them.hasMoreElements()) { 
      String it = them.nextElement();
      System.err.println(it + "=" + getServletConfig().getInitParameter(it));
    }
    
    response.setCharacterEncoding("UTF-8");
    try {
      System.err.println("about to estblish outputPool");
      outputPool(request, response);
      
      System.err.println("No Error here");
    } catch (ResourceNotFoundException e) {
      try {
        response.sendError(404, e.getMessage());
      } catch (IOException e1) {
        error(request, response, new AnticipatedException("Problem reporting error: " + e.getMessage(), e1, 500));
      }
    } catch (EntityDoesNotExistException e) {
      try {
        response.sendError(404, e.getMessage());
      } catch (IOException e1) {
        error(request, response, new AnticipatedException("Problem reporting error: " + e.getMessage(),e1, 500));
      }
    } catch (AnticipatedException e) {
      error(request, response, e);
    }
  }

  void outputPool(HttpServletRequest request, HttpServletResponse response) throws ResourceNotFoundException {
    Query query = Query.fromRequest(request);
    switch (query.getReturnType()) {
    case META_TIMESTAMP:
      try {
        response.getWriter().write(new Long(startTime.getTimeInMillis()).toString());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    case META_NEXT_ID:
      try {
        response.getWriter().write(new Long(snapshot.getGaboto().getCurrentHighestId() + 1).toString());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    case META_TYPES:
      output(snapshot.getGaboto().getConfig().getGabotoOntologyLookup().getRegisteredEntityClassesAsClassNames(), query, response);
      return;
    case ALL:
      output(EntityPool.createFrom(snapshot), query, response);
      return;
    case INDIVIDUAL:
      System.err.println("Still here1");
      EntityPool pool = new EntityPool(gaboto, snapshot);
      System.err.println("Still here2");
      establishParticipantUri(query);
      System.err.println("Still here3");
      System.err.println("We have " + snapshot.size() + " entities in snapshot before loadEntity");
      pool.addEntity(snapshot.loadEntity(query.getUri()));
      System.err.println("Still here4");
      output(pool, query, response);
      System.err.println("Still here5");
      return;
    case TYPE_COLLECTION:
      output(loadPoolWithEntitiesOfType(query.getType()), query, response);
      return;
    case PROPERTY_ANY: // value null
      output(loadPoolWithEntitiesOfProperty(query.getRequestedProperty(), query.getRequestedPropertyValue()), query,
              response);
      return;
    case PROPERTY_SUBJECT:
      EntityPool subjectPool = null;
      if (requiresResource(query.getRequestedProperty())) {
        establishParticipantUri(query);
        if (query.getUri() == null)
          throw new ResourceNotFoundException("Resource not found with coding " + query.getParticipantCoding() + 
              " and value " + query.getParticipantCode());
        GabotoEntity object = snapshot.loadEntity(query.getUri());
        EntityPool creationPool = EntityPool.createFrom(snapshot);
        System.err.println("CreationPool size " + creationPool.size());
        object.setCreatedFromPool(creationPool);
        subjectPool = loadPoolWithActiveParticipants(object, query.getRequestedProperty()); 
      } else { 
        subjectPool = loadPoolWithEntitiesOfProperty(query.getRequestedProperty(), query.getRequestedPropertyValue());         
      }
      output(subjectPool, query, response);
      return;
    case PROPERTY_OBJECT: 
      establishParticipantUri(query);
      if (query.getUri() == null)
        throw new ResourceNotFoundException("Resource not found with coding " + query.getParticipantCoding() + 
            " and value " + query.getParticipantCode());
      GabotoEntity subject = snapshot.loadEntity(query.getUri());
      
      EntityPool objectPool = loadPoolWithPassiveParticipants(subject, query.getRequestedProperty());
      output(objectPool, query, response);
      return;
    case NOT_FILTERED_TYPE_COLLECTION:
      EntityPool p = loadPoolWithEntitiesOfType(query.getType());
      EntityPool p2 = loadPoolWithEntitiesOfType(query.getType());
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

  private EntityPool loadPoolWithEntitiesOfProperty(Property prop, String value) {
    System.err.println("loadPoolWithEntitiesOfProperty" + prop + ":" + value);
    if (prop == null)
      throw new NullPointerException();
    EntityPool pool = null;
    if (value == null) {
      pool = snapshot.loadEntitiesWithProperty(prop);
    } else {
      String values[] = value.split("[|]");
      for (String v : values) {
        if (requiresResource(prop)) {
          Resource r = getResource(v);
          System.err.println("About to load " + prop + " with value " + r);
          pool = becomeOrAdd(pool, snapshot.loadEntitiesWithProperty(prop, r));
        } else {
          pool = becomeOrAdd(pool, snapshot.loadEntitiesWithProperty(prop, v));
        }
      }
    }
    return pool;
  }
  
  @SuppressWarnings("unchecked")
  private EntityPool loadPoolWithActiveParticipants(GabotoEntity passiveParticipant, Property prop) { 
    if (prop == null)
      throw new NullPointerException();
    EntityPool pool = new EntityPool(gaboto, snapshot);
    System.err.println("loadPoolWithActiveParticipants" + passiveParticipant.getUri() + "  prop " + prop + " which ");
    Set<Entry<String, Object>> passiveProperties = passiveParticipant.getAllPassiveProperties().entrySet(); 
    for (Entry<String, Object> entry : passiveProperties) {
      if (entry.getKey().equals(prop.getURI())) {
        if (entry.getValue() != null) {
          if (entry.getValue() instanceof HashSet) { 
            HashSet<Object> them = (HashSet<Object>)entry.getValue(); 
            for (Object e : them) { 
              if (e instanceof GabotoEntity) {
                System.err.println("Adding set member :" + e);
                pool.add((GabotoEntity)e);
              }
            }
          } else if (entry.getValue() instanceof GabotoEntity) { 
            System.err.println("Adding individual :" + entry.getKey());
            pool.add((GabotoEntity)entry.getValue());            
          } else { 
            System.err.println("Ignoring:" + entry.getKey());
          }
        } else { 
          System.err.println("Ignoring:" + entry.getKey());
        }
      } else { 
        System.err.println("Ignoring:" + entry.getKey());
      }
    }
    return pool;
  }

  private void establishParticipantUri(Query query) throws ResourceNotFoundException { 
    if (query.needsCodeLookup()) {
      System.err.println("need");
      Property coding = Query.getPropertyFromAbreviation(query.getParticipantCoding());
      System.err.println("establishUri" + query.getParticipantCode());
      
      EntityPool objectPool = snapshot.loadEntitiesWithProperty(coding, query.getParticipantCode());
      boolean found = false;
      for (GabotoEntity objectKey: objectPool) { 
        if (found)
          throw new RuntimeException("Found two:" + objectKey);
        query.setParticipantUri(objectKey.getUri());
        found = true;
      }
    }
    if (query.getParticipantUri() == null)
      throw new ResourceNotFoundException("No resource found with coding " + query.getParticipantCoding() + 
          " and value " + query.getParticipantCode());
  }
  @SuppressWarnings("unchecked")
  private EntityPool loadPoolWithPassiveParticipants(GabotoEntity activeParticipant, Property prop) { 
    if (prop == null)
      throw new NullPointerException();
    EntityPool pool = new EntityPool(gaboto, snapshot);
    System.err.println("loadPoolWithPassiveParticipants" + activeParticipant.getUri() + "  prop " + prop + " which ");
    Set<Entry<String, Object>> directProperties = activeParticipant.getAllDirectProperties().entrySet(); 
    for (Entry<String, Object> entry : directProperties) {
      if (entry.getKey().equals(prop.getURI())) {
        if (entry.getValue() != null) {
          if (entry.getValue() instanceof HashSet) { 
            HashSet<Object> them = (HashSet<Object>)entry.getValue(); 
            for (Object e : them) { 
              if (e instanceof GabotoEntity) {
                pool.add((GabotoEntity)e);
              }
            }
          } else if (entry.getValue() instanceof GabotoEntity) { 
            pool.add((GabotoEntity)entry.getValue());            
          } else { 
            System.err.println("Ignoring:" + entry.getKey());
          }
        } else { 
          System.err.println("Ignoring:" + entry.getKey());
        }
      } else { 
        System.err.println("Ignoring:" + entry.getKey());
      }
    }
    return pool;
  }

  private EntityPool becomeOrAdd(EntityPool pool, EntityPool poolToAdd) {
    System.err.println("BecomeOrAdd" + pool);
    if (poolToAdd == null)
      throw new NullPointerException();
    if (pool == null) {
      return poolToAdd;
    } else {
      for (GabotoEntity e : poolToAdd.getEntities()) {
        pool.addEntity(e);
      }
      return pool;
    }
  }

  private Resource getResource(String v) {
    String vUri = v;
    if (!vUri.startsWith(config.getNSData()))
        vUri = config.getNSData() + v;
    try {
      return snapshot.getResource(vUri);
    } catch (ResourceDoesNotExistException e) {
      throw new RuntimeException(e);
    }
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

  private EntityPool loadPoolWithEntitiesOfType(String type) {
    System.err.println("Type:" + type);
    String types[] = type.split("[|]");

    EntityPoolConfiguration conf = new EntityPoolConfiguration(snapshot);
    for (String t : types) {
      if (!snapshot.getGaboto().getConfig().getGabotoOntologyLookup().isValidName(t))
        throw new IllegalArgumentException("Found no URI matching type " + t);
      String typeURI = OxPointsVocab.NS + t;
      conf.addAcceptedType(typeURI);
    }

    return EntityPool.createFrom(conf);
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
        throw new AnticipatedException("Unexpected format " + query.getFormat(), 400);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void output(EntityPool pool, Query query, HttpServletResponse response) {
    System.err.println("Pool has " + pool.getSize() + " elements");

    String output = "";
    if (query.getFormat().equals("kml")) {
      output = createKml(pool, query);
      response.setContentType("application/vnd.google-earth.kml+xml");
    } else if (query.getFormat().equals("json") || query.getFormat().equals("js")) {

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
      EntityPoolTransformer transformer;
      try {
        transformer = RDFPoolTransformerFactory.getRDFPoolTransformer(GabotoQuery.FORMAT_RDF_XML_ABBREV);
        output = transformer.transform(pool);
      } catch (UnsupportedQueryFormatException e) {
        throw new IllegalArgumentException(e);
      }
      response.setContentType("application/rdf+xml");
    } else if (query.getFormat().equals("txt")) {
      try { 
        for (GabotoEntity entity: pool.getEntities()) { 
          response.getWriter().write(entity.toString() + "\n");
          for (Entry<String, Object> entry : entity.getAllDirectProperties().entrySet()) { 
            if (entry.getValue() != null)
              response.getWriter().write("  " + entry.getKey() + " : " + entry.getValue() + "\n");
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      response.setContentType("text/plain");
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

  private String createKml(EntityPool pool, Query query) {
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
  
  public static String getGPSBabelVersion() {
    if (gpsbabelVersion != null ) 
      return gpsbabelVersion;

    // '/usr/bin/gpsbabel -V
    OutputStream stdin = null;
    InputStream stderr = null;
    InputStream stdout = null;

    String output = "";
    String command = "/usr/bin/gpsbabel -V";
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
      stdin.flush();
      stdin.close();

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
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      process.destroy();
    }
    //GPSBabel Version 1.3.6
    gpsbabelVersion =  output.substring("GPSBabel Version ".length());
    return gpsbabelVersion;
  }

}
