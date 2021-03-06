package asl.seedscan;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asl.metadata.EpochData;
import asl.metadata.MetaServer;
import asl.metadata.Station;
import asl.metadata.meta_new.StationMeta;
import asl.seedscan.database.MetricInjector;
import asl.seedscan.database.MetricReader;
import asl.seedscan.event.EventCMT;
import asl.seedscan.event.EventLoader;
import asl.seedscan.metrics.Metric;
import asl.seedscan.metrics.MetricData;
import asl.seedscan.metrics.MetricResult;
import asl.seedscan.metrics.MetricWrapper;
import asl.seedsplitter.DataSet;
import asl.seedsplitter.SeedSplitter;
import asl.timeseries.CrossPower;
import asl.timeseries.CrossPowerKey;
import sac.SacTimeSeries;
import seed.Blockette320;

class Scanner implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(asl.seedscan.Scanner.class);
	private static final long MILLISECONDS_IN_DAY = 1000 * 60 * 60 * 24;

	private Station station;
	private MetricInjector injector;
	private MetricReader reader;
	private Scan scan;
	private MetaServer metaServer;



	// Class to assign seedplitter object and seedsplitter table
	private static class SplitterObject {
		private SeedSplitter splitter;
		private Hashtable<String, ArrayList<DataSet>> table;

		private SplitterObject(SeedSplitter splitter, Hashtable<String, ArrayList<DataSet>> table) {
			this.splitter = splitter;
			this.table = table;
		}
	}

	// Class to run Future task (seedplitter.doInBackground())
	private static class Task implements Callable<Hashtable<String, ArrayList<DataSet>>> {
		private SeedSplitter splitter;

		private Task(SeedSplitter splitter) {
			this.splitter = splitter;
		}

		public Hashtable<String, ArrayList<DataSet>> call() throws Exception {
			Hashtable<String, ArrayList<DataSet>> table = null;
			table = splitter.doInBackground();
			return table;
		}
	}

	Scanner(MetricReader reader, MetricInjector injector, Station station, Scan scan, MetaServer metaServer) {
		this.reader = reader;
		this.injector = injector;
		this.station = station;
		this.metaServer = metaServer;
		this.scan = scan;
	}

	public void run() {
		scan();
	}

	private void scan() {
		logger.debug("Enter scan(): Thread id=[{}]", Thread.currentThread().getId());

		GregorianCalendar timestamp = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		
		MetricData currentMetricData = null;
		MetricData nextMetricData = null;

		// Look for cfg:start_date first:
		if (scan.getStartDate() >= 1970001 && scan.getStartDate() < 2114001) {
			timestamp.set(Calendar.YEAR, scan.getStartDate() / 1000);
			timestamp.set(Calendar.DAY_OF_YEAR, scan.getStartDate() % 1000);
		} else if (scan.getStartDate() != 0 // Catch if no startDate is set
				&& (scan.getStartDate() < 1970001 || scan.getStartDate() > 2114001)) {
			logger.error(
					"Start Date=[{}] is invalid. Either it must be inbetween 1970001 and 2114001 OR 0 to use start_day.",
					scan.getStartDate());
			return; // Can't scan an invalid date so get out of here.
		} else { // Use cfg:start_day
			timestamp.setTimeInMillis(timestamp.getTimeInMillis() - (scan.getStartDay() * MILLISECONDS_IN_DAY));
		}

		// timestamp is now set to current time - (24 hours x StartDay). What we
		// really want is to set it
		// to the start (hh:mm=00:00) of the first day we want to scan
		timestamp.set(Calendar.HOUR_OF_DAY, 0);
		timestamp.set(Calendar.MINUTE, 0);
		timestamp.set(Calendar.SECOND, 0);
		timestamp.set(Calendar.MILLISECOND, 0);

		// CMT Event loader - use to load events for each day
		EventLoader eventLoader = new EventLoader(scan.getEventsDir());

		// Loop over days to scan, from most recent (currentDay=startDay) to
		// oldest (currentDay=startDay - daysToScan - 1)
		// e.g.,
		// i currentDay nextDay
		// -----------------------
		// 0 072 073
		// 1 071 072
		// 2 070 071
		// :
		// daysToScan - 1

		for (int i = 0; i < scan.getDaysToScan(); i++) {
			if (i != 0) {
				timestamp.setTimeInMillis(timestamp.getTimeInMillis() - MILLISECONDS_IN_DAY);
			}
			GregorianCalendar nextDayTimestamp = (GregorianCalendar) timestamp.clone();
			nextDayTimestamp.setTimeInMillis(timestamp.getTimeInMillis() + MILLISECONDS_IN_DAY);

			logger.debug(String.format("Scan Station=%s Day=%s Thread id=[%d]", station,
					EpochData.epochToDateString(timestamp), Thread.currentThread().getId()));

			// [1] Get all the channel metadata for this station, for this day
			// StationMeta stnMeta = metaGen.getStationMeta(station, timestamp);
			StationMeta stnMeta = metaServer.getStationMeta(station, timestamp);
			if (stnMeta == null) { // No Metadata found for this station + this
				// day --> skip day
				logger.warn(String.format("== Scanner: No Metadata found for Station:%s_%s + Day:%s --> Skipping\n",
						station.getNetwork(), station.getStation(), EpochData.epochToDateString(timestamp)));
				continue;
			}

			if (i == 0) {
				stnMeta.printStationInfo();
			}

			logger.info(String.format("Scan Station=%s Day=%s", station, EpochData.epochToDateString(timestamp)));

			Hashtable<String, EventCMT> eventCMTs = eventLoader.getDayEvents(timestamp);
			Hashtable<String, Hashtable<String, SacTimeSeries>> eventSynthetics = null;

			if (eventCMTs != null) {
				eventSynthetics = eventLoader.getDaySynthetics(timestamp, station);
			} else {
				// System.out.format("== Scanner: NO CMTs FOUND for this
				// day\n");
			}

			// [2] Read in all the seed files for this station, for this day &
			// for the next day
			// If this isn't the first day of the scan then simply copy current
			// into next so we
			// don't have to reread all of the seed files in

			if (i == 0) {
				nextMetricData = getMetricData(nextDayTimestamp);
			} else {
				// Need to null out ref to next day before passing
				// currentMetricData to avoid chaining refs
				if (currentMetricData != null) {
					currentMetricData.setNextMetricDataToNull();
					nextMetricData = currentMetricData;
				}
			}
			currentMetricData = null;
			currentMetricData = getMetricData(timestamp);

			if (currentMetricData != null) {
				// This doesn't mean nextMetricData isn't null!
				currentMetricData.setNextMetricData(nextMetricData);
			}

			// [3] Loop over Metrics to compute, for this station, for this day
			Hashtable<CrossPowerKey, CrossPower> crossPowerMap = null;

			try { // wrapper.getNewInstance()
				for (MetricWrapper wrapper : scan.getMetrics()) {
					Metric metric = wrapper.getNewInstance();
					metric.setBaseOutputDir(scan.getPlotsDir());

					if (currentMetricData == null) {
						metric.setData(new MetricData(this.reader, stnMeta));
					} else {
						metric.setData(currentMetricData);
					}
					if (eventCMTs != null) {
						metric.setEventTable(eventCMTs);
						if (eventSynthetics != null) {
							metric.setEventSynthetics(eventSynthetics);
						}
					}

					// Hand off the crossPowerMap from metric to metric,
					// adding to it each time
					if (crossPowerMap != null) {
						metric.setCrossPowerMap(crossPowerMap);
					}
					metric.process();
					// Save the current crossPowerMap for the next metric:
					crossPowerMap = metric.getCrossPowerMap();

					// This is a little convoluted: calibration.getResult()
					// returns a MetricResult, which may contain many values
					// in a Hashtable<String,String> = map.
					// MetricResult.getResult(id) returns value = String

					MetricResult results = metric.getMetricResult();
					if (results == null) {
					} else {
						for (String id : results.getIdSortedSet()) {
							double value = results.getResult(id);
							/* @formatter:off */
							logger.info(String.format("%s [%7s] [%s] %15s:%6.2f", results.getMetricName(),
									results.getStation(), EpochData.epochToDateString(results.getDate()), id, value));

							if (Double.isNaN(value)) {
								logger.error(String.format("%s [%s] [%s] %s: ERROR: metric value = [ NaN ] !!\n",
										results.getMetricName(), results.getStation(),
										EpochData.epochToDateString(results.getDate()), id));
							}
							if (Double.isInfinite(value)) {
								logger.error(String.format("%s [%s] [%s] %s: ERROR: metric value = [ Infinity ] !!\n",
										results.getMetricName(), results.getStation(),
										EpochData.epochToDateString(results.getDate()), id));
							}
							/* @formatter:on */
						}
						if (injector.isConnected()) {
							try {
								injector.inject(results);
							} catch (InterruptedException ex) {
								String message = String.format("Scanner: InterruptedException injecting metric [%s]",
										metric.toString());
								logger.warn(message, ex);
							}
						} else {
							logger.warn("Injector *IS NOT* connected --> Don't inject");
						}
					}
				} // end loop over metrics
			} catch (InstantiationException e) {
				logger.error("Scanner InstantationException:", e);
			} catch (IllegalAccessException e) {
				logger.error("Scanner IllegalAccessException:", e);
			} catch (NoSuchFieldException e) {
				logger.error("Scanner NoSuchFieldException:", e);
			} catch (IllegalArgumentException e) {
				logger.error("Scanner IllegalArgumentException:", e);
			}
		} // end loop over day to scan
	} // end scan()

	/**
	 * SeedSplitter function: processing times greater than 3 min. will move to
	 * the next day
	 */
	private SplitterObject executeSplitter(File[] files, int timeout, GregorianCalendar timestamp)
			throws TimeoutException, ExecutionException, InterruptedException {
		Hashtable<String, ArrayList<DataSet>> table = null;
		SeedSplitter splitter = new SeedSplitter(files);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Hashtable<String, ArrayList<DataSet>>> future = executor.submit(new Task(splitter));

		try {
			logger.info(String.format("== STARTED SeedSplitter() process for [%s]:[%s]\n", station,
					EpochData.epochToDateString(timestamp)));
			table = future.get(timeout, TimeUnit.SECONDS);
			logger.info(String.format("== FINISHED SeedSplitter() process for [%s]:[%s]\n", station,
					EpochData.epochToDateString(timestamp)));
		} catch (TimeoutException e) {
			future.cancel(true);
			throw e;
		} catch (ExecutionException e) {
			future.cancel(true);
			throw e;
		} catch (InterruptedException e) {
			future.cancel(true);
			throw e;
		}
		executor.shutdown();
		executor.awaitTermination(300, TimeUnit.SECONDS);

		return new SplitterObject(splitter, table);
	}

	/**
	 * Return a MetricData object for the station + timestamp
	 */
	private MetricData getMetricData(GregorianCalendar timestamp) {

		StationMeta stationMeta = metaServer.getStationMeta(station, timestamp);
		if (stationMeta == null) {
			return null;
		}

		ArchivePath pathEngine = new ArchivePath(timestamp, station);
		String path = pathEngine.makePath(scan.getPathPattern());
		File dir = new File(path);
		File[] files = null;
		boolean dataExists = true;

		/**
		 * MTH: There are some non-seed files (e.g., data_avail.txt) included in
		 * files[]. For some reason the file netday.index causes the splitter to
		 * hang. Either restrict the file list to .seed files (as I do below)
		 * -or- Debug splitter so it drops non-seed/miniseed files.
		 **/
		FilenameFilter textFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				String lowercaseName = name.toLowerCase();
				File file = new File(dir + "/" + name);
				if (lowercaseName.endsWith(".seed") && (file.length() > 0)) {
					return true;
				} else {
					return false;
				}
			}
		};

		if (!dir.exists()) {
			logger.info("Path '" + dir + "' does not exist.");
			dataExists = false;
		} else if (!dir.isDirectory()) {
			logger.info("Path '" + dir + "' is not a directory.");
			dataExists = false;
		} else { // The dir exists --> See if we have any useful seed files in
					// it:

			files = dir.listFiles(textFilter);
			if (files == null) {
				dataExists = false;
			} else if (files.length == 0) {
				dataExists = false;
			}
		}

		if (!dataExists) {
			return null;
		}

		logger.info(dir.getPath() + " contains " + files.length + " files.");

		// execute SeedSplitter process (180 sec timer will be issued)
		try {
			int timeout = 180;
			SplitterObject splitObj = executeSplitter(files, timeout, timestamp);
			SeedSplitter splitter = splitObj.splitter;
			Hashtable<String, ArrayList<DataSet>> table = splitObj.table;

			Hashtable<String, ArrayList<Integer>> qualityTable = null;
			qualityTable = splitter.getQualityTable();

			Hashtable<String, ArrayList<Blockette320>> calibrationTable = null;
			calibrationTable = splitter.getCalTable();

			return new MetricData(reader, table, qualityTable, stationMeta, calibrationTable);
		} catch (TimeoutException e) {
			StringBuilder message = new StringBuilder();
			message.append(String.format("== TimeoutException: Skipping to next day for [%s]:[%s]\n", station,
					EpochData.epochToDateString(timestamp)));
			logger.error(message.toString());
			return null;
		} catch (ExecutionException e) {
			logger.error(String.format("== ExecutionException: Skipping to next day for [%s]:[%s]\n", station,
					EpochData.epochToDateString(timestamp)));
			return null;
		} catch (InterruptedException e) {
			logger.error(String.format("== InterruptedException: Skipping to next day for [%s]:[%s]\n", station,
					EpochData.epochToDateString(timestamp)));
			return null;
		}
	}
}
