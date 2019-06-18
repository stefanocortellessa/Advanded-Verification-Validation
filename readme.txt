All’interno della cartella ‘ES Beats’ sono presenti tutte le directory di configurazione di ElasticSearch e dei vari beat utilizzati (Metricbeat, Packetbeat, Filebeat).
Nella cartella ‘APIs Code’ invece sono presenti le API Java implementate.

1.a) Creata Dashboard denominata ‘System Analyzer’.
1.b) Creata API Java (all’interno della cartella ‘APIs Code’) denominata 'packetbeatAPIs'
1.c) Creata API Java (all’interno della cartella ‘APIs Code’) denominata 'filebeatAPIs'
	Dato che il beat (filebeat) utilizzato per la raccolta dei dati necessari è in forma dockerizzata, in quanto altrimenti non sarebbe stato possibile accedere ai dati relativi ai container, per far si che funzioni correttamente  e che possa comunicare con elasticsearch (in forma non dockerizzata) è necessario:
		- Decommentare la riga #55 (‘network.host: 0.0.0.0’) nel file ‘elasticsearch.yml’ in ‘ES Beats/elasticsearch-6.7.1/config’
		- Inserire l’IP corretto nella riga #61 (hosts: [“yourIP:9200"]) nel file ‘filebeat.yml’ in ‘ES Beats/filebeat-6.7.2/filebeat’
		- Inserire l’IP corretto nella riga #1 (‘host’) nel file ‘config.properties’ in ‘APIs Code/filebeatAPIs/resources’

2.a) Creata API Java (all’interno della cartella ‘APIs Code’) denominata 'AverageAPIs'
2.b) Creata API Java (all’interno della cartella ‘APIs Code’) denominata 'metricbeatAPIs'
