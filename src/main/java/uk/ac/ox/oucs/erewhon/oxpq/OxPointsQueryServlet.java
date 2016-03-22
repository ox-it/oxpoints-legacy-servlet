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
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringEscapeUtils;

import uk.ac.ox.oucs.oxpoints.gaboto.entities.Organization;
import uk.ac.ox.oucs.oxpoints.gaboto.entities.OxpEntity;
import uk.ac.ox.oucs.oxpoints.gaboto.entities.Place;

import net.sf.gaboto.EntityDoesNotExistException;
import net.sf.gaboto.GabotoFactory;
import net.sf.gaboto.GabotoSnapshot;
import net.sf.gaboto.ResourceDoesNotExistException;
import net.sf.gaboto.node.GabotoEntity;
import net.sf.gaboto.node.pool.EntityPool;
import net.sf.gaboto.node.pool.EntityPoolConfiguration;
import net.sf.gaboto.query.GabotoQuery;
import net.sf.gaboto.query.UnsupportedQueryFormatException;
import net.sf.gaboto.time.TimeInstant;
import net.sf.gaboto.transformation.EntityPoolTransformer;
import net.sf.gaboto.transformation.GeoJSONPoolTransfomer;
import net.sf.gaboto.transformation.JSONPoolTransformer;
import net.sf.gaboto.transformation.KMLPoolTransformer;
import net.sf.gaboto.transformation.RDFPoolTransformerFactory;

