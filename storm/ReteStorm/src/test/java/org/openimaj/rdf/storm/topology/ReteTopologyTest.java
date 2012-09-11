package org.openimaj.rdf.storm.topology;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openimaj.io.FileUtils;
import org.openimaj.rdf.storm.topology.builder.NTriplesFileOutputReteTopologyBuilder;
import org.openjena.riot.SysRIOT;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.generated.StormTopology;
import backtype.storm.utils.Utils;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;

/**
 * Test a set of Jena production rules constructed in a distributable {@link StormTopology}
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 */
public class ReteTopologyTest {

	private static final String PREFIX = "PREFIX rdfs:<http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX example:<http://example.com/> ";
	/**
	 *
	 */
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	private File output;
	private File input;

	/**
	 * prepare the output
	 * @throws IOException
	 */
	@Before
	public void before() throws IOException {
		this.output = folder.newFile("output.ntriples");
		this.input = folder.newFile("input.ntriples");
	}

	/**
	 * Load the nTriples file from /test.rdfs and the rules from /test.rules
	 * @throws IOException
	 */
	@Test
	public void testReteTopology() throws IOException{
		InputStream inputStream = ReteTopologyTest.class.getResourceAsStream("/test.rdfs");
		FileUtils.copyStreamToFile(inputStream, this.input);

		Config conf = new Config();
		conf.setDebug(false);
		conf.setNumWorkers(2);
		conf.setMaxSpoutPending(1);
		conf.setFallBackOnJavaSerialization(false);
		conf.setSkipMissingKryoRegistrations(false);
		String inURL = this.input.toURI().toURL().toString();
		String outPath = this.output.getAbsolutePath();
		URL outURL = new File(outPath).toURI().toURL();
		NTriplesFileOutputReteTopologyBuilder builder = new NTriplesFileOutputReteTopologyBuilder(inURL, outPath);
		InputStream ruleStream = ReteTopologyTest.class.getResourceAsStream("/test.rules");
		StormTopology topology = ReteTopology.buildTopology(conf, builder, ruleStream);

		LocalCluster cluster = new LocalCluster();
		cluster.submitTopology("reteTopology", conf, topology);

		Utils.sleep(5000);
		SysRIOT.wireIntoJena();
		final Model model = ModelFactory.createDefaultModel();
		model.read(outURL.toString(), null, "N-TRIPLES");

		ResultSet results = executeQuery(PREFIX+"SELECT ?person WHERE {?person rdfs:type example:HumanBeing.}",model);
		checkBinding(results,Var.alloc("person"),"http://example.com/John","http://example.com/Steve");
		cluster.killTopology("reteTopology");
		cluster.shutdown();
	}

	private boolean checkBinding(ResultSet results, Var var, String ... strings) {
		List<Binding> bindings = new ArrayList<Binding>();
		while(results.hasNext()){
			bindings.add(results.nextBinding());
		}
		if(bindings.size()!=strings.length)return false;
		Set<String> allowed = Sets.newHashSet(strings);
		for (Binding binding : bindings) {
			boolean found = false;
			Node bound = binding.get(var);
			for (String string : allowed) {
				if(bound.toString().equals(string))found=true;
			}
			if(!found) {
				return false;
			}
		}
		return true;
	}

	private ResultSet executeQuery(String querystring, Model model) {
		Query query = QueryFactory.create(querystring);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		ResultSet select = qe.execSelect();
		return select;
	}
}