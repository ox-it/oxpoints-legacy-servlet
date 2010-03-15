package uk.ac.ox.oucs.erewhon.oxpq;

import java.util.Iterator;

import net.sf.gaboto.SPARQLQuerySolutionProcessor;

import org.apache.commons.lang.StringEscapeUtils;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;

class SPARQLQueryResultProcessor implements SPARQLQuerySolutionProcessor {
	boolean first = true;
	private String output = "";

	@Override
	public void processSolution(QuerySolution solution) {
		Iterator<String> it;
		
		if (first) {
			output += "<?xml version=\"1.0\"?>\n";
			output += "<sparql xmlns=\"http://www.w3.org/2005/sparql-results#\">\n";
			output += "  <head>\n";
			it = solution.varNames();
			while (it.hasNext())
				output += "    <variable name=\"" + StringEscapeUtils.escapeXml(it.next()) + "\"/>\n";
			output += "  </head>\n";
			output += "  <results>\n";
			first = false;
		}
		
		output += "    <result>\n";
		it = solution.varNames();
		while (it.hasNext()) {
			String varName = it.next();
			RDFNode node = solution.get(varName);
			output += "      <binding name=\""+StringEscapeUtils.escapeXml(varName)+"\">\n";
			if (node.isURIResource())
				output += "        <uri>"+StringEscapeUtils.escapeXml(node.toString())+"</uri>\n";
			else if (node.isLiteral()) {
				Literal literal = (Literal) node;
				output += "        <literal";
				if (!literal.getLanguage().equals(""))
					output += " xml:lang=\""+StringEscapeUtils.escapeXml(literal.getLanguage())+"\"";
				if (!literal.getDatatype().equals(""))
					output += " datatype=\""+StringEscapeUtils.escapeXml(literal.getDatatypeURI())+"\"";
				output += ">"+StringEscapeUtils.escapeXml(literal.getString())+"</literal>\n";
			} else if (node.isAnon()) {
				output += "<bnode>"+ StringEscapeUtils.escapeXml(node.toString()) + "</bnode>\n";
			}
			output += "      </binding>\n";
		}
		output += "    </result>";
	}

	@Override
	public boolean stopProcessing() {
		return false;
	}
	
	public String getOutput() {
		return output + "  </results>\n</sparql>\n";
	}
}
