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

	public ESManager() throws InterruptedException {
		
		String microServiceName = "gateway";
		String lowerBoundInterval = "1557739376675000";  //start date
		String upperBoundInterval = "1557739376675000";	 //end date
		String[] operationName = {"http:/products/findproduct"};
		
		setConfiguration();
		
		durationAverageQuery(microServiceName, lowerBoundInterval, upperBoundInterval, operationName); 
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
	 * 
	 * @param microserviceName
	 * @param lowerBoundInterval
	 * @param upperBoundInterval
	 * @param operationName
	 * @return a Map containing the average duration value corresponding to the operationName 
	 */
	public HashMap <String, Double> durationAverageQuery(String microserviceName, String lowerBoundInterval, String upperBoundInterval, String[] operationName) {
		
		double averageValue;
		HashMap <String, Double> result = new HashMap <String, Double>();
		
		for(int i = 0; i < operationName.length; i++) {
			
			client = openConnection();
			
			SearchRequest searchRequest = new SearchRequest(index); 
			SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder(); 
			
			// Creates an aggregation by terms, identified by id, and a sub-aggregation that calculates the average value
			// size needs to be higher or equal than 20 (containers number)
			TermsAggregationBuilder aggregation = AggregationBuilders.terms("groupByTraceID").field("traceId")
					.subAggregation(AggregationBuilders.avg("average" + microserviceName).field("duration")).size(20);
			
			// Group record by time range and metric name
			searchSourceBuilder.query(QueryBuilders.boolQuery().must(QueryBuilders.rangeQuery("timestamp").gte(lowerBoundInterval).lte(upperBoundInterval))
																.must(QueryBuilders.termQuery("localEndpoint.serviceName", microserviceName))
																.must(QueryBuilders.termQuery("name", operationName[i])));
			
			searchSourceBuilder.aggregation(aggregation);
			searchRequest.source(searchSourceBuilder);
			
			try {
				
				SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
				
				Terms aggr = searchResponse.getAggregations().get("groupByTraceID");
							
				for (Terms.Bucket entry : aggr.getBuckets()) {
					
					Avg avgAgg = entry.getAggregations().get("average" + microserviceName);
					averageValue = avgAgg.getValue();
					result.put(operationName[i], averageValue);
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
			closeConnection();
		}
		//System.out.println(result);
		return result;
	}
}