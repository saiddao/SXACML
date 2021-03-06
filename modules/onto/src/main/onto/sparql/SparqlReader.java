package onto.sparql;

import onto.utils.Box;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Set;


import onto.utils.OntologyUtils;
import org.semanticweb.owlapi.model.OWLOntology;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;


//TODO ??? enable declaration of prefixes when instantiating the reader, to reuse between queries
public class SparqlReader implements Closeable{

	private InfModel model;

	public SparqlReader(OWLOntology ontology) {
		model = OntologyUtils.createJenaModel(ontology);
	}

	public void executeQuery(String qry, IResultProcessor resultProcessor) {
		QueryExecution qe = null;
		ResultSet rs;
		try {
			qe = QueryExecutionFactory.create(qry, model);
			rs = qe.execSelect();
			resultProcessor.processResult(rs);
		} finally {
			if(qe != null)
				qe.close();
		}		
	}
	
	public String readSingleString(String query, final String returnName){
		final Box<String> result = new Box<String>();
		executeQuery(query, new SingleValueResultProcessor() {
			
			@Override
			public void processSolution(QuerySolution sol) {
				result.set(sol.get(returnName).asLiteral().getString());				
			}
		});
		
		return result.get();
	}

    public Set<String> readAllValuesAsString(String query, final String returnName){
        final Set<String> result = new HashSet<String>();
        executeQuery(query, new ValueSetResultProcessor() {

            @Override
            public void processSolution(QuerySolution sol) {
                result.add(sol.get(returnName).asLiteral().getString());
            }
        });

        return result;
    }

	public void close() {
		model.close();
		model.getGraph().close();
	}

}
