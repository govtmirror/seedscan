package asl.seedscan.metrics;

import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asl.metadata.Channel;
import asl.metadata.Station;

/**
 * DeadChannelMetric - Computes Difference (over 4-8 second period) between the
 * the power spectral density (psd) of a channel and the NLNM if this value is
 * at or below a 7dB threshold the channel is dead
 */

public class DeadChannelMetric extends PowerBandMetric {
	private static final Logger logger = LoggerFactory.getLogger(asl.seedscan.metrics.DeadChannelMetric.class);

	// MetricDatabase metricDB;

	@Override
	public long getVersion() {
		return 1;
	}

	@Override
	public String getBaseName() {
		return "DeadChannelMetric";
	}

	public void process() {
		logger.info("-Enter- [ Station {} ] [ Day {} ]", getStation(), getDay());

		Station station;
		String metric;

		String netstat;
		String net;
		String stat;
		Calendar date;
		String period;
		Double NLNMValue;
		Double threshold;
		String NLNMBaseName;
		String NLNMName;

		date = getDate();
		metric = getName();
		threshold = -7.0;

		// Pull lower/upper limits for NLNMDeviationMetric
		netstat = getStation();
		String[] tmp = netstat.split("[_]");
		net = tmp[0];
		stat = tmp[1];
		station = new Station(net, stat);

		// Set NLNMDeviationMetric name (used for DB pull)
		NLNMBaseName = "NLNMDeviationMetric";
		String[] tokens = metric.split("[:]");
		period = tokens[1];
		NLNMName = NLNMBaseName + ":" + period;

		// Get NLNM Metric Value using name, date and channels
		List<Channel> channels = stationMeta.getChannelArray("LH", false, true);

		if (channels == null || channels.size() == 0) {
			logger.warn("No LH? channels found for station={} day={}", station.toString(), getDay());
			return;
		}

		// Loop over channels, get metadata & data for channel and calculate metric
		for (Channel channel : channels) {
			if (!metricData.hasChannelData(channel)) {
				logger.info("No data found for channel:[{}] day:[{}] --> Skip metric", channel, getDay());
				continue;
			}

			NLNMValue = metricData.getMetricValue(date, NLNMName, station, channel);
			ByteBuffer digest = metricData.valueDigestChanged(channel, createIdentifier(channel), getForceUpdate());

			// => oldDigest == newDigest, no need to recompute metric
			if (digest == null) {
				logger.info("Digest unchanged station:[{}] channel:[{}] day:[{}] --> Skip metric", station, channel,
						getDay());
				continue;
			}

			double result = 0.0;
			if (NLNMValue == null) {
				// Do nothing --> skip to next channel
			} else {
				// Dead channel if -7dB below NLNM
				if (NLNMValue <= threshold) {
					result = 0.0;
				} else if (NLNMValue > threshold) {
					result = 1.0;
				} else {
					result = NO_RESULT;
				}

				// Add result to metricResult
				if (result != NO_RESULT) {
					metricResult.addResult(channel, result, digest);
				}
			}
		}
	}
}
