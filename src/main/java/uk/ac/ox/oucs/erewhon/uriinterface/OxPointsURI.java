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
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import org.oucs.gaboto.exceptions.EntityPoolInvalidConfigurationException;
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
 * http://127.0.0.1:8080/OxPointsUriGui/OxPointsURI?property=oxp:hasOUCSCode&value=oucs&format=kml
 * 
 */

public class OxPointsURI extends HttpServlet {

  private static final long serialVersionUID = 4155078999145248554L;

  static Map<String,String> namespacePrefixes = new TreeMap<String, String>();
  {
    namespacePrefixes.put("oxp:", OxPointsVocab.NS);
    namespacePrefixes.put("dc:", DC.NS);
    namespacePrefixes.put("vCard:", VCard.NS);
    namespacePrefixes.put("geo:", GeoVocab.NS);
  }
  
  private static Gaboto gaboto;
  
  private static GabotoSnapshot snapshot;

  
  
  public void init(){
    // load Gaboto
    try {
      GabotoLibrary.init(GabotoConfiguration.fromConfigFile());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    gaboto = GabotoFactory.getEmptyInMemoryGaboto();
    
    gaboto.read(getResourceOrDie("graphs.rdf"), getResourceOrDie("cdg.rdf"));
    gaboto.recreateTimeDimensionIndex();
    
    snapshot = gaboto.getSnapshot(TimeInstant.now());
    
  }
  
  private InputStream getResourceOrDie(String fileName) { 
    String resourceName = "resources/" + fileName;
    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
    if (is == null) 
      throw new NullPointerException("File " + resourceName + " cannot be loaded");
    return is;
  }
  /**
   * 
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response){
    String mode = request.getParameter("mode");
    if(mode == null)
      processDefaultMode(request, response);
    else if(mode.equals("all"))
      doAllOfType(request, response);
    else 
      throw new IllegalArgumentException("Unexpected 'mode' parameter: " + mode);
  }

  private void processDefaultMode(HttpServletRequest request, HttpServletResponse response) {
        
    // load parameters
    String property = request.getParameter("property");
    if (property == null) { 
      throw new IllegalArgumentException("'property' parameter missing");
    }
    String fullProperty = getPropertyURI(property);
    
    
    GabotoEntityPool pool;
    
    String value = request.getParameter("value");
    if (value == null) { 
      pool = snapshot.loadEntitiesWithProperty(fullProperty);
    } else { 
    
      String values[] = value.split("[|]");
    
      pool = new GabotoEntityPool(gaboto, snapshot);
      for(String v : values){
        if (property.endsWith("subsetOf")) 
          v = "<http://ns.ox.ac.uk/namespaces/oxpoints/data/unit/" + v + ">";
        System.err.println("Property:"+fullProperty);
        System.err.println("Value:"+v);
        GabotoEntityPool p = snapshot.loadEntitiesWithProperty(fullProperty, v);
        for(GabotoEntity e : p.getEntities())
          pool.addEntity(e);
      }
    }
    
    output(pool, request, response);
  }
  
  private void doAllOfType(HttpServletRequest request, HttpServletResponse response) {
    
    String type = request.getParameter("type");
    if (type == null) { 
      throw new IllegalArgumentException("'type' parameter missing");
    }
    type = "#" + type;
    boolean doneOne = false;
    for(String classURI : GabotoOntologyLookup.getRegisteredClassesAsURIs()){
      System.err.println(classURI);
      if(classURI.endsWith(type)){
        GabotoEntityPoolConfiguration config = new GabotoEntityPoolConfiguration(snapshot);
        config.addAcceptedType(classURI);
        GabotoEntityPool pool;
        try {
          pool = GabotoEntityPool.createFrom(config);
        } catch (EntityPoolInvalidConfigurationException e) {
          throw new RuntimeException(e);
        }
        output(pool, request, response);
        doneOne = true;
        break;
      }
    }
    if(!doneOne)
      throw new RuntimeException("No, matching type found :" + type);
  }

  private String lowercaseRequestParameter(HttpServletRequest request, String name) { 
    String p = request.getParameter(name);
    if (p != null) 
      p = p.toLowerCase();
    return p;
  }
  
  private void output(GabotoEntityPool pool, HttpServletRequest request, HttpServletResponse response){
    String format = lowercaseRequestParameter(request,"format");
    if (format == null) format = "xml"; 
    String orderBy = lowercaseRequestParameter(request,"orderBy");

    String displayParentNameStringValue = lowercaseRequestParameter(request,"parentName");
    boolean displayParentName;
    if (displayParentNameStringValue == null) {  
      displayParentName = true; 
    } else {
      if (displayParentNameStringValue.equals("false"))
        displayParentName = false; 
      else
        displayParentName = true; 
    }
    
    String jsCallback = lowercaseRequestParameter(request,"jsCallback");
    // clean params
    if(jsCallback != null){
      jsCallback = jsCallback.replaceAll("[^a-zA-Z0-9_]", "");
    }
    
    String output = "";
    if(format.equals("kml")){
      response.setContentType("application/vnd.google-earth.kml+xml");
      
      KMLPoolTransformer transformer = new KMLPoolTransformer();
      if(orderBy != null){
        transformer.setOrderBy(getPropertyURI(orderBy));
      }
      transformer.setDisplayParentName(displayParentName);

      output = transformer.transform(pool);
    } else if(format.equals("json")){
      response.setContentType("text/plain");
      JSONPoolTransformer transformer = new JSONPoolTransformer();
      String jsonNesting = lowercaseRequestParameter(request,"jsonNesting");
      if(jsonNesting != null){
        try{
          int level = Integer.parseInt(jsonNesting);
          transformer.setNesting(level);
        } catch(NumberFormatException e){
          throw new IllegalArgumentException(e);
        }
      }

      output = (String)transformer.transform(pool);
      if(jsCallback != null)
        output = jsCallback + "(" + output + ");";
      System.err.println("Callback:"+jsCallback);
    } else if(format.equals("geojson")){
      response.setContentType("text/plain");
      GeoJSONPoolTransfomer transformer = new GeoJSONPoolTransfomer();
      if(orderBy != null){
        transformer.setOrderBy(getPropertyURI(orderBy));
      }
      transformer.setDisplayParentName(displayParentName);

      output += (String)transformer.transform(pool);
      if(jsCallback != null)
        output = jsCallback + "(" + output + ");";
    } else if(format.equals("xml")){ // default
      response.setContentType("text/xml");
      
      EntityPoolTransformer transformer;
      try {
        transformer = RDFPoolTransformerFactory.getRDFPoolTransformer(GabotoQuery.FORMAT_RDF_XML_ABBREV);
        output = (String)transformer.transform(pool);
      } catch (UnsupportedFormatException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Unexpected format parameter: [" + format + "]");
    }
      
    try {
      response.getWriter().write(output);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private String getPropertyURI(String property){
    for(String prefix : namespacePrefixes.keySet()){
      if(property.startsWith(prefix))
        return namespacePrefixes.get(prefix) + property.substring(prefix.length());
    }
    throw new IllegalArgumentException("Found no URI matching property " + property);
  }

}
