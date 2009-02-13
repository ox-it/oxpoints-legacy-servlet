package org.oucs.oxpoints.web.interfaces.gui;


import java.io.File;
import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.oucs.oxpoints.OxPointsConfiguration;
import org.oucs.oxpoints.OxPointsConfigurationDummyImpl;
import org.oucs.oxpoints.OxPointsSystem;
import org.oucs.oxpoints.exceptions.OxPointsException;
import org.oucs.oxpoints.helperscripts.importing.TEIImporter;
import org.oucs.oxpoints.model.OxPoints;
import org.oucs.oxpoints.model.OxPointsFactory;
import org.oucs.oxpoints.model.query.OxPointsQuery;
import org.oucs.oxpoints.model.query.defined.ListOfTypedEntities;
import org.oucs.oxpoints.timedim.TimeInstant;
import org.oucs.oxpoints.util.OxPointsOntologyUtils;
import org.oucs.oxpoints.vocabulary.OxPointsVocab;
import org.xml.sax.SAXException;

import com.hp.hpl.jena.ontology.OntClass;

public class OxPointsURI extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4155078999145248554L;

	private static OxPoints oxp;
	
	public void init(){
		OxPointsSystem.init(new OxPointsConfiguration(){
			@Override
			public String getDbURL() {
				return "jdbc:mysql://localhost:8889/oxp";
			}
			@Override
			public String getDbUser() {
				return "root";
			}
			@Override
			public String getDbPassword() {
				return "root";
			}
			
			@Override
			public String getResourcePath() {
				return "/Users/arno/Documents/workspace/Erewhon-Oxpoints/resources";
			}
		});

		try {
			oxp = OxPointsFactory.getEmptyTestOxPoints();
			new TEIImporter(oxp, new File("/Users/arno/Documents/workspace/Erewhon-Oxpoints/conversion/resources/oxpoints.old.xml")).run();
		} catch (OxPointsException e1) {
			e1.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 */
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response){
		OxPointsQuery query;
		try {
			String result = "";
			
			String yearS = request.getParameter("y");
			String monthS = request.getParameter("m");
			String dayS = request.getParameter("d");
			
			Integer year = null, month = null, day = null;
			try{
				if(null != yearS)
					year = Integer.parseInt(yearS);
				if(null != monthS)
					month = Integer.parseInt(monthS);
				if(null != dayS)
					day = Integer.parseInt(dayS);

			} catch(NumberFormatException e){}
				
			TimeInstant ti;
			if(null == year)
				ti = TimeInstant.now();
			else
				ti = new TimeInstant(year, month, day);
			
			String typeName = request.getParameter("type");
			if(typeName != null){
				OntClass type = OxPointsOntologyUtils.getEntityType(typeName);
				if(null == type)
					result = "Unknown type: " + type;
				else {
					query = new ListOfTypedEntities(oxp, type, ti );
					result = (String) query.execute(OxPointsQuery.FORMAT_KML);
				}
			} else {
				query = new ListOfTypedEntities(oxp, OxPointsVocab.College, ti );
				result = (String) query.execute(OxPointsQuery.FORMAT_KML);
			}
			
			response.setContentType("text/xml");
 			response.getWriter().write(result);
		} catch (OxPointsException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
