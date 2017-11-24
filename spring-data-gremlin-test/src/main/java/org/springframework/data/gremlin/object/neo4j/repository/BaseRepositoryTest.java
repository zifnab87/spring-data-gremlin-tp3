package org.springframework.data.gremlin.object.neo4j.repository;

import com.tinkerpop.blueprints.*;
import com.tinkerpop.gremlin.groovy.jsr223.GremlinGroovyScriptEngine;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.util.Pipeline;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.gremlin.object.neo4j.TestService;
import org.springframework.data.gremlin.object.neo4j.domain.*;
import org.springframework.data.gremlin.tx.GremlinGraphFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Rollback
@TestExecutionListeners(
        inheritListeners = false,
        listeners = { DependencyInjectionTestExecutionListener.class })
@SuppressWarnings("SpringJavaAutowiringInspection")
public abstract class BaseRepositoryTest {
    private static final Logger logger = LoggerFactory.getLogger(BaseRepositoryTest.class);

    @Autowired
    protected PersonRepository repository;

    @Autowired
    protected AddressRepository addressRepository;

    @Autowired
    protected LocationRepository locationRepository;

    @Autowired
    protected LikesRepository likesRepository;

    @Autowired
    protected GremlinGraphFactory factory;

    @Autowired
    protected TestService testService;

    protected Person graham;

    protected Person lara;

    @Before
    public void before() {

        Graph graph = factory.graph();
        factory.beginTx(graph);
        for (Vertex vertex : graph.getVertices()) {
            graph.removeVertex(vertex);
        }

        for (Edge edge : graph.getEdges()) {
            graph.removeEdge(edge);
        }
        factory.commitTx(graph);

        Address address = new Address("Australia", "Newcastle", "Scenic Dr", new Area("2291"));
        addressRepository.save(address);

        graham = new Person("Graham", "Webber", address, true);
        graham.addVehicle(Person.VEHICLE.CAR);
        graham.addVehicle(Person.VEHICLE.MOTORBIKE);

        Set<Located> locations = new HashSet<Located>();
        for (int i = 0; i < 5; i++) {
            Location location = new Location(-33 + i, 151 + i);
            locationRepository.save(location);
            Located located = new Located(new Date(), graham, location);
            locations.add(located);
        }

        lara = new Person("Lara", "Ivanovic", address, true);
        graham.setLocations(locations);
        graham.setCurrentLocation(locations.iterator().next());


        graham.setOwns(new House(3));
        graham.getOwned().add(new House(1));
        graham.getOwned().add(new House(2));
        Pet milo = new Pet("Milo", Pet.TYPE.DOG);
        graham.getPets().add(milo);
        graham.getPets().add(new Pet("Charlie", Pet.TYPE.CAT));
        graham.getPets().add(new Pet("TOC", Pet.TYPE.CAT));

        graham.setFavouritePet(milo);


        repository.save(graham);
        repository.save(new Person("Vanja", "Ivanovic", address, true));
        repository.save(lara);
        repository.save(new Person("Jake", "Webber", address, false));
        repository.save(new Person("Sandra", "Ivanovic", new Address("Australia", "Sydney", "Wilson St", new Area("2043")), false));
//        Graph graph = factory.graph();

        Likes like = new Likes(graham, lara);
        likesRepository.save(like);

        Iterable<Vertex> addresses = graph.query().has("street").vertices();
        assertNotNull(addresses);
        for (Vertex addr : addresses) {
            assertNotNull(addr);
            assertTrue(addr.getProperty("street").equals("Wilson St") || addr.getProperty("street").equals("Scenic Dr"));
        }

        ScriptEngine engine = new GremlinGroovyScriptEngine();

        Bindings bindings = engine.createBindings();
        bindings.put("g", graph);
        bindings.put("firstName", "Jake");

        try {
            Pipeline obj = (Pipeline) engine.eval("g.V().has('firstName', firstName)", bindings);
            assertTrue(obj.hasNext());
            Object o = obj.next();
            assertNotNull(o);
        } catch (ScriptException e) {
            e.printStackTrace();
        }

        GremlinPipeline<Graph, Vertex> pipe = new GremlinPipeline<Graph, Vertex>(graph).V().or(new GremlinPipeline().has("firstName", "Jake"), new GremlinPipeline().has("firstName", "Graham"));

        assertTrue("No Jake or Graham in Pipe!", pipe.hasNext());
        for (Vertex obj : pipe) {
            assertNotNull(obj);
            assertTrue(obj.getProperty("firstName").equals("Graham") || obj.getProperty("firstName").equals("Jake"));
        }


        GremlinPipeline<Object, ? extends Element> linkedPipe = new GremlinPipeline<Object, Element>(graph).V().outE("lives_at").inV().has("city", "Newcastle");

        assertTrue("No lives_at in Pipe!", linkedPipe.hasNext());
        for (Element obj : linkedPipe) {
            assertNotNull(obj);
            assertTrue(obj.getProperty("city").equals("Newcastle"));
        }

        GremlinPipeline<Object, Edge> likesPipe = new GremlinPipeline<Object, Edge>(graph).V().has("firstName", "Lara").inE("Likes");

        assertTrue("No likes in Pipe!", likesPipe.hasNext());
        for (Element obj : likesPipe) {
            assertNotNull(obj);
            Edge edge = (Edge)obj;
            Vertex v = edge.getVertex(Direction.OUT);
            assertTrue(v.getProperty("firstName").equals("Graham"));
        }

        factory.commitTx(graph);
    }

//    @After
    public void after() {

        Graph graph = factory.graph();
        factory.beginTx(graph);
        for (Vertex vertex : graph.getVertices()) {
            graph.removeVertex(vertex);
        }

        for (Edge edge : graph.getEdges()) {
            graph.removeEdge(edge);
        }
        factory.commitTx(graph);
    }

    @Test
    public void should_autowire_repos() {
        assertNotNull(repository);
        assertNotNull(addressRepository);
        assertNotNull(locationRepository);
    }

}
