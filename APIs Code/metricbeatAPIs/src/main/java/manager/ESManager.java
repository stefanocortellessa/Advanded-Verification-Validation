package manager;

import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

public class ESManager {
	
	private String host;
	private String scheme;
	private String index;
	private String type;
	private int port;
	
	private RestHighLevelClient client;

	/**
	 * 
	 * @throws InterruptedException
	 */
	public ESManager() throws InterruptedException {
		
		String [] metrics = {"cpu", "diskio", "memory", "network"};
		String lowerBoundInterval = "2019-05-20T14:34:18.357Z";  //start date
		String upperBoundInterval = "2019-05-20T14:34:18.357Z";	 //end date
		
		setConfiguration();
		client = openConnection();
		
		for(int i = 0; i <= metrics.length - 1 ;i++) {
			
			retrievesAverageValue(metrics[i], lowerBoundInterval, upperBoundInterval);
		}
		closeConnection();
	}

	private RestHighLevelClient openConnection() {

		RestHighLevelClient client = new RestHighLevelClient(
							RestClient.builder(new HttpHost(host, port, scheme)));
		//System.out.println("Connection Established..");
		return client;
	}

	public void closeConnection() {
		
		try {
			client.close();
			//System.out.println("Connection Closed!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Sets every configuration needed for working properly
	 * */
	void setConfiguration() {
		
		try {
			
			Properties properties = new Properties();
			String propertiesFileName = "resources/config.properties";
			File initialFile = new File(propertiesFileName);
			InputStream inputStream = new FileInputStream(initialFile);

			properties.load(inputStream);

			host = properties.getProperty("host");
			port = Integer.parseInt(properties.getProperty("port"));
			scheme = properties.getProperty("scheme");
			index = properties.getProperty("index");
			type = properties.getProperty("type");
			
			inputStream.close();
			
		} catch (Exception e) {
			System.out.println("Exception: " + e);
		}
	}
	
	/**
	 * Retrieves records filtered by 'metric' and by time 'range' and calculates each average values
	 * 
	 * @param metric : metric that is currently analysed
	 * @param lowerBoundInterval 
	 * @param upperBoundInterval 
	 * @return a Map containing the container id associated to the metric average value
	 */
	public HashMap<String, HashMap<String, Double>> retrievesAverageValue(String metric, String lowerBoundInterval, String upperBoundInterval) {
		
		HashMap<String, HashMap<String, Double>> result = new HashMap<String, HashMap<String, Double>>();
		HashMap<String, Double> metricsAverage = new HashMap<String, Double>();
		double averageValue;
		String containerID;
		
		SearchRequest searchRequest = new SearchRequest(index); 
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder(); 
		
		// Creates an aggregation by terms, identified by id, and a sub-aggregation that calculates the average value - size needs to be higher or equal than 20 (containers number)
		TermsAggregationBuilder aggregation = AggregationBuilders.terms("groupByContainerID").field("docker.container.id")
				.subAggregation(AggregationBuilders.avg("average" + metric).field("metricset.rtt")).size(20);
		
		// Group record by time range and metric name
		searchSourceBuilder.query(QueryBuilders.boolQuery().must(QueryBuilders.rangeQuery("@timestamp").gte(lowerBoundInterval).lte(upperBoundInterval))
														   .must(QueryBuilders.termQuery("metricset.name", metric) )
														   .must(QueryBuilders.termQuery("metricset.module", "docker") ) );
		
		searchSourceBuilder.aggregation(aggregation);
		searchRequest.source(searchSourceBuilder);
		
		try {
			
			SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
			
			
			Terms aggr = searchResponse.getAggregations().get("groupByContainerID");
			
			int i = 0;
			for (Terms.Bucket entry : aggr.getBuckets()) {
				
				Avg avgAgg = entry.getAggregations().get("average" + metric);
				
				containerID = (String) entry.getKey();   
				averageValue = (double) avgAgg.getValue();
				
				metricsAverage.put(metric, averageValue);
				result.put(containerID, metricsAverage);
				//System.out.println("containerID : " + containerID); 
				//System.out.println("averageValue : " + averageValue); 
			}
		} catch (ElasticsearchException e) {
			if (e.status() == RestStatus.NOT_FOUND) {
				System.out.println("Index doesn't exists");
		    }
			e.getDetailedMessage();
		} catch (java.io.IOException ex) {
			ex.getLocalizedMessage();
		}
		System.out.println(result);
		return result;
	}
}