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

import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.oucs.gaboto.vocabulary.DC;
import org.oucs.gaboto.vocabulary.GabotoKML;
import org.oucs.gaboto.vocabulary.GabotoVocab;
import org.oucs.gaboto.vocabulary.GeoVocab;
import org.oucs.gaboto.vocabulary.OxPointsVocab;
import org.oucs.gaboto.vocabulary.RDFCON;
import org.oucs.gaboto.vocabulary.RDFG;
import org.oucs.gaboto.vocabulary.TimeVocab;
import org.oucs.gaboto.vocabulary.VCard;

import com.hp.hpl.jena.rdf.model.Property;

public class Query {
  
  private String arc = null;
  private String orderBy = null;
  private String folderClassName = null;
  private String requestedPropertyName = null;
  private String requestedPropertyValue = null;
  private boolean displayParentName = true;
  private int jsonDepth = 1;
  private String format = null;
  // See http://bob.pythonmac.org/archives/2005/12/05/remote-json-jsonp/
  private String jsCallback = null;
  
  private Property orderByProperty;
  private Property arcProperty;
  private Property requestedProperty;
  
  private String resultsetSpec;
  private ReturnType returnType = ReturnType.ALL;
  private String folderClassURI = OxPointsVocab.NS + "College";
  
  public enum ReturnType {
    META_TIMESTAMP, META_TYPES, ALL, TYPE_COLLECTION, COLLECTION, INDIVIDUAL 
  } 
  
  private static Map<String, String> namespacePrefixes = new TreeMap<String, String>();
  private static String id;
  private static String uri;
  private static String type;
  {
    // HACK the ordering so that oxpoints#subsetOf is prioritised over DC#subsetOf
    namespacePrefixes.put("1oxp:",   OxPointsVocab.NS);
    namespacePrefixes.put("2dc:",    DC.NS);
    namespacePrefixes.put("3vCard:", VCard.NS);
    namespacePrefixes.put("4geo:",   GeoVocab.NS);
  }

  private Query() {}
  
  /**
   * @param request
   */
  @SuppressWarnings("unchecked")
  public static  Query fromRequest(HttpServletRequest request) {
    Query q = new Query();
    q.format = "xml";

    q.arc = null;
    q.orderBy = null;
    q.folderClassName = null;
    q.requestedPropertyName = null;
    q.requestedPropertyValue = null;
    q.displayParentName = true;
    q.jsonDepth = 1;

    String pathInfo = request.getPathInfo();
    if (pathInfo == null) 
      throw new AnticipatedException("Expected path info");
    int dotPosition = pathInfo.lastIndexOf('.');
    System.err.println(pathInfo);
    if (dotPosition == -1) {
      q.setResultsetSpec(pathInfo);
    } else {
      q.format = pathInfo.substring(dotPosition + 1);
      q.setResultsetSpec(pathInfo.substring(0,dotPosition));
    }    
    System.err.println(q.resultsetSpec);
    if (q.resultsetSpec.startsWith("/timestamp")) {
      q.returnType = ReturnType.META_TIMESTAMP;
    } else if (q.resultsetSpec.startsWith("/types") || q.resultsetSpec.startsWith("/classess")) {
      q.returnType = ReturnType.META_TYPES;
    } else if (q.resultsetSpec.startsWith("/all")) {
      q.returnType = ReturnType.ALL;
    } else if (q.resultsetSpec.startsWith("/id/")) {
      id = q.resultsetSpec.substring(4);
      uri = "http://m.ox.ac.uk/oxpoints/id/" + id;
      
      q.returnType = ReturnType.INDIVIDUAL;
    } else if (q.resultsetSpec.startsWith("/type/") || q.resultsetSpec.startsWith("/class/")) {  
      type = q.resultsetSpec.substring(6);
      q.returnType = ReturnType.TYPE_COLLECTION;
    } else if (startsWithPropertyName(q.resultsetSpec)) {
      q.requestedPropertyName = getPropertyName(q.resultsetSpec); 
      q.requestedPropertyValue = getPropertyValue(q.resultsetSpec); 
      q.requestedProperty = getPropertyFromAbreviation(q.requestedPropertyName);
      q.returnType = ReturnType.COLLECTION;
    } else
      throw new AnticipatedException("Unexpected path info " + pathInfo);
    
    Enumeration<String> en = request.getParameterNames();
    while (en.hasMoreElements()) {
      String pName = en.nextElement();
      String pValue =  request.getParameter(pName);

      System.err.println("Param:" + pName + "=" + pValue);
      if (pName.equals("arc")) {
        q.arcProperty = getPropertyFromAbreviation(pValue);
        if (q.arcProperty != null)
          q.arc = pValue;
        else 
          throw new AnticipatedException("Unrecognised arc property name " + pValue);
      } else if (pName.equals("folderType")) { 
        q.folderClassURI = getValidClassURI(pValue);
        if (q.folderClassURI != null) {
          q.folderClassName = pValue;
        } else 
          throw new AnticipatedException("Unrecognised folder type " + pValue);
      } else if (pName.equals("property")) {
        q.requestedProperty = getPropertyFromAbreviation(pValue);
        if (q.requestedProperty != null)
          q.requestedPropertyName = pValue;
        else 
          throw new AnticipatedException("Unrecognised property name " + pValue);
      } else if (pName.equals("value"))
        // FIXME We should know the type, and so should be able to validate
        q.requestedPropertyValue = pValue;
      else if (pName.equals("orderBy")) {
        q.orderByProperty = getPropertyFromAbreviation(pValue); 
        if (q.orderByProperty != null)
          q.orderBy = pValue;
        else 
          throw new AnticipatedException("Unrecognised orderBy property name " + pValue);
      }
      else if (pName.equals("jsCallback"))
        q.jsCallback = pValue;
      else if (pName.equals("parentName")) {
        if (pValue.equalsIgnoreCase("false")) 
          q.displayParentName = false;
      }
      else if (pName.equals("jsonNesting")) {
        if (pValue != null) {
          try {
            q.jsonDepth = Integer.parseInt(pValue);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
          }
        }
      } else throw new AnticipatedException(
          "Unrecognised parameter " + pName + ":" + request.getParameter(pName));
    }
    return q;
  }

