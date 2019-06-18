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
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
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
		
		setConfiguration();
		client = openConnection();

		tomcatErrorMessagesRatio(); 
		
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
	 * 
	 * @param logFilePath : path to the log file inspected
	 * @return a String containing the container id written in the path
	 */
	public String parseMessage(String logFilePath) {
		
		String[] splittedPath = logFilePath.split("/");
		String containerID = splittedPath[5]; // retrieves the container id that is in the fifth array position
		
		return containerID;
	}
	
	/**
	 * 
	 * @param tomcatMessages : Map containing the number of tomcat general messages divided by id
	 * @param tomcatErrors : Map containing the number of tomcat error messages divided by id
	 * @return a Map containing the ratio between tomcat error messages AND tomcat general messages
	 */
	public HashMap<String, Double> calculateErrorRatio (Map<String, Double> tomcatMessages, HashMap<String, Double> tomcatErrors){
		
		HashMap<String, Double> result = new HashMap<String, Double>();
		
		for(Map.Entry<String, Double> mess: tomcatMessages.entrySet()) {	
			for(Map.Entry<String, Double> err: tomcatErrors.entrySet()) {
				if(mess.getKey().equals(err.getKey())) {
					
					// calculates the ratio between number of tomcat errors and total tomcat messages
					result.put(mess.getKey(), err.getValue() / mess.getValue());
				}
			}
		}
		//System.out.print("result: " + result);
		return result;
	}
	
	/**
	 * 
	 * @return a Map containing the ratio between tomcat error messages AND tomcat general messages
	 */
	public HashMap<String, Double> tomcatErrorMessagesRatio() {
		
		String containerID;
		HashMap<String, Double> tomcatMessages = new HashMap<String, Double>();
		HashMap<String, Double> tomcatErrors = new HashMap<String, Double>();
		HashMap<String, Double> result = new HashMap<String, Double>();
		
		SearchRequest searchRequest = new SearchRequest(index); 
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder(); 
		
		/*
		 * First query needed to retrieve the total messages number in which tomcat is involved
		 * */
		
		// Creates an aggregation by terms, identified by source - size needs to be higher or equal than 20 (containers number)
		TermsAggregationBuilder tomcatMessagesAggregation = AggregationBuilders.terms("groupByContainerID").field("source").size(20);
				
		// Group records by match of tomcat word in 'message' field 
		searchSourceBuilder.query(QueryBuilders.matchQuery("message", "tomcat"));
		
		searchSourceBuilder.aggregation(tomcatMessagesAggregation);
		searchRequest.source(searchSourceBuilder);
		
		try {
			
			SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
			
			Terms aggr = searchResponse.getAggregations().get("groupByContainerID");
			
			for (Terms.Bucket entry : aggr.getBuckets()) {
				
				containerID = parseMessage((String)entry.getKey());
				tomcatMessages.put(containerID, (double) entry.getDocCount());
			}
			//System.out.println("tomcatMessages: " + tomcatMessages);
		} catch (ElasticsearchException e) {
			if (e.status() == RestStatus.NOT_FOUND) {
				System.out.println("Index doesn't exists");
		    }
			e.getDetailedMessage();
		} catch (java.io.IOException ex) {
			ex.getLocalizedMessage();
		}
		
		/*
		 * Second query needed to retrieve the total ERROR messages number in which tomcat is involved
		 * */
		
		// Creates an aggregation by terms, identified by source - size needs to be higher than 12 (containers number)
		TermsAggregationBuilder tomcatErrorsAggregation = AggregationBuilders.terms("groupByID").field("source").size(20);
		
		// Group records by match with the string 'tomcat' in the 'message' field and 'event.stream' equal to 'stderr'
		searchSourceBuilder.query(QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("message", "tomcat"))
														   .must(QueryBuilders.termQuery("event.stream", "stderr")));
		
		searchSourceBuilder.aggregation(tomcatErrorsAggregation);
		searchRequest.source(searchSourceBuilder);
		
		try {
			
			SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
			
			Terms aggr = searchResponse.getAggregations().get("groupByID");
			
			for (Terms.Bucket entry : aggr.getBuckets()) {
				
				containerID = parseMessage((String)entry.getKey());
				tomcatErrors.put(containerID, (double) entry.getDocCount());
			}
			//System.out.println("tomcatErrors: " + tomcatErrors);
		} catch (ElasticsearchException e) {
			if (e.status() == RestStatus.NOT_FOUND) {
				System.out.println("Index doesn't exists");
		    }
			e.getDetailedMessage();
		} catch (java.io.IOException ex) {
			ex.getLocalizedMessage();
		}
		result = calculateErrorRatio(tomcatMessages, tomcatErrors);
		System.out.println("result: " + result);
		return result;
	}
}