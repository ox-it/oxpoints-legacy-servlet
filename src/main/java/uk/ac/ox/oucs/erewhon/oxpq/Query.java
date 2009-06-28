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

import javax.servlet.http.HttpServletRequest;

import com.hp.hpl.jena.rdf.model.Property;

class Query {
  
  private static Property orderByProperty;
  String arc = null;
  String orderBy = null;
  String folderType = null;
  String propertyName = null;
  String propertyValue = null;
  boolean displayParentName = true;
  int jsonDepth = 1;
  String format = null;
  // See http://bob.pythonmac.org/archives/2005/12/05/remote-json-jsonp/
  String jsCallback = null;
  
  
  /**
   * @param request
   */
  @SuppressWarnings("unchecked")
  public static  Query fromRequest(HttpServletRequest request) {
    Query q = new Query();
    q.format = "xml";

    q.arc = null;
    q.orderBy = null;
    q.folderType = null;
    q.propertyName = null;
    q.propertyValue = null;
    q.displayParentName = true;
    q.jsonDepth = 1;

    Enumeration<String> en = request.getParameterNames();
    while (en.hasMoreElements()) {
      String pName = en.nextElement();
      String pValue =  request.getParameter(pName);

      System.err.println("Param:" + pName + "=" + pValue);
      if (pName.equals("arc"))
        q.arc = pValue;
      else if (pName.equals("folderType"))
        q.folderType = pValue;
      else if (pName.equals("property"))
        q.propertyName = pValue;
      else if (pName.equals("value"))
        q.propertyValue = pValue;
      else if (pName.equals("orderBy")) {
        orderByProperty = OxPointsQueryServlet.getPropertyFromAbreviation(pValue); 
        if (orderByProperty != null)
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
   * @return the arc
   */
  String getArc() {
    return arc;
  }
  /**
   * @param arc the arc to set
   */
  void setArc(String arc) {
    this.arc = arc;
  }
  /**
   * @return the orderBy
   */
  String getOrderBy() {
    return orderBy;
  }
  /**
   * @param orderBy the orderBy to set
   */
  void setOrderBy(String orderBy) {
    this.orderBy = orderBy;
  }
  /**
   * @return the folderType
   */
  String getFolderType() {
    return folderType;
  }
  /**
   * @param folderType the folderType to set
   */
  void setFolderType(String folderType) {
    this.folderType = folderType;
  }
  /**
   * @return the propertyName
   */
  String getPropertyName() {
    return propertyName;
  }
  /**
   * @param propertyName the propertyName to set
   */
  void setPropertyName(String propertyName) {
    this.propertyName = propertyName;
  }
  /**
   * @return the propertyValue
   */
  String getPropertyValue() {
    return propertyValue;
  }
  /**
   * @param propertyValue the propertyValue to set
   */
  void setPropertyValue(String propertyValue) {
    this.propertyValue = propertyValue;
  }
  /**
   * @return the displayParentName
   */
  boolean getDisplayParentName() {
    return displayParentName;
  }
  /**
   * @param displayParentName the displayParentName to set
   */
  void setDisplayParentName(boolean displayParentName) {
    this.displayParentName = displayParentName;
  }
  /**
   * @return the jsonDepth
   */
  int getJsonDepth() {
    return jsonDepth;
  }
  /**
   * @param jsonDepth the jsonDepth to set
   */
  void setJsonDepth(int jsonDepth) {
    this.jsonDepth = jsonDepth;
  }
  /**
   * @return the format
   */
  String getFormat() {
    return format;
  }
  /**
   * @param format the format to set
   */
  void setFormat(String format) {
    this.format = format;
  }
  /**
   * @return the jsCallback
   */
  String getJsCallback() {
    return jsCallback;
  }
  /**
   * @param jsCallback the jsCallback to set
   */
  void setJsCallback(String jsCallback) {
    this.jsCallback = jsCallback;
  }

  /**
   * @return the orderByURI
   */
  Property getOrderByProperty() {
    return orderByProperty;
  }
   
  
}