  /**
   * @return the folderClassURI
   */
  public String getFolderClassURI() {
    return folderClassURI;
  }

  /**
   * @return the requestedPropertyName
   */
  public String getRequestedPropertyName() {
    return requestedPropertyName;
  }

  /**
   * @return the requestedPropertyValue
   */
  public String getRequestedPropertyValue() {
    return requestedPropertyValue;
  }

  public ReturnType getReturnType() {
    return returnType;
  }
  
  
  /**
   * @return the arcProperty
   */
  public Property getArcProperty() {
    return arcProperty;
  }

  /**
   * @return the requestedProperty
   */
  public Property getRequestedProperty() {
    return requestedProperty;
  }

  
  /**
   * @return the arc
   */
  public String getArc() {
    return arc;
  }

  /**
   * @return the orderBy
   */
  public String getOrderBy() {
    return orderBy;
  }
  /**
   * @return the folder class name
   */
  public String getFolderClassName() {
    return folderClassName;
  }
  /**
   * @return the propertyName
   */
  public String getPropertyName() {
    return requestedPropertyName;
  }
  /**
   * @return the displayParentName
   */
  public boolean getDisplayParentName() {
    return displayParentName;
  }
  /**
   * @return the jsonDepth
   */
  public int getJsonDepth() {
    return jsonDepth;
  }
  /**
   * @return the format
   */
  public String getFormat() {
    return format;
  }
  /**
   * @return the jsCallback
   */
  public String getJsCallback() {
    if (jsCallback == null && format.equals("js"))
      jsCallback ="oxpoints";
    return jsCallback;
  }

