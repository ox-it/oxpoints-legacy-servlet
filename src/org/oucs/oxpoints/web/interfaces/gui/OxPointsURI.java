package org.oucs.oxpoints.web.interfaces.gui;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.oucs.oxpoints.OxPointsConfiguration;
import org.oucs.oxpoints.OxPointsLibrary;
import org.oucs.oxpoints.entities.pool.OxPointsEntityPool;
import org.oucs.oxpoints.entities.pool.OxPointsEntityPoolConfiguration;
import org.oucs.oxpoints.entities.transformation.EntityPoolTransformer;
import org.oucs.oxpoints.entities.transformation.RDFPoolTransformerFactory;
import org.oucs.oxpoints.entities.transformation.json.JSONPoolTransformer;
import org.oucs.oxpoints.entities.transformation.kml.KMLPoolTransformer;
import org.oucs.oxpoints.exceptions.EntityPoolInvalidConfigurationException;
import org.oucs.oxpoints.exceptions.UnsupportedFormatException;
import org.oucs.oxpoints.helperscripts.importing.TEIImporter;
import org.oucs.oxpoints.model.OxPoints;
import org.oucs.oxpoints.model.OxPointsFactory;
import org.oucs.oxpoints.model.OxPointsSnapshot;
import org.oucs.oxpoints.model.query.OxPointsQuery;
import org.oucs.oxpoints.timedim.TimeInstant;
import org.oucs.oxpoints.util.OxPointsOntologyLookup;
import org.oucs.oxpoints.util.Performance;
import org.oucs.oxpoints.vocabulary.DC;
import org.oucs.oxpoints.vocabulary.GeoVocab;
import org.oucs.oxpoints.vocabulary.OxPointsVocab;
import org.oucs.oxpoints.vocabulary.VCard;
import org.xml.sax.SAXException;

public class OxPointsURI extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4155078999145248554L;

	private static OxPoints oxp;
	
	private static OxPointsSnapshot snapshot;
	
	public void init(){
		// load oxPoints
		oxp = OxPointsFactory.getEmptyInMemoryOxPoints();
		
		InputStream fis = Thread.currentThread().getContextClassLoader().getResourceAsStream("resources/graphs.rdf");
		InputStream cdg_fis = Thread.currentThread().getContextClassLoader().getResourceAsStream("resources/cdg.rdf");
		
		oxp.read(fis, cdg_fis);
		oxp.recreateTimeDimensionIndex();
		
		snapshot = oxp.getSnapshot(TimeInstant.now());
		
	}
	
	/**
	 * 
	 */
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response){
		String mode = request.getParameter("mode");
		Performance.start("process request");
		if(null == mode)
			processDefaultMode(request, response);
		else if(mode.equals("all"))
			processAllMode(request, response);
		Performance.stop();

	}

	private void processAllMode(HttpServletRequest request,
			HttpServletResponse response) {
		
		String type = "#" + request.getParameter("type");
		
		for(String classURI : OxPointsOntologyLookup.getRegisteredClassesAsURIs()){
			if(classURI.endsWith(type)){
				OxPointsEntityPoolConfiguration config = new OxPointsEntityPoolConfiguration(snapshot);
				config.addAcceptedType(classURI);
				try {
					OxPointsEntityPool pool = OxPointsEntityPool.createFrom(config);
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
		OxPointsSnapshot snapshot = oxp.getSnapshot(TimeInstant.now());

		// load pool
		String realProperty = getPropertyURI(property);
		OxPointsEntityPool pool;
		
		if(null != value)
			pool = snapshot.loadEntitiesWithProperty(realProperty, value);
		else
			pool = snapshot.loadEntitiesWithProperty(realProperty);
		
		output(pool, request, response);
	}
	
	private void output(OxPointsEntityPool pool, HttpServletRequest request, HttpServletResponse response){
		String format = request.getParameter("format");
		String orderBy = request.getParameter("orderBy");
		String jsonNesting = request.getParameter("jsonNesting");
		
		String output = "";
		if(format != null && format.toLowerCase().equals("kml")){
			response.setContentType("text/xml");
			
			KMLPoolTransformer transformer = new KMLPoolTransformer();
			if(null != orderBy){
				String realOrderBy = getPropertyURI(orderBy);
				transformer.setOrderBy(realOrderBy);
			}
			
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
		} else {
			response.setContentType("text/xml");
			
			EntityPoolTransformer transformer;
			try {
				transformer = RDFPoolTransformerFactory.getRDFPoolTransformer(OxPointsQuery.FORMAT_RDF_XML_ABBREV);
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
