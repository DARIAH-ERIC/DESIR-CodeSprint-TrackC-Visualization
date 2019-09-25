package pl.edu.icm.desir.data.exchange;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFReader;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.FileUtils;

import org.apache.log4j.Logger;
import pl.edu.icm.desir.data.model.Actor;
import pl.edu.icm.desir.data.model.Event;
import pl.edu.icm.desir.data.model.ScaledTime;
import pl.edu.icm.desir.data.model.SpatiotemporalPoint;

public class RdfModelExtractor implements ModelBuilder {

	private static final String BASE_URI = "http://desir.icm.edu.pl/";
	private static final String ACTOR_NAMESPACE = "http://desir.icm.edu.pl/actor#";
	private static final String EVENT_NAMESPACE = "http://desir.icm.edu.pl/event#";
	private static final String HAS_NAME_NAMESPACE = "http://desir.icm.edu.pl/hasName";
	private static final String HAS_TITLE_NAMESPACE = "http://desir.icm.edu.pl/hasTitle";
	private static final String OCCURRED_NAMESPACE = "http://desir.icm.edu.pl/occurred";
	private static final String PARTICIPATES_IN_NAMESPACE = "http://desir.icm.edu.pl/participatesIn";
	private static final DateTimeFormatter YEAR_FORMATTER = new DateTimeFormatterBuilder()
			.appendPattern("yyyy")
			.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
			.parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
			.toFormatter();
	private List<Actor> actors;
	private List<Event> events;
	private String filename;
	private static final Logger LOG = Logger.getLogger(RdfModelExtractor.class);

	public RdfModelExtractor(String filename) {
		this.filename = filename;
	}

	public void parseInputData(InputStream in) throws IOException {

		Map<String, Actor> actorsMap = new HashMap<>();
		Map<String, Event> eventsMap = new HashMap<>();

		Model model = ModelFactory.createDefaultModel();
		String syntax = FileUtils.guessLang(filename) ;
		if ( syntax == null || syntax.equals("") )
			syntax = FileUtils.langXML ;
		RDFReader r = model.getReader(syntax);
		r.setProperty("iri-rules", "strict");
		r.setProperty("error-mode", "strict"); // Warning will be errors.
		r.read(model, in, BASE_URI);
		in.close();

		StmtIterator iter = model.listStatements();
		try {
			while (iter.hasNext()) {
				Statement stmt = iter.next();

				Resource subject = stmt.getSubject();
				Resource predicate = stmt.getPredicate();
				RDFNode object = stmt.getObject();

				switch (predicate.getNameSpace()) {
					case HAS_NAME_NAMESPACE:
						if (actorsMap.containsKey(subject.getURI())) {
							actorsMap.get(subject.getURI()).setName(object.toString());
						} else {
							Actor actor = new Actor(subject.getLocalName(), object.toString());
							actorsMap.put(subject.getURI(), actor);
						}
						break;
					case HAS_TITLE_NAMESPACE:
						if (eventsMap.containsKey(subject.getURI())) {
							eventsMap.get(subject.getURI()).setName(object.toString());
						} else {
							Event event = new Event(subject.getLocalName(), object.toString(), null, null);
							event.setName(object.toString());
							eventsMap.put(subject.getURI(), event);
						}
						break;
					case OCCURRED_NAMESPACE:
						SpatiotemporalPoint stPoint = new SpatiotemporalPoint();
						ScaledTime st = new ScaledTime();
						st.setLocalDate(LocalDate.parse(object.toString(), YEAR_FORMATTER));
						if (eventsMap.containsKey(subject.getURI())) {
							eventsMap.get(subject.getURI()).setStartPoint(stPoint);
							eventsMap.get(subject.getURI()).setEndPoint(stPoint);
						} else {
							Event event = new Event(subject.getLocalName(), object.toString(), stPoint, stPoint);
							eventsMap.put(subject.getURI(), event);
						}
						break;
					case PARTICIPATES_IN_NAMESPACE:
						Actor actor;
						if (actorsMap.containsKey(subject.getURI())) {
							actor = actorsMap.get(subject.getURI());
						} else {
							actor = new Actor(subject.getLocalName(), object.toString());
						}
						actor.setParticipation(new ArrayList<>());

						Event event = new Event(subject.getLocalName(), object.toString(), null, null);
						if (eventsMap.containsKey(object.toString())) {
							event = eventsMap.get(object.toString());
						}
						actor.getParticipation().add(event);
						actorsMap.put(subject.getURI(), actor);
						eventsMap.put(object.toString(), event);
						break;
				}
			}
		} finally {
			if (iter != null)
				iter.close();
		}

		actors = new ArrayList<>(actorsMap.values());
		events = new ArrayList<>(eventsMap.values());
	}

	@Override
	public List<Actor> getActors() {
		return actors;
	}

	@Override
	public List<Event> getEvents() {
		return events;
	}
}
