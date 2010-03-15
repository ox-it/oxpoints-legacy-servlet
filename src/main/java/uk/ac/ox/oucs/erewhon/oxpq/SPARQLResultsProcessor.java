package uk.ac.ox.oucs.erewhon.oxpq;

import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;

class SPARQLQueryResultProcessor {

	public static String performQuery(Model model, String query) {
		String output = "";

		QueryExecution qexec = QueryExecutionFactory.create( query, model );

		ResultSet results = qexec.execSelect();

		List<String> vars = results.getResultVars();

		output += "<?xml version=\"1.0\"?>\n";
		output += "<sparql xmlns=\"http://www.w3.org/2005/sparql-results#\">\n";
		output += "  <head>\n";

		for (String var : vars)
			output += "    <variable name=\"" + StringEscapeUtils.escapeXml(var) + "\"/>\n";

		output += "  </head>\n";
		output += "  <results>\n";

		while (results.hasNext()) {
			QuerySolution solution = results.next();
			output += "    <result>\n";
			for (String var :vars) {
				RDFNode node = solution.get(var);


				output += "      <binding name=\""+StringEscapeUtils.escapeXml(var)+"\">\n";
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
			output += "    </result>\n";
		}
		output += "  </results>\n";
		output += "</sparql>\n";
		
		return output;
	}

}
