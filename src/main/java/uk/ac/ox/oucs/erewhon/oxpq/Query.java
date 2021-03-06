/**
 * Copyright 2009 University of Oxford
 *
 * Written by Tim Pizey for the Erewhon Project
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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import net.sf.gaboto.time.TimeInstant;
import net.sf.gaboto.vocabulary.DCTermsVocab;
import net.sf.gaboto.vocabulary.DCVocab;
import net.sf.gaboto.vocabulary.FOAFVocab;
import net.sf.gaboto.vocabulary.GabotoKMLVocab;
import net.sf.gaboto.vocabulary.GabotoVocab;
import net.sf.gaboto.vocabulary.GeoVocab;
import net.sf.gaboto.vocabulary.OxPointsVocab;
import net.sf.gaboto.vocabulary.RDFContext;
import net.sf.gaboto.vocabulary.RDFGraph;
import net.sf.gaboto.vocabulary.SKOSVocab;
import net.sf.gaboto.vocabulary.TimeVocab;
import net.sf.gaboto.vocabulary.VCardVocab;
import net.sf.gaboto.vocabulary.MeterVocab;

import com.hp.hpl.jena.rdf.model.Property;

public final class Query {


	private Collection<String> participantIDs;     // subject or object id
	private Collection<String> participantURIs;  
	private String participantCoding; // Coding system, other than id
	private Collection<String> participantCodes;   

	private String type;
	private String arc = null;
	private String orderBy = null;
	private String requestedPropertyName = null;
	private String requestedPropertyValue = null;
	private boolean displayParentName = true;
	private int jsonDepth = 1;
	private String format = null;
	// See http://bob.pythonmac.org/archives/2005/12/05/remote-json-jsonp/
	private String jsCallback = null;

	private Property notProperty;
	private Property orderByProperty;
	private Property arcProperty;
	private Property requestedProperty;
	private boolean searchIndirect = false;
	private boolean searchPassive = false;
	private String sparqlQuery = null;
	private TimeInstant timeInstant = null;

	private ReturnType returnType = ReturnType.ALL;
	private String folderClassURI = OxPointsVocab.NS + "College";
	private boolean needsCodeLookup;

	private static final String ENTITY_PREFIX = "http://oxpoints.oucs.ox.ac.uk/id/";

	public enum ReturnType {
		META_TIMESTAMP, META_TYPES, META_NEXT_ID, ALL, TYPE_COLLECTION, 
		INDIVIDUAL, 
		NOT_FILTERED_TYPE_COLLECTION,
		PROPERTY_ANY, 
		PROPERTY_SUBJECT,
		PROPERTY_OBJECT,
		SPARQL_QUERY
	} 

	private static Map<String, String> namespacePrefixes = new TreeMap<String, String>();

	{
		// HACK the ordering so that oxpoints#subsetOf is prioritised over DC#subsetOf
		namespacePrefixes.put("1oxp:",   OxPointsVocab.NS);
		namespacePrefixes.put("2dc:",    DCVocab.NS);
		namespacePrefixes.put("3vCard:", VCardVocab.NS);
		namespacePrefixes.put("4geo:",   GeoVocab.NS);
	}

	private static Map<String,Property> propertyMapping = getPropertyMapping();

	private static Map<String,Property> getPropertyMapping() {
		Map<String,Property> map = new HashMap<String,Property>();

		map.put("isPartOf", DCTermsVocab.isPartOf);
		map.put("occupies", OxPointsVocab.occupies);
		map.put("member", SKOSVocab.member);
		map.put("hasOUCSCode", OxPointsVocab.hasOUCSCode);
		map.put("hasOLISCode", OxPointsVocab.hasOLISCode);
		map.put("hasOLISAlephCode", OxPointsVocab.hasOLISAlephCode);
		map.put("hasOBNCode", OxPointsVocab.hasOBNCode);
		map.put("hasFinanceCode", OxPointsVocab.hasFinanceCode);
		map.put("primaryPlace", OxPointsVocab.primaryPlace);
		map.put("title", DCVocab.title);
		map.put("hasITHomepage", OxPointsVocab.hasITHomepage);
		map.put("hasWeblearn", OxPointsVocab.hasWeblearn);
		map.put("homepage", FOAFVocab.homepage);
		map.put("depiction", FOAFVocab.depiction);
		map.put("adr", VCardVocab.adr);
		map.put("lat", GeoVocab.lat);
		map.put("long", GeoVocab.long_);

		map.put("supplies", MeterVocab.supplies);
		map.put("downstreamOf", MeterVocab.downstreamOf);

		//map.put("sameAs", OWLVocab.sameAs);

		return map;
	}

	private Query() {}

	/**
	 * @param request
	 */
	@SuppressWarnings("unchecked")
	public static Query fromRequest(HttpServletRequest request) {
		Query q = new Query();
		q.format = "xml";

		q.arc = null;
		q.orderBy = null;
		q.requestedPropertyName = null;
		q.requestedPropertyValue = null;
		q.displayParentName = true;
		q.jsonDepth = 1;

		String pathInfo = request.getPathInfo();
		if (pathInfo == null) 
			throw new AnticipatedException("Expected path info", 400  );
		int dotPosition = pathInfo.lastIndexOf('.');
		//System.err.println(pathInfo);
		String resultsetSpec;
		if (dotPosition == -1) {
			resultsetSpec = pathInfo;
		} else {
			q.format = pathInfo.substring(dotPosition + 1);
			resultsetSpec = pathInfo.substring(0,dotPosition);
		}    
		//System.err.println(resultsetSpec);
		if (resultsetSpec.startsWith("/timestamp")) {
			q.returnType = ReturnType.META_TIMESTAMP;
		} else if (resultsetSpec.startsWith("/types") || resultsetSpec.startsWith("/classes")) {
			q.returnType = ReturnType.META_TYPES;
		} else if (resultsetSpec.startsWith("/next")) {
			q.returnType = ReturnType.META_NEXT_ID;
		} else if (resultsetSpec.startsWith("/all")) {
			q.returnType = ReturnType.ALL;
		} else if (resultsetSpec.startsWith("/id/")) {
			q.setParticipantIDs(resultsetSpec.substring(4));
			q.returnType = ReturnType.INDIVIDUAL;
		} else if (resultsetSpec.startsWith("/type/")) {  
			q.type = resultsetSpec.substring(6);
			q.returnType = ReturnType.TYPE_COLLECTION;
		} else if (resultsetSpec.startsWith("/class/")) {  
			q.type = resultsetSpec.substring(7);
			q.returnType = ReturnType.TYPE_COLLECTION;
		} else if (resultsetSpec.startsWith("/sparql")) {
			q.returnType = ReturnType.SPARQL_QUERY;
		} else if (parseTemplate(resultsetSpec,q)) {
		} else if (startsWithPropertyName(resultsetSpec)) {
			q.requestedPropertyName = getPropertyName(resultsetSpec); 
			q.requestedPropertyValue = getPropertyValue(resultsetSpec); 
			q.requestedProperty = getPropertyFromAbbreviation(q.requestedPropertyName);
			q.returnType = ReturnType.PROPERTY_ANY;
		} else
			throw new AnticipatedException("Unexpected path info " + pathInfo, 400);

		Enumeration<String> en = request.getParameterNames();
		while (en.hasMoreElements()) {
			String pName = en.nextElement();
			String pValue =  request.getParameter(pName);

			//System.err.println("Param:" + pName + "=" + pValue);
			if (pName.equals("arc")) {
				q.arcProperty = getPropertyFromAbbreviation(pValue);
				if (q.arcProperty != null)
					q.arc = pValue;
				else 
					throw new AnticipatedException("Unrecognised arc property name " + pValue, 400);
			} else if (pName.equals("not")) {
				q.returnType = ReturnType.NOT_FILTERED_TYPE_COLLECTION;
				q.notProperty = getPropertyFromAbbreviation(pValue);
				if (q.notProperty == null)
					throw new AnticipatedException("Unrecognised not property name " + pValue, 400);
			} else if (pName.equals("folderType")) { 
				q.folderClassURI = getValidClassURI(pValue);
				if (q.folderClassURI == null)
					throw new AnticipatedException("Unrecognised folder type " + pValue, 400);
			} /*
      else if (pName.equals("property")) {
        q.requestedProperty = getPropertyFromAbreviation(pValue);
        if (q.requestedProperty != null)
          q.requestedPropertyName = pValue;
        else 
          throw new AnticipatedException("Unrecognised property name " + pValue);
      } 
      else if (pName.equals("value"))
        // FIXME We should know the type, and so should be able to validate
        q.requestedPropertyValue = pValue;
			 */
			else if (pName.equals("orderBy")) {
				q.orderByProperty = getPropertyFromAbbreviation(pValue); 
				if (q.orderByProperty != null)
					q.orderBy = pValue;
				else 
					throw new AnticipatedException("Unrecognised orderBy property name " + pValue, 400);
			} else if (pName.equals("date")) {
				q.timeInstant = new TimeInstant(pValue);
			} else if (pName.equals("indirect")) {
				q.searchIndirect = pValue.equals("true");
			} else if (pName.equals("passive")) {
				q.searchPassive = pValue.equals("true");
			} else if (pName.equals("jsCallback")) {
				q.jsCallback = pValue;
			} else if (pName.equals("parentName")) {
				if (pValue.equalsIgnoreCase("false")) 
					q.displayParentName = false;
			} else if (pName.equals("query")) {
				q.sparqlQuery = pValue;
			} else if (pName.equals("version")) {
				// We want to ignore this one.
			} else if (pName.equals("jsonNesting")) {
				try {
					q.jsonDepth = Integer.parseInt(pValue);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException(e);
				}
			} else throw new AnticipatedException(
					"Unrecognised parameter " + pName + ":" + request.getParameter(pName), 400);
		}
		return q;
	}

	public String getFolderClassURI() {
		return folderClassURI;
	}
	public ReturnType getReturnType() {
		return returnType;
	}


	public Property getArcProperty() {
		return arcProperty;
	}
	public Property getRequestedProperty() {
		return requestedProperty;
	}
	/*
  public String getRequestedPropertyName() {
    return requestedPropertyName;
  }
	 */
	public String getRequestedPropertyValue() {
		return requestedPropertyValue;
	}
	public Property getOrderByProperty() {
		return orderByProperty;
	}
	public Property getNotProperty() {
		return notProperty;
	}


	public String getArc() {
		return arc;
	}
	public String getOrderBy() {
		return orderBy;
	}
	public boolean getDisplayParentName() {
		return displayParentName;
	}
	public int getJsonDepth() {
		return jsonDepth;
	}
	public String getFormat() {
		return format;
	}
	public String getJsCallback() {
		if (jsCallback == null && format.equals("js"))
			jsCallback ="oxpoints";
		return jsCallback;
	}

	public String getType() {
		return type;
	}

	public boolean getSearchIndirect() {
		return searchIndirect;
	}
	public boolean getSearchPassive() {
		return searchPassive;
	}


	public static boolean parseTemplate(String pathInfo, Query q) { 
		String[] tokens = getTokens(pathInfo);
		if (tokens == null) return false;
		switch (tokens.length) {
		case 0: 
			return false;
		case 1: 
			q.requestedProperty = getPropertyFromAbbreviation(tokens[0]);
			if (q.requestedProperty != null) {
				q.requestedPropertyName = tokens[0];
				q.returnType = ReturnType.PROPERTY_ANY;
				return true;
			} else if (isAnEntitySpec(tokens[0],q)) { 
				q.returnType = ReturnType.INDIVIDUAL;
				return true;          
			} else
				return false;
		case 2: 
			q.requestedProperty = getPropertyFromAbbreviation(tokens[0]);
			if (q.requestedProperty != null) {
				q.requestedPropertyName = tokens[0];
				if (isAnEntitySpec(tokens[1],q)) {
					q.returnType = ReturnType.PROPERTY_SUBJECT; // _ prop obj
					return true;
				} else
					return false;
			} else if (isAnEntitySpec(tokens[0],q)) {
				q.requestedProperty = getPropertyFromAbbreviation(tokens[1]);
				if (q.requestedProperty != null) {
					q.requestedPropertyName = tokens[1];
					q.returnType = ReturnType.PROPERTY_OBJECT; // subj prop _
					return true;          
				} else
					return false;
			} else { 
				return false;          
			}
		default: 
			throw new RuntimeException("Fell through case:" + tokens.length); 
		}
	}

	static boolean isAnEntitySpec(String it, Query q) { 
		if (it.startsWith("oucs:")) {
			q.needsCodeLookup = true;
			q.participantCoding = "hasOUCSCode";
			q.setParticipantCodes(it.substring(5));
			return true;
		} else if (it.startsWith("olis:")) {
			q.needsCodeLookup = true;
			q.participantCoding = "hasOLISCode";
			q.setParticipantCodes(it.substring(5));
			return true;
		} else if (it.startsWith("olis-aleph:")) {
			q.needsCodeLookup = true;
			q.participantCoding = "hasOLISAlephCode";
			try {
				q.setParticipantCodes(URLDecoder.decode(it.substring(11), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			return true;
		} else if (it.startsWith("obn:")) {
			q.needsCodeLookup = true;
			q.participantCoding = "hasOBNCode";
			q.setParticipantCodes(it.substring(4));
			return true;
		} else if (it.startsWith("measure:")) {
			q.needsCodeLookup = true;
			q.participantCoding = "measureIdentifier";
			q.setParticipantCodes(it.substring(8));
			return true;
		} else if (it.startsWith("finance:")) {
			q.needsCodeLookup = true;
			q.participantCoding = "hasFinanceCode";
			q.setParticipantCodes(it.substring(8));
			return true;
		} else if (it.startsWith("id:")) {
			q.requestedPropertyValue = it.substring(3); 
			q.setParticipantIDs(q.requestedPropertyValue);
			return true;
		} else if (it.matches("^[0-9]+$")) { 
			q.requestedPropertyValue = it;       
			q.setParticipantIDs(it);
			return true;
		} else {
			return false;
		}
	}

	public static 
	boolean startsWithPropertyName(String pathInfo) {
		return getPropertyName(pathInfo) != null;
	}
	public 
	Property getProperty(String pathInfo) { 
		String[] tokens = getTokens(pathInfo);
		return getPropertyFromAbbreviation(tokens[0]); 
	}
	private static 
	String getPropertyName(String pathInfo) {
		String[] tokens = getTokens(pathInfo);
		//System.err.println("Token:" + tokens[0]);
		if (getPropertyFromAbbreviation(tokens[0]) != null)
			return tokens[0];
		else 
			return null;
	}
	public static 
	String getPropertyValue(String pathInfo) {
		String[] tokens = getTokens(pathInfo);
		if (tokens.length == 1)
			return null;
		return tokens[1].replace('+', ' ');
	}

	private static  
	String[] getTokens(String pathInfo) { 
		if (pathInfo == null) 
			return null;
		if (pathInfo.equals("")) 
			return null;
		if (pathInfo.equals("/")) 
			return null;
		if (!pathInfo.startsWith("/")) 
			throw new IllegalArgumentException("Malformed pathInfo:" + pathInfo);

		String trimmedPathInfo = pathInfo.substring(1,pathInfo.length());
		if (trimmedPathInfo.length() == 0)
			return null;

		return trimmedPathInfo.split("/");
	}


	public static 
	Property getPropertyFromAbbreviation(String propertyAbbreviation) {
		if (propertyMapping.containsKey(propertyAbbreviation))
			return propertyMapping.get(propertyAbbreviation);
		else
			return null;
	}

	static String getValidClassURI(String className) { 
		for (String prefix : namespacePrefixes.keySet()) {
			String key = namespacePrefixes.get(prefix) + className;
			if (isValidClass(key))
				return key; 
		}
		return null;    
	}
	static boolean isValidClass(String className) { 
		if (OxPointsVocab.MODEL.getOntClass(className) != null)
			return true;
		if (VCardVocab.MODEL.getOntClass(className) != null)
			return true;
		if (GabotoVocab.MODEL.getOntClass(className) != null)
			return true;
		if (GabotoKMLVocab.MODEL.getOntClass(className) != null)
			return true;
		if (GeoVocab.MODEL.getOntClass(className) != null)
			return true;
		if (DCVocab.MODEL.getOntClass(className) != null)
			return true;
		if (RDFContext.MODEL.getOntClass(className) != null)
			return true;
		if (RDFGraph.MODEL.getOntClass(className) != null)
			return true;
		if (TimeVocab.MODEL.getOntClass(className) != null)
			return true;
		return false;
	}

	public String getParticipantCoding() {
		return participantCoding;
	}

	public Collection<String> getParticipantCodes() {
		return participantCodes;
	}

	public boolean needsCodeLookup() {
		return needsCodeLookup;
	}

	public void setParticipantURIs(Collection<String> uris) {
		this.participantURIs = uris;
	}

	public Collection<String> getParticipantURIs() {
		return participantURIs;
	}

	public String getSparqlQuery() {
		return sparqlQuery;
	}

	/**
	 * @param participantIDs a comma-separated list of IDs
	 */
	void setParticipantIDs(String participantIDs) {
		setParticipantIDs(Arrays.asList(participantIDs.split(",")));
	}
	void setParticipantIDs(Collection<String> participantIDs) {
		Set<String> uris = new HashSet<String>();
		for (String participantID : participantIDs) {
			if (!participantID.matches("^\\d{8}$"))
				throw new AnticipatedException("Invalid id " + participantID, 400);
			uris.add(ENTITY_PREFIX + participantID);
		}
		this.participantIDs = participantIDs;
		setParticipantURIs(uris);
	}
	void setParticipantCodes(String participantCodes) {
		setParticipantCodes(Arrays.asList(participantCodes.split(",")));
	}
	void setParticipantCodes(Collection<String> participantCodes) {
		this.participantCodes = participantCodes;
	}

	public TimeInstant getTimeInstant() {
		return timeInstant;
	}

}

