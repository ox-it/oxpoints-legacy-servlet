package org.oucs.oxpoints.web.interfaces.gui;


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
import org.oucs.gaboto.entities.pool.GabotoEntityPool;
import org.oucs.gaboto.entities.pool.GabotoEntityPoolConfiguration;
import org.oucs.gaboto.entities.transformation.EntityPoolTransformer;
import org.oucs.gaboto.entities.transformation.RDFPoolTransformerFactory;
import org.oucs.gaboto.entities.transformation.json.GeoJSONPoolTransfomer;
import org.oucs.gaboto.entities.transformation.json.JSONPoolTransformer;
import org.oucs.gaboto.entities.transformation.kml.KMLPoolTransformer;
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


public class OxPointsURI extends HttpServlet {

	/**
	 * 
	 */
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
		String value = request.getParameter("value");
		
		// load snapshot
		GabotoSnapshot snapshot = gaboto.getSnapshot(TimeInstant.now());

		// load pool
		String realProperty = getPropertyURI(property);
		GabotoEntityPool pool;
		
		if(null != value)
			pool = snapshot.loadEntitiesWithProperty(realProperty, value);
		else
			pool = snapshot.loadEntitiesWithProperty(realProperty);
		
		output(pool, request, response);
	}
	
	private void output(GabotoEntityPool pool, HttpServletRequest request, HttpServletResponse response){
		String format = request.getParameter("format");
		String orderBy = request.getParameter("orderBy");
		String jsonNesting = request.getParameter("jsonNesting");
		boolean displayParentName = request.getParameter("parentName") == null ? true : request.getParameter("parentName").equals("false");
		
		String output = "";
		if(format != null && format.toLowerCase().equals("kml")){
			response.setContentType("text/xml");
			
			KMLPoolTransformer transformer = new KMLPoolTransformer();
			if(null != orderBy){
				String realOrderBy = getPropertyURI(orderBy);
				transformer.setOrderBy(realOrderBy);
			}
			transformer.setDisplayParentName(displayParentName);
			
			output = transformer.transform(pool);
		} else if(format != null &&  format.toLowerCase().equals("json")){
			JSONPoolTransformer transformer = new JSONPoolTransformer();
			if(null != jsonNesting){
				try{
					int level = Integer.parseInt(jsonNesting);
					transformer.setNesting(level);
				} catch(NumberFormatException e){}
			}
			output = (String) transformer.transform(pool);
		} else if(format != null &&  format.toLowerCase().equals("geojson")){
			GeoJSONPoolTransfomer transformer = new GeoJSONPoolTransfomer();
			if(null != orderBy){
				String realOrderBy = getPropertyURI(orderBy);
				transformer.setOrderBy(realOrderBy);
			}
			transformer.setDisplayParentName(displayParentName);
			output = (String) transformer.transform(pool);
		} else {
			response.setContentType("text/xml");
			
			EntityPoolTransformer transformer;
			try {
				transformer = RDFPoolTransformerFactory.getRDFPoolTransformer(GabotoQuery.FORMAT_RDF_XML_ABBREV);
				output = (String) transformer.transform(pool);
			} catch (UnsupportedFormatException e) {
				e.printStackTrace();
			}
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
