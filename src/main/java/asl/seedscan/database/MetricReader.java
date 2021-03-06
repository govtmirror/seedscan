package asl.seedscan.database;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asl.concurrent.Task;
import asl.concurrent.TaskThread;

/**
 * @author Joel D. Edwards
 * 
 */
@SuppressWarnings("cast") //Why is this here?
public class MetricReader extends TaskThread<QueryContext<? extends Object>> {
	private static final Logger logger = LoggerFactory
			.getLogger(asl.seedscan.database.MetricInjector.class);

	private MetricDatabase metricDB;

	/**
	 * 
	 */
	public MetricReader(MetricDatabase metricDB) {
		super();
		this.metricDB = metricDB;
	}

	public boolean isConnected() {
		// System.out.println("== MetricReader.isConnected() = " +
		// metricDB.isConnected() );
		return metricDB.isConnected();
	}

	/**
	 * 
	 * 
	 * @see asl.concurrent.TaskThread#setup()
	 */
	@Override
	protected void setup() {
		// Pre-run logic goes here
	}

	/**
	 * Perform task can generate an ClassCastException if a result is
	 * unexpected. This will be caught and logged. The Task will fail however
	 * and may generate some null pointers.
	 * 
	 * @see asl.concurrent.TaskThread#performTask(asl.concurrent.Task)
	 */
	@SuppressWarnings({ "unchecked" })
	@Override
	protected void performTask(Task<QueryContext<? extends Object>> task) {
		// TODO: Determine if command should be an enum since it appears to
		// behave as one.
		String command = task.getCommand();

		logger.info("performTask: command=" + command + " task=" + task);

		try {
			/*
			 * TODO: If command becomes an enum, This should be a switch.
			 * String switches aren't permitted until Java 7.
			 * 
			 */
			if (command.equals("GET-METRIC-VALUE-DIGEST")) {
				MetricContext<ByteBuffer> context = (MetricContext<ByteBuffer>) task
						.getData();
				MetricValueIdentifier id = context.getId();
				ByteBuffer digest = metricDB.getMetricValueDigest(id.getDate(),
						id.getMetricName(), id.getStation(), id.getChannel());
				QueryResult<ByteBuffer> result = new QueryResult<ByteBuffer>(
						digest);
				context.getReplyQueue().put(result);
			} else if (command.equals("GET-METRIC-VALUE")) {
				MetricContext<Double> context = (MetricContext<Double>) task
						.getData();
				MetricValueIdentifier id = context.getId();
				Double value = metricDB.getMetricValue(id.getDate(),
						id.getMetricName(), id.getStation(), id.getChannel());
				QueryResult<Double> result = new QueryResult<Double>(value);
				context.getReplyQueue().put(result);
			}
		} catch (ClassCastException ex) {
			logger.error(
					"A cast in MetricReader, performTask failed due to unchecked type.",
					ex);
		} catch (InterruptedException ex) {
			logger.warn(
					"Interrupted while attempting to send reply. This may have caused a station thread to hang!",
					ex);
		}

	}

	/**
	 * 
	 * 
	 * @see asl.concurrent.TaskThread#cleanup()
	 */
	@Override
	protected void cleanup() {
		// Post-run logic goes here
	}

	public Double getMetricValue(MetricValueIdentifier id) {
		Double value = null;
		try {
			MetricContext<Double> context = new MetricContext<Double>(id);
			addTask("GET-METRIC-VALUE", context);
			value = context.getReplyQueue().take().getResult();
		} catch (InterruptedException ex) {
			logger.warn(
					"Interrupted while awaiting reply from database reader thread.",
					ex);
		}
		return value;
	}

	/**
	 * Currently getMetricValueDigest() is the only method called (from the
	 * MetricData class)
	 */
	public ByteBuffer getMetricValueDigest(MetricValueIdentifier id) {
		ByteBuffer digest = null;
		try {
			MetricContext<ByteBuffer> context = new MetricContext<ByteBuffer>(
					id);
			addTask("GET-METRIC-VALUE-DIGEST", context);
			digest = context.getReplyQueue().take().getResult();
		} catch (InterruptedException ex) {
			logger.warn(
					"Interrupted while awaiting reply from database reader thread.",
					ex);
		}
		return digest;
	}
}
