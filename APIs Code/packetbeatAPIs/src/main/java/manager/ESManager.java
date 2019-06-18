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
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
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
		
		String[] protocols = {"tls", "http"};
		
		setConfiguration();
		client = openConnection();
		
		for(int i = 0; i < protocols.length; i++) {
			getResponseAndStatus(protocols[i]); 
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
	
	
	public long getQuerySize(String protocol) {
		
		SearchRequest searchRequest = new SearchRequest(index); 
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder(); 
		long size = 0;
		
		searchSourceBuilder.query(QueryBuilders.termQuery("type", protocol));
		searchRequest.source(searchSourceBuilder);
		
		try {
			
			SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
			
			// explores response hits
			SearchHits hits = searchResponse.getHits();
			SearchHit[] searchHits = hits.getHits();
			
			size = hits.totalHits;
		} catch (ElasticsearchException e) {
			if (e.status() == RestStatus.NOT_FOUND) {
				System.out.println("Index doesn't exists");
		    }
			e.getDetailedMessage();
		} catch (java.io.IOException ex) {
			ex.getLocalizedMessage();
		}
		//System.out.println("Hits: " + size);
		return size;
	}
	
	/**
	 * 
	 * @param protocol
	 * @return a Map identified by the protocol with its responseTimes and statuses
	 */
	public HashMap<String,HashMap<Integer, String>> getResponseAndStatus(String protocol) {
		
		HashMap<String,HashMap<Integer, String>> result = new HashMap<String,HashMap<Integer, String>>();
		HashMap<Integer, String> times = new HashMap<Integer, String>();
		int size;
		
		SearchRequest searchRequest = new SearchRequest(index); 
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder(); 
		
		size = (int)getQuerySize(protocol);
		searchSourceBuilder.query(QueryBuilders.termQuery("type", protocol)).size(size);
		searchRequest.source(searchSourceBuilder);
		try {
			
			SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
			
			// explores response hits
			SearchHits hits = searchResponse.getHits();
			SearchHit[] searchHits = hits.getHits();
			
			String [] statuses = new String[(int) hits.totalHits];
			Integer [] responseTimes = new Integer [(int) hits.totalHits];
			int j = 0;
			
			for (SearchHit hit : searchHits) {
				
				statuses[j] = (String) hit.getSourceAsMap().get("status");
				responseTimes[j] = (Integer) hit.getSourceAsMap().get("responsetime");
				
				j++;
			}
			
			for(int k = 0; k < statuses.length; k++) {
				
				times.put(responseTimes[k], statuses[k]);
				result.put(protocol, times);
			}
			//System.out.println("Hits: " + hits.totalHits);
		} catch (ElasticsearchException e) {
			if (e.status() == RestStatus.NOT_FOUND) {
				System.out.println("Index doesn't exists");
		    }
			e.getDetailedMessage();
		} catch (java.io.IOException ex) {
			ex.getLocalizedMessage();
		}
		//System.out.println(result);
		return result;
	}
}