  /**
   * @return the orderByURI
   */
  public Property getOrderByProperty() {
    return orderByProperty;
  }
    
  
  public static 
  boolean startsWithPropertyName(String pathInfo) {
    return getPropertyName(pathInfo) != null;
  }
  public Property getProperty(String pathInfo) { 
    String[] tokens = getTokens(pathInfo);
    return getPropertyFromAbreviation(tokens[0]); 
  }
  private static 
  String getPropertyName(String pathInfo) {
    String[] tokens = getTokens(pathInfo);
    System.err.println("Token:" + tokens[0]);
    if (getPropertyFromAbreviation(tokens[0]) != null)
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
  
  static private 
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


  private static Property getPropertyFromAbreviation(String propertyAbreviation) {
    for (String prefix : namespacePrefixes.keySet()) {
      System.err.println("Que:"+prefix);
      String key = namespacePrefixes.get(prefix) + propertyAbreviation;
      Property p = getPropertyNamed(key);
      System.err.println("Que:"+key + "=" + p);
      if (p != null)
        return p; 
    }
    return null;
  }

  static String getValidClassURI(String className) { 
    for (String prefix : namespacePrefixes.keySet()) {
      System.err.println("Que:"+prefix);
      String key = namespacePrefixes.get(prefix) + className;
      if (isValidClass(key))
        return key; 
    }
    return null;    
  }
  static boolean isValidClass(String className) { 
    if (OxPointsVocab.MODEL.getOntClass(className) != null)
      return true;
    if (VCard.MODEL.getOntClass(className) != null)
      return true;
    if (GabotoVocab.MODEL.getOntClass(className) != null)
      return true;
    if (GabotoKML.MODEL.getOntClass(className) != null)
      return true;
    if (GeoVocab.MODEL.getOntClass(className) != null)
      return true;
    if (DC.MODEL.getOntClass(className) != null)
      return true;
    if (RDFCON.MODEL.getOntClass(className) != null)
      return true;
    if (RDFG.MODEL.getOntClass(className) != null)
      return true;
    if (TimeVocab.MODEL.getOntClass(className) != null)
      return true;
    return false;
  }
  static Property getPropertyNamed(String pName) { 
    if (OxPointsVocab.MODEL.getObjectProperty(pName) != null)
      return OxPointsVocab.MODEL.getObjectProperty(pName);
    if (VCard.MODEL.getObjectProperty(pName) != null)
      return VCard.MODEL.getObjectProperty(pName);    
    if (GabotoVocab.MODEL.getObjectProperty(pName) != null)
      return GabotoVocab.MODEL.getObjectProperty(pName);
    if (GabotoKML.MODEL.getObjectProperty(pName) != null)
      return GabotoKML.MODEL.getObjectProperty(pName);    
    if (GeoVocab.MODEL.getObjectProperty(pName) != null)
      return GeoVocab.MODEL.getObjectProperty(pName);    
    if (DC.MODEL.getObjectProperty(pName) != null)
      return DC.MODEL.getObjectProperty(pName);    
    if (RDFCON.MODEL.getObjectProperty(pName) != null)
      return RDFCON.MODEL.getObjectProperty(pName);    
    if (RDFG.MODEL.getObjectProperty(pName) != null)
      return RDFG.MODEL.getObjectProperty(pName);    
    if (TimeVocab.MODEL.getObjectProperty(pName) != null)
      return TimeVocab.MODEL.getObjectProperty(pName);    

    if (OxPointsVocab.MODEL.getAnnotationProperty(pName) != null)
      return OxPointsVocab.MODEL.getAnnotationProperty(pName);
    if (VCard.MODEL.getAnnotationProperty(pName) != null)
      return VCard.MODEL.getAnnotationProperty(pName);    
    if (GabotoVocab.MODEL.getAnnotationProperty(pName) != null)
      return GabotoVocab.MODEL.getAnnotationProperty(pName);
    if (GabotoKML.MODEL.getAnnotationProperty(pName) != null)
      return GabotoKML.MODEL.getAnnotationProperty(pName);    
    if (GeoVocab.MODEL.getAnnotationProperty(pName) != null)
      return GeoVocab.MODEL.getAnnotationProperty(pName);    
    if (DC.MODEL.getAnnotationProperty(pName) != null)
      return DC.MODEL.getAnnotationProperty(pName);    
    if (RDFCON.MODEL.getAnnotationProperty(pName) != null)
      return RDFCON.MODEL.getAnnotationProperty(pName);    
    if (RDFG.MODEL.getAnnotationProperty(pName) != null)
      return RDFG.MODEL.getAnnotationProperty(pName);    
    if (TimeVocab.MODEL.getAnnotationProperty(pName) != null)
      return TimeVocab.MODEL.getAnnotationProperty(pName);    
    return null;
  }
  static boolean isValidPropertyName(String pName) { 
    return getPropertyNamed(pName) != null;
 }

  public void setResultsetSpec(String resultsetSpec) {
    this.resultsetSpec = resultsetSpec;
  }

  public String getResultsetSpec() {
    return resultsetSpec;
  }

  public String getUri() {
    return uri;
  }

  public String getType() {
    return type;
  }

  
}