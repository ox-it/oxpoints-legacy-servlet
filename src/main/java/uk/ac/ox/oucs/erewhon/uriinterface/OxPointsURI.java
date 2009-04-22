package uk.ac.ox.oucs.erewhon.uriinterface;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

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
import org.xml.sax.SAXException;

import com.hp.hpl.jena.vocabulary.DC;

/**
 * Try invoking with 
 * http://127.0.0.1:8080/OxPointsUriGui/OxPointsURI?property=oxp:hasOUCSCode&value=oucs&format=kml
 * 
 */

public class OxPointsURI extends HttpServlet {

  private static final long serialVersionUID = 4155078999145248554L;

  private static Gaboto gaboto;
  
  private static GabotoSnapshot snapshot;
  
  public void init(){
    // load Gaboto
    try {
      GabotoLibrary.init(GabotoConfiguration.fromConfigFile());
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    gaboto = GabotoFactory.getEmptyInMemoryGaboto();
    
    InputStream fis = Thread.currentThread().getContextClassLoader().getResourceAsStream("resources/graphs.rdf");
    InputStream cdg_fis = Thread.currentThread().getContextClassLoader().getResourceAsStream("resources/cdg.rdf");
    
    gaboto.read(fis, cdg_fis);
    gaboto.recreateTimeDimensionIndex();
    
    snapshot = gaboto.getSnapshot(TimeInstant.now());
    
  }
  
  /**
   * 
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response){
    String mode = request.getParameter("mode");
    if(null == mode)
      processDefaultMode(request, response);
    else if(mode.equals("all"))
      processAllMode(request, response);
  }

  private void processAllMode(HttpServletRequest request,
      HttpServletResponse response) {
    
    String type = "#" + request.getParameter("type");
    
    for(String classURI : GabotoOntologyLookup.getRegisteredClassesAsURIs()){
      if(classURI.endsWith(type)){
        GabotoEntityPoolConfiguration config = new GabotoEntityPoolConfiguration(snapshot);
        config.addAcceptedType(classURI);
        try {
          GabotoEntityPool pool = GabotoEntityPool.createFrom(config);
          output(pool, request, response);
        } catch (EntityPoolInvalidConfigurationException e) {
          e.printStackTrace();
        }
        
        break;
      }
    }
  }

  private void processDefaultMode(HttpServletRequest request,
      HttpServletResponse response) {
        
    // load parameters
    String property = request.getParameter("property");
    if (property == null) { 
      error(response, "'property' parameter missing");
      return;
    }
    String value = request.getParameter("value");
    if (value == null) { 
      error(response, "'value' parameter missing");
      return;
    }
    
    String values[] = value.split("[|]");
    
    // load snapshot
    // FIXME hiding field
    //GabotoSnapshot snapshot = gaboto.getSnapshot(TimeInstant.now());

    // load pool
    String realProperty = getPropertyURI(property);
    GabotoEntityPool pool;
    
    if(null == value)
      pool = snapshot.loadEntitiesWithProperty(realProperty);
    else {
      pool = new GabotoEntityPool(gaboto, snapshot);
      for(String v : values){
        GabotoEntityPool p = snapshot.loadEntitiesWithProperty(realProperty, v);
        for(GabotoEntity e : p.getEntities())
          pool.addEntity(e);
      }
    }
    
    output(pool, request, response);
  }
  
  private void error(HttpServletResponse response, String errorText) { 
    try { 
          response.getWriter().write("Error: " + errorText);
    } catch (IOException e) { 
      e.printStackTrace();
    }
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
    String jsonNesting = lowercaseRequestParameter(request,"jsonNesting");
    boolean displayParentName = lowercaseRequestParameter(request,"parentName") == null ? 
    		true : lowercaseRequestParameter(request,"parentName").equals("false");
    String jsonCallback = lowercaseRequestParameter(request,"jsCallback");

    // clean params
    if(jsonCallback != null){
      jsonCallback = jsonCallback.replaceAll("[^a-zA-Z0-9_]", "");
    }
    
    String output = "";
    if(format.equals("kml")){
      // Apache and the browser should handle content to based upon extension
      response.setContentType("application/vnd.google-earth.kml+xml");
      
      KMLPoolTransformer transformer = new KMLPoolTransformer();
      if(orderBy != null){
        String realOrderBy = getPropertyURI(orderBy);
        transformer.setOrderBy(realOrderBy);
      }
      transformer.setDisplayParentName(displayParentName);
      
      output = transformer.transform(pool);
    } else if(format.equals("json")){
      response.setContentType("text/plain");
      JSONPoolTransformer transformer = new JSONPoolTransformer();
      if(jsonNesting != null){
        try{
          int level = Integer.parseInt(jsonNesting);
          transformer.setNesting(level);
        } catch(NumberFormatException e){
          throw new RuntimeException(e);
        }
      }

      if(null != jsonCallback)
        output = jsonCallback + "(\n";
      output = (String) transformer.transform(pool);
      if(null != jsonCallback)
        output += ");";
    } else if(format.equals("geojson")){
      response.setContentType("text/plain");
      GeoJSONPoolTransfomer transformer = new GeoJSONPoolTransfomer();
      if(orderBy != null){
        String realOrderBy = getPropertyURI(orderBy);
        transformer.setOrderBy(realOrderBy);
      }
      transformer.setDisplayParentName(displayParentName);

      if(jsonCallback != null)
        output = jsonCallback + "(" + "\n";
      output += (String) transformer.transform(pool);
      if(jsonCallback != null)
        output += ");";
    } else if(format.equals("xml")){ // default
      // Apache and the browser should handle content to based upon extension
      response.setContentType("text/xml");
      
      EntityPoolTransformer transformer;
      try {
        transformer = RDFPoolTransformerFactory.getRDFPoolTransformer(GabotoQuery.FORMAT_RDF_XML_ABBREV);
        output = (String) transformer.transform(pool);
      } catch (UnsupportedFormatException e) {
        e.printStackTrace();
      }
    } else { 
      throw new RuntimeException("Bug: unexpected format :" + format + ":");
    }
      

    try {
      response.getWriter().write(output);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  private String getPropertyURI(String property){
    // prepare lookup tables
    Map<String,String> namespacePrefixes = new HashMap<String, String>();
    namespacePrefixes.put("oxp:", OxPointsVocab.NS);
    namespacePrefixes.put("dc:", DC.NS);
    namespacePrefixes.put("vCard:", VCard.NS);
    namespacePrefixes.put("geo:", GeoVocab.NS);
    
    for(String prefix : namespacePrefixes.keySet()){
      if(property.startsWith(prefix))
        return namespacePrefixes.get(prefix) + property.substring(prefix.length());
    }
    
    return "";
  }

}