import com.hp.hpl.jena.query.QueryParseException;
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

		response.setCharacterEncoding("UTF-8");
		try {
			outputPool(request, response);
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
		if (query.getTimeInstant() == null)
			snapshot = GabotoFactory.getSnapshot(dataDirectory, TimeInstant.from(startTime));
		else 
			snapshot = GabotoFactory.getSnapshot(dataDirectory, query.getTimeInstant());
		//System.err.println("Snapshot " + snapshot + " contains " + snapshot.size() + " entities ");


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
			EntityPool pool = new EntityPool(gaboto, snapshot);
			establishParticipantURIs(query);
			if (snapshot != null)
				System.err.println("We have " + snapshot.size() + " entities in snapshot before loadEntity");
			for (String participantURI : query.getParticipantURIs())
				pool.addEntity(snapshot.loadEntity(participantURI));
			output(pool, query, response);
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
				establishParticipantURIs(query);

				Set<GabotoEntity> objects = new HashSet<GabotoEntity>();
				EntityPool creationPool = EntityPool.createFrom(snapshot);
				for (String uri : query.getParticipantURIs()) {
					GabotoEntity object = snapshot.loadEntity(uri);
					object.setCreatedFromPool(creationPool);
					objects.add(object);
				}
				subjectPool = loadPoolWithActiveParticipants(objects, query.getRequestedProperty()); 
			} else { 
				subjectPool = loadPoolWithEntitiesOfProperty(query.getRequestedProperty(), query.getRequestedPropertyValue());         
			}
			output(subjectPool, query, response);
			return;
		case PROPERTY_OBJECT: 
			establishParticipantURIs(query);
			Set<GabotoEntity> subjects = new HashSet<GabotoEntity>();
			for (String uri : query.getParticipantURIs()) {
				GabotoEntity object = snapshot.loadEntity(uri);
				subjects.add(object);
			}

			EntityPool objectPool = loadPoolWithPassiveParticipants(subjects, query.getRequestedProperty());
			output(objectPool, query, response);
			return;
		case NOT_FILTERED_TYPE_COLLECTION:
			EntityPool p = loadPoolWithEntitiesOfType(query.getType());
			EntityPool p2 = loadPoolWithEntitiesOfType(query.getType());
			for (GabotoEntity e : p.getEntities()) 
				if (e.getPropertyValue(query.getNotProperty(), query.getSearchPassive(), query.getSearchIndirect()) != null)
					p2.removeEntity(e);

			output(p2, query,
					response);
			return;
		case SPARQL_QUERY:
			PrintWriter writer;
			try {
				writer = response.getWriter();
			} catch (IOException e1) {
				throw new RuntimeException("Couldn't write to stream");
			}
			
			if (query.getFormat().equals("html") || query.getSparqlQuery() == null) {
				writer.println("<?xml version=\"1.0\"?>");
				writer.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
				writer.println("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">\n  <head>");
				writer.println("    <title>OxPoints SPARQL Endpoint</title>\n  </head>");
				writer.println("  <body>\n    <h1>OxPoints SPARQL Endpoint</h1>");
				if (query.getSparqlQuery() != null) {
					writer.println("    <h2>Results</h2>");
					writer.print("    <div class=\"results\" style=\"border:1px solid #888; padding:5px; font-size:10pt; max-height:400px; overflow:auto;\"><pre style=\"margin:0;\">");
					try {
						writer.write(StringEscapeUtils.escapeHtml(SPARQLQueryResultProcessor.performQuery(snapshot.getModel(), query.getSparqlQuery())));
					} catch (QueryParseException e) {
						writer.write("ERROR: " + e.getMessage());
						response.setStatus(400);
					}
					writer.println("</pre></div>");
				}
				writer.println("    <h2>Query</h2>");
				String sparqlQuery = (query.getSparqlQuery() != null) ? query.getSparqlQuery() :
					"PREFIX oxp: <http://ns.ox.ac.uk/namespace/oxpoints/2009/02/owl#>\n"
				  + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n"
				  + "PREFIX dc: <http://purl.org/dc/elements/1.1/>\n\n"
				  + "SELECT ?title ?homepage WHERE {\n"
				  + "    ?college a oxp:College ;\n"
				  + "             dc:title ?title ;\n"
				  + "             foaf:homepage ?homepage\n"
				  + "} LIMIT 10\n";
				writer.println("    <form method=\"GET\" action=\"sparql.html\">");
				writer.println("      <textarea name=\"query\" rows=\"10\" cols=\"80\">"+StringEscapeUtils.escapeHtml(sparqlQuery)+"</textarea>");
				writer.println("      <p><input type=\"submit\"/></p>");
				writer.println("    </form>\n  </body>\n</html>");
				response.setContentType("application/xhtml+xml");
				
			} else
				outputSparqlResults(response, writer, snapshot, query.getSparqlQuery());

				
			return;
		default:
			throw new RuntimeException("Fell through case with value " + query.getReturnType());
		}

	}

	private void outputSparqlResults(HttpServletResponse response, PrintWriter writer, GabotoSnapshot snapshot, String sparqlQuery) {
		try {
			writer.write(SPARQLQueryResultProcessor.performQuery(snapshot.getModel(), sparqlQuery));
			if (response != null)
				response.setContentType("text/xml");
		} catch (QueryParseException e) {
			writer.write(e.getMessage());
			if (response != null) {
				response.setStatus(400);
				response.setContentType("text/plain");
			}
		}
	}

	private EntityPool loadPoolWithEntitiesOfProperty(Property prop, String value) {
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
					pool = becomeOrAdd(pool, snapshot.loadEntitiesWithProperty(prop, r));
				} else {
					pool = becomeOrAdd(pool, snapshot.loadEntitiesWithProperty(prop, v));
				}
			}
		}
		return pool;
	}

	private EntityPool loadPoolWithActiveParticipants(GabotoEntity passiveParticipant, Property prop) {
		Set<GabotoEntity> passiveParticipants = new HashSet<GabotoEntity>();
		passiveParticipants.add(passiveParticipant);
		return loadPoolWithActiveParticipants(passiveParticipants, prop);
	}

	@SuppressWarnings("unchecked")
	private EntityPool loadPoolWithActiveParticipants(Collection<GabotoEntity> passiveParticipants, Property prop) { 
		if (prop == null)
			throw new NullPointerException();
		EntityPool pool = new EntityPool(gaboto, snapshot);
		//System.err.println("loadPoolWithActiveParticipants" + passiveParticipant.getUri() + "  prop " + prop + " which ");
		for (GabotoEntity passiveParticipant : passiveParticipants) {
		Set<Entry<String, Object>> passiveProperties = passiveParticipant.getAllPassiveProperties().entrySet(); 
		for (Entry<String, Object> entry : passiveProperties) {
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
		}
		return pool;
	}

	private void establishParticipantURIs(Query query) throws ResourceNotFoundException { 
		if (query.needsCodeLookup()) {
			Property coding = Query.getPropertyFromAbbreviation(query.getParticipantCoding());
			Collection<String> uris = new HashSet<String>();

			for (String participantCode : query.getParticipantCodes()) {
				EntityPool objectPool = snapshot.loadEntitiesWithProperty(coding, participantCode);
				boolean found = false;
				for (GabotoEntity objectKey: objectPool) { 
					if (found)
						throw new RuntimeException("Found two:" + objectKey);
					uris.add(objectKey.getUri());
					found = true;
				}
			}
			query.setParticipantURIs(uris);
		}
		if (query.getParticipantURIs() == null || query.getParticipantURIs().size() == 0)
			throw new ResourceNotFoundException("No resource found with coding " + query.getParticipantCoding() + 
					" and value " + query.getParticipantCodes().toString());
	}
	private EntityPool loadPoolWithPassiveParticipants(GabotoEntity activeParticipant, Property prop) {
		Set<GabotoEntity> activeParticipants = new HashSet<GabotoEntity>();
		activeParticipants.add(activeParticipant);
		return loadPoolWithPassiveParticipants(activeParticipants, prop);
	}
	@SuppressWarnings("unchecked")
	private EntityPool loadPoolWithPassiveParticipants(Collection<GabotoEntity> activeParticipants, Property prop) { 
		if (prop == null)
			throw new NullPointerException();
		EntityPool pool = new EntityPool(gaboto, snapshot);
		
		for (GabotoEntity activeParticipant : activeParticipants) {
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
		}
		return pool;
	}

	private EntityPool becomeOrAdd(EntityPool pool, EntityPool poolToAdd) {
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
		if (property.getLocalName().endsWith("isPartOf")) {
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
		String types[] = type.split("[|]");

		EntityPoolConfiguration conf = new EntityPoolConfiguration(snapshot);
		for (String t : types) {
			if (!snapshot.getGaboto().getConfig().getGabotoOntologyLookup().isValidName(t))
				throw new IllegalArgumentException("Found no URI matching type " + t);
			String typeURI = snapshot.getGaboto().getConfig().getGabotoOntologyLookup().getURIForName(t);
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
		//System.err.println("Pool has " + pool.getSize() + " elements");

		String output = "", format = query.getFormat();
		if (query.getFormat().equals("kml")) {
			output = createKml(pool, query);
			response.setContentType("application/vnd.google-earth.kml+xml");
		} else if (query.getFormat().equals("json") || query.getFormat().equals("js")) {
			response.setContentType(query.getJsCallback() != null ? "text/javascript" : "application/json");

			JSONPoolTransformer transformer = new JSONPoolTransformer();
			transformer.setNesting(query.getJsonDepth());

			output = transformer.transform(pool);
			if (query.getFormat().equals("js")) {
				output = query.getJsCallback() + "(" + output + ");";
			}
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

		} else if (query.getFormat().equals("autosuggest")) {
			response.setContentType(query.getJsCallback() != null ? "text/javascript" : "application/json");
			Map<String,List<OxpEntity>> places = new HashMap<String,List<OxpEntity>>();
			Set<OxpEntity> entities = new HashSet<OxpEntity>();
			for (GabotoEntity entity : pool.getEntities()) {
				String name = ((OxpEntity) entity).getName();
				if (name == null)
					continue;
				if (!places.containsKey(name))
					places.put(name, new LinkedList<OxpEntity>());
				places.get(name).add((OxpEntity) entity);
			}
			for (List<OxpEntity> entitiesByName : places.values()) {
				Set<OxpEntity> toRemove = new HashSet<OxpEntity>();
				for (int i=0; i < entitiesByName.size(); i++) {
					if (!(entitiesByName.get(i) instanceof Organization))
						continue;
				    Organization org = (Organization) entitiesByName.get(i);
					for (int j=0; j < entitiesByName.size(); j++) { 
						if (!(entitiesByName.get(j) instanceof Place))
							continue;
						Place place = (Place) entitiesByName.get(j);
						if (org.getOccupiedPlaces() != null && org.getOccupiedPlaces().contains(place))
							toRemove.add(org);
					}
				}
				for (OxpEntity entity : entitiesByName)
					if (!toRemove.contains(entity))
						entities.add(entity);
			}
			
			
			PrintWriter writer;
			boolean first = true;
			try {
				writer = response.getWriter();
			} catch (IOException e) {
				throw new RuntimeException("Couldn't write to output stream.");
			}
			if (query.getJsCallback() != null)
				writer.print(query.getJsCallback()+'(');
			writer.print("{\"items\": [");
			
			for (OxpEntity entity : entities) {
				if (!first) {
					writer.print(",");
				} else
					first = false;
				writer.print("\n  {\"id\": \"");
				writer.print(entity.getUri().substring(entity.getUri().lastIndexOf("/")+1));
				writer.print("\", \"name\": \"");
				String name;
				if (entity instanceof Place)
					name = ((Place) entity).getFullyQualifiedTitle();
				else
					name = entity.getName();
				writer.print(StringEscapeUtils.escapeJava(name)+"\"");
				
				List<String> altLabels = new LinkedList<String>();
				if (entity.getAltLabels() != null) altLabels.addAll(entity.getAltLabels());
				if (entity.getHiddenLabels() != null) altLabels.addAll(entity.getHiddenLabels());
				if (altLabels.size() > 0) {
					String labels = altLabels.get(0);
					for (int i=1; i<altLabels.size(); i++)
						labels += "\t" + altLabels.get(i);
					writer.print(", \"altNames\": \"");
					writer.print(StringEscapeUtils.escapeJava(labels));
					writer.print("\"");
				}
				writer.print("}");
				
			}
			if (query.getJsCallback() != null)
				writer.println("\n]});");
			else
				writer.println("\n]}");
			response.setContentType("text/javascript");
			
			

		} else if (format.equals("xml") || format.equals("n3") || format.equals("ttl") || format.equals("nt")) {
			String outputFormat, contentType;
			if (format.equals("xml")) {
				outputFormat = GabotoQuery.FORMAT_RDF_XML_ABBREV;
				contentType = "application/rdf+xml";
			} else if (format.equals("ttl")) {
				outputFormat = GabotoQuery.FORMAT_RDF_TURTLE;
				contentType = "text/turtle";
			} else if (format.equals("nt")) {
				outputFormat = GabotoQuery.FORMAT_RDF_N_TRIPLE;
				contentType = "text/plain";
			} else {
				outputFormat = GabotoQuery.FORMAT_RDF_N3;
				contentType = "text/n3";
			}

			EntityPoolTransformer transformer;
			try {
				transformer = RDFPoolTransformerFactory.getRDFPoolTransformer(outputFormat);
				output = transformer.transform(pool);
			} catch (UnsupportedQueryFormatException e) {
				throw new IllegalArgumentException(e);
			}
			response.setContentType(contentType);

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
		//System.err.println("GPSBabel command:" + command);
		Process process;
		try {
			process = Runtime.getRuntime().exec(command, null, null);
		} catch (IOException e) {
			throw new AnticipatedException("Not able to use GPSBabel to produce the desired output.", 501);
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
		//System.err.println("GPSBabel command:" + command);
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
