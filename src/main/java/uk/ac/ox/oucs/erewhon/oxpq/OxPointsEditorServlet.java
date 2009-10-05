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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.gaboto.time.TimeInstant;


public class OxPointsEditorServlet extends OxPointsServlet  {

  private static final long serialVersionUID = 3903309895476205922L;

  

  public void init() {
    super.init();
  }




  /**
   * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    System.err.println("doPost");
    
    //Gaboto t = GabotoFactory.getEmptyInMemoryGaboto();
    System.err.println("Entities:" + gaboto.getJenaModelViewOnNamedGraphSet().size());
    String id = gaboto.generateIdUri(); 
    System.err.println("We have " + gaboto.getJenaModelViewOnNamedGraphSet().size() +  " known entities before read");
    BufferedReader is = new BufferedReader(new InputStreamReader(request.getInputStream()));
    String line;
    String lines = "";
    while ((line = is.readLine()) != null) {
      lines += line;
      lines += " ";
    }
    System.err.println("Lines:" + lines + ":");
    System.err.println("We have " + snapshot.size() + " entities in snapshot before read");
    gaboto.read(lines);
    System.err.println("We have " + gaboto.getJenaModelViewOnNamedGraphSet().size() +  " entities in gaboto after read");    
    System.err.println("We have " + snapshot.size() + " entities in snapshot after read");
    
    gaboto.recreateTimeDimensionIndex();
    System.err.println("We have " + snapshot.size() + " entities in snapshot after index recreation");
    snapshot = gaboto.getSnapshot(TimeInstant.from(startTime));
    System.err.println("We have " + snapshot.size() + " entities in snapshot after refresh");
    System.err.println("We have " + gaboto.getJenaModelViewOnNamedGraphSet().size() +  " entities in gaboto after read");
    //for (Statement s in t.getSnapshot(t.getConfig().getContextDependantGraphURI()).getModel().listStatements() )
    response.setStatus(201);
    String successUrl = servletURL(request, "OxPQ") + "/id/44443610";// + gaboto.getCurrentHighestId();
    System.err.println("servletURL:" + successUrl);
    response.setHeader("Location", successUrl);
  }



  /**
   * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
   */
  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doPost(req, resp);
  }
  
}
